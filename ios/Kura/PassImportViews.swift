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
            VStack(alignment: .leading, spacing: 12) {
                if !style.logoText.isEmpty { Text(style.logoText).font(.title3.bold()) }
                if let art = style.artwork["strip"] ?? style.artwork["thumbnail"] {
                    MediaThumbnail(data: art, maximumPixels: 800).frame(maxHeight: 160)
                        .accessibilityHidden(true)
                }
                ForEach(PassAppearance.groupNames, id: \.self) { group in
                    ForEach(Array((style.groups[group] ?? []).enumerated()), id: \.offset) { _, field in
                        VStack(alignment: .leading) {
                            if !field.label.isEmpty { Text(field.label).font(.caption.bold()) }
                            Text(field.value).font(group == "primaryFields" ? .title3 : .body)
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
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
        }
    }
}
