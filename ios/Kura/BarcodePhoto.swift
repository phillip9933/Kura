import CoreGraphics
import CoreTransferable
import Foundation
import ImageIO
import UniformTypeIdentifiers
import Vision
import ZXingCpp

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
            return BarcodePhoto(codes: try codes(in: image))
        }
    }

    static func codes(in image: CGImage) throws -> [ScannedBarcode] {
        try Task.checkCancellation()
        let reader = ZXIBarcodeReader()
        reader.options.maxNumberOfSymbols = 16
        let decoded = (try? reader.read(image)) ?? []
        let offline = decoded.compactMap { reading in
            ScannedBarcode(payload: reading.text, format: format(of: reading))
        }
        try Task.checkCancellation()
        var seen = Set<ScannedBarcode>()
        if !offline.isEmpty { return offline.filter { seen.insert($0).inserted } }

        let request = VNDetectBarcodesRequest()
        try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
        try Task.checkCancellation()
        return (request.results ?? []).compactMap { observation in
            guard let code = ScannedBarcode(payload: observation.payloadStringValue,
                                            format: BarcodeFormat(vision: observation.symbology)),
                  seen.insert(code).inserted else { return nil }
            return code
        }
    }

    private static func format(of result: ZXIResult) -> BarcodeFormat? {
        // ZXing exposes GS1 application identifiers in parenthesized, human-readable form.
        if result.symbologyIdentifier == "]C1", result.text.hasPrefix("(") { return .gs1128 }
        switch result.format {
        case .QR_CODE, .MICRO_QR_CODE, .RMQR_CODE: return .qr
        case .AZTEC: return .aztec
        case .PDF_417: return .pdf417
        case .DATA_MATRIX: return .dataMatrix
        case .CODE_128: return .code128
        case .CODE_39: return .code39
        case .CODE_93: return .code93
        case .CODABAR: return .codabar
        case .EAN_8: return .ean8
        case .EAN_13: return .ean13
        case .UPC_E, .UPC_A:
            // The reader can expand UPC-E into its 12/13-digit retail representation.
            if result.text.count == 13 { return .ean13 }
            return result.text.count == 12 ? .upca : .upce
        case .ITF: return .itf
        case .TELEPEN: return .telepen
        default:
            // The 3.1.1 Objective-C bridge reports NONE for some concrete variants
            // (such as QR Model 2). The decoded AIM identifier still identifies the family.
            switch result.symbologyIdentifier.prefix(2) {
            case "]Q": return .qr
            case "]z": return .aztec
            case "]L": return .pdf417
            case "]A": return .code39
            case "]I": return result.text.count == 14 ? .itf14 : .itf
            default: return nil
            }
        }
    }
}
