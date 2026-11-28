// swift-tools-version: 5.7
import PackageDescription

let package = Package(
    name: "KuraBarcodeKit",
    products: [.library(name: "KuraBarcodeKit", targets: ["KuraBarcodeKit"])],
    targets: [
        .target(name: "KuraBarcodeKit", cSettings: [.define("ZINT_NO_PNG")],
                linkerSettings: [.linkedLibrary("m")])
    ]
)
