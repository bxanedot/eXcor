import Foundation
import JavaScriptCore

struct VyonExError: Error, CustomStringConvertible {
    let description: String
    init(_ description: String) { self.description = description }
}

private func ejsScript(_ name: String) throws -> String {
    guard let url = Bundle.module.url(forResource: name, withExtension: "js", subdirectory: "ejs") else {
        throw VyonExError("missing ejs/\(name).js")
    }
    return try String(contentsOf: url, encoding: .utf8)
}

private func evaluate(_ context: JSContext, _ script: String) throws -> JSValue? {
    let value = context.evaluateScript(script)
    if let exception = context.exception { throw VyonExError("JavaScript: \(exception)") }
    return value
}

/// Parsing the player recurses deeply and secondary threads get only 512 KB of stack, so all
/// JavaScript runs on a fresh thread with a large one.
func onJSThread<T>(_ body: @escaping () throws -> T) async throws -> T {
    try await withCheckedThrowingContinuation { continuation in
        let thread = Thread { continuation.resume(with: Result { try body() }) }
        thread.stackSize = 16 << 20
        thread.start()
    }
}

/// Signature timestamp the player API expects for this player script.
func signatureTimestamp(_ playerCode: String) -> Int? {
    let re = try! NSRegularExpression(pattern: #"(?:signatureTimestamp|sts)\s*:\s*(\d{5})"#)
    guard let m = re.firstMatch(in: playerCode, range: NSRange(playerCode.startIndex..., in: playerCode)),
          let r = Range(m.range(at: 1), in: playerCode)
    else { return nil }
    return Int(playerCode[r])
}

/// Turns the raw player script into one that exposes its `n` and `sig` functions, using
/// yt-dlp's EJS solver. Parsing the player is the expensive step, so this is what gets cached.
func preparePlayer(_ playerCode: String) throws -> String {
    guard let context = JSContext() else { throw VyonExError("no JSContext") }
    _ = try evaluate(context, try ejsScript("lib.min") + "\nObject.assign(globalThis, lib);\n" + ejsScript("core.min"))
    context.setObject(playerCode, forKeyedSubscript: "_player" as NSString)
    let result = try evaluate(context, "jsc({type:'player',player:_player,requests:[],output_preprocessed:true}).preprocessed_player")
    guard let prepared = result, prepared.isString, let text = prepared.toString() else { throw VyonExError("no prepared player") }
    return text
}

/// A loaded prepared player. A JSContext must not be used from two threads at once, hence the lock.
final class PlayerJS: @unchecked Sendable {
    let id: String
    let sts: Int?
    private let context: JSContext
    private let lock = NSLock()

    init(id: String, sts: Int?, prepared: String) throws {
        guard let context = JSContext() else { throw VyonExError("no JSContext") }
        _ = try evaluate(context, "var _vx={n:null,sig:null};(function(_result){\n\(prepared)\n})(_vx);")
        self.id = id
        self.sts = sts
        self.context = context
    }

    func solve(_ type: String, _ challenge: String) throws -> String {
        lock.lock()
        defer { lock.unlock() }
        let result = context.objectForKeyedSubscript("_vx")?.objectForKeyedSubscript(type)?.call(withArguments: [challenge])
        if let exception = context.exception {
            context.exception = nil
            throw VyonExError("JavaScript: \(exception)")
        }
        guard let result, result.isString, let text = result.toString() else { throw VyonExError("no \(type) result") }
        return text
    }
}

/// Fetches and prepares player scripts, keeping one version in memory at a time.
actor Players {
    private let http: URLSession
    private var currentId: String?
    private var player: (id: String, task: Task<PlayerJS, Error>)?

    init(http: URLSession) { self.http = http }

    /// The player version YouTube currently serves, found through the iframe API as yt-dlp does.
    func current() async throws -> PlayerJS {
        if let currentId { return try await get(currentId) }
        guard let id = playerId(of: try await fetchText(http, URLRequest(url: URL(string: "\(ytOrigin)/iframe_api")!))) else {
            throw VyonExError("no player version in iframe API")
        }
        currentId = id
        return try await get(id)
    }

    func get(_ id: String) async throws -> PlayerJS {
        if let player, player.id == id { return try await player.task.value }
        // yt-dlp also always uses the main variant, so every page shares one cache entry.
        let http = http
        let task = Task<PlayerJS, Error> {
            let url = URL(string: "\(ytOrigin)/s/player/\(id)/player_ias.vflset/en_US/base.js")!
            let code = try await fetchText(http, URLRequest(url: url))
            return try await onJSThread { try PlayerJS(id: id, sts: signatureTimestamp(code), prepared: try preparePlayer(code)) }
        }
        player = (id, task)
        do {
            return try await task.value
        } catch {
            if self.player?.id == id { self.player = nil }
            throw error
        }
    }

    func invalidate() {
        player = nil
        currentId = nil
    }
}

func fetchText(_ http: URLSession, _ request: URLRequest) async throws -> String {
    let (data, response) = try await http.data(for: request)
    let status = (response as? HTTPURLResponse)?.statusCode ?? 0
    // Only the path: query strings are not logged.
    guard (200..<300).contains(status) else { throw VyonExError("HTTP \(status) for \(request.url?.path ?? "")") }
    return String(decoding: data, as: UTF8.self)
}
