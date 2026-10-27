import CryptoKit
import Foundation

let ytOrigin = "https://www.youtube.com"
let desktopUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

struct Identity {
    let profileId: String
    let clientName: String
    let clientId: Int
    let clientVersion: String
    let userAgent: String
    var device: [String: String] = [:]
    let needsPlayerJS: Bool
    let supportsCookies: Bool
    let needsPoToken: Bool
}

// yt-dlp 2026.10.21 defaults to tv_samsung, visionos, web (values from its INNERTUBE_CLIENTS).
// Live on 2026-10-26: TVHTML5 gave ciphered URLs that play once decoded, VISIONOS gave direct URLs,
// WEB gave SABR-only formats without a PO token. VISIONOS does not take cookies, so like yt-dlp
// it is skipped for logged-in sessions.
let identities = [
    Identity(
        profileId: "TV_SAMSUNG", clientName: "TVHTML5", clientId: 7, clientVersion: "5.20260707",
        userAgent: "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
        device: ["deviceMake": "Samsung", "deviceModel": "UKS9800", "osName": "Tizen", "osVersion": "2.4.0"],
        needsPlayerJS: true, supportsCookies: true, needsPoToken: false
    ),
    Identity(
        profileId: "VISIONOS", clientName: "VISIONOS", clientId: 101, clientVersion: "1.02",
        userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        device: ["deviceMake": "Apple", "deviceModel": "RealityDevice17,1", "osName": "visionOS", "osVersion": "26.5.23O471"],
        needsPlayerJS: false, supportsCookies: false, needsPoToken: false
    ),
    Identity(
        profileId: "WEB", clientName: "WEB", clientId: 1, clientVersion: "2.20260708.00.00", userAgent: desktopUserAgent,
        needsPlayerJS: true, supportsCookies: true, needsPoToken: true
    ),
]

func playerRequestBody(
    _ id: Identity, videoId: String, session: VyonExSession, visitorData: String?, sts: Int?, poToken: String?
) throws -> Data {
    var client: [String: Any] = id.device
    client["clientName"] = id.clientName
    client["clientVersion"] = id.clientVersion
    client["userAgent"] = id.userAgent
    client["hl"] = session.language
    client["gl"] = session.region
    client["visitorData"] = visitorData
    client["timeZone"] = "UTC"
    client["utcOffsetMinutes"] = 0
    var playback: [String: Any] = ["html5Preference": "HTML5_PREF_WANTS"]
    playback["signatureTimestamp"] = sts
    var body: [String: Any] = [
        "context": ["client": client],
        "videoId": videoId,
        "playbackContext": ["contentPlaybackContext": playback],
        "contentCheckOk": true,
        "racyCheckOk": true,
    ]
    if let poToken { body["serviceIntegrityDimensions"] = ["poToken": poToken] }
    return try JSONSerialization.data(withJSONObject: body)
}

func cookieValue(_ cookie: String?, _ name: String) -> String? {
    for part in (cookie ?? "").split(separator: ";") {
        let pair = part.trimmingCharacters(in: .whitespaces).split(separator: "=", maxSplits: 1)
        if pair.count == 2, pair[0] == name { return String(pair[1]) }
    }
    return nil
}

// Same test as yt-dlp: LOGIN_INFO plus one of the SAPISID cookies.
func isLoggedIn(_ cookie: String?) -> Bool {
    cookieValue(cookie, "LOGIN_INFO") != nil
        && ["SAPISID", "__Secure-1PAPISID", "__Secure-3PAPISID"].contains { cookieValue(cookie, $0) != nil }
}

// Port of yt-dlp's _get_sid_authorization_header: "<scheme> <ts>_<sha1("<ts> <sid> <origin>")>".
func sidAuthorization(_ cookie: String, now: Date) -> String? {
    let ts = Int(now.timeIntervalSince1970)
    let schemes: [(String, String?)] = [
        ("SAPISIDHASH", cookieValue(cookie, "SAPISID") ?? cookieValue(cookie, "__Secure-3PAPISID")),
        ("SAPISID1PHASH", cookieValue(cookie, "__Secure-1PAPISID")),
        ("SAPISID3PHASH", cookieValue(cookie, "__Secure-3PAPISID")),
    ]
    let parts = schemes.compactMap { scheme, sid in
        sid.map { sid in
            let hash = Insecure.SHA1.hash(data: Data("\(ts) \(sid) \(ytOrigin)".utf8)).map { String(format: "%02x", $0) }.joined()
            return "\(scheme) \(ts)_\(hash)"
        }
    }
    return parts.isEmpty ? nil : parts.joined(separator: " ")
}

typealias JSONObject = [String: Any]

func at(_ value: Any?, _ keys: String...) -> Any? {
    keys.reduce(value) { ($0 as? JSONObject)?[$1] }
}

func int(_ value: Any?) -> Int? {
    if let n = value as? Int { return n }
    if let d = value as? Double { return Int(d) }
    if let s = value as? String { return Int(s) }
    return nil
}

struct AudioFormat {
    let itag: Int?
    let url: String
    let mimeType: String
    let bitrate: Int
    let averageBitrate: Int?
    let channels: Int?
    let sampleRate: Int?
    let loudnessDb: Double?

    init(_ json: JSONObject, url: String) {
        itag = int(json["itag"])
        self.url = url
        mimeType = json["mimeType"] as? String ?? ""
        bitrate = int(json["bitrate"]) ?? 0
        averageBitrate = int(json["averageBitrate"])
        channels = int(json["audioChannels"])
        sampleRate = int(json["audioSampleRate"])
        loudnessDb = json["loudnessDb"] as? Double
    }
}

