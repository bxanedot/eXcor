import Foundation
@testable import eXcorKit

enum CleanroomFixture {
    static func read(_ name: String) throws -> String {
        let source = URL(fileURLWithPath: #filePath)
        let root = source.deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let url = root.appendingPathComponent("fixtures/cleanroom/\(name)")
        return try String(contentsOf: url, encoding: .utf8)
    }
}

final class ExcorRequestGate: @unchecked Sendable {
    private let lock = NSLock()
    private let started = DispatchSemaphore(value: 0)
    private let released = DispatchSemaphore(value: 0)
    private var hasBlocked = false

    func suspendResponse() -> (Int, Data) {
        lock.lock()
        let shouldBlock = !hasBlocked
        hasBlocked = true
        lock.unlock()
        guard shouldBlock else { return (503, Data()) }
        started.signal()
        _ = released.wait(timeout: .now() + 10)
        return (503, Data())
    }

    func waitUntilStarted() async -> Bool {
        await Task.detached { self.started.wait(timeout: .now() + 5) == .success }.value
    }

    func release() { released.signal() }
}

final class ExcorRequestRecorder: @unchecked Sendable {
    private let lock = NSLock()
    private var recorded = [URLRequest]()

    func append(_ request: URLRequest) {
        lock.lock()
        recorded.append(request)
        lock.unlock()
    }

    func requests() -> [URLRequest] {
        lock.lock()
        defer { lock.unlock() }
        return recorded
    }
}

struct TestStore: ExcorConfigStore {
    let enabled: Bool
    init(enabled: Bool = true) { self.enabled = enabled }
    func getString(_ key: String) -> String? { nil }
    func getLong(_ key: String) -> Int64? { nil }
    func putString(_ key: String, _ value: String) {}
    func putLong(_ key: String, _ value: Int64) {}
}

final class TestClock: @unchecked Sendable {
    private let lock = NSLock()
    private var value = Date(timeIntervalSince1970: 1_800_000_000)
    func now() -> Date { lock.lock(); defer { lock.unlock() }; return value }
    func advance(seconds: TimeInterval) { lock.lock(); value = value.addingTimeInterval(seconds); lock.unlock() }
}

struct TestHost: ExcorKitHost {
    let configStore: any ExcorConfigStore
    let clock: TestClock
    let cookie: String?
    let visitorData: String?
    let region: String
    let language: String
    let sessionOverride: URLSession?
    var now: @Sendable () -> Date { { self.clock.now() } }
    var urlSession: URLSession? { sessionOverride }
    var tokenProvider: (any PoTokenProvider)? { nil }

    init(cookie: String? = nil, enabled: Bool = false, clock: TestClock = TestClock(), urlSession: URLSession? = nil, visitorData: String? = "visitor", region: String = "US", language: String = "en") {
        self.cookie = cookie
        self.visitorData = visitorData
        self.region = region
        self.language = language
        self.configStore = TestStore(enabled: enabled)
        self.clock = clock
        self.sessionOverride = urlSession
    }

    func session() async -> ExcorSession {
        ExcorSession(cookie: cookie, visitorData: visitorData, region: region, language: language)
    }

    func log(_ level: ExcorLogLevel, _ message: String, trackId: String?) {}
}

struct TestTokenProvider: PoTokenProvider {
    let capabilities = PoTokenProviderCapabilities(providers: [.external])
    func poToken(videoId: String, visitorData: String, cookie: String?) async throws -> PoTokenResult? { nil }
    func prewarm(cookie: String?) async {}
    func invalidateAttestation() async {}
    func close() async {}
}
