import Foundation

let maxRemembered = 64
let exclusionSeconds: TimeInterval = 10 * 60
private let watchPageProfiles = ["WEB", "WEB_WATCH_PAGE"]

/// Turns a YouTube video ID into a directly playable audio URL. Safe to use from any thread.
public final class VyonExKit: @unchecked Sendable {
    private let host: VyonExKitHost
    private let http: URLSession
    let players: Players
    // Guards `remembered` and `exclusions`.
    private let lock = NSLock()
    private var remembered: [String: VyonExStream] = [:]
    private var exclusions: [String: Date] = [:]

    public init(host: VyonExKitHost, session: URLSession? = nil) {
        self.host = host
        if let session {
            http = session
        } else {
            // The library's own cookie handling: only the host session's cookie is sent.
            let config = URLSessionConfiguration.ephemeral
            config.httpShouldSetCookies = false
            config.httpCookieAcceptPolicy = .never
            config.timeoutIntervalForRequest = 20
            config.timeoutIntervalForResource = 30
            http = URLSession(configuration: config)
        }
        players = Players(http: http)
    }

    public func initialize() {
        Task {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            await warmUp()
        }
    }

    public func onSessionChanged() {
        locked {
            remembered = [:]
            exclusions = [:]
        }
        if host.configStore.enabled { Task { await warmUp() } }
    }

    private func warmUp() async {
        do {
            let session = await host.session()
            _ = try await players.current()
            await host.tokenProvider?.prewarm(cookie: session.cookie)
            log(.debug, "warm-up done")
        } catch {
            log(.warn, "warm-up failed: \(error)")
        }
    }

    /// The player API first, then the watch page. Throws only on cancellation.
    public func extract(videoId: String, maxKbps: Int, skipClients: Set<String> = [], requireM4a: Bool = false) async throws -> VyonExStream? {
        do {
            if let stream = try await extractPrimary(videoId: videoId, maxKbps: maxKbps, skipClients: skipClients, requireM4a: requireM4a) {
                return stream
            }
        } catch {
            try rethrowIfCancelled(error)
            log(.info, "player API failed, trying the watch page: \(error)", videoId)
        }
        return try await watchPageStream(videoId: videoId, maxKbps: maxKbps, skipClients: skipClients, requireM4a: requireM4a)
    }

    /// Player API extraction. Throws when YouTube says the video cannot be played.
    func extractPrimary(videoId: String, maxKbps: Int, skipClients: Set<String>, requireM4a: Bool) async throws -> VyonExStream? {
        let session = await host.session()
        let loggedIn = isLoggedIn(session.cookie)
        var visitorData = session.visitorData
        var unplayable: String?
        for id in identities {
            if skipClients.contains(id.profileId) || skipClients.contains(id.clientName) || isExcluded(videoId, id.profileId) { continue }
            if loggedIn && !id.supportsCookies { continue }
            do {
                var token: PoTokenResult?
                if id.needsPoToken {
                    guard let provider = host.tokenProvider, let visitorData,
                          let result = try await provider.poToken(videoId: videoId, visitorData: visitorData, cookie: session.cookie)
                    else { continue }
                    token = result
                }
                let pr = try await playerResponse(
                    id, videoId: videoId, session: session, visitorData: token?.visitorData ?? visitorData,
                    poToken: token?.playerRequestToken, loggedIn: loggedIn
                )
                visitorData = visitorData ?? at(pr, "responseContext", "visitorData") as? String
                let status = at(pr, "playabilityStatus", "status") as? String
                guard status == "OK" else {
                    let reason = "\(status ?? "nil"): \(at(pr, "playabilityStatus", "reason") as? String ?? "nil")"
                    unplayable = unplayable ?? reason
                    log(.debug, "\(id.profileId) refused (\(reason))", videoId)
                    continue
                }
                let formats = try await playableFormats(pr, poToken: token?.streamingDataToken, playerId: nil)
                guard let format = selectFormat(formats, maxKbps: maxKbps, requireM4a: requireM4a) else {
                    log(.debug, "\(id.profileId) has no usable audio format", videoId)
                    continue
                }
                if let stream = try await accept(videoId, format, pr, id.clientName, id.profileId, mediaHeaders(id.userAgent, session)) {
                    return stream
                }
            } catch {
                try rethrowIfCancelled(error)
                log(.warn, "\(id.profileId) failed: \(error)", videoId)
            }
        }
        if let unplayable { throw VyonExError("VyonEx: video \(videoId) is unplayable (\(unplayable))") }
        return nil
    }

