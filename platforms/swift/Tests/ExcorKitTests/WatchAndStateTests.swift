import XCTest
import Foundation
@testable import eXcorKit

final class WatchPageTests: XCTestCase {
    func testReadsEmbeddedPlayerResponseAndScriptURL() throws {
        let html = try CleanroomFixture.read("watch-page.html")
        let page = try parseWatchPage(html)
        XCTAssertEqual(page.apiKey, "fixture-key")
        XCTAssertEqual(page.playerResponse.playabilityStatus.status, "OK")
        XCTAssertEqual(page.playerScriptURL?.path, "/s/player/fixture/base.js")
        XCTAssertEqual(page.playerResponse.streamingData?.allFormats.count, 2)
        XCTAssertEqual(audioCandidate(page.playerResponse.streamingData!.allFormats[0])?.format, .m4a)
        XCTAssertEqual(audioCandidate(page.playerResponse.streamingData!.allFormats[0])?.kbps, 127)
    }

    func testRejectsUntrustedPlayerScriptURL() throws {
        let html = try CleanroomFixture.read("watch-page.html").replacingOccurrences(of: "/s/player/fixture/base.js", with: "https://evil.test/player.js")
        XCTAssertThrowsError(try parseWatchPage(html))
    }

    func testRejectsPageWithoutPlayerResponse() {
        XCTAssertThrowsError(try parseWatchPage("<html>no player data</html>"))
    }
}

final class EngineStateTests: XCTestCase {
    func testRefusedURLIsForgottenAndExcluded() async {
        let kit = ExcorKit(host: TestHost(cookie: "cookie"))
        let stream = ExcorStream(videoId: "video", url: "https://rr1.googlevideo.com/videoplayback?c=WEB", kbps: 128, mimeType: "audio/webm", loudnessDb: nil, clientName: "WEB", profileId: "web", headers: ["Cookie": "cookie"])
        await kit.rememberForTesting(stream)

        let headers = await kit.headers(for: stream.url)
        let refusedVideo = await kit.onRefused(url: stream.url)
        let forgottenHeaders = await kit.headers(for: stream.url)
        let excluded = await kit.isExcludedForTesting(videoId: "video", profileId: "web")
        XCTAssertEqual(headers?["Cookie"], "cookie")
        XCTAssertEqual(refusedVideo, "video")
        XCTAssertNil(forgottenHeaders)
        XCTAssertTrue(excluded)
    }

    func testExclusionExpiresAfterTenMinutes() async {
        let clock = TestClock()
        let kit = ExcorKit(host: TestHost(clock: clock))
        await kit.exclude(videoId: "video", profileId: "web")
        let initiallyExcluded = await kit.isExcludedForTesting(videoId: "video", profileId: "web")
        XCTAssertTrue(initiallyExcluded)
        clock.advance(seconds: 601)
        let expired = await kit.isExcludedForTesting(videoId: "video", profileId: "web")
        XCTAssertFalse(expired)
    }

    func testSessionChangeClearsRememberedStreamsAndExclusions() async {
        let kit = ExcorKit(host: TestHost())
        let stream = ExcorStream(videoId: "video", url: "https://rr1.googlevideo.com/videoplayback", kbps: 128, mimeType: "audio/webm", loudnessDb: nil, clientName: "WEB", profileId: "web", headers: [:])
        await kit.rememberForTesting(stream)
        await kit.exclude(videoId: "video", profileId: "web")
        await kit.onSessionChanged()

        let headers = await kit.headers(for: stream.url)
        let excluded = await kit.isExcludedForTesting(videoId: "video", profileId: "web")
        XCTAssertNil(headers)
        XCTAssertFalse(excluded)
    }

    func testRememberedStreamCacheClearsAllWhenSixtyFifthIsAdded() async {
        let kit = ExcorKit(host: TestHost())
        for index in 0..<65 {
            let stream = ExcorStream(videoId: "video-\(index)", url: "https://rr1.googlevideo.com/videoplayback?itag=\(index)", kbps: 1, mimeType: "audio/mp4", loudnessDb: nil, clientName: "WEB", profileId: "web", headers: [:])
            await kit.rememberForTesting(stream)
        }
        let old = await kit.headers(for: "https://rr1.googlevideo.com/videoplayback?itag=0")
        let newest = await kit.headers(for: "https://rr1.googlevideo.com/videoplayback?itag=64")
        XCTAssertNil(old)
        XCTAssertNotNil(newest)
    }

    func testCancellationReturnsNoCandidatesAndKeepsTaskCancelled() async {
        let kit = ExcorKit(host: TestHost())
        let task = Task {
            let candidates = await kit.audioCandidates(videoId: "video")
            return (Task.isCancelled, candidates)
        }
        task.cancel()
        let (cancelled, candidates) = await task.value
        XCTAssertTrue(cancelled)
        XCTAssertTrue(candidates.isEmpty)
    }
}
