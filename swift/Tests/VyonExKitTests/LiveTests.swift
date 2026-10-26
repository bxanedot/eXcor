import XCTest
import VyonExKit

/// Opt-in: VYONEX_LIVE=1 swift test
final class LiveTests: XCTestCase {
    private final class Host: VyonExKitHost {
        func session() async -> VyonExSession { VyonExSession(cookie: nil, visitorData: nil, region: "US", language: "en") }
        let configStore: VyonExConfigStore = FakeStore(enabled: true)
        var now: Date { Date() }
        func log(_ level: VyonExLogLevel, _ message: String, trackId: String?) { print("\(level) [\(trackId ?? "")] \(message)") }
    }

    func testExtractsRangeFetchableStreams() async throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["VYONEX_LIVE"] == "1", "set VYONEX_LIVE=1")
        let kit = VyonExKit(host: Host())
        // Rick Astley "Never Gonna Give You Up", Luis Fonsi "Despacito", Ed Sheeran "Shape of You".
        for videoId in ["dQw4w9WgXcQ", "kJQP7kiw5Fk", "JGwWNGJdvx8"] {
            let extracted = try await kit.extract(videoId: videoId, maxKbps: 160)
            let stream = try XCTUnwrap(extracted, videoId)
            print("\(videoId) -> \(stream)")
            var request = URLRequest(url: URL(string: stream.url)!)
            request.setValue("bytes=0-1023", forHTTPHeaderField: "Range")
            for (name, value) in stream.headers { request.setValue(value, forHTTPHeaderField: name) }
            let (_, response) = try await URLSession.shared.data(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            XCTAssertTrue(status == 200 || status == 206, "\(videoId): HTTP \(status)")
        }
    }
}
