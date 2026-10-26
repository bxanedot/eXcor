import Foundation
@testable import VyonExKit

// swift/Tests/VyonExKitTests/Support.swift -> repository root -> fixtures/ (shared with the Kotlin tests).
let fixtures = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    .appendingPathComponent("fixtures")

func fixture(_ name: String) throws -> String {
    try String(contentsOf: fixtures.appendingPathComponent(name), encoding: .utf8)
}

func fixtureJSON(_ name: String) throws -> JSONObject {
    try JSONSerialization.jsonObject(with: Data(fixture(name).utf8)) as! JSONObject
}

/// Fails every request, so tests that must stay offline provably do.
final class OfflineProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() { client?.urlProtocol(self, didFailWithError: URLError(.notConnectedToInternet)) }
    override func stopLoading() {}
}

func offlineSession() -> URLSession {
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [OfflineProtocol.self]
    return URLSession(configuration: config)
}

struct FakeStore: VyonExConfigStore {
    let enabled: Bool
    func getString(_ key: String) -> String? { nil }
    func getLong(_ key: String) -> Int64? { nil }
    func putString(_ key: String, _ value: String) {}
    func putLong(_ key: String, _ value: Int64) {}
}

final class FakeHost: VyonExKitHost, @unchecked Sendable {
    private let lock = NSLock()
    private var _now = Date(timeIntervalSince1970: 1_000_000)
    private var _enabled = true
    private var _sessionCalls = 0
    private var _logs: [String] = []

    var now: Date {
        get { sync { _now } }
        set { sync { _now = newValue } }
    }
    var enabled: Bool {
        get { sync { _enabled } }
        set { sync { _enabled = newValue } }
    }
    var sessionCalls: Int { sync { _sessionCalls } }
    var logs: [String] { sync { _logs } }
    var configStore: VyonExConfigStore { FakeStore(enabled: enabled) }

    func session() async -> VyonExSession {
        sync { _sessionCalls += 1 }
        return VyonExSession(cookie: nil, visitorData: nil, region: "US", language: "en")
    }

    func log(_ level: VyonExLogLevel, _ message: String, trackId: String?) {
        sync { _logs.append("\(level) \(message)") }
    }

    private func sync<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }
}
