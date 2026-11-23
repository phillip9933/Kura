import CoreGraphics
import CoreTransferable
import Foundation
import ImageIO
import UniformTypeIdentifiers
import Vision

enum BarcodePhotoError: Error { case invalid, tooLarge }

/// Photos supplies a temporary file. Read and decode it while its transfer scope is active.
struct BarcodePhoto: Transferable {
    let codes: [ScannedBarcode]

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .image) { item in
            try Task.checkCancellation()
            let url = item.file
            let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize
            guard let size, size > 0, size <= 10 * 1024 * 1024 else { throw BarcodePhotoError.tooLarge }
            let options = [kCGImageSourceShouldCache: false] as CFDictionary
            guard let input = CGImageSourceCreateWithURL(url as CFURL, options),
                  let properties = CGImageSourceCopyPropertiesAtIndex(input, 0, options) as? [CFString: Any],
                  let width = properties[kCGImagePropertyPixelWidth] as? Int,
                  let height = properties[kCGImagePropertyPixelHeight] as? Int,
                  height > 0, width > 0, width <= 40_000_000 / height
            else { throw BarcodePhotoError.invalid }
            let thumbnail = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 2400
            ] as CFDictionary
            guard let image = CGImageSourceCreateThumbnailAtIndex(input, 0, thumbnail)
            else { throw BarcodePhotoError.invalid }
            let found = try observations(in: image)
            try Task.checkCancellation()
            var seen = Set<ScannedBarcode>()
            let codes = found.compactMap { observation -> ScannedBarcode? in
                guard let code = ScannedBarcode(payload: observation.payloadStringValue,
                                                format: BarcodeFormat(vision: observation.symbology)),
                      seen.insert(code).inserted else { return nil }
                return code
            }
            return BarcodePhoto(codes: codes)
        }
    }

    // Small standalone barcode images can fill the entire frame. Vision also accepts a
    // camera-sized frame with background around the symbol, as in a photograph of a card.
    static func observations(in image: CGImage, symbologies: [VNBarcodeSymbology]? = nil) throws -> [VNBarcodeObservation] {
        func detect(_ input: CGImage) throws -> [VNBarcodeObservation] {
            try Task.checkCancellation()
            let request = VNDetectBarcodesRequest()
            if let symbologies { request.symbologies = symbologies }
            try VNImageRequestHandler(cgImage: input, options: [:]).perform([request])
            return request.results ?? []
        }
        let original = try detect(image)
        guard original.isEmpty else { return original }
        let width = 1024, height = 768
        guard let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
        else { return original }
        let fit = min(CGFloat(width - 160) / CGFloat(image.width), CGFloat(height - 160) / CGFloat(image.height))
        let scale = fit >= 1 ? floor(fit) : fit
        let w = (CGFloat(image.width) * scale).rounded(), h = (CGFloat(image.height) * scale).rounded()
        context.setFillColor(gray: 1, alpha: 1)
        context.fill(CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)))
        context.interpolationQuality = .none
        context.draw(image, in: CGRect(x: floor((CGFloat(width) - w) / 2), y: floor((CGFloat(height) - h) / 2), width: w, height: h))
        guard let framed = context.makeImage() else { return original }
        return try detect(framed)
    }
}