    func watchPageStream(videoId: String, maxKbps: Int, skipClients: Set<String>, requireM4a: Bool) async throws -> VyonExStream? {
        if watchPageProfiles.contains(where: { skipClients.contains($0) || isExcluded(videoId, $0) }) { return nil }
        do {
            let session = await host.session()
            guard let page = try await watchPage(videoId, session),
                  let format = selectFormat(page.formats, maxKbps: maxKbps, requireM4a: requireM4a)
            else { return nil }
            return try await accept(videoId, format, page.playerResponse, "WEB", "WEB_WATCH_PAGE", watchHeaders(session))
        } catch {
            try rethrowIfCancelled(error)
            log(.warn, "watch page failed: \(error)", videoId)
            return nil
        }
    }

    public func audioCandidates(videoId: String) async -> [VyonExAudioCandidate] {
        do {
            let session = await host.session()
            let formats = try await watchPage(videoId, session)?.formats ?? []
            return formats.map {
                VyonExAudioCandidate(url: $0.url, kbps: ($0.averageBitrate ?? $0.bitrate) / 1000, mimeType: $0.mimeType, format: audioFormat(of: $0.mimeType))
            }
        } catch {
            log(.warn, "audio candidates failed: \(error)", videoId)
            return []
        }
    }

    public func headers(for url: String) -> [String: String]? {
        locked { remembered[url]?.headers }
    }

    public func onRefused(url: String) -> String? {
        guard let refused = locked({ remembered.removeValue(forKey: url) }) else { return nil }
        exclude(videoId: refused.videoId, profileId: refused.profileId)
        Task { await players.invalidate() }
        return refused.videoId
    }

    public func exclude(videoId: String, profileId: String) {
        log(.warn, "excluding \(profileId) for 10 minutes", videoId)
        let now = host.now
        locked {
            exclusions = exclusions.filter { $0.value > now }
            exclusions["\(videoId)/\(profileId)"] = now.addingTimeInterval(exclusionSeconds)
        }
    }

    func isExcluded(_ videoId: String, _ profileId: String) -> Bool {
        let now = host.now
        return locked { (exclusions["\(videoId)/\(profileId)"] ?? .distantPast) > now }
    }

    func remember(_ stream: VyonExStream) -> VyonExStream {
        locked {
            if remembered.count >= maxRemembered && remembered[stream.url] == nil { remembered = [:] }
            remembered[stream.url] = stream
        }
        return stream
    }

