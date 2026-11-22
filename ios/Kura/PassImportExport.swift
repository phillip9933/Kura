import ImageIO
import SwiftUI
import UniformTypeIdentifiers

enum PassExport {
    static func encode(_ card: WalletCard) async throws -> Data {
        try card.validate()
        let style = card.passStyle
        let type = style?.type ?? (card.section == .cards ? "storeCard" : "generic")
        var body: [String: Any] = [:]
        for (name, fields) in style?.groups ?? [:] {
            body[name] = fields.map { ["key": $0.key, "label": $0.label, "value": $0.value] }
        }
        if type == "boardingPass" { body["transitType"] = style?.transitType ?? "PKTransitTypeGeneric" }
        let section: String
        switch card.section {
        case .cards: section = "wallets"
        case .passes: section = "passes"
        case .identities: section = "identities"
        }
        var json: [String: Any] = [
            "formatVersion": 1, "passTypeIdentifier": "pass.app.kura.wallet", "teamIdentifier": "KURA",
            "serialNumber": card.id.uuidString, "organizationName": style?.organization ?? card.name,
            "description": card.name, type: body,
            "userInfo": ["app.kura.wallet": ["section": section, "category": card.category, "customFields": card.fields] as [String: Any]]
        ]
        json["expirationDate"] = card.expires?.formatted(.iso8601)
        json["logoText"] = style?.logoText
        json["backgroundColor"] = style?.backgroundColor
        json["foregroundColor"] = style?.foregroundColor
        json["labelColor"] = style?.labelColor
        if !card.payload.isEmpty {
            let names: [BarcodeFormat: String] = [.qr: "PKBarcodeFormatQR", .aztec: "PKBarcodeFormatAztec",
                                                   .pdf417: "PKBarcodeFormatPDF417", .code128: "PKBarcodeFormatCode128"]
            json["barcodes"] = [["format": names[card.format] ?? card.format.rawValue, "message": card.payload, "messageEncoding": "utf-8"]]
        }
        let descriptor = try JSONSerialization.data(withJSONObject: json, options: [.sortedKeys])
        guard descriptor.count <= PassArchive.jsonLimit else { throw WalletMediaError.size }
        var files = ["pass.json": descriptor]
        for (stem, bytes) in style?.artwork ?? [:] { files[stem + ".png"] = bytes }
        if let image = card.media.logo { files["logo.png"] = try png(image) }
        if let image = card.media.front { files["background.png"] = try png(image) }
        if files["icon.png"] == nil {
            files["icon.png"] = Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAX+XDSwAAAABJRU5ErkJggg==")
        }
        let manifest = files.mapValues(PassImport.manifestDigest)
        files["manifest.json"] = try JSONSerialization.data(withJSONObject: manifest, options: [.sortedKeys])
        return try PassArchive.pack(files)
    }

    private static func png(_ bytes: Data) throws -> Data {
        let image = try MediaProcessor.thumbnail(bytes)
        let output = NSMutableData()
        guard let target = CGImageDestinationCreateWithData(output, UTType.png.identifier as CFString, 1, nil)
        else { throw WalletMediaError.invalidImage }
        CGImageDestinationAddImage(target, image, nil)
        guard CGImageDestinationFinalize(target), output.length <= PassArchive.expandedLimit else { throw WalletMediaError.size }
        return output as Data
    }
}

struct PassExportButton: View {
    let card: WalletCard
    @State private var confirming = false
    @State private var preparing = false
    @State private var export = false
    @State private var document: PassDocument?
    @State private var errorMessage: String?

    var body: some View {
        VStack(alignment: .leading) {
            Button(preparing ? "Preparing pass…" : "Export pass file") { confirming = true }.disabled(preparing)
            if let errorMessage { Text(errorMessage) }
        }
        .confirmationDialog("Export an unencrypted pass?", isPresented: $confirming, titleVisibility: .visible) {
            Button("Choose destination") { preparing = true }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Anyone with the file can read its fields, barcode and artwork. Attachments and back photos stay in Kura. This unsigned file is for compatible pass readers.")
        }
        .task(id: preparing) {
            guard preparing else { return }
            do {
                let data = try await PassExport.encode(card)
                try Task.checkCancellation()
                document = PassDocument(bytes: data)
                errorMessage = nil
                export = true
            } catch is CancellationError { return } catch { errorMessage = "Kura could not export this pass." }
            preparing = false
        }
        .fileExporter(isPresented: $export, document: document, contentType: PassDocument.type,
                      defaultFilename: "Kura-pass.pkpass") { result in
            document = nil
            if case .failure(let error) = result, (error as NSError).code != NSUserCancelledError {
                errorMessage = "Kura could not save this pass. Try another folder."
            }
        }
    }
}

private struct PassDocument: FileDocument {
    static var type: UTType { UTType(filenameExtension: "pkpass") ?? .data }
    static var readableContentTypes: [UTType] { [type] }
    let bytes: Data
    init(bytes: Data) { self.bytes = bytes }
    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents, data.count <= PassArchive.inputLimit
        else { throw WalletMediaError.size }
        bytes = data
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: bytes) }
}
