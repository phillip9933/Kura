import CoreImage
import CoreImage.CIFilterBuiltins
import ImageIO
import UIKit
import UniformTypeIdentifiers
import Vision

enum CropShape: String, CaseIterable {
    case card = "Horizontal card", portrait = "Vertical card", square = "Square", original = "Photo proportions"
    func ratio(_ bounds: CGRect) -> CGFloat {
        switch self {
        case .card: return 85.6 / 53.98
        case .portrait: return 53.98 / 85.6
        case .square: return 1
        case .original: return bounds.width / bounds.height
        }
    }
}

struct PhotoCrop: Equatable {
    var shape: CropShape = .card
    var quarterTurns = 0
    var zoom: CGFloat = 1
    var x: CGFloat = 0
    var y: CGFloat = 0
    var autoAlign = true
    var angleDegrees: CGFloat = 0

    func rect(in bounds: CGRect) -> CGRect {
        let ratio = shape.ratio(bounds)
        let angle = angleDegrees * .pi / 180
        let c = abs(cos(angle)), s = abs(sin(angle))
        // Inverse-map all four mask corners to the photo. These two inequalities
        // keep even the most tilted mask fully covered at the pan extremes.
        let width = min(bounds.width / (c + s / ratio), bounds.height / (s + c / ratio))
            / min(4, max(1, zoom))
        let height = width / ratio
        let panWidth = max(0, (bounds.width - c * width - s * height) / 2)
        let panHeight = max(0, (bounds.height - s * width - c * height) / 2)
        let center = CGPoint(x: bounds.midX + min(1, max(-1, x)) * panWidth,
                             y: bounds.midY - min(1, max(-1, y)) * panHeight)
            .applying(CGAffineTransform(rotationAngle: angle))
        let left = ceil(center.x - width / 2)
        let bottom = ceil(center.y - height / 2)
        return CGRect(x: left, y: bottom, width: max(0, floor(center.x + width / 2) - left),
                      height: max(0, floor(center.y + height / 2) - bottom))
    }
}

struct PreparedPhoto {
    let original: CGImage
    let aligned: CGImage?
}

enum MediaProcessor {
    private static let context = CIContext(options: [.cacheIntermediates: false])

    static func thumbnail(_ data: Data, size: Int = 2048) throws -> CGImage {
        let source = try WalletMedia.imageSource(data)
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: size,
            kCGImageSourceShouldCacheImmediately: true
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
        else { throw WalletMediaError.invalidImage }
        return image
    }

    static func prepare(_ data: Data) async throws -> PreparedPhoto {
        try Task.checkCancellation()
        let image = try thumbnail(data)
        let request = VNDetectRectanglesRequest()
        request.minimumConfidence = 0.65
        request.minimumAspectRatio = 0.35
        request.minimumSize = 0.25
        request.maximumObservations = 1
        do {
            try await performVisionRequest(request, image: image)
        } catch {
            try Task.checkCancellation()
            return PreparedPhoto(original: image, aligned: nil)
        }
        try Task.checkCancellation()
        guard let rectangle = request.results?.first else { return PreparedPhoto(original: image, aligned: nil) }
        let input = CIImage(cgImage: image)
        func point(_ p: CGPoint) -> CGPoint {
            CGPoint(x: p.x * input.extent.width, y: p.y * input.extent.height)
        }
        let correction = CIFilter.perspectiveCorrection()
        correction.inputImage = input
        correction.topLeft = point(rectangle.topLeft)
        correction.topRight = point(rectangle.topRight)
        correction.bottomLeft = point(rectangle.bottomLeft)
        correction.bottomRight = point(rectangle.bottomRight)
        guard let result = correction.outputImage, !result.extent.isEmpty,
              result.extent.width <= 4096, result.extent.height <= 4096 else {
            return PreparedPhoto(original: image, aligned: nil)
        }
        return PreparedPhoto(original: image, aligned: context.createCGImage(result, from: result.extent))
    }

    static func cropped(_ photo: PreparedPhoto, using crop: PhotoCrop) async throws -> CGImage {
        try Task.checkCancellation()
        let source = crop.autoAlign ? photo.aligned ?? photo.original : photo.original
        let orientation: [CGImagePropertyOrientation] = [.up, .right, .down, .left]
        let input = CIImage(cgImage: source).oriented(orientation[crop.quarterTurns % 4])
        let rect = crop.rect(in: input.extent)
        let rotated = input.transformed(by: CGAffineTransform(rotationAngle: crop.angleDegrees * .pi / 180))
        guard rect.width >= 1, rect.height >= 1,
              let output = context.createCGImage(rotated, from: rect) else { throw WalletMediaError.invalidImage }
        return output
    }

