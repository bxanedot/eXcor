import Foundation
import JavaScriptCore
import Dispatch

struct PlayerJSOutput: Sendable {
    let preparedPlayer: String?
    let signatures: [String: String]
    let throttling: [String: String]
}

enum PlayerJSTransformer {
    private static let evaluator = PlayerJSEvaluator()

    static func solve(playerScript: String?, preparedPlayer: String?, signatures: [String], throttling: [String]) async throws -> PlayerJSOutput {
        guard playerScript != nil || preparedPlayer != nil else { throw ExcorError.missingPlayerScript }
        guard signatures.count + throttling.count <= 100,
              (playerScript?.utf8.count ?? preparedPlayer?.utf8.count ?? 0) <= 8 * 1024 * 1024,
              (signatures + throttling).allSatisfy({ $0.utf8.count <= 4_096 })
        else { throw ExcorError.oversizedResponse }

        var requests = [[String: Any]]()
        if !signatures.isEmpty { requests.append(["type": "sig", "challenges": signatures]) }
        if !throttling.isEmpty { requests.append(["type": "n", "challenges": throttling]) }
        var input: [String: Any] = ["requests": requests]
        if let preparedPlayer {
            input["type"] = "preprocessed"
            input["preprocessed_player"] = preparedPlayer
        } else if let playerScript {
            input["type"] = "player"
            input["player"] = playerScript
            input["output_preprocessed"] = true
        }

        let inputJSON = try JSONSerialization.data(withJSONObject: input, options: [.sortedKeys])
        guard let inputString = String(data: inputJSON, encoding: .utf8) else { throw ExcorError.challengeFailed }
        let inputLiteral = try javascriptStringLiteral(inputString)
        let library = try resource("ExcorEjsLib")
        let core = try resource("ExcorEjsCore")
        let urlShim = try resource("ExcorURLShim")
        let program = """
        \(library)
        Object.assign(globalThis, lib.lib);
        globalThis.require = function(name) {
          if (name === 'meriyah' || name === 'astring') return lib.lib[name];
          throw new Error('Unsupported EJS dependency');
        };
        \(urlShim)
        \(core)
        JSON.stringify(jsc.default(JSON.parse(\(inputLiteral))))
        """
        let output = try await evaluator.evaluate(program)
        guard let outputData = output.data(using: .utf8), outputData.count <= 8 * 1024 * 1024,
              let result = try JSONSerialization.jsonObject(with: outputData) as? [String: Any],
              result["type"] as? String == "result"
        else { throw ExcorError.challengeFailed }

        var signatureResults = [String: String]()
        var throttleResults = [String: String]()
        let types = (signatures.isEmpty ? [] : ["sig"]) + (throttling.isEmpty ? [] : ["n"])
        let responses = result["responses"] as? [[String: Any]] ?? []
        for (index, response) in responses.enumerated() where response["type"] as? String == "result" {
            guard let data = response["data"] as? [String: String], let type = types[safe: index] else { continue }
            if type == "sig" { signatureResults.merge(data) { _, new in new } }
            if type == "n" { throttleResults.merge(data) { _, new in new } }
        }
        return PlayerJSOutput(
            preparedPlayer: result["preprocessed_player"] as? String,
            signatures: signatureResults,
            throttling: throttleResults
        )
    }

    static func signatureTimestamp(in script: String) -> Int? {
        guard let expression = try? NSRegularExpression(pattern: #"(?:signatureTimestamp|sts)\s*:\s*([0-9]{5})"#),
              let match = expression.firstMatch(in: script, range: NSRange(script.startIndex..., in: script)),
              let range = Range(match.range(at: 1), in: script)
        else { return nil }
        return Int(script[range])
    }

    private static func resource(_ name: String) throws -> String {
        guard let url = Bundle.module.url(forResource: name, withExtension: "js", subdirectory: "Resources") else { throw ExcorError.challengeFailed }
        let values = try url.resourceValues(forKeys: [.fileSizeKey])
        guard (values.fileSize ?? 0) <= 8 * 1024 * 1024 else { throw ExcorError.oversizedResponse }
        return try String(contentsOf: url, encoding: .utf8)
    }

    private static func javascriptStringLiteral(_ value: String) throws -> String {
        let data = try JSONSerialization.data(withJSONObject: [value], options: [.fragmentsAllowed])
        guard let encoded = String(data: data, encoding: .utf8), encoded.count >= 2 else { throw ExcorError.challengeFailed }
        return String(encoded.dropFirst().dropLast())
    }
}

private final class PlayerJSEvaluation: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<String, Error>?
    private var finished = false

