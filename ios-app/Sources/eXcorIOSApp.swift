import SwiftUI
import Foundation
import VyonEx

// SwiftUI is a native shell over the SAME Kotlin Multiplatform extraction/metadata engine.
final class IOSConfig: NSObject, VyonExConfigStore {
    var enabled: Bool { false }
    func getString(key: String) -> String? { nil }
    func getLong(key: String) -> KotlinLong? { nil }
    func putString(key: String, value: String) {}
    func putLong(key: String, value: Int64) {}
}

final class IOSHost: NSObject, VyonExHost {
    let configStore: VyonExConfigStore = IOSConfig()
    var tokenProvider: TokenProvider? { nil }

    func session() async throws -> VyonExSession {
        let locale = Locale.current
        return VyonExSession(
            cookie: nil, visitorData: nil,
            region: locale.regionCode ?? "US",
            language: locale.languageCode ?? "en"
        )
    }

    func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
    func log(level: VyonExLogLevel, message: String, trackId: String?) {
        // No provider tokens or media URLs are logged by this shell.
    }
}

private enum CatalogMode: String, CaseIterable, Identifiable {
    case link = "Inspect link"
    case deezerTracks = "Deezer tracks"
    case deezerAlbums = "Deezer albums"
    case deezerArtists = "Deezer artists"
    case spotifyTracks = "Spotify tracks"
    case spotifyAlbums = "Spotify albums"
    case spotifyArtists = "Spotify artists"
    case youtubeMusic = "YouTube Music"

    var id: String { rawValue }
}

private struct CatalogRow: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let imageURL: URL?
    let url: URL?
    let detail: String

    init(item: MusicCatalogItem) {
        id = "\(item.provider)-\(item.kind)-\(item.id)"
        title = item.title ?? item.id
        subtitle = item.artists.compactMap { $0.name }.joined(separator: ", ")
        imageURL = item.images.first.flatMap { URL(string: $0.url) }
        url = URL(string: item.url)
        let description = [
            "Catalog: \(item.provider)",
            "Type: \(item.kind)",
            item.album.map { "Album: \($0)" },
            item.releaseDate.map { "Released: \($0)" },
            item.durationMs.map { "Duration: \($0.int64Value / 1000) seconds" },
            item.isrc.map { "ISRC: \($0)" },
            item.upc.map { "UPC: \($0)" },
            item.label.map { "Label: \($0)" },
            item.copyright.map { "Copyright: \($0)" },
            item.fullyEnriched ? nil : "Spotify link preview; complete catalog metadata requires an OAuth token.",
        ].compactMap { $0 }
        detail = description.joined(separator: "\n")
    }

    init(result: VyonExSearchResult) {
        id = "\(result.kind)-\(result.id)"
        title = result.title ?? result.id
        subtitle = result.channelName ?? "YouTube Music"
        imageURL = result.thumbnails.first.flatMap { URL(string: $0.url) }
        switch result.kind {
        case .video: url = URL(string: "https://www.youtube.com/watch?v=\(result.id)")
        case .playlist: url = URL(string: "https://www.youtube.com/playlist?list=\(result.id)")
        case .album: url = URL(string: "https://music.youtube.com/browse/\(result.id)")
        default: url = URL(string: "https://www.youtube.com/channel/\(result.id)")
        }
        detail = "YouTube Music · \(subtitle)"
    }
}

@MainActor
private final class CatalogModel: ObservableObject {
    @Published var query = ""
    @Published var mode: CatalogMode = .link
    @Published var spotifyToken = ""
    @Published private(set) var rows: [CatalogRow] = []
    @Published private(set) var status = "Ready"
    @Published private(set) var isLoading = false

    private let engine = VyonEx(host: IOSHost())

    init() { engine.initialize() }

    func run() {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        let activeMode = mode
        let credential = spotifyToken.isEmpty ? nil : spotifyToken
        spotifyToken = "" // tokens are supplied per request, never saved.
        isLoading = true
        status = "Loading…"
        Task {
            do {
                let values: [CatalogRow]
                switch activeMode {
                case .link:
                    let result = try await engine.musicCatalog.lookup(url: text, spotifyAccessToken: credential)
                    values = result.map { [CatalogRow(item: $0)] } ?? []
                case .deezerTracks, .deezerAlbums, .deezerArtists:
                    let kind: MusicCatalogKind = activeMode == .deezerTracks ? .track : activeMode == .deezerAlbums ? .album : .artist
                    let items = try await engine.musicCatalog.searchDeezer(term: text, kind: kind, limit: 20)
                    values = items.map { CatalogRow(item: $0) }
                case .spotifyTracks, .spotifyAlbums, .spotifyArtists:
                    guard let credential, !credential.isEmpty else {
                        throw NSError(domain: "eXcor", code: 1,
                            userInfo: [NSLocalizedDescriptionKey: "Spotify search requires an OAuth access token."])
                    }
                    let kind: MusicCatalogKind = activeMode == .spotifyTracks ? .track : activeMode == .spotifyAlbums ? .album : .artist
                    let items = try await engine.musicCatalog.searchSpotify(term: text, kind: kind, accessToken: credential, limit: 10)
                    values = items.map { CatalogRow(item: $0) }
                case .youtubeMusic:
                    let page = try await engine.searchMusic(query: text)
                    values = page.results.map { CatalogRow(result: $0) }
                }
                rows = values
                status = "\(values.count) result(s)"
            } catch {
                rows = []
                status = "Request failed: \(error.localizedDescription)"
            }
            isLoading = false
        }
    }
}

private struct CoverView: View {
    let url: URL?
    var body: some View {
        AsyncImage(url: url) { image in
            image.resizable().scaledToFit()
        } placeholder: {
            Image(systemName: "music.note")
                .resizable().scaledToFit().foregroundColor(.secondary).padding(20)
        }
        .frame(width: 80, height: 80)
        .background(Color.secondary.opacity(0.07))
        .cornerRadius(8)
    }
}

struct ContentView: View {
    @StateObject private var model = CatalogModel()
    var body: some View {
        NavigationView {
            VStack(spacing: 12) {
                Picker("Catalog", selection: $model.mode) {
                    ForEach(CatalogMode.allCases) { mode in
                        Text(mode.rawValue).tag(mode)
                    }
                }
                .pickerStyle(.menu)
                TextField(model.mode == .link ? "Paste a Spotify or Deezer link" : "Search artists, albums or tracks", text: $model.query)
                    .textFieldStyle(.roundedBorder)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                if model.mode == .link || model.mode == .spotifyTracks || model.mode == .spotifyAlbums || model.mode == .spotifyArtists {
                    SecureField("Spotify OAuth token (optional for links)", text: $model.spotifyToken)
                        .textFieldStyle(.roundedBorder)
                }
                Button(action: model.run) {
                    if model.isLoading { ProgressView() } else { Text("Search / Inspect") }
                }.disabled(model.isLoading)
                Text(model.status).font(.footnote).foregroundColor(.secondary)
                List(model.rows) { row in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .top) {
                            CoverView(url: row.imageURL)
                            VStack(alignment: .leading) {
                                Text(row.title).font(.headline)
                                Text(row.subtitle).font(.subheadline).foregroundColor(.secondary)
                            }
                        }
                        Text(row.detail).font(.caption).foregroundColor(.secondary)
                        if let url = row.url {
                            Link("Open in source catalog", destination: url)
                                .font(.footnote)
                        }
                    }.padding(.vertical, 6)
                }
            }
            .padding(.horizontal)
            .navigationTitle("eXcor")
        }
    }
}

@main
struct VyonExIOSApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
