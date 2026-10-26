import XCTest
@testable import VyonExKit

final class RedactionTests: XCTestCase {
    private let secrets = ["SECRET-COOKIE", "SECRET-VISITOR", "SECRET-PLAYER-TOKEN", "SECRET-GVS-TOKEN"]

    private func assertNoSecrets(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        for secret in secrets { XCTAssertFalse(text.contains(secret), text, file: file, line: line) }
    }

    func testPoTokenResultPrintsOnlyPresence() {
        let token = PoTokenResult(playerRequestToken: "SECRET-PLAYER-TOKEN", streamingDataToken: "", visitorData: "SECRET-VISITOR")
        assertNoSecrets("\(token)")
        assertNoSecrets(String(reflecting: token))
        XCTAssertEqual("\(token)", "PoTokenResult(playerRequestToken: present, streamingDataToken: missing, visitorData: present)")
    }

    func testSessionHidesCookieAndVisitorData() {
        let session = VyonExSession(cookie: "SID=SECRET-COOKIE", visitorData: "SECRET-VISITOR", region: "US", language: "en")
        assertNoSecrets("\(session)")
        XCTAssertEqual("\(session)", "VyonExSession(cookie: present, visitorData: present, region: US, language: en)")
    }

    func testStreamHidesURLTokenAndCookieHeader() {
        let stream = VyonExStream(
            videoId: "dQw4w9WgXcQ", url: "https://r1.googlevideo.com/videoplayback?pot=SECRET-GVS-TOKEN", kbps: 128,
            mimeType: "audio/webm; codecs=\"opus\"", loudnessDb: -1.5, clientName: "WEB", profileId: "WEB",
            headers: ["User-Agent": "ua", "Cookie": "SID=SECRET-COOKIE"]
        )
        assertNoSecrets("\(stream)")
        assertNoSecrets(String(reflecting: stream))
    }
}
