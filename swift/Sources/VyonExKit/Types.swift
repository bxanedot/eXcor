import Foundation

public struct VyonExStream: Sendable, Equatable, CustomStringConvertible {
    public var videoId: String
    public var url: String
    public var kbps: Int
    public var mimeType: String
    public var loudnessDb: Double?
    public var clientName: String
    public var profileId: String
    public var headers: [String: String]

    public init(
        videoId: String, url: String, kbps: Int, mimeType: String, loudnessDb: Double?,
        clientName: String, profileId: String, headers: [String: String]
    ) {
        self.videoId = videoId
        self.url = url
        self.kbps = kbps
        self.mimeType = mimeType
        self.loudnessDb = loudnessDb
        self.clientName = clientName
        self.profileId = profileId
        self.headers = headers
    }

    // The URL may carry a PO token and the headers a cookie.
    public var description: String {
        "VyonExStream(videoId: \(videoId), kbps: \(kbps), mimeType: \(mimeType), loudnessDb: \(loudnessDb.map { "\($0)" } ?? "nil"), "
            + "clientName: \(clientName), profileId: \(profileId), headers: \(headers.keys.sorted()))"
    }
}

public enum VyonExAudioFormat: Sendable { case m4a, webmOpus, other }

public struct VyonExAudioCandidate: Sendable, Equatable {
    public var url: String
    public var kbps: Int
    public var mimeType: String
    public var format: VyonExAudioFormat

    public init(url: String, kbps: Int, mimeType: String, format: VyonExAudioFormat) {
        self.url = url
        self.kbps = kbps
        self.mimeType = mimeType
        self.format = format
    }
}

public struct VyonExSession: Sendable, Equatable, CustomStringConvertible {
    public var cookie: String?
    public var visitorData: String?
    public var region: String
    public var language: String

    public init(cookie: String?, visitorData: String?, region: String, language: String) {
        self.cookie = cookie
        self.visitorData = visitorData
        self.region = region
        self.language = language
    }

    public var description: String {
        "VyonExSession(cookie: \(presence(cookie)), visitorData: \(presence(visitorData)), region: \(region), language: \(language))"
    }
}

public enum VyonExLogLevel: Sendable { case debug, info, warn, error }

public struct PoTokenResult: Sendable, Equatable, CustomStringConvertible {
    public var playerRequestToken: String
    public var streamingDataToken: String
    public var visitorData: String

    public init(playerRequestToken: String, streamingDataToken: String, visitorData: String) {
        self.playerRequestToken = playerRequestToken
        self.streamingDataToken = streamingDataToken
        self.visitorData = visitorData
    }

    public var description: String {
        "PoTokenResult(playerRequestToken: \(presence(playerRequestToken)), "
            + "streamingDataToken: \(presence(streamingDataToken)), visitorData: \(presence(visitorData)))"
    }
}

public enum PoTokenProviderKind: Sendable, Hashable {
    case webBotguard, webpageAttestation, androidDroidguard, iosAttestation, external
}

public struct TokenProviderCapabilities: Sendable, Equatable {
    public var providers: Set<PoTokenProviderKind>
    public var usesWebView: Bool

    public init(providers: Set<PoTokenProviderKind> = [], usesWebView: Bool = false) {
        self.providers = providers
        self.usesWebView = usesWebView
    }
}

public protocol PoTokenProvider: Sendable {
    var capabilities: TokenProviderCapabilities { get }
    func poToken(videoId: String, visitorData: String, cookie: String?) async throws -> PoTokenResult?
    func prewarm(cookie: String?) async
    func invalidateAttestation() async
    func close() async
}

public extension PoTokenProvider {
    var capabilities: TokenProviderCapabilities { TokenProviderCapabilities() }
    func prewarm(cookie: String?) async {}
    func invalidateAttestation() async {}
    func close() async {}
}

public protocol VyonExConfigStore: Sendable {
    var enabled: Bool { get }
    func getString(_ key: String) -> String?
    func getLong(_ key: String) -> Int64?
    func putString(_ key: String, _ value: String)
    func putLong(_ key: String, _ value: Int64)
}

public protocol VyonExKitHost: Sendable {
    func session() async -> VyonExSession
    var configStore: VyonExConfigStore { get }
    var now: Date { get }
    func log(_ level: VyonExLogLevel, _ message: String, trackId: String?)
    var tokenProvider: PoTokenProvider? { get }
}

public extension VyonExKitHost {
    var tokenProvider: PoTokenProvider? { nil }
}

func presence(_ value: String?) -> String { (value ?? "").isEmpty ? "missing" : "present" }
