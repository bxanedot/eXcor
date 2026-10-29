import XCTest
import Foundation
@testable import eXcorKit

final class ClientAndPlayerTests: XCTestCase {
    func testLoggedOutAndAuthenticatedClientOrders() {
        XCTAssertEqual(clientOrder(for: ExcorSession(cookie: nil, visitorData: nil, region: "US", language: "en")).map(\.name), ["TVHTML5", "VISIONOS", "WEB"])
        XCTAssertEqual(clientOrder(for: ExcorSession(cookie: "cookie", visitorData: nil, region: "US", language: "en")).map(\.name), ["WEB_EMBEDDED_PLAYER", "TVHTML5", "WEB"])
    }

    func testClientSkipMatchesNamesAndProfilesAndCapsAttempts() {
        let session = ExcorSession(cookie: nil, visitorData: nil, region: "US", language: "en")
        XCTAssertEqual(eligibleClients(for: session, skipped: ["visionos"], excluded: ["web"]).map(\.profileId), ["tv_samsung"])
        XCTAssertLessThanOrEqual(eligibleClients(for: session, skipped: [], excluded: []).count, 3)
    }

    func testPlayerRequestUsesSessionLocaleAndPoToken() throws {
        let identity = clientOrder(for: ExcorSession(cookie: nil, visitorData: "visitor", region: "CA", language: "fr")).last!
        let token = PoTokenResult(playerRequestToken: "request", streamingDataToken: "stream", visitorData: "token-visitor")
        let body = makePlayerRequestBody(videoId: "abc", session: ExcorSession(cookie: "cookie", visitorData: "visitor", region: "CA", language: "fr"), identity: identity, token: token, signatureTimestamp: 12345)
        let context = try XCTUnwrap(body["context"] as? [String: Any])
        let client = try XCTUnwrap(context["client"] as? [String: Any])
        XCTAssertEqual(client["hl"] as? String, "fr")
        XCTAssertEqual(client["gl"] as? String, "CA")
        let integrity = try XCTUnwrap(body["serviceIntegrityDimensions"] as? [String: String])
        XCTAssertEqual(integrity["poToken"], "request")
        XCTAssertEqual(body["videoId"] as? String, "abc")
        let headers = playerRequestHeaders(session: ExcorSession(cookie: "cookie", visitorData: "visitor", region: "CA", language: "fr"), identity: identity)
        XCTAssertEqual(headers["X-YouTube-Client-Name"], String(identity.id))
        XCTAssertEqual(headers["Cookie"], "cookie")
        XCTAssertEqual(headers["X-Goog-Visitor-Id"], "visitor")
        XCTAssertEqual(headers["Accept-Language"], "fr-CA,fr;q=0.9")
    }

    func testMalformedPlayerDataIsRejected() {
        XCTAssertThrowsError(try decodePlayerResponse(Data("not-json".utf8)))
    }

    func testSignatureTimestampReadsFiveDigitPlayerValue() {
        XCTAssertEqual(PlayerJSTransformer.signatureTimestamp(in: "x={signatureTimestamp:12345};"), 12345)
        XCTAssertNil(PlayerJSTransformer.signatureTimestamp(in: "x={signatureTimestamp:1234};"))
    }

    func testPlaybackQueryRetainsYouTubeParametersAndAddsTokens() throws {
        let base = "https://rr1.googlevideo.com/videoplayback?c=WEB&n=old&range=0-1023"
        let url = try XCTUnwrap(playableMediaURL(base: base, signature: nil, encryptedSignature: "cipher", signatureParameter: "sig", signatures: ["cipher": "decoded"], throttling: ["old": "new"], streamingToken: "stream-token"))
        let parts = try XCTUnwrap(URLComponents(string: url))
        let query = queryParameters(try XCTUnwrap(parts.percentEncodedQuery))
        XCTAssertEqual(query["c"], "WEB")
        XCTAssertEqual(query["range"], "0-1023")
        XCTAssertEqual(query["n"], "new")
        XCTAssertEqual(query["sig"], "decoded")
        XCTAssertEqual(query["pot"], "stream-token")
    }

    func testSyntheticPlayerSignatureAndThrottleTransforms() async throws {
        let output = try await PlayerJSTransformer.solve(
            playerScript: nil,
            preparedPlayer: "_result.sig = sig => sig.split('').reverse().join(''); _result.n = n => n + 'x';",
            signatures: ["abcdef"],
            throttling: ["123"]
        )
        XCTAssertEqual(output.signatures["abcdef"], "fedcba")
        XCTAssertEqual(output.throttling["123"], "123x")
    }

    func testPlayerTransformCanUseURLSearchParams() async throws {
        let output = try await PlayerJSTransformer.solve(
            playerScript: nil,
            preparedPlayer: "_result.n = n => new URL('https://rr1.googlevideo.com/videoplayback?n=' + n).searchParams.get('n');",
            signatures: [],
            throttling: ["abc"]
        )
        XCTAssertEqual(output.throttling["abc"], "abc")
    }

    func testEvaluatorReturnsTimeoutForSlowScript() async {
        let evaluator = PlayerJSEvaluator(timeout: 0.1)
        do {
            _ = try await evaluator.evaluate("const deadline = Date.now() + 500; while (Date.now() < deadline) {} 1")
            XCTFail("A slow player script must be bounded")
        } catch let error as ExcorError {
            XCTAssertEqual(error.errorDescription, "eXcor: player script exceeded its execution time limit")
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testEvaluatorSerializesConcurrentScripts() async throws {
        let evaluator = PlayerJSEvaluator(timeout: 2)
        async let first = evaluator.evaluate("1 + 1")
        async let second = evaluator.evaluate("2 + 2")
        let results = try await (first, second)
        XCTAssertEqual(results.0, "2")
        XCTAssertEqual(results.1, "4")
    }
}
