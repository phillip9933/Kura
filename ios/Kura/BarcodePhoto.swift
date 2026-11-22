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
            let request = VNDetectBarcodesRequest()
            try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
            try Task.checkCancellation()
            var seen = Set<ScannedBarcode>()
            let codes = (request.results ?? []).compactMap { observation -> ScannedBarcode? in
                guard let code = ScannedBarcode(payload: observation.payloadStringValue,
                                                format: BarcodeFormat(vision: observation.symbology)),
                      seen.insert(code).inserted else { return nil }
                return code
            }
            return BarcodePhoto(codes: codes)
        }
    }
}
