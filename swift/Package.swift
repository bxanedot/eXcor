// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "VyonExKit",
    platforms: [.iOS(.v15), .macOS(.v12)],
    products: [.library(name: "VyonExKit", targets: ["VyonExKit"])],
    targets: [
        // ejs/ holds yt-dlp's challenge solver (Unlicense); the Kotlin build compiles in the same files.
        .target(name: "VyonExKit", resources: [.copy("ejs")]),
        .testTarget(name: "VyonExKitTests", dependencies: ["VyonExKit"]),
    ]
)
