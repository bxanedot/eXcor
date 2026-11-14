// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "eXcorKit",
    platforms: [.iOS(.v15), .macOS(.v12)],
    products: [.library(name: "eXcorKit", targets: ["eXcorKit"])],
    targets: [
        .target(name: "eXcorKit", resources: [.copy("Resources")]),
        .testTarget(name: "eXcorKitTests", dependencies: ["eXcorKit"]),
    ]
)
