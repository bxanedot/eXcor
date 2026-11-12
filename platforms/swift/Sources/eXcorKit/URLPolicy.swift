import Foundation

func isAllowedPlaybackURL(_ raw: String) -> Bool {
    guard let parts = URLComponents(string: raw),
          parts.scheme?.lowercased() == "https",
          parts.port == nil || parts.port == 443,
          parts.user == nil,
          parts.password == nil,
          parts.path == "/videoplayback",
          let host = parts.host?.lowercased()
    else { return false }

    return host == "googlevideo.com" || host.hasSuffix(".googlevideo.com") ||
        host == "youtube.com" || host.hasSuffix(".youtube.com")
}
