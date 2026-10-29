import Foundation

func makePlayerRequestBody(
    videoId: String,
    session: ExcorSession,
    identity: ExcorClientIdentity,
    token: PoTokenResult?,
    signatureTimestamp: Int?
) -> [String: Any] {
    var client: [String: Any] = [
        "clientName": identity.name,
        "clientVersion": identity.version,
        "hl": requestLanguage(session),
        "gl": requestRegion(session),
    ]
    if let visitorData = token?.visitorData ?? session.visitorData { client["visitorData"] = visitorData }
    var body: [String: Any] = ["videoId": videoId, "context": ["client": client]]
    if let token { body["serviceIntegrityDimensions"] = ["poToken": token.playerRequestToken] }
    if let signatureTimestamp {
        body["playbackContext"] = ["contentPlaybackContext": ["signatureTimestamp": signatureTimestamp]]
    }
    return body
}

func playerRequestHeaders(session: ExcorSession, identity: ExcorClientIdentity) -> [String: String] {
    var headers = sessionRequestHeaders(session: session, userAgent: identity.userAgent, accept: "application/json")
    headers.merge([
        "Content-Type": "application/json",
        "Origin": "https://www.youtube.com",
        "Referer": "https://www.youtube.com/",
        "X-YouTube-Client-Name": String(identity.id),
        "X-YouTube-Client-Version": identity.version,
    ]) { _, new in new }
    return headers
}

func requestLanguage(_ session: ExcorSession) -> String {
    session.language.isEmpty ? "en" : session.language
}

func requestRegion(_ session: ExcorSession) -> String {
    session.region.isEmpty ? "US" : session.region
}

func acceptLanguage(_ session: ExcorSession) -> String {
    let language = requestLanguage(session)
    return "\(language)-\(requestRegion(session)),\(language);q=0.9"
}

func sessionRequestHeaders(session: ExcorSession, userAgent: String, accept: String) -> [String: String] {
    var headers = [
        "User-Agent": userAgent,
        "Accept": accept,
        "Accept-Language": acceptLanguage(session),
    ]
    if let cookie = session.requestCookie { headers["Cookie"] = cookie }
    if let visitor = session.visitorData, !visitor.isEmpty { headers["X-Goog-Visitor-Id"] = visitor }
    return headers
}

struct ExcorCipherParameters: CustomStringConvertible {
    let url: String?
    let signature: String?
    let encryptedSignature: String?
    let signatureParameter: String
    var description: String { "ExcorCipherParameters(url: <redacted>, signature: <redacted>)" }
}

func parseSignatureCipher(_ raw: String, fallbackURL: String? = nil) -> ExcorCipherParameters {
    let values = queryParameters(raw)
    return ExcorCipherParameters(
        url: values["url"] ?? fallbackURL,
        signature: values["sig"] ?? values["signature"],
        encryptedSignature: values["s"],
        signatureParameter: values["sp"] ?? "signature"
    )
}

func playableMediaURL(
    base: String,
    signature: String?,
    encryptedSignature: String?,
    signatureParameter: String,
    signatures: [String: String],
    throttling: [String: String],
    streamingToken: String?
) -> String? {
    var result = base
    if let encryptedSignature {
        guard let decoded = signatures[encryptedSignature], let updated = replacingQueryParameter(signatureParameter, decoded, in: result) else { return nil }
        result = updated
    } else if let signature {
        guard let updated = replacingQueryParameter(signatureParameter, signature, in: result) else { return nil }
        result = updated
    }
    if let n = queryParameter("n", in: result) {
        guard let decoded = throttling[n], let updated = replacingQueryParameter("n", decoded, in: result) else { return nil }
        result = updated
    }
    if let streamingToken {
        guard let updated = replacingQueryParameter("pot", streamingToken, in: result) else { return nil }
        result = updated
    }
    return isAllowedPlaybackURL(result) ? result : nil
}

func queryParameters(_ raw: String) -> [String: String] {
    guard let parts = URLComponents(string: "https://cipher.invalid/?\(raw)") else { return [:] }
    return (parts.queryItems ?? []).reduce(into: [:]) { $0[$1.name] = $1.value ?? "" }
}

func queryParameter(_ name: String, in raw: String) -> String? {
    URLComponents(string: raw)?.queryItems?.first(where: { $0.name == name })?.value
}

func replacingQueryParameter(_ name: String, _ value: String, in raw: String) -> String? {
    guard var parts = URLComponents(string: raw) else { return nil }
    let original = parts.queryItems ?? []
    var items = [URLQueryItem]()
    var replaced = false
    for item in original {
        if item.name == name {
            if !replaced {
                items.append(URLQueryItem(name: name, value: value))
                replaced = true
            }
        } else {
            items.append(item)
        }
    }
    if !replaced { items.append(URLQueryItem(name: name, value: value)) }
    parts.queryItems = items
    return parts.string
}
