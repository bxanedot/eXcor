// Type-checked by CI against VyonEx.xcframework: every member the host app uses (BRIEF section 3).
import Foundation
import VyonEx

final class ApiCheckStore: NSObject, VyonExConfigStore {
    var enabled: Bool { false }
    func getString(key: String) -> String? { nil }
    func getLong(key: String) -> KotlinLong? { nil }
    func putString(key: String, value: String) {}
    func putLong(key: String, value: Int64) {}
}

final class ApiCheckTokens: NSObject, TokenProvider {
    var capabilities: TokenProviderCapabilities {
        TokenProviderCapabilities(providers: [PoTokenProviderKind.iosAttestation], usesWebView: false)
    }
    func getPoToken(videoId: String, visitorData: String, cookie: String?) async throws -> PoTokenResult? { nil }
    func prewarm(cookie: String?) async throws {}
    func invalidateAttestation() async throws {}
    func close() async throws {}
}

final class ApiCheckHost: NSObject, VyonExHost {
    let configStore: VyonExConfigStore = ApiCheckStore()
    let tokenProvider: TokenProvider? = ApiCheckTokens()
    func session() async throws -> VyonExSession {
        VyonExSession(cookie: nil, visitorData: nil, region: "US", language: "en")
    }
    func nowMs() -> Int64 { 0 }
    func log(level: VyonExLogLevel, message: String, trackId: String?) {}
}

func apiCheck() async throws {
    let vyonEx = VyonEx(host: ApiCheckHost())
    vyonEx.initialize()
    vyonEx.initializeFallback()
    let candidates: [VyonExAudioCandidate] = try await vyonEx.audioCandidates(videoId: "v")
    for candidate in candidates {
        let format: VyonExAudioFormat = candidate.format
        _ = (candidate.url, candidate.kbps, candidate.mimeType, format == .m4a || format == .webmOpus || format == .other)
    }
    if let stream: VyonExStream = try await vyonEx.extract(videoId: "v", maxKbps: 128, skipClients: ["WEB"], requireM4a: false) {
        let headers: [String: String] = stream.headers
        _ = (stream.videoId, stream.url, stream.kbps, stream.mimeType, stream.loudnessDb, stream.clientName, stream.profileId, headers)
    }
    let headers: [String: String]? = vyonEx.headersFor(url: "u")
    let refused: String? = vyonEx.onRefused(url: "u")
    _ = (headers, refused)
    vyonEx.exclude(videoId: "v", profileId: "p")
    vyonEx.onSessionChanged()

    let levels: [VyonExLogLevel] = [.debug, .info, .warn, .error]
    let kinds: [PoTokenProviderKind] = [.webBotguard, .webpageAttestation, .androidDroidguard, .iosAttestation, .external]
    let token = PoTokenResult(playerRequestToken: "a", streamingDataToken: "b", visitorData: "c")
    let session = VyonExSession(cookie: "c", visitorData: "v", region: "US", language: "en")
    let capabilities = TokenProviderCapabilities(providers: Set(kinds), usesWebView: true)
    _ = (levels, token.playerRequestToken, token.streamingDataToken, token.visitorData)
    _ = (session.cookie, session.visitorData, session.region, session.language, capabilities.providers, capabilities.usesWebView)
    _ = VyonExAudioCandidate(url: "u", kbps: 1, mimeType: "m", format: .m4a)
    _ = VyonExStream(videoId: "v", url: "u", kbps: 1, mimeType: "m", loudnessDb: nil, clientName: "c", profileId: "p", headers: [:])
}
