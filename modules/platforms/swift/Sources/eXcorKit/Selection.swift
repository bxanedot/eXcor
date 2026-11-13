import Foundation

func selectAudioFormat(_ input: [ExcorPlaybackFormat], maxKbps: Int, requireM4a: Bool) -> ExcorPlaybackFormat? {
    let usable = input.filter { format in
        guard format.isAudio, let url = format.url else { return false }
        return isAllowedPlaybackURL(url)
    }
    if requireM4a {
        return usable.filter(\.isM4a).max(by: lowerQuality)
    }
    if maxKbps <= 64 {
        let m4a = usable.filter(\.isM4a)
        return (m4a.isEmpty ? usable : m4a).min { ($0.bitrate ?? 0) < ($1.bitrate ?? 0) }
    }
    let webm = usable.filter(\.isWebm)
    return (webm.isEmpty ? usable : webm).max(by: lowerQuality)
}

private func lowerQuality(_ lhs: ExcorPlaybackFormat, _ rhs: ExcorPlaybackFormat) -> Bool {
    if codecRank(lhs) != codecRank(rhs) { return codecRank(lhs) < codecRank(rhs) }
    if qualityBitrate(lhs) != qualityBitrate(rhs) { return qualityBitrate(lhs) < qualityBitrate(rhs) }
    return cappedSampleRate(lhs) < cappedSampleRate(rhs)
}

private func codecRank(_ format: ExcorPlaybackFormat) -> Int {
    if format.isWebm { return 2 }
    if format.isM4a { return 1 }
    return 0
}

private func qualityBitrate(_ format: ExcorPlaybackFormat) -> Int {
    (format.bitrate ?? 0) + (format.audioChannels == 2 ? 50_000 : format.audioChannels == nil ? 25_000 : 0)
}

private func cappedSampleRate(_ format: ExcorPlaybackFormat) -> Int {
    min(format.audioSampleRate ?? 0, 48_000)
}

func audioCandidate(_ format: ExcorPlaybackFormat) -> ExcorAudioCandidate? {
    guard format.isAudio, let url = format.url, isAllowedPlaybackURL(url) else { return nil }
    let kind: ExcorAudioFormat
    if format.isM4a {
        kind = .m4a
    } else if format.isWebm && format.mimeType.lowercased().contains("opus") {
        kind = .webmOpus
    } else {
        kind = .other
    }
    return ExcorAudioCandidate(url: url, kbps: (format.averageBitrate ?? format.bitrate ?? 0) / 1_000, mimeType: format.mimeType, format: kind)
}
