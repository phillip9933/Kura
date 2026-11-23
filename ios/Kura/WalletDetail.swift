import SwiftUI
import UniformTypeIdentifiers

struct WalletDetail: View {
    @ObservedObject var wallet: WalletSession
    let id: UUID
    let edit: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var barcode = false
    @State private var confirmDelete = false
    @State private var error: String?
    private let token: UUID

    init(wallet: WalletSession, id: UUID, edit: @escaping () -> Void) {
        self.wallet = wallet; self.id = id; self.edit = edit
        token = wallet.generation
    }

    var body: some View {
        if let card = wallet.cards.first(where: { $0.id == id }) {
            List {
                if card.passStyle != nil { PassAppearanceView(card: card) }
                Section {
                    if !card.category.isEmpty { LabeledContent("Category", value: card.category) }
                    if let scheduled = card.passStyle?.relevantDate {
                        LabeledContent("Relevant date", value: scheduled.formatted(date: .abbreviated, time: .shortened))
                    }
                    if let expiration = card.expires {
                        LabeledContent("Expires", value: expiration.formatted(date: .abbreviated, time: card.isImportedPass ? .shortened : .omitted))
                    }
                    if !card.payload.isEmpty {
                        Button { barcode = true } label: {
                            Label("Show \(card.format.rawValue)", systemImage: "barcode.viewfinder")
                        }
                        let display = card.passStyle?.barcodeAlternative.flatMap { $0.isEmpty ? nil : $0 } ?? card.payload
                        HStack {
                            Text(verbatim: display).font(.body.monospaced())
                            Spacer()
                            PrivateCopyButton(label: "Copy ticket code", value: display)
                        }
                    }
                }
                if !card.fields.isEmpty {
                    Section("Details") {
                        ForEach(card.fields.keys.sorted(), id: \.self) { key in
                            let prefs = wallet.snapshot.preferences
                            let def = (prefs.definitions(for: card.section) + FieldDefinition.suggested(section: card.section, category: card.category)).first { $0.name == key }
                            let raw = card.fields[key] ?? ""
                            HStack {
                                LabeledContent(key, value: def?.display(raw, currency: prefs.currency) ?? raw)
                                PrivateCopyButton(label: "Copy " + key, value: raw)
                            }
                        }
                    }
                }
                if !card.notes.isEmpty {
                    Section("Notes") {
                        HStack {
                            Text(verbatim: card.notes)
                            Spacer()
                            PrivateCopyButton(label: "Copy notes", value: card.notes)
                        }
                    }
                }
                MediaDetails(media: card.media)
                MediaMissingSlots(card: card) { original, media in
                    var updated = original
                    updated.media = media
                    try await wallet.save(updated, generation: token, replacing: original)
                }
                Section {
                    Button(card.favorite ? "Remove from favorites" : "Add to favorites") {
                        var updated = card; updated.favorite.toggle(); save(updated, original: card)
                    }
                    Button(card.archived ? "Unarchive item" : "Archive item") {
                        var updated = card; updated.archived.toggle(); save(updated, original: card)
                    }
                    NavigationLink("Share securely") { BackupTransferSendView(card: card) }
                    NavigationLink("Export encrypted item") { BackupPassExportView(wallet: wallet, card: card) }
                    PassExportButton(card: card)
                    Button("Delete item", role: .destructive) { confirmDelete = true }
                }
                if let error { Text(error) }
            }
            .disabled(wallet.saving)
            .navigationTitle(card.name)
            .toolbar { if !card.isImportedPass { Button("Edit", action: edit) } }
            .confirmationDialog("Delete this item?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Delete item", role: .destructive) {
                    Task {
                        do { try await wallet.delete(card, generation: token); dismiss() }
                        catch is CancellationError {}
                        catch { self.error = "Kura could not delete this item." }
                    }
                }
            } message: { Text("This removes it from the current wallet. Existing backups can still contain it.") }
            .fullScreenCover(isPresented: $barcode) {
                BarcodeViewer(title: card.name, payload: card.payload, format: card.format,
                    maximizeBrightness: wallet.snapshot.preferences.brightness, flipped: wallet.snapshot.preferences.flipped)
            }
        }
    }

    private func save(_ updated: WalletCard, original: WalletCard) {
        Task {
            do { try await wallet.save(updated, generation: token, replacing: original) }
            catch is CancellationError {}
            catch { self.error = "Kura could not update this item." }
        }
    }
}


private struct PrivateCopyButton: View {
    let label: String
    let value: String
    @State private var copied = false

    var body: some View {
        Button {
            UIPasteboard.general.setItems([[UTType.utf8PlainText.identifier: value]],
                                         options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(40)])
            copied = true
        } label: {
            Label(copied ? "Copied" : label, systemImage: copied ? "checkmark" : "doc.on.doc")
                .labelStyle(.iconOnly)
        }
        .buttonStyle(.borderless)
        .task(id: copied) {
            guard copied else { return }
            do { try await Task.sleep(nanoseconds: 2_000_000_000); copied = false } catch {}
        }
    }
}
