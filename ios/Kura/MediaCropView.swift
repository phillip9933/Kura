import SwiftUI

struct MediaCropView: View {
    let bytes: Data
    let slot: WalletImageSlot
    let fields: [String: String]
    let finish: (Data, [String: String]) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var photo: PreparedPhoto?
    @State private var crop = PhotoCrop()
    @State private var rendered: CGImage?
    @State private var renderedCrop: PhotoCrop?
    @State private var editingSuggestions = false
    @State private var recognizing = false
    @State private var suggestions: [String: String] = [:]
    @State private var selected: Set<String> = []
    @State private var errorMessage: String?
    @State private var gestureZoom: CGFloat?
    @State private var gesturePosition: CGPoint?

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                let wide = geometry.size.width > geometry.size.height
                let layout = wide ? AnyLayout(HStackLayout(alignment: .top)) : AnyLayout(VStackLayout())
                layout {
                    preview
                        .frame(maxWidth: wide ? geometry.size.width * 0.5 : .infinity)
                        .frame(maxHeight: wide ? .infinity : geometry.size.height * 0.38)
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            if let errorMessage { Text(errorMessage) }
                            if editingSuggestions { review }
                            else if photo != nil { controls }
                            if rendered != nil {
                                Button(editingSuggestions ? "Apply selected details and use image" : "Use image") {
                                    guard let rendered, renderedCrop == crop else { return }
                                    do {
                                        let chosen = editingSuggestions ? suggestions.filter { selected.contains($0.key) } : [:]
                                        finish(try MediaProcessor.jpeg(rendered), chosen)
                                        dismiss()
                                    } catch { errorMessage = error.localizedDescription }
                                }
                                .buttonStyle(.borderedProminent)
                                .disabled(recognizing || renderedCrop != crop)
                            }
                        }.padding()
                    }
                }
            }
            .navigationTitle(slot.title + " image")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
        .task {
            do {
                let result = try await MediaProcessor.prepare(bytes)
                try Task.checkCancellation()
                photo = result
                if slot == .logo { crop.shape = .original; crop.autoAlign = false }
            } catch is CancellationError {} catch { errorMessage = error.localizedDescription }
        }
        .task(id: RenderKey(crop: crop, ready: photo != nil)) {
            guard let photo else { return }
            do {
                let value = try await MediaProcessor.cropped(photo, using: crop)
                try Task.checkCancellation()
                rendered = value
                renderedCrop = crop
                errorMessage = nil
            } catch is CancellationError {} catch { errorMessage = error.localizedDescription }
        }
        .task(id: recognizing) {
            guard recognizing, let rendered else { return }
            do {
                let result = try await MediaProcessor.recognize(rendered, fields: fields)
                try Task.checkCancellation()
                suggestions = result
                selected = Set(result.keys)
                editingSuggestions = true
                errorMessage = nil
            } catch is CancellationError { return } catch { errorMessage = error.localizedDescription }
            recognizing = false
        }
    }

    @ViewBuilder
    private var preview: some View {
        if let rendered {
            GeometryReader { geometry in
                Image(decorative: rendered, scale: 1).resizable().scaledToFit()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityLabel("Crop preview")
                    .contentShape(Rectangle())
                    .gesture(DragGesture().onChanged { value in
                        guard !recognizing, !editingSuggestions else { return }
                        let start = gesturePosition ?? CGPoint(x: crop.x, y: crop.y)
                        gesturePosition = start
                        crop.x = min(1, max(-1, start.x - 2 * value.translation.width / max(1, geometry.size.width)))
                        crop.y = min(1, max(-1, start.y - 2 * value.translation.height / max(1, geometry.size.height)))
                    }.onEnded { _ in gesturePosition = nil })
                    .simultaneousGesture(MagnificationGesture().onChanged { scale in
                        guard !recognizing, !editingSuggestions else { return }
                        let start = gestureZoom ?? crop.zoom
                        gestureZoom = start
                        crop.zoom = min(4, max(1, start * scale))
                    }.onEnded { _ in gestureZoom = nil })
                    .padding(8)
            }
        } else if errorMessage == nil {
            ProgressView("Preparing image").frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private struct RenderKey: Equatable {
        let crop: PhotoCrop
        let ready: Bool
    }

    private var controls: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Frame", selection: $crop.shape) {
                ForEach(CropShape.allCases, id: \.self) { Text($0.rawValue).tag($0) }
            }
            if photo?.aligned != nil {
                Toggle("Correct perspective", isOn: $crop.autoAlign)
            }
            Button("Rotate 90°") { crop.quarterTurns = (crop.quarterTurns + 1) % 4 }
            Text("Fine rotation: " + String(Int(crop.angleDegrees)) + "°")
            Slider(value: $crop.angleDegrees, in: -45...45, step: 1).accessibilityLabel("Fine rotation")
            Text("Zoom")
            Slider(value: $crop.zoom, in: 1...4).accessibilityLabel("Zoom")
            Text("Horizontal position")
            Slider(value: $crop.x, in: -1...1).accessibilityLabel("Horizontal position")
            Text("Vertical position")
            Slider(value: $crop.y, in: -1...1).accessibilityLabel("Vertical position")
            Button("Use full photo") { crop = PhotoCrop(shape: .original, autoAlign: false) }
            if slot != .logo && fields.contains(where: { $0.value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) {
                Button("Review autofill") { recognizing = true }
                    .disabled(renderedCrop != crop)
                if recognizing { ProgressView("Reading text on this device") }
            }
        }.disabled(recognizing)
    }

    private var review: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Review autofill").font(.headline)
            if suggestions.isEmpty {
                Text("No labelled values matched an empty field.")
            }
            ForEach(suggestions.keys.sorted(), id: \.self) { key in
                Toggle(key, isOn: Binding(
                    get: { selected.contains(key) },
                    set: { include in
                        if include { selected.insert(key) } else { selected.remove(key) }
                    }
                ))
                TextField(key, text: Binding(get: { suggestions[key] ?? "" }, set: { suggestions[key] = $0 }))
                    .textFieldStyle(.roundedBorder).disabled(!selected.contains(key))
            }
            Button("Back to crop") { editingSuggestions = false }
        }
    }
}
