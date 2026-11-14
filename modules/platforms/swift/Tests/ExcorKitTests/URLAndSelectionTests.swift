import Foundation
import XCTest
@testable import eXcorKit

final class URLPolicyTests: XCTestCase {
    func testAcceptsOnlyHTTPSPlaybackURLsOnAllowedHostsAndPath() {
        XCTAssertTrue(isAllowedPlaybackURL("https://rr1.googlevideo.com/videoplayback?c=WEB"))
        XCTAssertTrue(isAllowedPlaybackURL("https://youtube.com/videoplayback?c=WEB"))

        XCTAssertFalse(isAllowedPlaybackURL("http://rr1.googlevideo.com/videoplayback"))
        XCTAssertFalse(isAllowedPlaybackURL("https://rr1.googlevideo.com:444/videoplayback"))
        XCTAssertFalse(isAllowedPlaybackURL("https://user@rr1.googlevideo.com/videoplayback"))
        XCTAssertFalse(isAllowedPlaybackURL("https://googlevideo.com.evil.test/videoplayback"))
        XCTAssertFalse(isAllowedPlaybackURL("https://rr1.googlevideo.com/notplayback"))
    }

    func testPlayerAndRedirectTransportOnlyUseHTTPSYouTubeHosts() throws {
        XCTAssertTrue(isAllowedYouTubeTransportURL(try XCTUnwrap(URL(string: "https://www.youtube.com/youtubei/v1/player"))))
        XCTAssertTrue(isAllowedYouTubeTransportURL(try XCTUnwrap(URL(string: "https://rr1.googlevideo.com/videoplayback")), includeMediaHost: true))
        XCTAssertFalse(isAllowedYouTubeTransportURL(try XCTUnwrap(URL(string: "https://api.example.test/")), includeMediaHost: true))
        XCTAssertFalse(isAllowedYouTubeTransportURL(try XCTUnwrap(URL(string: "http://www.youtube.com/watch")), includeMediaHost: true))
    }

    func testCrossAuthorityRedirectStripsSessionCredentials() throws {
        let source = try XCTUnwrap(URL(string: "https://www.youtube.com/watch"))
        let destination = try XCTUnwrap(URL(string: "https://rr1.googlevideo.com/videoplayback"))
        var request = URLRequest(url: destination)
        request.setValue("SID=secret", forHTTPHeaderField: "Cookie")
        request.setValue("legacy=secret", forHTTPHeaderField: "Cookie2")
        request.setValue("visitor-secret", forHTTPHeaderField: "X-Goog-Visitor-Id")
        request.setValue("Bearer secret", forHTTPHeaderField: "Authorization")
        request.setValue("Basic secret", forHTTPHeaderField: "Proxy-Authorization")
        request.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")

        let redirected = try XCTUnwrap(safeYouTubeRedirectRequest(request, from: source))
        XCTAssertFalse(redirected.httpShouldHandleCookies)
        XCTAssertNil(redirected.value(forHTTPHeaderField: "Cookie"))
        XCTAssertNil(redirected.value(forHTTPHeaderField: "Cookie2"))
        XCTAssertNil(redirected.value(forHTTPHeaderField: "X-Goog-Visitor-Id"))
        XCTAssertNil(redirected.value(forHTTPHeaderField: "Authorization"))
        XCTAssertNil(redirected.value(forHTTPHeaderField: "Proxy-Authorization"))
        XCTAssertEqual(redirected.value(forHTTPHeaderField: "User-Agent"), "Mozilla/5.0")
    }

    func testSameAuthorityRedirectKeepsSessionCredentialsAndForeignRedirectIsDenied() throws {
        let source = try XCTUnwrap(URL(string: "https://www.youtube.com/watch"))
        var sameAuthority = URLRequest(url: try XCTUnwrap(URL(string: "https://www.youtube.com/feed")))
        sameAuthority.setValue("SID=secret", forHTTPHeaderField: "Cookie")
        XCTAssertEqual(safeYouTubeRedirectRequest(sameAuthority, from: source)?.value(forHTTPHeaderField: "Cookie"), "SID=secret")

        let foreign = URLRequest(url: try XCTUnwrap(URL(string: "https://evil.example/videoplayback")))
        XCTAssertNil(safeYouTubeRedirectRequest(foreign, from: source))
    }
}

final class SelectionTests: XCTestCase {
    func testRequireM4aUsesHighestRankedM4aOnly() {
        let webm = format("audio/webm; codecs=\"opus\"", 256_000, channels: 2)
        let lowM4a = format("audio/mp4; codecs=\"mp4a.40.2\"", 96_000, channels: 2)
        let highM4a = format("audio/mp4; codecs=\"mp4a.40.2\"", 160_000, channels: 2)

        XCTAssertEqual(selectAudioFormat([webm, lowM4a, highM4a], maxKbps: 192, requireM4a: true), highM4a)
    }

    func testLowQualityPrefersLowestBitrateM4a() {
        let webm = format("audio/webm; codecs=\"opus\"", 32_000)
        let lowM4a = format("audio/mp4; codecs=\"mp4a.40.2\"", 64_000)
        let highM4a = format("audio/mp4; codecs=\"mp4a.40.2\"", 128_000)

        XCTAssertEqual(selectAudioFormat([webm, highM4a, lowM4a], maxKbps: 64, requireM4a: false), lowM4a)
    }

    func testAutoUsesWebmThenEffectiveBitrateAndCappedSampleRate() {
        let mp4 = format("audio/mp4; codecs=\"mp4a.40.2\"", 320_000)
        let webmMono = format("audio/webm; codecs=\"opus\"", 120_000, channels: 1, sampleRate: 44_100)
        let webmStereo = format("audio/webm; codecs=\"opus\"", 95_000, channels: 2, sampleRate: 96_000)

        XCTAssertEqual(selectAudioFormat([mp4, webmMono, webmStereo], maxKbps: 192, requireM4a: false), webmStereo)
    }

    func testMaxKbpsAbove64IsNotABitrateCap() {
        let ordinary = format("audio/webm; codecs=\"opus\"", 192_000, channels: 2)
        let high = format("audio/webm; codecs=\"opus\"", 320_000, channels: 2)

        XCTAssertEqual(selectAudioFormat([ordinary, high], maxKbps: 96, requireM4a: false), high)
    }

    func testSelectionRejectsAudioFormatsOutsidePlaybackAllowlist() {
        let unsafe = ExcorPlaybackFormat(url: "https://example.test/videoplayback", mimeType: "audio/mp4", bitrate: 128_000)
        XCTAssertNil(selectAudioFormat([unsafe], maxKbps: 128, requireM4a: false))
    }
}

private func format(_ mime: String, _ bitrate: Int, channels: Int? = nil, sampleRate: Int? = nil) -> ExcorPlaybackFormat {
    ExcorPlaybackFormat(
        url: "https://rr1.googlevideo.com/videoplayback?c=WEB&itag=1\(bitrate)",
        signatureCipher: nil,
        mimeType: mime,
        bitrate: bitrate,
        averageBitrate: nil,
        audioChannels: channels,
        audioSampleRate: sampleRate
    )
}
