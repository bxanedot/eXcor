import XCTest
@testable import VyonExKit

/// Serves the captured fixtures; media answers depend on the URL's `c` client.
class FixtureProtocol: URLProtocol {
    class var refusedClients: Set<String> { [] }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func stopLoading() {}

    override func startLoading() {
        let url = request.url!
        var status = 200
        var body = Data()
        do {
            if url.path == "/iframe_api" {
                body = Data(try fixture("iframe_api.js").utf8)
            } else if url.path.hasPrefix("/s/player/dce23b30/") {
                body = Data(try fixture("player-dce23b30.js").utf8)
            } else if url.path == "/youtubei/v1/player" {
                let tv = request.value(forHTTPHeaderField: "X-YouTube-Client-Name") == "7"
                body = Data(try fixture(tv ? "player-response-tv_samsung.json" : "player-response-visionos.json").utf8)
            } else if url.host?.hasSuffix(".googlevideo.com") == true {
                // The probe asks for one byte with the stream's own headers.
                guard request.value(forHTTPHeaderField: "Range") == "bytes=0-0",
                      request.value(forHTTPHeaderField: "User-Agent") != nil
                else { throw URLError(.badURL) }
                let client = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "c" }?.value
                status = Self.refusedClients.contains(client ?? "") ? 403 : 206
                body = Data("x".utf8)
            } else {
                throw URLError(.unsupportedURL)
            }
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
            return
        }
        client?.urlProtocol(self, didReceive: HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil)!,
                            cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: body)
        client?.urlProtocolDidFinishLoading(self)
    }
}

final class AnswerAll: FixtureProtocol {}
final class RefuseVisionOS: FixtureProtocol { override class var refusedClients: Set<String> { ["VISIONOS"] } }
final class RefuseTV: FixtureProtocol { override class var refusedClients: Set<String> { ["TVHTML5"] } }

/// BRIEF 4.2: only a format "whose URL works" may be returned.
final class ProbeTests: XCTestCase {
    private let host = FakeHost()

    private func kit(_ protocolClass: FixtureProtocol.Type) -> VyonExKit {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [protocolClass]
        return VyonExKit(host: host, session: URLSession(configuration: config))
    }

    func testRefusedURLIsNotReturnedAndItsProfileIsExcluded() async throws {
        let kit = kit(RefuseVisionOS.self)
        let stream = try await kit.extractPrimary(videoId: "dQw4w9WgXcQ", maxKbps: 160, skipClients: ["TV_SAMSUNG"], requireM4a: false)
        XCTAssertNil(stream)
        XCTAssertTrue(kit.isExcluded("dQw4w9WgXcQ", "VISIONOS"))
        XCTAssertTrue(host.logs.contains("warn VyonEx: VISIONOS URL refused (HTTP 403)"), "\(host.logs)")
    }

    func testAnsweringURLIsReturnedAndRemembered() async throws {
        let kit = kit(AnswerAll.self)
        let extracted = try await kit.extractPrimary(videoId: "dQw4w9WgXcQ", maxKbps: 160, skipClients: ["TV_SAMSUNG"], requireM4a: false)
        let stream = try XCTUnwrap(extracted)
        XCTAssertEqual(stream.profileId, "VISIONOS")
        // VISIONOS has no response-wide loudnessDb; itag 251 carries its own.
        XCTAssertEqual(stream.loudnessDb, 0.97999954)
        XCTAssertEqual(kit.headers(for: stream.url), stream.headers)
        XCTAssertFalse(kit.isExcluded("dQw4w9WgXcQ", "VISIONOS"))
    }

    func testRefusedIdentityFallsThroughToTheNext() async throws {
        // TV_SAMSUNG's ciphered URLs are decoded with the captured player (JavaScriptCore) before being probed.
        let kit = kit(RefuseTV.self)
        let extracted = try await kit.extractPrimary(videoId: "dQw4w9WgXcQ", maxKbps: 160, skipClients: [], requireM4a: false)
        let stream = try XCTUnwrap(extracted, "\(host.logs)")
        XCTAssertEqual(stream.profileId, "VISIONOS")
        XCTAssertTrue(kit.isExcluded("dQw4w9WgXcQ", "TV_SAMSUNG"))
    }
}
