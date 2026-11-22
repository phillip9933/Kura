import AVFoundation
import SwiftUI
import UIKit

/// The session queue owns configuration, start, and stop. Preview and result delivery stay on the main thread.
private final class CameraCapture {
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "app.kura.iphone.barcode.camera")
    private var configured = false

    func start(delegate: AVCaptureMetadataOutputObjectsDelegate, failure: @escaping () -> Void) {
        queue.async {
            if !self.configured {
                guard self.configure(delegate: delegate) else {
                    DispatchQueue.main.async(execute: failure)
                    return
                }
                self.configured = true
            }
            self.session.startRunning()
        }
    }

    func stop() { queue.async { self.session.stopRunning() } }

    private func configure(delegate: AVCaptureMetadataOutputObjectsDelegate) -> Bool {
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: camera) else { return false }
        let output = AVCaptureMetadataOutput()
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        guard session.canAddInput(input) else { return false }
        session.addInput(input)
        guard session.canAddOutput(output) else {
            session.removeInput(input)
            return false
        }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(delegate, queue: .main)
        output.metadataObjectTypes = output.availableMetadataObjectTypes.filter { BarcodeCamera.formats[$0] != nil }
        guard !output.metadataObjectTypes.isEmpty else {
            session.removeOutput(output)
            session.removeInput(input)
            return false
        }
        return true
    }
}

struct BarcodeCamera: UIViewControllerRepresentable {
    let onResult: (ScannedBarcode) -> Void
    let onFailure: () -> Void

    static let formats: [AVMetadataObject.ObjectType: BarcodeFormat] = [
        .qr: .qr, .aztec: .aztec, .pdf417: .pdf417, .code128: .code128,
        .dataMatrix: .dataMatrix, .code39: .code39, .code39Mod43: .code39,
        .code93: .code93, .ean8: .ean8, .ean13: .ean13, .upce: .upce,
        .interleaved2of5: .itf, .itf14: .itf14, .codabar: .codabar
    ]

    func makeUIViewController(context: Context) -> Controller {
        let controller = Controller()
        controller.onResult = onResult
        controller.onFailure = onFailure
        return controller
    }

    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.onResult = onResult
        controller.onFailure = onFailure
    }

    static func dismantleUIViewController(_ controller: Controller, coordinator: ()) {
        controller.finish()
    }

    final class Controller: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
        private let capture = CameraCapture()
        private lazy var preview = AVCaptureVideoPreviewLayer(session: capture.session)
        private var active = false
        var onResult: ((ScannedBarcode) -> Void)?
        var onFailure: (() -> Void)?

        override func viewDidLoad() {
            super.viewDidLoad()
            view.backgroundColor = .black
            preview.videoGravity = .resizeAspectFill
            view.layer.addSublayer(preview)
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            preview.frame = view.bounds
            if let orientation = view.window?.windowScene?.interfaceOrientation,
               let video = AVCaptureVideoOrientation(rawValue: orientation.rawValue),
               preview.connection?.isVideoOrientationSupported == true {
                preview.connection?.videoOrientation = video
            }
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            active = true
            capture.start(delegate: self) { [weak self] in
                guard let self, self.active else { return }
                self.onFailure?()
            }
        }

        override func viewWillDisappear(_ animated: Bool) {
            super.viewWillDisappear(animated)
            finish()
        }

        func finish() {
            active = false
            capture.stop()
        }

        func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject],
                            from connection: AVCaptureConnection) {
            guard active else { return }
            for case let code as AVMetadataMachineReadableCodeObject in objects {
                if let result = ScannedBarcode(payload: code.stringValue, format: BarcodeCamera.formats[code.type]) {
                    finish()
                    onResult?(result)
                    return
                }
            }
        }
    }
}