    private func playerResponse(
        _ id: Identity, videoId: String, session: VyonExSession, visitorData: String?, poToken: String?, loggedIn: Bool
    ) async throws -> JSONObject {
        var sts: Int?
        if id.needsPlayerJS { sts = try await players.current().sts }
        var request = URLRequest(url: URL(string: "\(ytOrigin)/youtubei/v1/player?prettyPrint=false")!)
        request.httpMethod = "POST"
        request.httpBody = try playerRequestBody(id, videoId: videoId, session: session, visitorData: visitorData, sts: sts, poToken: poToken)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(id.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue(String(id.clientId), forHTTPHeaderField: "X-YouTube-Client-Name")
        request.setValue(id.clientVersion, forHTTPHeaderField: "X-YouTube-Client-Version")
        request.setValue(ytOrigin, forHTTPHeaderField: "Origin")
        request.setValue(visitorData, forHTTPHeaderField: "X-Goog-Visitor-Id")
        if let cookie = session.cookie {
            request.setValue(cookie, forHTTPHeaderField: "Cookie")
            if loggedIn {
                request.setValue(sidAuthorization(cookie, now: host.now), forHTTPHeaderField: "Authorization")
                request.setValue(ytOrigin, forHTTPHeaderField: "X-Origin")
            }
        }
        let text = try await fetchText(http, request)
        guard let pr = try JSONSerialization.jsonObject(with: Data(text.utf8)) as? JSONObject else { throw VyonExError("player API: not an object") }
        return pr
    }

    private func watchPage(_ videoId: String, _ session: VyonExSession) async throws -> (playerResponse: JSONObject, formats: [AudioFormat])? {
        let url = "\(ytOrigin)/watch?v=\(encodeParameter(videoId))&hl=\(encodeParameter(session.language))&gl=\(encodeParameter(session.region))"
        var request = URLRequest(url: URL(string: url)!)
        for (name, value) in watchHeaders(session) { request.setValue(value, forHTTPHeaderField: name) }
        // Non-login cookies (consent) still matter for the page itself.
        request.setValue(session.cookie, forHTTPHeaderField: "Cookie")
        guard let page = parseWatchPage(try await fetchText(http, request)),
              at(page.playerResponse, "playabilityStatus", "status") as? String == "OK"
        else { return nil }
        return (page.playerResponse, try await playableFormats(page.playerResponse, poToken: nil, playerId: page.playerId))
    }

    private func playableFormats(_ pr: JSONObject, poToken: String?, playerId: String?) async throws -> [AudioFormat] {
        var result: [AudioFormat] = []
        for json in audioFormats(of: pr) {
            var url: String?
            do {
                url = try await playableURL(json) { type, challenge in
                    let player: PlayerJS
                    if let playerId { player = try await players.get(playerId) } else { player = try await players.current() }
                    return try await onJSThread { try player.solve(type, challenge) }
                }
            } catch {
                try rethrowIfCancelled(error)
                log(.debug, "could not decode itag \(int(json["itag"]).map { String($0) } ?? "?"): \(error)")
            }
            if let poToken, let decoded = url { url = "\(decoded)&pot=\(encodeParameter(poToken))" }
            if let url, isAllowedURL(url) { result.append(AudioFormat(json, url: url)) }
        }
        return result
    }

    /// BRIEF 4.2 only counts a format "whose URL works", so the URL is probed for one byte with the
    /// stream's own headers. A refusal excludes the profile, as onRefused would, and returns nil.
    private func accept(
        _ videoId: String, _ format: AudioFormat, _ pr: JSONObject, _ clientName: String, _ profileId: String, _ headers: [String: String]
    ) async throws -> VyonExStream? {
        guard let url = URL(string: format.url) else { return nil }
        var request = URLRequest(url: url)
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        request.setValue("bytes=0-0", forHTTPHeaderField: "Range")
        let (_, response) = try await http.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            log(.warn, "\(profileId) URL refused (HTTP \(status))", videoId)
            exclude(videoId: videoId, profileId: profileId)
            return nil
        }
        log(.info, "chose \(profileId) itag \(format.itag.map { String($0) } ?? "?") (\(format.mimeType), \(format.bitrate / 1000) kbps)", videoId)
        return remember(VyonExStream(
            videoId: videoId, url: format.url, kbps: format.bitrate / 1000, mimeType: format.mimeType,
            loudnessDb: at(pr, "playerConfig", "audioConfig", "loudnessDb") as? Double,
            clientName: clientName, profileId: profileId, headers: headers
        ))
    }

    private func mediaHeaders(_ userAgent: String, _ session: VyonExSession) -> [String: String] {
        var headers = ["User-Agent": userAgent]
        if isLoggedIn(session.cookie) { headers["Cookie"] = session.cookie }
        return headers
    }

    private func watchHeaders(_ session: VyonExSession) -> [String: String] {
        var headers = [
            "User-Agent": desktopUserAgent,
            "Accept": "*/*",
            "Accept-Language": "\(session.language)-\(session.region),\(session.language);q=0.9",
            "Referer": "\(ytOrigin)/",
            "Origin": ytOrigin,
        ]
        if isLoggedIn(session.cookie) { headers["Cookie"] = session.cookie }
        return headers
    }

    private func locked<T>(_ body: () throws -> T) rethrows -> T {
        lock.lock()
        defer { lock.unlock() }
        return try body()
    }

    private func log(_ level: VyonExLogLevel, _ message: String, _ trackId: String? = nil) {
        host.log(level, "VyonEx: \(message)", trackId: trackId)
    }
}

/// URLSession reports cancellation as URLError.cancelled; either way, let it through.
private func rethrowIfCancelled(_ error: Error) throws {
    if error is CancellationError || Task.isCancelled { throw CancellationError() }
}