    static func jpeg(_ image: CGImage) async throws -> Data {
        try Task.checkCancellation()
        let bytes = NSMutableData()
        guard let writer = CGImageDestinationCreateWithData(bytes, UTType.jpeg.identifier as CFString, 1, nil)
        else { throw WalletMediaError.invalidImage }
        CGImageDestinationAddImage(writer, image, [kCGImageDestinationLossyCompressionQuality: 0.88] as CFDictionary)
        guard CGImageDestinationFinalize(writer), bytes.length <= WalletMedia.fileLimit else { throw WalletMediaError.size }
        return bytes as Data
    }

    static func recognize(_ image: CGImage, fields: [String: String]) async throws -> [String: String] {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        let supported = try request.supportedRecognitionLanguages()
        request.recognitionLanguages = ["en-US", "ja-JP"].filter { supported.contains($0) }
        guard !request.recognitionLanguages.isEmpty else { throw WalletMediaError.unavailableOCR }
        try Task.checkCancellation()
        try await performVisionRequest(request, image: image)
        try Task.checkCancellation()
        let lines = (request.results ?? []).prefix(256).compactMap { $0.topCandidates(1).first?.string }
        return suggestions(lines: lines, fields: fields)
    }

    private static func performVisionRequest(_ request: VNRequest, image: CGImage) async throws {
        try await withVisionCancellation(cancel: { request.cancel() }) {
            try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
        }
    }

    static func withVisionCancellation(cancel: @escaping @Sendable () -> Void,
                                       perform: @Sendable () throws -> Void) async throws {
        try await withTaskCancellationHandler {
            try Task.checkCancellation()
            do { try perform() }
            catch {
                try Task.checkCancellation()
                throw error
            }
            try Task.checkCancellation()
        } onCancel: {
            // Cancelling a view task can run its handler on the wallet's lock thread.
            DispatchQueue.global(qos: .userInitiated).async(execute: cancel)
        }
    }

    static func suggestions(lines: [String], fields: [String: String]) -> [String: String] {
        let aliases = [
            (["member number", "borrower number", "card number", "document number", "number"], ["Member ID", "Member number", "Membership number", "Card number", "Card No", "会員番号", "カード番号"]),
            (["member name", "borrower name", "cardholder", "full name"], ["Member name", "Cardholder", "Name", "Name on card", "氏名", "お名前", "名義人"]),
            (["expiry", "expiry date"], ["Expires", "Expiry", "Expiry date", "Valid until", "有効期限"]),
            (["issuer", "name / organization"], ["Issuer", "Organization", "発行元"]),
            (["card name"], ["Organization", "Issuer", "カード名", "発行元"]),
            (["network"], ["Card network", "Payment network", "国際ブランド"])
        ]
        var found: [String: String] = [:]
        for (field, current) in fields where current.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            guard !field.isEmpty, field.count <= 160 else { continue }
            let names = [field] + (aliases.first { $0.0.contains(field.lowercased()) }?.1 ?? [])
            let patterns = Set(names).sorted { $0.count > $1.count }.map { name in
                name.unicodeScalars.contains(where: { $0.value >= 0x3000 })
                    ? name.map { NSRegularExpression.escapedPattern(for: String($0)) }.joined(separator: #"\s*"#)
                    : NSRegularExpression.escapedPattern(for: name)
            }
            let pattern = #"^\s*(?:"# + patterns.joined(separator: "|") + #")\s*(?:[:：]\s*|\s+)(.{1,160})\s*$"#
            guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { continue }
            let values = Set(lines.prefix(256).compactMap { line -> String? in
                guard line.utf8.count <= 1024,
                      let match = regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)),
                      let range = Range(match.range(at: 1), in: line) else { return nil }
                let value = String(line[range]).trimmingCharacters(in: .whitespacesAndNewlines)
                return value.isEmpty ? nil : value
            })
            if values.count == 1 { found[field] = values.first }
        }
        return found
    }
}


struct RecognizedExpiry {
    let date: Date
    let monthOnly: Bool

    static func parse(_ value: String) -> Self? {
        let text = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if let date = WalletDateFormat.iso.parse(text) { return Self(date: date, monthOnly: false) }
        let formatter = WalletDateFormat.month.formatter
        for shape in ["MM/yy", "MM/yyyy", "yyyy-MM", "yyyy/MM", "yyyy年MM月", "yyyy年MM月dd日"] {
            formatter.dateFormat = shape
            if let date = formatter.date(from: text), formatter.string(from: date) == text {
                return Self(date: date, monthOnly: !shape.contains("dd"))
            }
        }
        return nil
    }
}