    init(continuation: CheckedContinuation<String, Error>) {
        self.continuation = continuation
    }

    func finish(_ result: Result<String, Error>) {
        lock.lock()
        guard !finished else { lock.unlock(); return }
        finished = true
        let continuation = self.continuation
        self.continuation = nil
        lock.unlock()
        continuation?.resume(with: result)
    }
}

actor PlayerJSEvaluator {
    private struct Waiter {
        let id: UUID
        let continuation: CheckedContinuation<Void, Error>
    }

    private let queue = DispatchQueue(label: "com.bxane.eXcor.player-js")
    private let timeout: TimeInterval
    private var running = false
    private var disabledError: ExcorError?
    private var waiters = [Waiter]()
    private let maximumWaiters = 16

    init(timeout: TimeInterval = 10) { self.timeout = timeout }

    func evaluate(_ program: String) async throws -> String {
        try await acquire()
        do {
            let output = try await runWithTimeout(program)
            release()
            return output
        } catch {
            let timedOut = (error as? ExcorError).map { error in
                if case .playerScriptTimedOut = error { return true }
                return false
            } ?? false
            release(timedOut: timedOut)
            throw error
        }
    }

    private func runWithTimeout(_ program: String) async throws -> String {
        let workQueue = queue
        let timeLimit = timeout
        return try await withCheckedThrowingContinuation { continuation in
            let evaluation = PlayerJSEvaluation(continuation: continuation)
            let timeoutWork = DispatchWorkItem {
                evaluation.finish(.failure(ExcorError.playerScriptTimedOut))
            }
            workQueue.async {
                let result: Result<String, Error>
                do { result = .success(try Self.run(program)) }
                catch { result = .failure(error) }
                evaluation.finish(result)
                timeoutWork.cancel()
            }
            DispatchQueue.global(qos: .userInitiated).asyncAfter(deadline: .now() + timeLimit, execute: timeoutWork)
        }
    }

    private func acquire() async throws {
        if let disabledError { throw disabledError }
        try Task.checkCancellation()
        guard running else { running = true; return }
        guard waiters.count < maximumWaiters else { throw ExcorError.challengeFailed }

        let id = UUID()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                waiters.append(Waiter(id: id, continuation: continuation))
            }
        } onCancel: {
            Task { await self.cancelWaiter(id) }
        }
        if Task.isCancelled {
            release()
            throw CancellationError()
        }
    }

    private func cancelWaiter(_ id: UUID) {
        guard let index = waiters.firstIndex(where: { $0.id == id }) else { return }
        waiters.remove(at: index).continuation.resume(throwing: CancellationError())
    }

    private func release(timedOut: Bool = false) {
        if timedOut { disabledError = .playerScriptTimedOut }
        if let disabledError {
            running = false
            waiters.forEach { $0.continuation.resume(throwing: disabledError) }
            waiters.removeAll()
        } else if waiters.isEmpty {
            running = false
        } else {
            waiters.removeFirst().continuation.resume()
        }
    }

    private static func run(_ program: String) throws -> String {
        let context = JSContext()
        var caughtException: String?
        context?.exceptionHandler = { _, exception in caughtException = exception?.toString() }
        guard let context, let output = context.evaluateScript(program)?.toString(), caughtException == nil else {
            throw ExcorError.challengeFailed
        }
        return output
    }
}

private extension Collection {
    subscript(safe index: Index) -> Element? { indices.contains(index) ? self[index] : nil }
}
