import XCTest
@testable import VyonExKit

final class SelectionTests: XCTestCase {
    // VISIONOS response: 139 (mp4 50k), 140 (mp4 130k), 249/250/251 (opus 49k/65k/136k).
    private func captured() throws -> [AudioFormat] {
        try audioFormats(of: fixtureJSON("player-response-visionos.json")).map { AudioFormat($0, url: $0["url"] as! String) }
    }

    private func fmt(_ itag: Int, _ mime: String, _ bitrate: Int, channels: Int? = 2, rate: Int? = 44_100) -> AudioFormat {
        var json: JSONObject = ["itag": itag, "mimeType": mime, "bitrate": bitrate]
        json["audioChannels"] = channels
        json["audioSampleRate"] = rate.map { String($0) }
        return AudioFormat(json, url: "https://r1.googlevideo.com/videoplayback?itag=\(itag)")
    }

    private let webm = "audio/webm; codecs=\"opus\""

    func testAutoTakesBestWebm() throws {
        XCTAssertEqual(selectFormat(try captured(), maxKbps: 320, requireM4a: false)?.itag, 251)
    }

    func testMaxKbpsAbove64IsNotACap() throws {
        XCTAssertEqual(selectFormat(try captured(), maxKbps: 65, requireM4a: false)?.itag, 251)
        XCTAssertEqual(selectFormat(try captured(), maxKbps: 128, requireM4a: false)?.itag, 251)
    }

    func testLowModeTakesLowestMp4() throws {
        XCTAssertEqual(selectFormat(try captured(), maxKbps: 64, requireM4a: false)?.itag, 139)
    }

    func testLowModeFallsBackToLowestOverall() throws {
        let webmOnly = try captured().filter { $0.mimeType.hasPrefix("audio/webm") }
        XCTAssertEqual(selectFormat(webmOnly, maxKbps: 48, requireM4a: false)?.itag, 249)
    }

    func testRequireM4aTakesBestMp4OrNothing() throws {
        XCTAssertEqual(selectFormat(try captured(), maxKbps: 32, requireM4a: true)?.itag, 140)
        XCTAssertEqual(selectFormat(try captured(), maxKbps: 320, requireM4a: true)?.itag, 140)
        XCTAssertNil(selectFormat(try captured().filter { $0.mimeType.hasPrefix("audio/webm") }, maxKbps: 320, requireM4a: true))
    }

    func testAutoRanksMp4OverOtherCodecsWithoutWebm() {
        XCTAssertEqual(selectFormat([fmt(1, "audio/mp4", 100_000), fmt(2, "audio/ogg", 300_000)], maxKbps: 160, requireM4a: false)?.itag, 1)
    }

    func testStereoAndUnknownChannelsGetABonus() {
        XCTAssertEqual(selectFormat([fmt(1, webm, 140_000, channels: 1), fmt(2, webm, 100_000)], maxKbps: 160, requireM4a: false)?.itag, 2)
        XCTAssertEqual(selectFormat([fmt(1, webm, 120_000, channels: 1), fmt(2, webm, 100_000, channels: nil)], maxKbps: 160, requireM4a: false)?.itag, 2)
        XCTAssertEqual(selectFormat([fmt(1, webm, 100_000), fmt(2, webm, 100_000, channels: nil)], maxKbps: 160, requireM4a: false)?.itag, 1)
    }

    func testSampleRateBreaksTiesUpTo48k() {
        XCTAssertEqual(selectFormat([fmt(1, webm, 100_000, rate: 44_100), fmt(2, webm, 100_000, rate: 48_000)], maxKbps: 160, requireM4a: false)?.itag, 2)
        // 96 kHz counts as 48 kHz, so the first of the tied formats stays.
        XCTAssertEqual(selectFormat([fmt(1, webm, 100_000, rate: 48_000), fmt(2, webm, 100_000, rate: 96_000)], maxKbps: 160, requireM4a: false)?.itag, 1)
    }
}
