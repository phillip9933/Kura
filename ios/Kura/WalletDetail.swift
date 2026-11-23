import SwiftUI

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
                    if let expiration = card.expires { LabeledContent("Expires", value: expiration.formatted(date: .abbreviated, time: .omitted)) }
                    if !card.payload.isEmpty {
                        Button { barcode = true } label: {
                            Label("Show \(card.format.rawValue)", systemImage: "barcode.viewfinder")
                        }
                        Text(card.payload).font(.body.monospaced()).textSelection(.enabled)
                    }
                }
                if !card.fields.isEmpty {
                    Section("Details") {
                        ForEach(card.fields.keys.sorted(), id: \.self) { key in
                            LabeledContent(key, value: card.fields[key] ?? "").textSelection(.enabled)
                        }
                    }
                }
                if !card.notes.isEmpty { Section("Notes") { Text(card.notes).textSelection(.enabled) } }
                MediaDetails(media: card.media)
                if card.passStyle == nil {
                    MediaMissingSlots(card: card) { original, media in
                        var updated = original
                        updated.media = media
                        try await wallet.save(updated, generation: token, replacing: original)
                    }
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
            .toolbar { Button("Edit", action: edit) }
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
