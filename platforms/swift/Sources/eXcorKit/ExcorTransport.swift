import Foundation

struct ExcorTransport {
    let session: URLSession

    init(session: URLSession?) {
        if let session {
            self.session = session
        } else {
            let configuration = URLSessionConfiguration.ephemeral
            configuration.timeoutIntervalForRequest = 30
            configuration.timeoutIntervalForResource = 20
            configuration.httpCookieStorage = nil
            configuration.httpShouldSetCookies = false
            configuration.urlCache = nil
            self.session = URLSession(configuration: configuration)
        }
    }

    func data(for request: URLRequest, maximumBytes: Int) async throws -> (Data, HTTPURLResponse) {
        try Task.checkCancellation()
        let redirectPolicy = ExcorRedirectPolicy()
        let (bytes, response) = try await session.bytes(for: request, delegate: redirectPolicy)
        try Task.checkCancellation()
        guard let response = response as? HTTPURLResponse else { throw ExcorError.invalidPlayerData }
        if let length = response.value(forHTTPHeaderField: "Content-Length").flatMap(Int.init), length > maximumBytes {
            throw ExcorError.oversizedResponse
        }
        guard (200..<300).contains(response.statusCode) else { throw ExcorError.httpStatus(response.statusCode) }
        var data = Data()
        data.reserveCapacity(min(maximumBytes, 64 * 1024))
        for try await byte in bytes {
            try Task.checkCancellation()
            guard data.count < maximumBytes else { throw ExcorError.oversizedResponse }
            data.append(byte)
        }
        return (data, response)
    }
}

private final class ExcorRedirectPolicy: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        guard let url = request.url else { completionHandler(nil); return }
        completionHandler(isAllowedYouTubeTransportURL(url, includeMediaHost: true) ? request : nil)
    }
}

func isAllowedYouTubeTransportURL(_ url: URL, includeMediaHost: Bool = false) -> Bool {
    guard let parts = URLComponents(url: url, resolvingAgainstBaseURL: false),
          parts.scheme?.lowercased() == "https",
          parts.port == nil || parts.port == 443,
          parts.user == nil,
          parts.password == nil,
          let host = parts.host?.lowercased()
    else { return false }
    let youtube = host == "youtube.com" || host.hasSuffix(".youtube.com")
    let media = host == "googlevideo.com" || host.hasSuffix(".googlevideo.com")
    return youtube || (includeMediaHost && media)
}
