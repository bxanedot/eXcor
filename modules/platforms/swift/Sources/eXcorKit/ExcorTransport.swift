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
        // A host-provided URLSession may have a cookie jar; only explicit request headers may carry session cookies.
        var cookieSafeRequest = request
        cookieSafeRequest.httpShouldHandleCookies = false
        let (bytes, response) = try await session.bytes(for: cookieSafeRequest, delegate: redirectPolicy)
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
        completionHandler(safeYouTubeRedirectRequest(request, from: response.url))
    }
}

func safeYouTubeRedirectRequest(_ request: URLRequest, from source: URL?) -> URLRequest? {
    guard let destination = request.url, isAllowedYouTubeTransportURL(destination, includeMediaHost: true) else { return nil }
    guard let source, sameAuthority(source, destination) else {
        var safeRequest = request
        safeRequest.httpShouldHandleCookies = false
        ["Authorization", "Proxy-Authorization", "Cookie", "Cookie2", "X-Goog-Visitor-Id"].forEach {
            safeRequest.setValue(nil, forHTTPHeaderField: $0)
        }
        return safeRequest
    }
    return request
}

private func sameAuthority(_ lhs: URL, _ rhs: URL) -> Bool {
    lhs.scheme?.lowercased() == rhs.scheme?.lowercased() &&
        lhs.host?.lowercased() == rhs.host?.lowercased() &&
        (lhs.port ?? 443) == (rhs.port ?? 443)
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
