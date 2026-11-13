import XCTest
import Foundation
@testable import eXcorKit

final class LivePlaybackTests: XCTestCase {
    func testLiveVideosReturnRangeReadableAudio() async throws {
        guard ProcessInfo.processInfo.environment["EXCOR_LIVE"] == "1" else {
            throw XCTSkip("Set EXCOR_LIVE=1 to run live YouTube checks")
        }
        let ids = (ProcessInfo.processInfo.environment["EXCOR_LIVE_IDS"] ?? "dQw4w9WgXcQ,CevxZvSJLk8,9bZkp7q19f0")
            .split(separator: ",").map(String.init)
        guard ids.count >= 3 else { throw XCTSkip("EXCOR_LIVE_IDS must provide three public music video IDs") }
        let kit = ExcorKit(host: TestHost())
        for videoId in ids.prefix(3) {
            guard let stream = try await kit.extract(videoId: videoId, maxKbps: 128) else {
                XCTFail("No playable stream for \(videoId)")
                continue
            }
            var request = URLRequest(url: try XCTUnwrap(URL(string: stream.url)))
            request.setValue("bytes=0-1023", forHTTPHeaderField: "Range")
            for (name, value) in stream.headers { request.setValue(value, forHTTPHeaderField: name) }
            let (_, response) = try await URLSession.shared.data(for: request)
            let status = try XCTUnwrap((response as? HTTPURLResponse)?.statusCode)
            XCTAssertTrue([200, 206].contains(status), "Unexpected range response: \(status)")
        }
    }
}
