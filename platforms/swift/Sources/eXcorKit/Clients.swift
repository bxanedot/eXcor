import Foundation

struct ExcorClientIdentity: Sendable {
    let name: String
    let profileId: String
    let id: Int
    let version: String
    let userAgent: String
    let needsSignatureTimestamp: Bool
    let needsStreamingToken: Bool
}

private let tvClient = ExcorClientIdentity(
    name: "TVHTML5", profileId: "tv_samsung", id: 7, version: "5.20260707",
    userAgent: "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
    needsSignatureTimestamp: false, needsStreamingToken: false
)
private let visionClient = ExcorClientIdentity(
    name: "VISIONOS", profileId: "visionos", id: 101, version: "1.02",
    userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
    needsSignatureTimestamp: false, needsStreamingToken: false
)
let webClient = ExcorClientIdentity(
    name: "WEB", profileId: "web", id: 1, version: "2.20260708.00.00",
    userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36",
    needsSignatureTimestamp: true, needsStreamingToken: true
)
private let embeddedClient = ExcorClientIdentity(
    name: "WEB_EMBEDDED_PLAYER", profileId: "web_embedded", id: 56, version: "2.20260708.00.00",
    userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)",
    needsSignatureTimestamp: true, needsStreamingToken: false
)

func clientOrder(for session: ExcorSession) -> [ExcorClientIdentity] {
    session.requestCookie != nil ? [embeddedClient, tvClient, webClient] : [tvClient, visionClient, webClient]
}

func eligibleClients(for session: ExcorSession, skipped: Set<String>, excluded: Set<String>) -> [ExcorClientIdentity] {
    let blocked = Set((skipped.union(excluded)).map { $0.lowercased() })
    return clientOrder(for: session)
        .filter { !blocked.contains($0.name.lowercased()) && !blocked.contains($0.profileId.lowercased()) }
        .prefix(3)
        .map { $0 }
}
