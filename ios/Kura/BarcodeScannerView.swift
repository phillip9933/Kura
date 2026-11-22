import AVFoundation
import PhotosUI
import SwiftUI

struct BarcodeScannerView: View {
    let onScan: (String, BarcodeFormat) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var cameraAccess = AVCaptureDevice.authorizationStatus(for: .video)
    @State private var cameraFailed = false
    @State private var showPhotos = false
    @State private var photo: PhotosPickerItem?
    @State private var reading = false
    @State private var choices: [ScannedBarcode] = []
    @State private var message: String?
    @State private var active = true
    @State private var completed = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if reading {
                    ProgressView("Reading barcode")
                } else if !choices.isEmpty {
                    List(choices, id: \.self) { result in
                        Button { select(result) } label: {
                            VStack(alignment: .leading) {
                                Text(result.format.rawValue).font(.caption)
                                Text(result.payload)
                            }
                        }
                    }
                } else if cameraAccess == .authorized && !cameraFailed && !showPhotos && photo == nil {
                    BarcodeCamera(onResult: select, onFailure: { cameraFailed = true })
                        .accessibilityLabel("Camera preview")
                    Text("Point the camera at a barcode.").multilineTextAlignment(.center)
                } else if cameraAccess == .notDetermined {
                    Button("Use camera") {
                        Task {
                            _ = await AVCaptureDevice.requestAccess(for: .video)
                            guard active else { return }
                            cameraAccess = AVCaptureDevice.authorizationStatus(for: .video)
                        }
                    }
                } else if !showPhotos && photo == nil {
                    Text("Camera scanning is unavailable. Choose a photo to read its barcode.")
                }
                if let message { Text(message).multilineTextAlignment(.center) }
                Button("Choose barcode photo") {
                    choices = []
                    message = nil
                    showPhotos = true
                }.disabled(reading)
            }
            .padding()
            .navigationTitle(choices.isEmpty ? "Scan barcode" : "Choose barcode")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { active = false; dismiss() } } }
            .photosPicker(isPresented: $showPhotos, selection: $photo, matching: .images)
            .task(id: photo) {
                guard let selection = photo else { return }
                reading = true
                do {
                    guard let result = try await selection.loadTransferable(type: BarcodePhoto.self)
                    else { throw BarcodePhotoError.invalid }
                    guard active, !Task.isCancelled else { return }
                    if result.codes.count == 1, let code = result.codes.first {
                        select(code)
                    } else {
                        choices = result.codes
                        message = choices.isEmpty ? "No supported barcode was found in this photo." : nil
                    }
                } catch {
                    guard active, !Task.isCancelled else { return }
                    message = "Kura could not read the photo. Choose an image up to 10 MB and 40 megapixels."
                }
                reading = false
                photo = nil
            }
        }
        .privacySensitive()
        .onAppear { active = true }
        .onDisappear { active = false }
    }

    private func select(_ code: ScannedBarcode) {
        guard active, !completed, !showPhotos else { return }
        completed = true
        onScan(code.payload, code.format)
        dismiss()
    }
}
