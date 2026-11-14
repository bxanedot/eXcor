import XCTest
import Foundation
@testable import eXcorKit

final class ExcorKitApiTests: XCTestCase {
    func testPublicAPICompilesWithSection8Names() async throws {
        let host = TestHost()
        let kit = ExcorKit(host: host)
        let initialize: @Sendable () async -> Void = { await kit.initialize() }
        let candidates: @Sendable (String) async -> [ExcorAudioCandidate] = { await kit.audioCandidates(videoId: $0) }
        let extract: @Sendable (String, Int) async throws -> ExcorStream? = { try await kit.extract(videoId: $0, maxKbps: $1) }
        let headers: @Sendable (String) async -> [String: String]? = { await kit.headers(for: $0) }
        let refused: @Sendable (String) async -> String? = { await kit.onRefused(url: $0) }
        let exclude: @Sendable (String, String) async -> Void = { await kit.exclude(videoId: $0, profileId: $1) }
        let sessionChanged: @Sendable () async -> Void = { await kit.onSessionChanged() }
        _ = (initialize, candidates, extract, headers, refused, exclude, sessionChanged)

        let _: ExcorAudioFormat = .m4a
        let _: ExcorLogLevel = .warn
        let _: PoTokenProviderKind = .webBotguard
        let _: PoTokenResult = PoTokenResult(playerRequestToken: "p", streamingDataToken: "s", visitorData: "v")
        let provider: any PoTokenProvider = TestTokenProvider()
        let _: PoTokenProviderCapabilities = provider.capabilities
        let _: ExcorStream = ExcorStream(videoId: "video", url: "url", kbps: 1, mimeType: "audio/mp4", loudnessDb: nil, clientName: "WEB", profileId: "web", headers: [:])
        let _: ExcorAudioCandidate = ExcorAudioCandidate(url: "url", kbps: 1, mimeType: "audio/mp4", format: .m4a)
        let _: ExcorSession = ExcorSession(cookie: nil, visitorData: nil, region: "US", language: "en")
    }

    func testDescriptionsRedactCredentialsAndPlaybackUrls() {
        let session = ExcorSession(cookie: "cookie-secret", visitorData: "visitor-secret", region: "US", language: "en")
        let token = PoTokenResult(playerRequestToken: "player-secret", streamingDataToken: "stream-secret", visitorData: "visitor-secret")
        let stream = ExcorStream(videoId: "video", url: "https://rr1.googlevideo.com/videoplayback?pot=url-secret", kbps: 128, mimeType: "audio/webm", loudnessDb: nil, clientName: "WEB", profileId: "web", headers: ["Cookie": "cookie-secret"])

        XCTAssertFalse(String(describing: session).contains("secret"))
        XCTAssertFalse(String(describing: token).contains("secret"))
        XCTAssertFalse(String(describing: stream).contains("secret"))
    }
}
