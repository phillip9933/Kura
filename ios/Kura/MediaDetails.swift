import PDFKit
import SwiftUI
import UniformTypeIdentifiers

struct MediaThumbnail: View {
    let data: Data
    var maximumPixels = 160
    @State private var image: CGImage?

    var body: some View {
        Group {
            if let image {
                Image(decorative: image, scale: 1).resizable().scaledToFit()
            } else {
                Image(systemName: "photo").accessibilityHidden(true)
            }
        }
        .task(id: data) {
            image = nil
            let output = await Self.load(data, maximumPixels)
            if !Task.isCancelled { image = output }
        }
    }

    nonisolated private static func load(_ data: Data, _ size: Int) async -> CGImage? {
        try? MediaProcessor.thumbnail(data, size: size)
    }
}

struct MediaDetails: View {
    let media: WalletMedia
    @State private var viewingImage: WalletImageSlot?
    @State private var viewingFile: WalletAttachment?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(WalletImageSlot.allCases) { slot in
                if let bytes = media[slot] {
                    Button { viewingImage = slot } label: {
                        VStack(alignment: .leading) {
                            MediaThumbnail(data: bytes, maximumPixels: 640).frame(maxHeight: 145)
                            Text("View " + slot.title.lowercased() + " image")
                        }
                    }
                }
            }
            if !media.attachments.isEmpty {
                Text("Attachments").font(.headline)
                ForEach(media.attachments) { attachment in
                    Button { viewingFile = attachment } label: {
                        Label(attachment.name, systemImage: "paperclip")
                    }
                }
            }
        }
        .sheet(item: $viewingImage) { slot in
            NavigationStack {
                if let bytes = media[slot] {
                    MediaThumbnail(data: bytes, maximumPixels: 2048).padding()
                        .accessibilityLabel(slot.title + " image")
                        .navigationTitle(slot.title)
                        .toolbar { Button("Done") { viewingImage = nil } }
                }
            }
        }
        .sheet(item: $viewingFile) { MediaAttachmentView(attachment: $0) }
    }
}

private struct MediaAttachmentView: View {
    let attachment: WalletAttachment
    @Environment(\.dismiss) private var dismiss
    @State private var exporting = false
    @State private var errorMessage: String?

    private var type: UTType {
        UTType(filenameExtension: (attachment.name as NSString).pathExtension) ?? .data
    }

    var body: some View {
        NavigationStack {
            VStack {
                if type.conforms(to: .image) {
                    MediaThumbnail(data: attachment.bytes, maximumPixels: 2048)
                        .accessibilityLabel(attachment.name)
                } else if type.conforms(to: .pdf) {
                    MediaPDF(data: attachment.bytes)
                } else if type.conforms(to: .plainText), attachment.bytes.count <= 512 * 1024,
                          let text = String(data: attachment.bytes, encoding: .utf8) {
                    ScrollView { Text(verbatim: text).padding() }
                } else {
                    Text("Save this attachment to open it in another app.").padding()
                }
                if let errorMessage { Text(errorMessage) }
                Button("Save a copy to Files") { exporting = true }.padding()
            }
            .navigationTitle(attachment.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("Done") { dismiss() } }
            .fileExporter(isPresented: $exporting, document: MediaDocument(bytes: attachment.bytes),
                          contentType: type, defaultFilename: attachment.name) { result in
                if case .failure(let error) = result, (error as NSError).code != NSUserCancelledError {
                    errorMessage = "Kura could not export this attachment. Try another folder."
                }
            }
        }
    }
}

private struct MediaDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    var bytes: Data
    init(bytes: Data) { self.bytes = bytes }
    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents, data.count <= WalletMedia.fileLimit
        else { throw WalletMediaError.size }
        bytes = data
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: bytes) }
}

private struct MediaPDF: UIViewRepresentable {
    let data: Data
    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.document = PDFDocument(data: data)
        view.autoScales = true
        return view
    }
    func updateUIView(_ view: PDFView, context: Context) {}
}

struct MediaMissingSlots: View {
    let card: WalletCard
    let save: (WalletCard, WalletMedia) async throws -> Void
    @State private var request: MediaCaptureRequest?

    var body: some View {
        if card.passStyle == nil {
            ForEach([WalletImageSlot.front, .back]) { slot in
                if card.media[slot] == nil {
                    Menu {
                        Button("Choose photo") { request = MediaCaptureRequest(slot: slot, source: .photo) }
                        Button("Take photo") { request = MediaCaptureRequest(slot: slot, source: .camera) }
                    } label: {
                        Label("Add " + slot.title.lowercased() + " image", systemImage: "photo.badge.plus")
                    }
                }
            }
            .sheet(item: $request) { item in
                MissingImageEditor(card: card, request: item, save: save)
            }
        }
    }
}

private struct MissingImageEditor: View {
    let card: WalletCard
    let request: MediaCaptureRequest
    let save: (WalletCard, WalletMedia) async throws -> Void
    @State private var media: WalletMedia
    @State private var saving = false
    @State private var errorMessage: String?
    @Environment(\.dismiss) private var dismiss

    init(card: WalletCard, request: MediaCaptureRequest, save: @escaping (WalletCard, WalletMedia) async throws -> Void) {
        self.card = card
        self.request = request
        self.save = save
        _media = State(initialValue: card.media)
    }

    var body: some View {
        NavigationStack {
            Form {
                MediaEditor(media: $media, ocrFields: [:], applyOCR: { _ in }, initialCapture: request)
                if let errorMessage { Text(errorMessage) }
            }
            .navigationTitle("Add " + request.slot.title.lowercased() + " image")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save image") { saving = true }.disabled(media[request.slot] == nil || saving)
                }
            }
            .disabled(saving)
            .interactiveDismissDisabled(saving)
            .task(id: saving) {
                guard saving else { return }
                do {
                    try await save(card, media)
                    try Task.checkCancellation()
                    dismiss()
                } catch is CancellationError { return } catch { errorMessage = "Kura could not save this image. Try again." }
                saving = false
            }
        }
    }
}
