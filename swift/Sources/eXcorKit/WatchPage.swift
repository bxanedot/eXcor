import Foundation

struct ExcorWatchPage: Sendable {
    let apiKey: String
    let playerResponse: ExcorPlayerResponse
    let playerScriptURL: URL?
}

func parseWatchPage(_ html: String) throws -> ExcorWatchPage {
    guard html.utf8.count <= 8 * 1024 * 1024,
          let rawResponse = embeddedObject(afterAny: ["var ytInitialPlayerResponse", "ytInitialPlayerResponse"], in: html),
          let responseData = rawResponse.data(using: .utf8)
    else { throw ExcorError.invalidWatchPage }

    let response = try decodePlayerResponse(responseData)
    guard let apiKey = capture(#""INNERTUBE_API_KEY"\s*:\s*"([^"\\]+)""#, in: html) else {
        throw ExcorError.missingPlayerApiKey
    }
    let scriptValue = response.playerScriptPath ?? capture(#"(?:PLAYER_JS_URL|jsUrl)"\s*[:=]\s*"([^"\\]+)""#, in: html)
    let baseURL = URL(string: "https://www.youtube.com")
    let scriptURL = scriptValue.flatMap { value in baseURL.flatMap { URL(string: value, relativeTo: $0)?.absoluteURL } }
    if let scriptURL {
        guard scriptURL.scheme == "https",
              scriptURL.host == "youtube.com" || scriptURL.host?.hasSuffix(".youtube.com") == true
        else { throw ExcorError.invalidPlayerScriptURL }
    }
    return ExcorWatchPage(apiKey: apiKey, playerResponse: response, playerScriptURL: scriptURL)
}

private func capture(_ pattern: String, in text: String) -> String? {
    guard let expression = try? NSRegularExpression(pattern: pattern),
          let match = expression.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
          let range = Range(match.range(at: 1), in: text)
    else { return nil }
    return String(text[range])
}

private func embeddedObject(afterAny markers: [String], in text: String) -> String? {
    for marker in markers {
        guard let markerRange = text.range(of: marker),
              let opening = text[markerRange.upperBound...].firstIndex(of: "{")
        else { continue }
        var depth = 0
        var quoted = false
        var escaped = false
        var end: String.Index?
        for index in text[opening...].indices {
            let char = text[index]
            if quoted {
                if escaped { escaped = false }
                else if char == "\\" { escaped = true }
                else if char == "\"" { quoted = false }
            } else if char == "\"" {
                quoted = true
            } else if char == "{" {
                depth += 1
            } else if char == "}" {
                depth -= 1
                if depth == 0 { end = text.index(after: index); break }
            }
        }
        if let end { return String(text[opening..<end]) }
    }
    return nil
}

enum ExcorError: Error, LocalizedError, Sendable {
    case invalidWatchPage
    case missingPlayerApiKey
    case invalidPlayerScriptURL
    case httpStatus(Int)
    case oversizedResponse
    case unplayable(String)
    case invalidPlayerData
    case missingPlayerScript
    case challengeFailed
    case playerScriptTimedOut

    var errorDescription: String? {
        switch self {
        case .invalidWatchPage: return "eXcor: watch page has no valid player response"
        case .missingPlayerApiKey: return "eXcor: watch page has no player API key"
        case .invalidPlayerScriptURL: return "eXcor: player script URL is not an allowed YouTube URL"
        case .httpStatus(let status): return "eXcor: YouTube returned HTTP \(status)"
        case .oversizedResponse: return "eXcor: response exceeded the size limit"
        case .unplayable(let reason): return "eXcor: video is unplayable (\(reason))"
        case .invalidPlayerData: return "eXcor: YouTube returned malformed player data"
        case .missingPlayerScript: return "eXcor: player script URL is missing"
        case .challengeFailed: return "eXcor: player challenge could not be solved"
        case .playerScriptTimedOut: return "eXcor: player script exceeded its execution time limit"
        }
    }
}
