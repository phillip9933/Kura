import SwiftUI
import UniformTypeIdentifiers

struct PassImportView: View {
    var existingCards: [WalletCard] = []
    let importItems: ([ImportedPass]) async throws -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var choosing = false
    @State private var url: URL?
    @State private var items: [ImportedPass] = []
    @State private var selected: Set<UUID> = []
    @State private var duplicateIDs: Set<UUID> = []
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
                                if duplicateIDs.contains(item.id) { Text("Duplicate pass · Import another copy").font(.subheadline) }
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
                duplicateIDs = PassImport.duplicates(found, existing: existingCards)
                selected = Set(found.map(\.id)).subtracting(duplicateIDs)
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
    @State private var displayedArtwork: DisplayedPassArtwork?

    var body: some View {
        if let style = card.passStyle {
            let palette = PassPalette(style)
            VStack(alignment: .leading, spacing: 12) {
                if !style.organization.isEmpty && style.organization != card.name { Text(style.organization).font(.headline) }
                if !style.logoText.isEmpty { Text(style.logoText).font(.title3.bold()) }
                if let art = style.artwork["strip"] { artwork(art, name: "Strip image", height: 160) }
                if let art = style.artwork["thumbnail"] { artwork(art, name: "Pass portrait", height: 145) }
                ForEach(PassAppearance.groupNames, id: \.self) { group in
                    if group == "backFields", let fields = style.groups[group], !fields.isEmpty {
                        DisclosureGroup("Terms and additional information") {
                            ForEach(Array(fields.enumerated()), id: \.offset) { _, field in
                                passField(field, palette: palette, primary: false)
                                PassFieldLinks(value: field.value)
                            }
                        }
                    } else {
                        ForEach(Array((style.groups[group] ?? []).enumerated()), id: \.offset) { _, field in
                            passField(field, palette: palette, primary: group == "primaryFields")
                        }
                    }
                }
                if let art = style.artwork["footer"] {
                    artwork(art, name: "Footer image", height: 80)
                }
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .foregroundStyle(palette.text)
            .background(palette.background, in: RoundedRectangle(cornerRadius: 12))
            .fullScreenCover(item: $displayedArtwork) { item in
                NavigationStack {
                    MediaImageViewer(bytes: item.data, label: item.name)
                        .navigationTitle(item.name)
                        .toolbar { Button("Done") { displayedArtwork = nil } }
                }
            }
        }
    }

    private struct DisplayedPassArtwork: Identifiable {
        let id = UUID()
        let data: Data
        let name: String
    }

    private func artwork(_ bytes: Data, name: String, height: CGFloat) -> some View {
        Button { displayedArtwork = DisplayedPassArtwork(data: bytes, name: name) } label: {
            MediaThumbnail(data: bytes, maximumPixels: 800).frame(maxHeight: height)
        }.buttonStyle(.plain).accessibilityLabel("View " + name.lowercased())
    }

    private func passField(_ field: PassField, palette: PassPalette, primary: Bool) -> some View {
        VStack(alignment: .leading) {
            if !field.label.isEmpty { Text(field.label).font(.caption.bold()).foregroundStyle(palette.label) }
            Text(verbatim: field.displayValue).font(primary ? .title3 : .body)
        }.accessibilityElement(children: .combine)
    }

}

private struct PassPalette {
    let text: Color
    let label: Color
    let background: Color


    init(_ style: PassAppearance) {
        guard let bg = WalletTileColor.parse(style.backgroundColor)?.over(.base) else {
            text = .primary; label = .secondary; background = Color(uiColor: .secondarySystemBackground)
            return
        }
        let fallback: WalletTileColor = bg.contrast(with: .black) > bg.contrast(with: .white) ? .black : .white
        func ink(_ source: String?) -> Color {
            guard let proposed = WalletTileColor.parse(source)?.over(bg), bg.contrast(with: proposed) >= 4.5 else { return fallback.color }
            return proposed.color
        }
        background = bg.color
        text = ink(style.foregroundColor)
        label = ink(style.labelColor)
    }
}

enum PassLinks {
    static func destinations(in text: String) -> [URL] {
        guard text.utf8.count <= 8192 else { return [] }
        let ns = text as NSString
        let source = NSRange(location: 0, length: ns.length)
        var links: [URL] = []
        var urlRanges: [NSRange] = []
        let web = try? NSRegularExpression(pattern: #"https?://[^\s<>"]+"#, options: .caseInsensitive)
        for match in web?.matches(in: text, range: source) ?? [] {
            urlRanges.append(match.range)
            let raw = ns.substring(with: match.range)
            let trimmed = String(raw.reversed().drop(while: { ".,;)".contains($0) }).reversed())
            guard let parts = URLComponents(string: trimmed), let host = parts.host, !host.isEmpty,
                  ["https", "http"].contains(parts.scheme?.lowercased() ?? ""),
                  parts.user == nil, parts.password == nil, let url = parts.url else { continue }
            if !links.contains(url) { links.append(url) }
        }
        let phones = try? NSRegularExpression(pattern: #"(?<!\w)\+?[0-9][0-9 ()-]{6,}[0-9](?!\w)"#)
        for match in phones?.matches(in: text, range: source) ?? [] {
            guard !urlRanges.contains(where: { NSIntersectionRange($0, match.range).length != 0 }) else { continue }
            let digits = ns.substring(with: match.range).filter { $0 == "+" || $0.isNumber }
            guard digits.filter(\.isNumber).count >= 7, let url = URL(string: "tel:" + digits) else { continue }
            if !links.contains(url) { links.append(url) }
        }
        return links
    }
}

private struct PassFieldLinks: View {
    let value: String
    @Environment(\.openURL) private var openURL
    @State private var unavailable = false

    var body: some View {
        ForEach(PassLinks.destinations(in: value), id: \.self) { url in
            Button {
                openURL(url) { accepted in unavailable = !accepted }
            } label: {
                Label(url.absoluteString, systemImage: url.scheme == "tel" ? "phone" : "safari")
            }
        }
        if unavailable { Text("No app is available to open this link.").font(.caption) }
    }
}
