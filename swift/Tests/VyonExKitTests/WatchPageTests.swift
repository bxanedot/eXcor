import XCTest
@testable import VyonExKit

final class WatchPageTests: XCTestCase {
    // A real logged-out page, captured on the owner's network on 2026-10-27 (see fixtures/README.md).
    func testReadsPlayerResponseAndPlayerFromPage() throws {
        let page = try XCTUnwrap(parseWatchPage(try fixture("watch-page.html")))
        XCTAssertEqual(page.playerId, "6b2fce74")
        XCTAssertEqual(at(page.playerResponse, "playabilityStatus", "status") as? String, "OK")
        XCTAssertEqual(at(page.playerResponse, "videoDetails", "videoId") as? String, "dQw4w9WgXcQ")
        XCTAssertEqual(audioFormats(of: page.playerResponse).map { int($0["itag"]) }, [140, 249, 250, 251])
    }

    func testBlockedPageHasNoPlayerResponse() throws {
        XCTAssertNil(parseWatchPage(try fixture("watch-page-blocked.html")))
    }

    func testReadsPlayerVersionFromIframeAPI() throws {
        XCTAssertEqual(playerId(of: try fixture("iframe_api.js")), "dce23b30")
    }

    func testSabrOnlyResponsesHaveNoURLs() throws {
        // WEB without a PO token: audio formats exist but carry neither url nor signatureCipher.
        let formats = audioFormats(of: try fixtureJSON("player-response-web.json"))
        XCTAssertFalse(formats.isEmpty)
        XCTAssertTrue(formats.allSatisfy { $0["url"] == nil && $0["signatureCipher"] == nil })
    }

    func testSabrOnlyPageYieldsNoStreamAndNoCandidates() async throws {
        // The real page's audio formats have neither url nor signatureCipher (SABR-only), which the brief rules out.
        let host = FakeHost()
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [AnswerAll.self]
        let kit = VyonExKit(host: host, session: URLSession(configuration: config))
        let stream = try await kit.watchPageStream(videoId: "dQw4w9WgXcQ", maxKbps: 160, skipClients: [], requireM4a: false)
        let candidates = await kit.audioCandidates(videoId: "dQw4w9WgXcQ")
        XCTAssertNil(stream)
        XCTAssertEqual(candidates, [])
        XCTAssertFalse(host.logs.contains { $0.hasPrefix("warn") }, "\(host.logs)")
    }
}
