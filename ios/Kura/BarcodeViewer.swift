import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct BarcodeViewer: View {
    let title: String
    let payload: String
    let maximizeBrightness: Bool
    @State private var format: BarcodeFormat
    @State private var flipped: Bool
    @State private var previousBrightness: CGFloat?
    @State private var copied = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    init(title: String, payload: String, format: BarcodeFormat,
         maximizeBrightness: Bool = false, flipped: Bool = false) {
        self.title = title
        self.payload = payload
        self.maximizeBrightness = maximizeBrightness
        _format = State(initialValue: format)
        _flipped = State(initialValue: flipped)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    BarcodePreview(payload: payload, format: format)
                    Text(payload).font(.body.monospaced())
                }
                .padding()
                .foregroundStyle(.black)
                .background(Color.white)
                .rotationEffect(.degrees(flipped ? 180 : 0))
                .padding()
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                ToolbarItem(placement: .primaryAction) {
                    Button(copied ? "Copied" : "Copy code") {
                        UIPasteboard.general.setItems([[UTType.utf8PlainText.identifier: payload]],
                            options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(40)])
                        copied = true
                    }
                }
                ToolbarItemGroup(placement: .bottomBar) {
                    Button { flipped.toggle() } label: { Label("Rotate", systemImage: "rotate.left") }
                    Spacer()
                    Picker("Display format", selection: $format) {
                        ForEach(BarcodeFormat.allCases) { Text($0.rawValue).tag($0) }
                    }.pickerStyle(.menu)
                }
            }
        }
        .privacySensitive()
        .onAppear { applyBrightness(scenePhase == .active) }
        .onChange(of: scenePhase) { applyBrightness($0 == .active) }
        .onDisappear { applyBrightness(false) }
    }

    private func applyBrightness(_ visible: Bool) {
        guard maximizeBrightness else { return }
        if visible && previousBrightness == nil {
            previousBrightness = UIScreen.main.brightness
            UIScreen.main.brightness = 1
        } else if !visible, let previousBrightness {
            UIScreen.main.brightness = previousBrightness
            self.previousBrightness = nil
        }
    }
}

struct BarcodePreview: View {
    let payload: String
    let format: BarcodeFormat
    @State private var image: CGImage?
    @State private var didRender = false

    private struct Request: Equatable { let payload: String; let format: BarcodeFormat }

    var body: some View {
        Group {
            if let image {
                Image(decorative: image, scale: 1).interpolation(.none).resizable().scaledToFit()
                    .accessibilityLabel(Text("\(format.rawValue) barcode"))
                    .accessibilityAddTraits(.isImage)
            } else if didRender {
                Text("This value cannot be displayed as \(format.rawValue).")
            } else {
                ProgressView("Preparing barcode")
            }
        }
        .task(id: Request(payload: payload, format: format)) {
            image = nil
            didRender = false
            let rendered = await CardQR.shared.image(for: payload, format: format)
            guard !Task.isCancelled else { return }
            image = rendered
            didRender = true
        }
    }
}
