import eXcor

func compileExcorAPI(host: ExcorHost) async throws {
    _ = host.catalogTokenProvider
    let library = Excor(host: host)
    _ = library.musicCatalog
    library.initialize()
    library.initializeFallback()
    let _: [ExcorAudioCandidate] = try await library.audioCandidates(videoId: "abcdefghijk")
    let _: ExcorStream? = try await library.extract(
        videoId: "abcdefghijk",
        maxKbps: 128,
        skipClients: ["WEB"],
        requireM4a: true
    )
    _ = library.headersFor(url: "https://rr1---sn.googlevideo.com/videoplayback")
    _ = library.onRefused(url: "https://rr1---sn.googlevideo.com/videoplayback")
    library.exclude(videoId: "abcdefghijk", profileId: "WEB")
    library.onSessionChanged()
}
