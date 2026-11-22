import SwiftUI

private enum CardRoute: Hashable {
    case details(UUID)
    case edit(UUID?)
}

struct CardsView: View {
    @ObservedObject var wallet: WalletSession
    @State private var path: [CardRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            List {
                if wallet.cards.isEmpty {
                    Section {
                        Text("No cards yet").font(.headline)
                        Text("Add a card name and the text for its QR code. For this early build, use test cards. Backup and restore are still in development.")
                    }
                }
                ForEach(wallet.cards) { card in
                    NavigationLink(card.name, value: CardRoute.details(card.id))
                }
            }
            .navigationTitle("Cards")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) { Button("Lock", action: wallet.lock) }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { path.append(.edit(nil)) } label: { Label("Add card", systemImage: "plus") }
                }
            }
            .navigationDestination(for: CardRoute.self) { route in
                switch route {
                case .edit(let id):
                    CardEditor(wallet: wallet, existing: wallet.cards.first { $0.id == id })
                case .details(let id):
                    if let card = wallet.cards.first(where: { $0.id == id }) {
                        CardDetails(card: card) { path.append(.edit(id)) }
                    }
                }
            }
        }
    }
}

private struct CardDetails: View {
    let card: WalletCard
    let edit: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                QRPreview(payload: card.payload).id(card.payload)
                Text(card.payload).font(.body.monospaced())
                    .accessibilityLabel(Text("QR code text: \(card.payload)"))
            }.padding()
        }
        .navigationTitle(card.name)
        .toolbar { Button("Edit", action: edit) }
    }
}

private struct QRPreview: View {
    let payload: String
    @State private var image: CGImage?
    @State private var finished = false

    var body: some View {
        Group {
            if let image {
                Image(decorative: image, scale: 1)
                    .interpolation(.none).resizable().scaledToFit()
                    .accessibilityHidden(true)
            } else if finished {
                Text("Kura could not display this QR code. Use the text below.")
            } else {
                ProgressView("Preparing QR code")
            }
        }
        .task(id: payload) {
            let rendered = await CardQR.shared.image(for: payload)
            guard !Task.isCancelled else { return }
            image = rendered
            finished = true
        }
    }
}

private struct CardEditor: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var wallet: WalletSession
    @State private var id: UUID
    let isNew: Bool
    @State private var name: String
    @State private var payload: String
    @State private var errorMessage: String?

    init(wallet: WalletSession, existing: WalletCard?) {
        self.wallet = wallet
        _id = State(initialValue: existing?.id ?? UUID())
        isNew = existing == nil
        _name = State(initialValue: existing?.name ?? "")
        _payload = State(initialValue: existing?.payload ?? "")
    }

    var body: some View {
        Form {
            Section("Card name") {
                TextField("Name", text: $name)
            }
            Section {
                TextField("QR code text", text: $payload)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.asciiCapable)
            } header: {
                Text("QR code")
            } footer: {
                Text("Enter the exact text to encode, including leading zeros and spaces.")
            }
            if let errorMessage { Text(errorMessage).foregroundStyle(.primary) }
            Button(wallet.saving ? "Saving…" : "Save card") {
                Task { await save() }
            }
            .disabled(wallet.saving || name.isEmpty || payload.isEmpty)
        }
        .autocorrectionDisabled()
        .disabled(wallet.saving)
        .navigationBarBackButtonHidden(wallet.saving)
        .navigationTitle(isNew ? "Add card" : "Edit card")
    }

    private func save() async {
        do {
            let card = try WalletCard(id: id, name: name, payload: payload)
            try await wallet.save(card)
            dismiss()
        } catch is CancellationError {
            return
        } catch {
            errorMessage = (error as? WalletError)?.localizedDescription ?? "Kura could not save this card. Try again."
        }
    }
}
