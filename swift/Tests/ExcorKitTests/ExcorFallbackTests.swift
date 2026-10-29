import XCTest
import Foundation
@testable import eXcorKit

final class ExcorFallbackTests: XCTestCase {
    override func setUp() {
        super.setUp()
        ExcorStubURLProtocol.reset()
    }

    override func tearDown() {
        ExcorStubURLProtocol.reset()
        super.tearDown()
    }

    func testAudioCandidatesIgnoreVideoChallengesAndReturnAllowedAudio() async throws {
        let html = try CleanroomFixture.read("watch-page.html")
        let session = makeSession { request in
            return (200, Data(html.utf8))
        }
        let kit = ExcorKit(host: TestHost(urlSession: session))

        let candidates = await kit.audioCandidates(videoId: "video")
        XCTAssertEqual(candidates.count, 1)
        XCTAssertEqual(candidates[0].format, .m4a)
        XCTAssertEqual(candidates[0].kbps, 127)
    }

    func testWatchRequestIncludesCurrentSessionContext() async throws {
        let html = try CleanroomFixture.read("watch-page.html")
        let recorder = ExcorRequestRecorder()
        let session = makeSession { request in
            recorder.append(request)
            return (200, Data(html.utf8))
        }
        let kit = ExcorKit(host: TestHost(cookie: "SID=session", urlSession: session, visitorData: "visitor-current", region: "CA", language: "fr"))

        let candidates = await kit.audioCandidates(videoId: "video")
        let request = try XCTUnwrap(recorder.requests().first { $0.url?.path == "/watch" })
        let components = try XCTUnwrap(URLComponents(url: try XCTUnwrap(request.url), resolvingAgainstBaseURL: false))
        let query = Dictionary(uniqueKeysWithValues: try XCTUnwrap(components.queryItems).map { ($0.name, $0.value ?? "") })
        XCTAssertEqual(candidates.count, 1)
        XCTAssertEqual(query["hl"], "fr")
        XCTAssertEqual(query["gl"], "CA")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Cookie"), "SID=session")
        XCTAssertEqual(request.value(forHTTPHeaderField: "X-Goog-Visitor-Id"), "visitor-current")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Accept-Language"), "fr-CA,fr;q=0.9")
    }

    func testPlayerScriptRequestIncludesCurrentSessionContext() async throws {
        let html = try CleanroomFixture.read("watch-page.html")
            .replacingOccurrences(of: "range=0-1023", with: "range=0-1023&n=needs-transform")
        let recorder = ExcorRequestRecorder()
        let session = makeSession { request in
            recorder.append(request)
            if request.url?.path == "/watch" { return (200, Data(html.utf8)) }
            if request.url?.path == "/s/player/fixture/base.js" { return (200, Data()) }
            return (404, Data())
        }
        let kit = ExcorKit(host: TestHost(cookie: "SID=session", urlSession: session, visitorData: "visitor-current", region: "CA", language: "fr"))

        _ = await kit.audioCandidates(videoId: "video")
        let request = try XCTUnwrap(recorder.requests().first { $0.url?.path == "/s/player/fixture/base.js" })
        XCTAssertEqual(request.value(forHTTPHeaderField: "Cookie"), "SID=session")
        XCTAssertEqual(request.value(forHTTPHeaderField: "X-Goog-Visitor-Id"), "visitor-current")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Accept-Language"), "fr-CA,fr;q=0.9")
    }

    func testPrimaryFailureRunsWatchPageFallback() async throws {
        let html = try CleanroomFixture.read("watch-page.html")
        let session = makeSession { request in
            if request.url?.path == "/watch" { return (200, Data(html.utf8)) }
            if request.url?.path == "/youtubei/v1/player" { return (503, Data()) }
            return (404, Data())
        }
        let kit = ExcorKit(host: TestHost(urlSession: session))

        let stream = try await kit.extract(videoId: "video", maxKbps: 128)
        let headers = await kit.headers(for: stream?.url ?? "")
        XCTAssertEqual(stream?.clientName, "WEB")
        XCTAssertEqual(stream?.profileId, "WEB_WATCH_PAGE")
        XCTAssertEqual(stream?.kbps, 128)
        XCTAssertEqual(headers?["Referer"], "https://www.youtube.com/")
    }

