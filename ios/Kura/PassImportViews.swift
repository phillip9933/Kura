import SwiftUI
import UniformTypeIdentifiers

struct PassImportView: View {
    let importItems: ([ImportedPass]) async throws -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var choosing = false
    @State private var url: URL?
    @State private var items: [ImportedPass] = []
    @State private var selected: Set<UUID> = []
    @State private var reading = false
    @State private var saving = false
    @State private var errorMessage: String?

    var body: some View {
        Form {
            Section {
                Button(items.isEmpty ? "Choose pass file" : "Choose another file") { choosing = true }
                if reading { ProgressView("Reading passes") }
                if let errorMessage { Text(errorMessage) }
            } footer: { Text("Choose a .pkpass file or a .pkpasses bundle.") }
            if !items.isEmpty {
                Section {
                    ForEach(items) { item in
                        Toggle(isOn: Binding(get: { selected.contains(item.id) }, set: { value in
                            if value { selected.insert(item.id) } else { selected.remove(item.id) }
                        })) {
                            VStack(alignment: .leading) {
                                Text(item.name)
                                if !item.organization.isEmpty { Text(item.organization).font(.subheadline) }
                                Text(item.manifestChecked ? "File checksums matched" : "No checksum manifest")
                                    .font(.caption)
                            }
                        }
                    }
                } header: { Text("Review passes") } footer: {
                    Text("Kura has not verified the issuer signature. Only import passes from a source you trust.")
                }
                Button("Import selected passes") { saving = true }.disabled(selected.isEmpty)
            }
        }
        .disabled(reading || saving)
        .navigationTitle("Import passes")
        .fileImporter(isPresented: $choosing, allowedContentTypes: [
            UTType(filenameExtension: "pkpass") ?? .data, UTType(filenameExtension: "pkpasses") ?? .data
        ]) { result in
            do { url = try result.get() }
            catch { if (error as NSError).code != NSUserCancelledError { errorMessage = "Kura could not open that file." } }
        }
        .task(id: url) {
            guard let url else { return }
            items = []
            selected.removeAll()
            reading = true
            errorMessage = nil
            do {
                let found = try await PassImport.read(url)
                try Task.checkCancellation()
                items = found
                selected = Set(found.map(\.id))
            } catch is CancellationError { return } catch {
                errorMessage = (error as? WalletMediaError)?.localizedDescription ?? WalletMediaError.damagedPass.localizedDescription
            }
            reading = false
            self.url = nil
        }
        .task(id: saving) {
            guard saving else { return }
            do {
                try await importItems(items.filter { selected.contains($0.id) })
                try Task.checkCancellation()
                dismiss()
            } catch is CancellationError { return } catch { errorMessage = "Kura could not save these passes. Try again." }
            saving = false
        }
    }
}

struct PassAppearanceView: View {
    let card: WalletCard

    var body: some View {
        if let style = card.passStyle {
            let palette = PassPalette(style)
            VStack(alignment: .leading, spacing: 12) {
                if !style.logoText.isEmpty { Text(style.logoText).font(.title3.bold()) }
                if let art = style.artwork["strip"] ?? style.artwork["thumbnail"] {
                    MediaThumbnail(data: art, maximumPixels: 800).frame(maxHeight: 160)
                        .accessibilityHidden(true)
                }
                ForEach(PassAppearance.groupNames, id: \.self) { group in
                    ForEach(Array((style.groups[group] ?? []).enumerated()), id: \.offset) { _, field in
                        VStack(alignment: .leading) {
                            if !field.label.isEmpty { Text(field.label).font(.caption.bold()).foregroundStyle(palette.label) }
                            Text(field.displayValue).font(group == "primaryFields" ? .title3 : .body)
                        }.accessibilityElement(children: .combine)
                    }
                }
                if let art = style.artwork["footer"] {
                    MediaThumbnail(data: art, maximumPixels: 800).frame(maxHeight: 80)
                        .accessibilityHidden(true)
                }
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .foregroundStyle(palette.text)
            .background(palette.background, in: RoundedRectangle(cornerRadius: 12))
        }
    }
}

private struct PassPalette {
    let text: Color
    let label: Color
    let background: Color

    private struct RGB {
        let r: Double, g: Double, b: Double
        var color: Color { Color(red: r, green: g, blue: b) }
        var luminance: Double {
            func linear(_ v: Double) -> Double { v <= 0.04045 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4) }
            return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
        }
        func contrast(_ other: RGB) -> Double {
            (max(luminance, other.luminance) + 0.05) / (min(luminance, other.luminance) + 0.05)
        }
        static func parse(_ source: String?) -> RGB? {
            guard let s = source?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else { return nil }
            if s.count == 7, s.first == "#", let hex = Int(s.dropFirst(), radix: 16) {
                return RGB(r: Double((hex >> 16) & 255) / 255, g: Double((hex >> 8) & 255) / 255, b: Double(hex & 255) / 255)
            }
            guard s.hasPrefix("rgb("), s.hasSuffix(")") else { return nil }
            let triple = s.dropFirst(4).dropLast().split(separator: ",", omittingEmptySubsequences: false)
            guard triple.count == 3 else { return nil }
            let components = triple.compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
            guard components.count == 3, components.allSatisfy({ (0...255).contains($0) }) else { return nil }
            return RGB(r: Double(components[0]) / 255, g: Double(components[1]) / 255, b: Double(components[2]) / 255)
        }
    }

    init(_ style: PassAppearance) {
        guard let bg = RGB.parse(style.backgroundColor) else {
            text = .primary; label = .secondary; background = Color(uiColor: .secondarySystemBackground)
            return
        }
        let black = RGB(r: 0, g: 0, b: 0), white = RGB(r: 1, g: 1, b: 1)
        let fallback = bg.contrast(black) > bg.contrast(white) ? black : white
        func ink(_ source: String?) -> Color {
            guard let proposed = RGB.parse(source), bg.contrast(proposed) >= 4.5 else { return fallback.color }
            return proposed.color
        }
        background = bg.color
        text = ink(style.foregroundColor)
        label = ink(style.labelColor)
    }
}
