import CoreGraphics
import Foundation
import KuraBarcodeKit

actor CardQR {
    static let shared = CardQR()

    func image(for payload: String, format: BarcodeFormat = .qr) -> CGImage? {
        guard !payload.isEmpty, payload.utf8.count <= 8192, !Task.isCancelled else { return nil }
        let encoded = format == .isbn ? payload.filter { $0 != "-" && $0 != " " } : payload
        var raster = KuraBarcodeImage()
        let ok = Data(encoded.utf8).withUnsafeBytes { bytes in
            kura_barcode_render(format.rawValue, bytes.bindMemory(to: UInt8.self).baseAddress,
                                bytes.count, &raster)
        }
        guard ok, let pixels = raster.pixels else { return nil }
        defer { kura_barcode_dispose(&raster) }
        guard !Task.isCancelled else { return nil }
        let width = Int(raster.width), height = Int(raster.height)
        let data = Data(bytes: pixels, count: width * height)
        guard let provider = CGDataProvider(data: data as CFData) else { return nil }
        return CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 8,
                       bytesPerRow: width, space: CGColorSpaceCreateDeviceGray(),
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),
                       provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
    }
}