    func testSkipWebPreventsWatchPageFallback() async throws {
        let html = try CleanroomFixture.read("watch-page.html")
        let session = makeSession { request in
            if request.url?.path == "/watch" { return (200, Data(html.utf8)) }
            if request.url?.path == "/youtubei/v1/player" { return (503, Data()) }
            return (404, Data())
        }
        let kit = ExcorKit(host: TestHost(urlSession: session))

        let stream = try await kit.extract(videoId: "video", maxKbps: 128, skipClients: ["web"])
        XCTAssertNil(stream)
    }

    func testSessionChangeDiscardsAnInFlightStream() async throws {
        let html = try CleanroomFixture.read("watch-page.html")
        let gate = ExcorRequestGate()
        let session = makeSession { request in
            if request.url?.path == "/watch" { return (200, Data(html.utf8)) }
            if request.url?.path == "/youtubei/v1/player" { return gate.suspendResponse() }
            return (503, Data())
        }
        let kit = ExcorKit(host: TestHost(cookie: "old=1", urlSession: session))
        let extraction = Task {
            try await kit.extract(videoId: "video", maxKbps: 128, skipClients: ["web", "visionos"])
        }

        let requestStarted = await gate.waitUntilStarted()
        XCTAssertTrue(requestStarted)
        await kit.onSessionChanged()
        gate.release()

        do {
            _ = try await extraction.value
            XCTFail("A result from the prior session must be discarded")
        } catch is CancellationError {
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
        let headers = await kit.headers(for: "https://rr1---sn.googlevideo.com/videoplayback?c=WEB&range=0-1023")
        XCTAssertNil(headers)
    }

    func testCancellationDuringWatchRequestPropagates() async throws {
        let gate = ExcorRequestGate()
        let session = makeSession { request in
            if request.url?.path == "/watch" { return gate.suspendResponse() }
            return (503, Data())
        }
        let kit = ExcorKit(host: TestHost(urlSession: session))
        let extraction = Task { try await kit.extract(videoId: "video", maxKbps: 128) }

        let requestStarted = await gate.waitUntilStarted()
        XCTAssertTrue(requestStarted)
        extraction.cancel()
        gate.release()

        do {
            _ = try await extraction.value
            XCTFail("Cancellation during transport must propagate")
        } catch is CancellationError {
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    private func makeSession(_ handler: @escaping @Sendable (URLRequest) -> (Int, Data)) -> URLSession {
        ExcorStubURLProtocol.handler = handler
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ExcorStubURLProtocol.self]
        return URLSession(configuration: configuration)
    }
}

private final class ExcorStubURLProtocol: URLProtocol, @unchecked Sendable {
    private final class State: @unchecked Sendable {
        let lock = NSLock()
        var requestHandler: (@Sendable (URLRequest) -> (Int, Data))?
    }
    private static let state = State()

    static var handler: (@Sendable (URLRequest) -> (Int, Data))? {
        get { state.lock.lock(); defer { state.lock.unlock() }; return state.requestHandler }
        set { state.lock.lock(); state.requestHandler = newValue; state.lock.unlock() }
    }

    static func reset() { handler = nil }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        guard let url = request.url else { client?.urlProtocol(self, didFailWithError: URLError(.badURL)); return }
        let result = Self.handler?(request) ?? (404, Data())
        guard let response = HTTPURLResponse(url: url, statusCode: result.0, httpVersion: "HTTP/1.1", headerFields: ["Content-Length": String(result.1.count)]) else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        if !result.1.isEmpty { client?.urlProtocol(self, didLoad: result.1) }
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
