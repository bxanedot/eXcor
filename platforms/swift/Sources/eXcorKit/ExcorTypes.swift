import Foundation

public enum ExcorLogLevel: Sendable, Hashable {
    case debug
    case info
    case warn
    case error
}

public enum ExcorAudioFormat: String, Sendable, Hashable {
    case m4a
    case webmOpus
    case other
}

public struct ExcorStream: Sendable, CustomStringConvertible {
    public let videoId: String
    public let url: String
    public let kbps: Int
    public let mimeType: String
    public let loudnessDb: Double?
    public let clientName: String
    public let profileId: String
    public let headers: [String: String]

    public init(videoId: String, url: String, kbps: Int, mimeType: String, loudnessDb: Double?, clientName: String, profileId: String, headers: [String: String]) {
        self.videoId = videoId
        self.url = url
        self.kbps = kbps
        self.mimeType = mimeType
        self.loudnessDb = loudnessDb
        self.clientName = clientName
        self.profileId = profileId
        self.headers = headers
    }

    public var description: String {
        "ExcorStream(videoId: \(videoId), url: <redacted>, kbps: \(kbps), mimeType: \(mimeType), clientName: \(clientName), profileId: \(profileId), headers: <redacted>)"
    }
}

public struct ExcorAudioCandidate: Sendable, CustomStringConvertible {
    public let url: String
    public let kbps: Int
    public let mimeType: String
    public let format: ExcorAudioFormat

    public init(url: String, kbps: Int, mimeType: String, format: ExcorAudioFormat) {
        self.url = url
        self.kbps = kbps
        self.mimeType = mimeType
        self.format = format
    }

    public var description: String { "ExcorAudioCandidate(url: <redacted>, kbps: \(kbps), mimeType: \(mimeType), format: \(format.rawValue))" }
}

public struct ExcorSession: Sendable, CustomStringConvertible {
    public let cookie: String?
    public let visitorData: String?
    public let region: String
    public let language: String

    public init(cookie: String?, visitorData: String?, region: String, language: String) {
        self.cookie = cookie
        self.visitorData = visitorData
        self.region = region
        self.language = language
    }

    public var description: String {
        "ExcorSession(cookie: \(cookie == nil ? "missing" : "present"), visitorData: \(visitorData == nil ? "missing" : "present"), region: \(region), language: \(language))"
    }

    var requestCookie: String? {
        guard let cookie, !cookie.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return cookie
    }
}

public struct PoTokenResult: Sendable, CustomStringConvertible {
    public let playerRequestToken: String
    public let streamingDataToken: String
    public let visitorData: String

    public init(playerRequestToken: String, streamingDataToken: String, visitorData: String) {
        self.playerRequestToken = playerRequestToken
        self.streamingDataToken = streamingDataToken
        self.visitorData = visitorData
    }

    public var description: String { "PoTokenResult(playerRequestToken: present, streamingDataToken: present, visitorData: present)" }
}

public enum PoTokenProviderKind: String, Sendable, Hashable {
    case webBotguard
    case webpageAttestation
    case androidDroidguard
    case iosAttestation
    case external
}

public struct PoTokenProviderCapabilities: Sendable {
    public let providers: Set<PoTokenProviderKind>
    public let usesWebView: Bool

    public init(providers: Set<PoTokenProviderKind> = [], usesWebView: Bool = false) {
        self.providers = providers
        self.usesWebView = usesWebView
    }
}

public protocol ExcorConfigStore: Sendable {
    var enabled: Bool { get }
    func getString(_ key: String) -> String?
    func getLong(_ key: String) -> Int64?
    func putString(_ key: String, _ value: String)
    func putLong(_ key: String, _ value: Int64)
}

public protocol PoTokenProvider: Sendable {
    var capabilities: PoTokenProviderCapabilities { get }
    func poToken(videoId: String, visitorData: String, cookie: String?) async throws -> PoTokenResult?
    func prewarm(cookie: String?) async
    func invalidateAttestation() async
    func close() async
}

public protocol ExcorKitHost: Sendable {
    func session() async -> ExcorSession
    var configStore: any ExcorConfigStore { get }
    var now: @Sendable () -> Date { get }
    var urlSession: URLSession? { get }
    var tokenProvider: (any PoTokenProvider)? { get }
    func log(_ level: ExcorLogLevel, _ message: String, trackId: String?)
}

struct ExcorPlaybackFormat: Decodable, Equatable, Sendable, CustomStringConvertible {
    var url: String?
    var signatureCipher: String?
    var cipher: String?
    var mimeType: String
    var bitrate: Int?
    var averageBitrate: Int?
    var audioChannels: Int?
    var audioSampleRate: Int?

    init(url: String? = nil, signatureCipher: String? = nil, cipher: String? = nil, mimeType: String, bitrate: Int? = nil, averageBitrate: Int? = nil, audioChannels: Int? = nil, audioSampleRate: Int? = nil) {
        self.url = url
        self.signatureCipher = signatureCipher
        self.cipher = cipher
        self.mimeType = mimeType
        self.bitrate = bitrate
        self.averageBitrate = averageBitrate
        self.audioChannels = audioChannels
        self.audioSampleRate = audioSampleRate
    }

    private enum CodingKeys: String, CodingKey {
        case url, signatureCipher, cipher, mimeType, bitrate, averageBitrate, audioChannels, audioSampleRate
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        url = try values.decodeIfPresent(String.self, forKey: .url)
        signatureCipher = try values.decodeIfPresent(String.self, forKey: .signatureCipher)
        cipher = try values.decodeIfPresent(String.self, forKey: .cipher)
        mimeType = try values.decode(String.self, forKey: .mimeType)
        bitrate = try values.decodeIfPresent(Int.self, forKey: .bitrate)
        averageBitrate = try values.decodeIfPresent(Int.self, forKey: .averageBitrate)
        audioChannels = try values.decodeIfPresent(Int.self, forKey: .audioChannels)
        audioSampleRate = try? values.decode(Int.self, forKey: .audioSampleRate)
        if audioSampleRate == nil, let text = try? values.decode(String.self, forKey: .audioSampleRate) {
            audioSampleRate = Int(text)
        }
    }

    var isAudio: Bool { mimeType.lowercased().hasPrefix("audio/") }
    var isM4a: Bool { mimeType.lowercased().hasPrefix("audio/mp4") }
    var isWebm: Bool { mimeType.lowercased().hasPrefix("audio/webm") }
    var description: String { "ExcorPlaybackFormat(url: <redacted>, cipher: <redacted>, mimeType: \(mimeType), bitrate: \(bitrate ?? 0))" }
}
