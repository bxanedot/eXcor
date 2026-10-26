import XCTest
@testable import VyonExKit

final class DecodeTests: XCTestCase {
    // Expected answers were checked live on 2026-10-26: the decoded URLs answered 206, undecoded ones 403.
    func testDecodesSignatureAndNAgainstCapturedPlayer() async throws {
        let code = try fixture("player-dce23b30.js")
        XCTAssertEqual(signatureTimestamp(code), 20746)
        let js = try await onJSThread { try PlayerJS(id: "dce23b30", sts: 20746, prepared: try preparePlayer(code)) }
        let expected = try fixtureJSON("challenges-dce23b30.json")
        let n = expected["n"] as! [String: String]
        let sig = expected["sig"] as! [String: String]
        for (challenge, answer) in n { XCTAssertEqual(try js.solve("n", challenge), answer) }
        for (challenge, answer) in sig { XCTAssertEqual(try js.solve("sig", challenge), answer) }

        var urls: [String] = []
        for format in audioFormats(of: try fixtureJSON("player-response-tv_samsung.json")) {
            let url = try await playableURL(format) { try js.solve($0, $1) }
            urls.append(try XCTUnwrap(url))
        }
        XCTAssertEqual(urls.count, 4)
        for url in urls {
            XCTAssertTrue(isAllowedURL(url), url)
            XCTAssertTrue(url.contains("&n=\(n.values.first!)&"), url)
            XCTAssertTrue(sig.values.contains { url.contains("&sig=\(encodeParameter($0))") }, url)
        }
    }
}