func audioFormats(of playerResponse: JSONObject) -> [JSONObject] {
    guard let data = playerResponse["streamingData"] as? JSONObject else { return [] }
    return ["formats", "adaptiveFormats"].flatMap { data[$0] as? [JSONObject] ?? [] }.filter {
        // Live, OTF and DRM formats cannot be fetched directly with byte ranges.
        ($0["mimeType"] as? String ?? "").hasPrefix("audio/") && $0["targetDurationSec"] == nil
            && $0["type"] as? String != "FORMAT_STREAM_TYPE_OTF" && $0["drmFamilies"] == nil
    }
}

private let unreserved = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")

func encodeParameter(_ s: String) -> String { s.addingPercentEncoding(withAllowedCharacters: unreserved) ?? s }

private func regex(_ pattern: String) -> NSRegularExpression { try! NSRegularExpression(pattern: pattern) }

private func firstGroup(_ re: NSRegularExpression, _ text: String) -> String? {
    guard let m = re.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)), let r = Range(m.range(at: 1), in: text)
    else { return nil }
    return String(text[r])
}

private let nParam = regex("([?&]n=)([^&]*)")

/// The format's URL with signature and `n` decoded, or nil when it has none (SABR-only).
func playableURL(_ format: JSONObject, solve: (_ type: String, _ challenge: String) async throws -> String) async throws -> String? {
    var url: String
    if let direct = format["url"] as? String {
        url = direct
    } else {
        guard let cipher = format["signatureCipher"] as? String,
              let items = URLComponents(string: "?" + cipher)?.queryItems,
              let base = items.first(where: { $0.name == "url" })?.value,
              let s = items.first(where: { $0.name == "s" })?.value
        else { return nil }
        let sp = items.first(where: { $0.name == "sp" })?.value ?? "signature"
        url = "\(base)&\(sp)=\(encodeParameter(try await solve("sig", s)))"
    }
    if let m = nParam.firstMatch(in: url, range: NSRange(url.startIndex..., in: url)),
       let whole = Range(m.range, in: url), let prefix = Range(m.range(at: 1), in: url), let value = Range(m.range(at: 2), in: url) {
        let n = try await solve("n", String(url[value]).removingPercentEncoding ?? String(url[value]))
        url.replaceSubrange(whole, with: url[prefix] + encodeParameter(n))
    }
    return url
}

// Checked on the raw string, so the host's URL parser cannot read a different host than we did.
private let allowedURL = regex(#"\Ahttps://(?:[a-z0-9-]+\.)*(?:googlevideo|youtube)\.com(?::443)?/videoplayback(?:\?[^#\s\\]*)?\z"#)

func isAllowedURL(_ url: String) -> Bool {
    allowedURL.firstMatch(in: url, range: NSRange(url.startIndex..., in: url)) != nil
}

private func autoKey(_ f: AudioFormat) -> (Int, Int, Int) {
    let codec = f.mimeType.hasPrefix("audio/webm") ? 2 : f.mimeType.hasPrefix("audio/mp4") ? 1 : 0
    let bonus = f.channels == 2 ? 50_000 : f.channels == nil ? 25_000 : 0
    return (codec, f.bitrate + bonus, min(f.sampleRate ?? 0, 48_000))
}

func selectFormat(_ formats: [AudioFormat], maxKbps: Int, requireM4a: Bool) -> AudioFormat? {
    let mp4 = formats.filter { $0.mimeType.hasPrefix("audio/mp4") }
    if requireM4a { return mp4.max { autoKey($0) < autoKey($1) } }
    if maxKbps <= 64 { return mp4.min { $0.bitrate < $1.bitrate } ?? formats.min { $0.bitrate < $1.bitrate } }
    // Above 64 kbps maxKbps is deliberately not a cap.
    let webm = formats.filter { $0.mimeType.hasPrefix("audio/webm") }
    return (webm.isEmpty ? formats : webm).max { autoKey($0) < autoKey($1) }
}

func audioFormat(of mimeType: String) -> VyonExAudioFormat {
    if mimeType.hasPrefix("audio/mp4") { return .m4a }
    if mimeType.hasPrefix("audio/webm"), mimeType.contains("opus") { return .webmOpus }
    return .other
}

private let playerIdPattern = regex(#"player\\?/([0-9a-fA-F]{8,})\\?/"#)

/// Player version from a page or the iframe API, which escapes its slashes.
func playerId(of text: String) -> String? { firstGroup(playerIdPattern, text) }

private let initialPlayerResponse = regex(#"ytInitialPlayerResponse\s*=\s*\{"#)

func parseWatchPage(_ html: String) -> (playerResponse: JSONObject, playerId: String?)? {
    guard let m = initialPlayerResponse.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
          let r = Range(m.range, in: html)
    else { return nil }
    // UTF-8 bytes are safe to scan: multi-byte sequences never contain ASCII braces or quotes.
    let bytes = Array(html[html.index(before: r.upperBound)...].utf8)
    var depth = 0, inString = false, i = 0
    while i < bytes.count {
        let c = bytes[i]
        if inString {
            if c == UInt8(ascii: "\\") { i += 1 } else if c == UInt8(ascii: "\"") { inString = false }
        } else if c == UInt8(ascii: "\"") {
            inString = true
        } else if c == UInt8(ascii: "{") {
            depth += 1
        } else if c == UInt8(ascii: "}") {
            depth -= 1
            if depth == 0 {
                guard let pr = try? JSONSerialization.jsonObject(with: Data(bytes[...i])) as? JSONObject else { return nil }
                return (pr, playerId(of: html))
            }
        }
        i += 1
    }
    return nil
}
