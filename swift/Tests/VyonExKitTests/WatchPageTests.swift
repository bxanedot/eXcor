import XCTest
@testable import VyonExKit

final class WatchPageTests: XCTestCase {
    func testReadsPlayerResponseAndPlayerFromPage() throws {
        let page = try XCTUnwrap(parseWatchPage(try fixture("watch-page-assembled.html")))
        XCTAssertEqual(page.playerId, "dce23b30")
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
}
