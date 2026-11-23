import AVFoundation
import CoreTransferable
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

private struct PickedPhoto: Transferable {
    let bytes: Data

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .image) { file in
            PickedPhoto(bytes: try WalletMedia.read(file.file))
        }
    }
}

private struct PhotoDraft: Identifiable {
    let id = UUID()
    let slot: WalletImageSlot
    let bytes: Data
}

struct MediaCaptureRequest: Identifiable {
    enum Source { case photo, camera }
    let id = UUID()
    let slot: WalletImageSlot
    let source: Source
}

struct MediaEditor: View {
    @Binding var media: WalletMedia
    let ocrFields: [String: String]
    let applyOCR: ([String: String]) -> Void
    let imagesEditable: Bool
    let initialCapture: MediaCaptureRequest?
    @State private var didStart = false
    @State private var picked: PhotosPickerItem?
    @State private var selectedSlot: WalletImageSlot = .front
    @State private var selectingPhoto = false
    @State private var requestingCamera = false
    @State private var capturing = false
    @State private var pendingPhoto: PhotoDraft?
    @State private var draft: PhotoDraft?
    @State private var importingFile = false
    @State private var attachmentURL: URL?
    @State private var errorMessage: String?
    @State private var removedImages: [WalletImageSlot: Data] = [:]

    init(media: Binding<WalletMedia>, ocrFields: [String: String],
         applyOCR: @escaping ([String: String]) -> Void, imagesEditable: Bool = true,
         initialCapture: MediaCaptureRequest? = nil) {
        _media = media
        self.ocrFields = ocrFields
        self.applyOCR = applyOCR
        self.imagesEditable = imagesEditable
        self.initialCapture = initialCapture
    }

    var body: some View {
        Group {
            if imagesEditable { imageSection }
            if initialCapture == nil { attachmentSection }
            if let errorMessage { Section { Text(errorMessage) } }
        }
        .onAppear {
            guard !didStart, let request = initialCapture else { return }
            didStart = true
            selectedSlot = request.slot
            if request.source == .photo { selectingPhoto = true } else { requestingCamera = true }
        }
        .photosPicker(isPresented: $selectingPhoto, selection: $picked, matching: .images)
        .task(id: picked) {
            guard let item = picked else { return }
            let slot = selectedSlot
            do {
                guard let value = try await item.loadTransferable(type: PickedPhoto.self) else {
                    throw WalletMediaError.invalidImage
                }
                try Task.checkCancellation()
                draft = PhotoDraft(slot: slot, bytes: value.bytes)
            } catch is CancellationError { return } catch { errorMessage = "Kura could not read this photo." }
            picked = nil
        }
        .task(id: requestingCamera) {
            guard requestingCamera else { return }
            let allowed = await AVCaptureDevice.requestAccess(for: .video)
            guard !Task.isCancelled else { return }
            requestingCamera = false
            if allowed && UIImagePickerController.isSourceTypeAvailable(.camera) {
                capturing = true
            } else {
                errorMessage = "Camera access is unavailable. Check Settings or choose a photo."
            }
        }
        .fullScreenCover(isPresented: $capturing, onDismiss: {
            draft = pendingPhoto
            pendingPhoto = nil
        }) {
            MediaCamera { result in
                switch result {
                case .success(let bytes): pendingPhoto = PhotoDraft(slot: selectedSlot, bytes: bytes)
                case .failure(let error): errorMessage = error.localizedDescription
                case nil: break
                }
                capturing = false
            }.ignoresSafeArea()
        }
        .sheet(item: $draft) { draft in
            MediaCropView(bytes: draft.bytes, slot: draft.slot, fields: ocrFields) { image, suggestion in
                do {
                    try media.replace(draft.slot, with: image)
                    removedImages.removeValue(forKey: draft.slot)
                    applyOCR(suggestion.filter {
                        ocrFields[$0.key]?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == true
                    })
                    errorMessage = nil
                } catch { errorMessage = error.localizedDescription }
            }
        }
        .fileImporter(isPresented: $importingFile, allowedContentTypes: [.data]) { result in
            do { attachmentURL = try result.get() }
            catch { if (error as NSError).code != NSUserCancelledError { errorMessage = "Kura could not open the selected file." } }
        }
        .task(id: attachmentURL) {
            guard let url = attachmentURL else { return }
            do {
                let attachment = try await Self.readAttachment(url)
                try Task.checkCancellation()
                try media.add(attachment)
                errorMessage = nil
            } catch is CancellationError { return } catch {
                errorMessage = (error as? WalletMediaError)?.localizedDescription ?? "Kura could not add this attachment."
            }
            attachmentURL = nil
        }
    }

    private var imageSection: some View {
        Section("Images") {
            ForEach(initialCapture.map { [$0.slot] } ?? WalletImageSlot.allCases) { slot in
                HStack {
                    if let bytes = media[slot] { MediaThumbnail(data: bytes).frame(width: 56, height: 46) }
                    Text(slot.title)
                    Spacer()
                    Menu {
                        Button("Choose photo") { selectedSlot = slot; selectingPhoto = true }
                        Button("Take photo") { selectedSlot = slot; requestingCamera = true }
                        if let bytes = media[slot] {
                            Button("Adjust crop") { draft = PhotoDraft(slot: slot, bytes: bytes) }
                            Button("Remove image", role: .destructive) {
                                do {
                                    try media.replace(slot, with: nil)
                                    removedImages[slot] = bytes
                                } catch { errorMessage = error.localizedDescription }
                            }
                        } else if let bytes = removedImages[slot] {
                            Button("Undo removal") {
                                do {
                                    try media.replace(slot, with: bytes)
                                    removedImages.removeValue(forKey: slot)
                                } catch { errorMessage = error.localizedDescription }
                            }
                        }
                    } label: {
                        Label("Edit " + slot.title.lowercased() + " image", systemImage: "pencil")
                            .labelStyle(.iconOnly)
                    }
                }
            }
        }
    }

    private var attachmentSection: some View {
        Section("Attachments") {
            ForEach(media.attachments) { attachment in
                HStack {
                    Text(attachment.name)
                    Spacer()
                    Button(role: .destructive) { media.removeAttachment(attachment.id) } label: {
                        Label("Remove " + attachment.name, systemImage: "minus.circle").labelStyle(.iconOnly)
                    }.buttonStyle(.borderless)
                }
            }
            Button("Add attachment") { importingFile = true }
        }

    }

    private static func readAttachment(_ url: URL) async throws -> WalletAttachment {
        try WalletAttachment(name: url.lastPathComponent, bytes: WalletMedia.read(url))
    }
}

private struct MediaCamera: UIViewControllerRepresentable {
    let finished: (Result<Data, Error>?) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(finished: finished) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.mediaTypes = [UTType.image.identifier]
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let finished: (Result<Data, Error>?) -> Void
        init(finished: @escaping (Result<Data, Error>?) -> Void) { self.finished = finished }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { finished(nil) }
        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            guard let image = info[.originalImage] as? UIImage,
                  let reduced = image.preparingThumbnail(of: CGSize(width: 2048, height: 2048)),
                  let data = reduced.jpegData(compressionQuality: 0.92) else {
                finished(.failure(WalletMediaError.invalidImage))
                return
            }
            finished(.success(data))
        }
    }
}
