import Foundation

public actor ExcorKit {
    private struct RememberedStream: Sendable {
        let videoId: String
        let profileId: String
        let headers: [String: String]
    }

    private struct PlayerSource: Sendable {
        let script: String
        let prepared: String?
        let timestamp: Int?
    }

    private let host: any ExcorKitHost
    private let transport: ExcorTransport
    private var remembered = [String: RememberedStream]()
    private var exclusions = [String: [String: Date]]()
    private var playerSources = [String: PlayerSource]()
    private var warmTask: Task<Void, Never>?
    private var sessionGeneration: UInt64 = 0

    public init(host: any ExcorKitHost) {
        self.host = host
        self.transport = ExcorTransport(session: host.urlSession)
    }

    public func initialize() {
        guard warmTask == nil else { return }
        warmTask = Task { [weak self] in
            do { try await Task.sleep(nanoseconds: 2_000_000_000) }
            catch { return }
            guard !Task.isCancelled else { return }
            guard let self else { return }
            await self.warmUp()
        }
    }

    public func audioCandidates(videoId: String) async -> [ExcorAudioCandidate] {
        do {
            try Task.checkCancellation()
            let generation = sessionGeneration
            let session = await host.session()
            try checkSessionGeneration(generation)
            let page = try await loadWatchPage(videoId: videoId, session: session)
            try checkSessionGeneration(generation)
            guard page.playerResponse.playabilityStatus.status == "OK" else { return [] }
            let formats = try await resolveFormats(page.playerResponse.streamingData?.allFormats ?? [], page: page, response: page.playerResponse, session: session, generation: generation, streamingToken: nil)
            try checkSessionGeneration(generation)
            return formats.compactMap(audioCandidate)
        } catch {
            if error is CancellationError || Task.isCancelled { return [] }
            host.log(.debug, "eXcor: audio candidate lookup failed", trackId: videoId)
            return []
        }
    }

    public func extract(videoId: String, maxKbps: Int, skipClients: Set<String> = [], requireM4a: Bool = false) async throws -> ExcorStream? {
        try Task.checkCancellation()
        let generation = sessionGeneration
        let session = await host.session()
        try checkSessionGeneration(generation)
        var page: ExcorWatchPage?
        do {
            let currentPage = try await loadWatchPage(videoId: videoId, session: session)
            try checkSessionGeneration(generation)
            page = currentPage
            if let stream = try await extractFromClients(videoId: videoId, page: currentPage, session: session, maxKbps: maxKbps, skipClients: skipClients, requireM4a: requireM4a, generation: generation) {
                try checkSessionGeneration(generation)
                return stream
            }
        } catch {
            if error is CancellationError || Task.isCancelled { throw CancellationError() }
            host.log(.warn, "eXcor: player API lookup failed; trying watch page", trackId: videoId)
        }

        do {
            let fallbackPage: ExcorWatchPage
            if let page { fallbackPage = page }
            else { fallbackPage = try await loadWatchPage(videoId: videoId, session: session) }
            try checkSessionGeneration(generation)
            let stream = try await watchPageFallback(videoId: videoId, page: fallbackPage, session: session, skipClients: skipClients, maxKbps: maxKbps, requireM4a: requireM4a, generation: generation)
            try checkSessionGeneration(generation)
            return stream
        } catch {
            if error is CancellationError || Task.isCancelled { throw CancellationError() }
            host.log(.warn, "eXcor: watch page fallback failed", trackId: videoId)
            throw error
        }
    }

    public func headers(for url: String) -> [String: String]? { remembered[url]?.headers }

    public func onRefused(url: String) -> String? {
        guard let stream = remembered.removeValue(forKey: url) else { return nil }
        exclude(videoId: stream.videoId, profileId: stream.profileId)
        playerSources.removeAll()
        return stream.videoId
    }

    public func exclude(videoId: String, profileId: String) {
        exclusions[videoId, default: [:]][profileId] = host.now().addingTimeInterval(10 * 60)
        host.log(.warn, "eXcor: excluded profile \(profileId) for this video", trackId: videoId)
    }

    public func onSessionChanged() {
        sessionGeneration &+= 1
        remembered.removeAll()
        exclusions.removeAll()
        playerSources.removeAll()
        warmTask?.cancel()
        warmTask = nil
        if host.configStore.enabled {
            warmTask = Task { [weak self] in
                guard let self else { return }
                await self.warmUp()
            }
        }
    }

    func rememberForTesting(_ stream: ExcorStream) { remember(stream) }

    func isExcludedForTesting(videoId: String, profileId: String) -> Bool {
        excludedProfiles(videoId: videoId).contains(profileId)
    }

    private func warmUp() async {
        let generation = sessionGeneration
        do {
            try Task.checkCancellation()
            let session = await host.session()
            try checkSessionGeneration(generation)
            if let provider = host.tokenProvider { await provider.prewarm(cookie: session.requestCookie) }
            let page = try await loadWatchPage(videoId: "dQw4w9WgXcQ", session: session)
            try checkSessionGeneration(generation)
            _ = try await loadPlayerSource(page: page, response: page.playerResponse, session: session, generation: generation)
        } catch {
            if error is CancellationError || Task.isCancelled { return }
            host.log(.warn, "eXcor: background warm-up failed")
        }
    }

    private func extractFromClients(videoId: String, page: ExcorWatchPage, session: ExcorSession, maxKbps: Int, skipClients: Set<String>, requireM4a: Bool, generation: UInt64) async throws -> ExcorStream? {
        var lastError: Error?
        let clients = eligibleClients(for: session, skipped: skipClients, excluded: excludedProfiles(videoId: videoId))
        for identity in clients {
            try Task.checkCancellation()
            try checkSessionGeneration(generation)
            var token: PoTokenResult?
            if identity.needsStreamingToken {
                guard let provider = host.tokenProvider else { continue }
                do {
                    token = try await provider.poToken(videoId: videoId, visitorData: session.visitorData ?? "", cookie: session.requestCookie)
                    try checkSessionGeneration(generation)
                } catch {
                    try checkSessionGeneration(generation)
                    if error is CancellationError || Task.isCancelled { throw CancellationError() }
                    lastError = error
                    continue
                }
                guard let token, !token.playerRequestToken.isEmpty, !token.streamingDataToken.isEmpty else { continue }
            }

            do {
                let source = identity.needsSignatureTimestamp ? try await loadPlayerSource(page: page, response: page.playerResponse, session: session, generation: generation) : nil
                try checkSessionGeneration(generation)
                if identity.needsSignatureTimestamp && source?.timestamp == nil { continue }
                let body = makePlayerRequestBody(videoId: videoId, session: session, identity: identity, token: token, signatureTimestamp: source?.timestamp)
                let data = try JSONSerialization.data(withJSONObject: body, options: [.sortedKeys])
                let request = try playerAPIRequest(apiKey: page.apiKey, identity: identity, session: session, body: data)
                let (responseData, _) = try await transport.data(for: request, maximumBytes: 5 * 1024 * 1024)
                try checkSessionGeneration(generation)
                let response = try decodePlayerResponse(responseData)
                guard response.playabilityStatus.status == "OK" else {
                    lastError = ExcorError.unplayable(response.playabilityStatus.explanation ?? response.playabilityStatus.status)
                    continue
                }
                let formats = try await resolveFormats(response.streamingData?.allFormats ?? [], page: page, response: response, session: session, generation: generation, streamingToken: token?.streamingDataToken)
                try checkSessionGeneration(generation)
                guard let format = selectAudioFormat(formats, maxKbps: maxKbps, requireM4a: requireM4a), let url = format.url else { continue }
                var headers = ["User-Agent": identity.userAgent]
                if let cookie = session.requestCookie { headers["Cookie"] = cookie }
                let stream = ExcorStream(
                    videoId: videoId, url: url, kbps: (format.bitrate ?? 0) / 1_000, mimeType: format.mimeType,
                    loudnessDb: response.loudnessDb, clientName: identity.name, profileId: identity.profileId, headers: headers
                )
                try checkSessionGeneration(generation)
                remember(stream)
                host.log(.info, "eXcor: selected \(identity.name) \(format.mimeType)", trackId: videoId)
                return stream
            } catch {
                try checkSessionGeneration(generation)
                if error is CancellationError || Task.isCancelled { throw CancellationError() }
                lastError = error
                host.log(.debug, "eXcor: client \(identity.name) did not yield a usable stream", trackId: videoId)
            }
        }
        if let lastError { throw lastError }
        return nil
    }

    private func watchPageFallback(videoId: String, page: ExcorWatchPage, session: ExcorSession, skipClients: Set<String>, maxKbps: Int, requireM4a: Bool, generation: UInt64) async throws -> ExcorStream? {
        try checkSessionGeneration(generation)
        let blocked = Set((skipClients.union(excludedProfiles(videoId: videoId))).map { $0.lowercased() })
        guard !blocked.contains("web"), !blocked.contains("web_watch_page"),
              page.playerResponse.playabilityStatus.status == "OK"
        else { return nil }
        let formats = try await resolveFormats(page.playerResponse.streamingData?.allFormats ?? [], page: page, response: page.playerResponse, session: session, generation: generation, streamingToken: nil)
        try checkSessionGeneration(generation)
        guard let format = selectAudioFormat(formats, maxKbps: maxKbps, requireM4a: requireM4a), let url = format.url else { return nil }
        var headers = [
            "User-Agent": webClient.userAgent,
            "Accept": "*/*",
            "Accept-Language": acceptLanguage(session),
            "Referer": "https://www.youtube.com/",
            "Origin": "https://www.youtube.com",
        ]
        if let cookie = session.requestCookie { headers["Cookie"] = cookie }
        let stream = ExcorStream(videoId: videoId, url: url, kbps: (format.bitrate ?? 0) / 1_000, mimeType: format.mimeType, loudnessDb: page.playerResponse.loudnessDb, clientName: "WEB", profileId: "WEB_WATCH_PAGE", headers: headers)
        try checkSessionGeneration(generation)
        remember(stream)
        host.log(.info, "eXcor: watch page selected \(format.mimeType)", trackId: videoId)
        return stream
    }

    private func loadWatchPage(videoId: String, session: ExcorSession) async throws -> ExcorWatchPage {
        guard var parts = URLComponents(string: "https://www.youtube.com/watch") else { throw ExcorError.invalidWatchPage }
        parts.queryItems = [
            URLQueryItem(name: "v", value: videoId),
            URLQueryItem(name: "hl", value: requestLanguage(session)),
            URLQueryItem(name: "gl", value: requestRegion(session)),
        ]
        guard let url = parts.url else { throw ExcorError.invalidWatchPage }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        for (name, value) in sessionRequestHeaders(session: session, userAgent: webClient.userAgent, accept: "text/html,application/xhtml+xml") {
            request.setValue(value, forHTTPHeaderField: name)
        }
        request.setValue("https://www.youtube.com/", forHTTPHeaderField: "Referer")
        let (data, _) = try await transport.data(for: request, maximumBytes: 8 * 1024 * 1024)
        guard let html = String(data: data, encoding: .utf8) else { throw ExcorError.invalidWatchPage }
        return try parseWatchPage(html)
    }

    private func playerAPIRequest(apiKey: String, identity: ExcorClientIdentity, session: ExcorSession, body: Data) throws -> URLRequest {
        guard var parts = URLComponents(string: "https://www.youtube.com/youtubei/v1/player") else { throw ExcorError.invalidPlayerData }
        parts.queryItems = [URLQueryItem(name: "key", value: apiKey), URLQueryItem(name: "prettyPrint", value: "false")]
        guard let url = parts.url else { throw ExcorError.invalidPlayerData }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = body
        for (name, value) in playerRequestHeaders(session: session, identity: identity) { request.setValue(value, forHTTPHeaderField: name) }
        return request
    }

    private func loadPlayerSource(page: ExcorWatchPage, response: ExcorPlayerResponse, session: ExcorSession, generation: UInt64) async throws -> PlayerSource {
        try checkSessionGeneration(generation)
        let baseURL = URL(string: "https://www.youtube.com")
        let responseScriptURL = response.playerScriptPath.flatMap { value in baseURL.flatMap { URL(string: value, relativeTo: $0)?.absoluteURL } }
        guard let raw = responseScriptURL ?? page.playerScriptURL,
              isAllowedYouTubeTransportURL(raw)
        else { throw ExcorError.missingPlayerScript }
        if let cached = playerSources[raw.absoluteString] { return cached }
        var request = URLRequest(url: raw)
        request.httpMethod = "GET"
        for (name, value) in sessionRequestHeaders(session: session, userAgent: webClient.userAgent, accept: "*/*") {
            request.setValue(value, forHTTPHeaderField: name)
        }
        request.setValue("https://www.youtube.com/", forHTTPHeaderField: "Referer")
        let (data, _) = try await transport.data(for: request, maximumBytes: 8 * 1024 * 1024)
        try checkSessionGeneration(generation)
        guard let script = String(data: data, encoding: .utf8) else { throw ExcorError.invalidPlayerData }
        let prepared = try await PlayerJSTransformer.solve(playerScript: script, preparedPlayer: nil, signatures: [], throttling: []).preparedPlayer
        try checkSessionGeneration(generation)
        let source = PlayerSource(script: script, prepared: prepared, timestamp: PlayerJSTransformer.signatureTimestamp(in: script))
        playerSources[raw.absoluteString] = source
        return source
    }

    private func resolveFormats(_ input: [ExcorPlaybackFormat], page: ExcorWatchPage, response: ExcorPlayerResponse, session: ExcorSession, generation: UInt64, streamingToken: String?) async throws -> [ExcorPlaybackFormat] {
        let audioFormats = input.filter(\.isAudio)
        let pending = audioFormats.map { format -> ExcorCipherParameters in
            if let value = format.signatureCipher ?? format.cipher { return parseSignatureCipher(value, fallbackURL: format.url) }
            return ExcorCipherParameters(url: format.url, signature: nil, encryptedSignature: nil, signatureParameter: "signature")
        }
        let signatures = pending.compactMap(\.encryptedSignature)
        let throttling = pending.compactMap { $0.url.flatMap { queryParameter("n", in: $0) } }
        var signatureResults = [String: String]()
        var throttleResults = [String: String]()
        if !signatures.isEmpty || !throttling.isEmpty {
            let source = try await loadPlayerSource(page: page, response: response, session: session, generation: generation)
            let output = try await PlayerJSTransformer.solve(
                playerScript: source.prepared == nil ? source.script : nil,
                preparedPlayer: source.prepared,
                signatures: Array(Set(signatures)),
                throttling: Array(Set(throttling))
            )
            signatureResults = output.signatures
            throttleResults = output.throttling
        }

        var resolved = [ExcorPlaybackFormat]()
        for (index, source) in audioFormats.enumerated() {
            guard let base = pending[index].url,
                  let current = playableMediaURL(base: base, signature: pending[index].signature, encryptedSignature: pending[index].encryptedSignature,
                      signatureParameter: pending[index].signatureParameter, signatures: signatureResults, throttling: throttleResults, streamingToken: streamingToken)
            else { continue }
            var format = source
            format.url = current
            format.signatureCipher = nil
            format.cipher = nil
            resolved.append(format)
        }
        return resolved
    }

    private func excludedProfiles(videoId: String) -> Set<String> {
        let now = host.now()
        var entries = exclusions[videoId] ?? [:]
        entries = Dictionary(uniqueKeysWithValues: entries.filter { $0.value > now })
        exclusions[videoId] = entries
        return Set(entries.keys)
    }

    private func remember(_ stream: ExcorStream) {
        if remembered.count >= 64 && remembered[stream.url] == nil { remembered.removeAll() }
        remembered[stream.url] = RememberedStream(videoId: stream.videoId, profileId: stream.profileId, headers: stream.headers)
    }

    private func checkSessionGeneration(_ expected: UInt64) throws {
        guard sessionGeneration == expected else { throw CancellationError() }
        try Task.checkCancellation()
    }
}
