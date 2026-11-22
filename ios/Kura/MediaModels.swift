import Foundation
import ImageIO
import UniformTypeIdentifiers

enum WalletMediaError: LocalizedError {
    case size, invalidFile, invalidImage, unavailableCamera, unavailableOCR, damagedPass

    var errorDescription: String? {
        switch self {
        case .size: return "This file or collection is too large to add."
        case .invalidFile: return "Choose a readable file with a valid name."
        case .invalidImage: return "Choose a photo Kura can read. Photos must be 16 megapixels or smaller."
        case .unavailableCamera: return "The camera is unavailable. Choose a photo instead."
        case .unavailableOCR: return "Text recognition is unavailable for the selected photo."
        case .damagedPass: return "Kura could not import this pass. Check the file and try again."
        }
    }
}

enum WalletImageSlot: String, CaseIterable, Identifiable {
    case front, back, logo
    var id: Self { self }
    var title: String {
        switch self {
        case .front: return "Front"
        case .back: return "Back"
        case .logo: return "Logo"
        }
    }
}

struct WalletAttachment: Identifiable, Codable, Equatable {
    var id = UUID()
    let name: String
    let bytes: Data

    init(name: String, bytes: Data) throws {
        self.name = name
        self.bytes = bytes
        try validate()
    }

    func validate() throws {
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              name.utf8.count <= 255, name != ".", name != "..",
              name.rangeOfCharacter(from: CharacterSet(charactersIn: "/\\:").union(.controlCharacters)) == nil
        else { throw WalletMediaError.invalidFile }
        guard !bytes.isEmpty, bytes.count <= WalletMedia.fileLimit else { throw WalletMediaError.size }
    }
}

struct WalletMedia: Codable, Equatable {
    static let fileLimit = 10 * 1024 * 1024
    private(set) var front: Data?
    private(set) var back: Data?
    private(set) var logo: Data?
    private(set) var attachments: [WalletAttachment] = []

    init() {}

    init(from decoder: Decoder) throws {
        let fields = try decoder.container(keyedBy: CodingKeys.self)
        front = try fields.decodeIfPresent(Data.self, forKey: .front)
        back = try fields.decodeIfPresent(Data.self, forKey: .back)
        logo = try fields.decodeIfPresent(Data.self, forKey: .logo)
        attachments = try fields.decodeIfPresent([WalletAttachment].self, forKey: .attachments) ?? []
        try validate()
    }

    subscript(slot: WalletImageSlot) -> Data? {
        switch slot {
        case .front: return front
        case .back: return back
        case .logo: return logo
        }
    }

    mutating func replace(_ slot: WalletImageSlot, with image: Data?) throws {
        var next = self
        switch slot {
        case .front: next.front = image
        case .back: next.back = image
        case .logo: next.logo = image
        }
        try next.validate()
        self = next
    }

    mutating func add(_ attachment: WalletAttachment) throws {
        var next = self
        next.attachments.append(attachment)
        try next.validate()
        self = next
    }

    mutating func removeAttachment(_ id: UUID) {
        attachments.removeAll { $0.id == id }
    }

    func validate() throws {
        guard attachments.count <= 32, Set(attachments.map(\.id)).count == attachments.count
        else { throw WalletMediaError.size }
        var size = 0
        for attachment in attachments {
            try attachment.validate()
            size += attachment.bytes.count
        }
        for image in [front, back, logo].compactMap({ $0 }) {
            _ = try Self.imageSource(image)
            size += image.count
        }
        guard size <= 24 * 1024 * 1024 else { throw WalletMediaError.size }
    }

    static func imageSource(_ bytes: Data) throws -> CGImageSource {
        guard !bytes.isEmpty, bytes.count <= fileLimit,
              let source = CGImageSourceCreateWithData(bytes as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
              CGImageSourceGetCount(source) > 0,
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? NSNumber,
              let height = properties[kCGImagePropertyPixelHeight] as? NSNumber,
              width.intValue > 0, height.intValue > 0, width.intValue <= 16_777_216 / height.intValue
        else { throw WalletMediaError.invalidImage }
        return source
    }

    static func read(_ url: URL, limit: Int = fileLimit) throws -> Data {
        guard url.isFileURL else { throw WalletMediaError.invalidFile }
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let attributes = try url.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey])
        guard attributes.isRegularFile == true, attributes.isSymbolicLink != true else {
            throw WalletMediaError.invalidFile
        }
        guard (attributes.fileSize ?? 0) <= limit else { throw WalletMediaError.size }
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var data = Data()
        while true {
            try Task.checkCancellation()
            guard let chunk = try handle.read(upToCount: min(65536, limit + 1 - data.count)), !chunk.isEmpty else { break }
            guard data.count + chunk.count <= limit else { throw WalletMediaError.size }
            data.append(chunk)
        }
        guard !data.isEmpty else { throw WalletMediaError.invalidFile }
        return data
    }
}
