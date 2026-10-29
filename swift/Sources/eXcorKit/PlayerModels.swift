import Foundation

struct ExcorPlayabilityStatus: Decodable, Sendable {
    let status: String
    let reason: String?
    let messages: [String]?

    var explanation: String? { reason ?? messages?.first }
}

struct ExcorStreamingData: Decodable, Sendable {
    let formats: [ExcorPlaybackFormat]?
    let adaptiveFormats: [ExcorPlaybackFormat]?

    var allFormats: [ExcorPlaybackFormat] { (formats ?? []) + (adaptiveFormats ?? []) }
}

private struct ExcorAssets: Decodable, Sendable {
    let js: String?
}

private struct ExcorAudioConfig: Decodable, Sendable {
    let loudnessDb: Double?
}

private struct ExcorPlayerConfig: Decodable, Sendable {
    let audioConfig: ExcorAudioConfig?
}

struct ExcorPlayerResponse: Decodable, Sendable {
    let playabilityStatus: ExcorPlayabilityStatus
    let streamingData: ExcorStreamingData?
    private let assets: ExcorAssets?
    private let playerConfig: ExcorPlayerConfig?

    var playerScriptPath: String? { assets?.js }
    var loudnessDb: Double? { playerConfig?.audioConfig?.loudnessDb }
}

func decodePlayerResponse(_ data: Data) throws -> ExcorPlayerResponse {
    try JSONDecoder().decode(ExcorPlayerResponse.self, from: data)
}
