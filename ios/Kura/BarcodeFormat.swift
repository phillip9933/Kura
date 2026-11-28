import Foundation
import Vision

enum BarcodeFormat: String, Codable, CaseIterable, Identifiable {
    case qr = "QR Code"
    case aztec = "Aztec"
    case pdf417 = "PDF417"
    case code128 = "Code 128"
    case dataMatrix = "Data Matrix"
    case code39 = "Code 39"
    case code93 = "Code 93"
    case ean8 = "EAN-8"
    case ean13 = "EAN-13"
    case upca = "UPC-A"
    case upce = "UPC-E"
    case itf = "ITF"
    case codabar = "Codabar"
    case itf14 = "ITF-14"
    case gs1128 = "GS1-128"
    case isbn = "ISBN"
    case telepen = "Telepen"
    case postnet = "POSTNET"
    case rm4scc = "RM4SCC"
    case ean2 = "EAN-2"
    case ean5 = "EAN-5"

    var id: Self { self }

    init?(passKitName: String) {
        switch passKitName {
        case "PKBarcodeFormatQR": self = .qr
        case "PKBarcodeFormatAztec": self = .aztec
        case "PKBarcodeFormatPDF417": self = .pdf417
        case "PKBarcodeFormatCode128": self = .code128
        case "PKBarcodeFormatCode39": self = .code39
        case "PKBarcodeFormatCodabar": self = .codabar
        case "PKBarcodeFormatEAN13": self = .ean13
        case "PKBarcodeFormatITF": self = .itf
        default: return nil
        }
    }

    init?(vision: VNBarcodeSymbology) {
        switch vision {
        case .qr: self = .qr
        case .aztec: self = .aztec
        case .pdf417: self = .pdf417
        case .code128: self = .code128
        case .dataMatrix: self = .dataMatrix
        case .code39, .code39Checksum, .code39FullASCII, .code39FullASCIIChecksum: self = .code39
        case .code93, .code93i: self = .code93
        case .ean8: self = .ean8
        case .ean13: self = .ean13
        case .upce: self = .upce
        case .i2of5, .i2of5Checksum: self = .itf
        case .itf14: self = .itf14
        case .codabar: self = .codabar
        default: return nil
        }
    }
}

struct ScannedBarcode: Hashable {
    let payload: String
    let format: BarcodeFormat

    init?(payload: String?, format: BarcodeFormat?) {
        guard let payload, let format, !payload.isEmpty, payload.utf8.count <= 8192 else { return nil }
        self.payload = payload
        self.format = format
    }
}
