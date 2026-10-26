import XCTest
@testable import VyonExKit

final class EngineTests: XCTestCase {
    private var host: FakeHost!
    private var kit: VyonExKit!

    override func setUp() {
        host = FakeHost()
        kit = VyonExKit(host: host, session: offlineSession())
    }

    private func stream(_ i: Int) -> VyonExStream {
        VyonExStream(
            videoId: "video\(i)", url: "https://r\(i).googlevideo.com/videoplayback?id=\(i)", kbps: 128,
            mimeType: "audio/webm; codecs=\"opus\"", loudnessDb: nil, clientName: "TVHTML5", profileId: "TV_SAMSUNG",
            headers: ["User-Agent": "agent\(i)"]
        )
    }

    func testRemembersHeadersByURL() {
        _ = kit.remember(stream(1))
        XCTAssertEqual(kit.headers(for: stream(1).url), ["User-Agent": "agent1"])
        XCTAssertNil(kit.headers(for: stream(2).url))
    }

    func testClearsAllRememberedStreamsWhenFull() {
        for i in 0..<maxRemembered { _ = kit.remember(stream(i)) }
        XCTAssertTrue((0..<maxRemembered).allSatisfy { kit.headers(for: stream($0).url) != nil })
        _ = kit.remember(stream(0)) // already known: no clearing
        XCTAssertNotNil(kit.headers(for: stream(1).url))
        _ = kit.remember(stream(maxRemembered))
        XCTAssertTrue((0..<maxRemembered).allSatisfy { kit.headers(for: stream($0).url) == nil })
        XCTAssertNotNil(kit.headers(for: stream(maxRemembered).url))
    }

    func testExclusionLastsTenMinutes() {
        kit.exclude(videoId: "video1", profileId: "VISIONOS")
        XCTAssertTrue(kit.isExcluded("video1", "VISIONOS"))
        XCTAssertFalse(kit.isExcluded("video1", "TV_SAMSUNG"))
        XCTAssertFalse(kit.isExcluded("video2", "VISIONOS"))
        host.now += exclusionSeconds - 1
        XCTAssertTrue(kit.isExcluded("video1", "VISIONOS"))
        host.now += 1
        XCTAssertFalse(kit.isExcluded("video1", "VISIONOS"))
        XCTAssertEqual(host.logs, ["warn VyonEx: excluding VISIONOS for 10 minutes"])
    }

    func testOnRefusedForgetsAndExcludesRememberedStreams() {
        _ = kit.remember(stream(1))
        XCTAssertEqual(kit.onRefused(url: stream(1).url), "video1")
        XCTAssertNil(kit.headers(for: stream(1).url))
        XCTAssertTrue(kit.isExcluded("video1", "TV_SAMSUNG"))
        XCTAssertNil(kit.onRefused(url: stream(1).url))
        XCTAssertNil(kit.onRefused(url: "https://r9.googlevideo.com/videoplayback"))
    }

    func testOnSessionChangedForgetsAndWarmsUpOnlyWhenEnabled() async throws {
        _ = kit.remember(stream(1))
        kit.exclude(videoId: "video1", profileId: "VISIONOS")
        host.enabled = false
        kit.onSessionChanged()
        XCTAssertNil(kit.headers(for: stream(1).url))
        XCTAssertFalse(kit.isExcluded("video1", "VISIONOS"))
        try await Task.sleep(nanoseconds: 500_000_000)
        XCTAssertEqual(host.sessionCalls, 0, "no warm-up when disabled")
        host.enabled = true
        kit.onSessionChanged()
        for _ in 0..<500 where host.sessionCalls == 0 { try await Task.sleep(nanoseconds: 10_000_000) }
        XCTAssertEqual(host.sessionCalls, 1)
    }

    func testSkippedExcludedAndTokenlessIdentitiesAreNotTried() async throws {
        kit.exclude(videoId: "video1", profileId: "TV_SAMSUNG")
        // VISIONOS is skipped by profile, WEB has no token provider: no request is made at all.
        let a = try await kit.extractPrimary(videoId: "video1", maxKbps: 160, skipClients: ["VISIONOS"], requireM4a: false)
        let b = try await kit.extractPrimary(videoId: "video1", maxKbps: 160, skipClients: ["TVHTML5", "VISIONOS"], requireM4a: false)
        XCTAssertNil(a)
        XCTAssertNil(b)
        XCTAssertFalse(host.logs.contains { $0.contains("failed") }, "\(host.logs)")
    }

    func testWatchPageFallbackHonoursSkipAndExclusion() async throws {
        let a = try await kit.watchPageStream(videoId: "video1", maxKbps: 160, skipClients: ["WEB"], requireM4a: false)
        let b = try await kit.watchPageStream(videoId: "video1", maxKbps: 160, skipClients: ["WEB_WATCH_PAGE"], requireM4a: false)
        kit.exclude(videoId: "video1", profileId: "WEB_WATCH_PAGE")
        let c = try await kit.extract(videoId: "video1", maxKbps: 160, skipClients: ["TV_SAMSUNG", "VISIONOS"])
        XCTAssertNil(a)
        XCTAssertNil(b)
        XCTAssertNil(c)
        XCTAssertFalse(host.logs.contains { $0.contains("failed") }, "\(host.logs)")
    }
}
