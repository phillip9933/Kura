import CoreTransferable
import ImageIO
import SwiftUI
import UniformTypeIdentifiers

enum PassExport {
    static func encode(_ card: WalletCard, timeZone: TimeZone = .current) async throws -> Data {
        try card.validate()
        let style = card.passStyle
        let type = style?.type ?? (card.section == .cards ? "storeCard" : "generic")
        var body: [String: Any] = [:]
        for (name, fields) in style?.groups ?? [:] {
            body[name] = fields.map(\.passJSON)
        }
        if type == "boardingPass" { body["transitType"] = style?.transitType ?? "PKTransitTypeGeneric" }
        let organization = style?.organization.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let section: String
        switch card.section {
        case .cards: section = "wallets"
        case .passes: section = "passes"
        case .identities: section = "identities"
        }
        var json: [String: Any] = [
            "formatVersion": 1, "passTypeIdentifier": "pass.app.kura.wallet", "teamIdentifier": "KURA",
            "serialNumber": card.id.uuidString, "organizationName": organization.isEmpty ? card.name : organization,
            "description": card.name, type: body,
            "userInfo": ["app.kura.wallet": ["section": section, "category": card.category, "customFields": card.fields] as [String: Any]]
        ]
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        json["relevantDate"] = style?.relevantDate.map(formatter.string(from:))
        if let expires = card.expires {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = timeZone
            let endOfDay = calendar.dateInterval(of: .day, for: expires)?.end.addingTimeInterval(-0.001)
            json["expirationDate"] = formatter.string(from: card.isImportedPass ? expires : (endOfDay ?? expires))
        }
        json["logoText"] = style?.logoText
        json["backgroundColor"] = style?.backgroundColor ?? card.fields["Background color"] ?? card.fields["Card color"] ?? card.fields["color"]
        json["foregroundColor"] = style?.foregroundColor
        json["labelColor"] = style?.labelColor
        if !card.payload.isEmpty {
            let names: [BarcodeFormat: String] = [.qr: "PKBarcodeFormatQR", .aztec: "PKBarcodeFormatAztec",
                                                   .pdf417: "PKBarcodeFormatPDF417", .code128: "PKBarcodeFormatCode128"]
            var barcode = ["format": names[card.format] ?? card.format.rawValue, "message": card.payload, "messageEncoding": "utf-8"]
            barcode["altText"] = style?.barcodeAlternative
            json["barcodes"] = [barcode]
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
    @State private var sharing = false
    @State private var showingShare = false
    @State private var document: PassDocument?
    @State private var errorMessage: String?

    var body: some View {
        VStack(alignment: .leading) {
            Button(preparing ? "Preparing pass…" : "Export pass file") { sharing = false; confirming = true }.disabled(preparing)
            Button("Share pass file") { sharing = true; confirming = true }.disabled(preparing)
            if let errorMessage { Text(errorMessage) }
        }
        .confirmationDialog("Export an unencrypted pass?", isPresented: $confirming, titleVisibility: .visible) {
            Button(sharing ? "Prepare for sharing" : "Choose destination") { preparing = true }
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
                if sharing { showingShare = true } else { export = true }
            } catch is CancellationError { return } catch { errorMessage = "Kura could not export this pass." }
            preparing = false
        }
        .sheet(isPresented: $showingShare, onDismiss: { document = nil }) {
            if let document {
                NavigationStack {
                    ShareLink(item: SharedPassFile(bytes: document.bytes),
                              preview: SharePreview("Kura pass", image: Image(systemName: "ticket"))) {
                        Label("Choose where to share", systemImage: "square.and.arrow.up")
                    }
                    .navigationTitle("Share pass file")
                    .toolbar { Button("Done") { showingShare = false } }
                }.presentationDetents([.medium])
            }
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


private struct SharedPassFile: Transferable {
    let bytes: Data
    static var transferRepresentation: some TransferRepresentation {
        DataRepresentation(exportedContentType: PassDocument.type) { (file: SharedPassFile) in file.bytes }
            .suggestedFileName("Kura-pass.pkpass")
    }
}
