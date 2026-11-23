import SwiftUI

struct BackupTransferSendView: View {
    let card: WalletCard
    @State private var password = ""
    @State private var confirmation = ""
    @State private var chunks: [String] = []
    @State private var page = 0
    @State private var image: CGImage?
    @State private var playing = false
    @State private var busy = false
    @State private var failure: String?
    @State private var job: Task<Void, Never>?
    private let worker = TransferCodecWorker()

    var body: some View {
        Form {
            if chunks.isEmpty {
                Section {
                    SecureField("Transfer password", text: $password)
                    SecureField("Confirm password", text: $confirmation)
                    Button("Create transfer codes") { prepare() }
                        .disabled(password.count < 8 || password != confirmation || busy)
                    if busy { ProgressView("Encrypting item") }
                } footer: {
                    Text("The codes include this item’s fields. Photos and attachments stay on this device. Give the recipient the password separately.")
                }
            } else {
                Section {
                    if let image {
                        Image(decorative: image, scale: 1).interpolation(.none)
                            .resizable().scaledToFit().accessibilityLabel("Encrypted transfer code")
                    }
                    Text("Code \(page + 1) of \(chunks.count)")
                    if chunks.count > 1 {
                        Button("Previous code") { playing = false; page = (page + chunks.count - 1) % chunks.count }
                        Button("Next code") { playing = false; page = (page + 1) % chunks.count }
                        Button(playing ? "Pause slideshow" : "Play slideshow") { playing.toggle() }
                    }
                }
            }
            if let failure { Text(failure) }
        }
        .navigationTitle("Share securely")
        .task(id: chunks.isEmpty ? "" : chunks[page]) {
            guard chunks.indices.contains(page) else { return }
            image = nil
            let rendered = await CardQR.shared.image(for: chunks[page])
            guard !Task.isCancelled else { return }
            image = rendered
            if rendered == nil { failure = "Kura could not draw the transfer code." }
        }
        .task(id: playing) {
            guard playing, chunks.count > 1 else { return }
            do {
                while !Task.isCancelled {
                    try await Task.sleep(nanoseconds: 1_500_000_000)
                    guard !chunks.isEmpty else { return }
                    page = (page + 1) % chunks.count
                }
            } catch { return }
        }
        .onDisappear {
            job?.cancel()
            password = ""
            confirmation = ""
            image = nil
            chunks = []
            page = 0
            playing = false
        }
    }

    private func prepare() {
        guard !busy else { return }
        let secret = password
        busy = true
        failure = nil
        job = Task { @MainActor in
            defer { busy = false }
            do {
                let result = try await worker.encode(card, password: secret)
                try Task.checkCancellation()
                chunks = result
                password = ""
                confirmation = ""
            } catch {
                if !Task.isCancelled { failure = (error as? BackupError)?.localizedDescription ?? "Kura could not create transfer codes." }
            }
        }
    }
}

struct BackupTransferReceiveView: View {
    @ObservedObject var wallet: WalletSession
    @Environment(\.dismiss) private var dismiss
    @State private var input = BackupTransferCollection()
    @State private var scanning = false
    @State private var password = ""
    @State private var staged: WalletCard?
    @State private var generation: UUID?
    @State private var busy = false
    @State private var message: String?
    @State private var job: Task<Void, Never>?
    private let worker = TransferCodecWorker()

    var body: some View {
        Form {
            if let staged {
                Section("Review item") {
                    Text(staged.name)
                    Text(staged.section.rawValue)
                    Button("Add to wallet") { add(staged) }.disabled(busy)
                    Button("Discard") { reset() }
                }
            } else {
                if input.count == 0 || input.count != input.total {
                    Button("Scan transfer code") { scanning = true }
                    PasteButton(payloadType: String.self) { values in
                        guard let first = values.first else { return }
                        receive(first)
                    }
                }
                if input.total > 0 {
                    Text("\(input.count) of \(input.total) codes received")
                    if input.count == input.total {
                        SecureField("Transfer password", text: $password)
                        Button("Review item") { review() }.disabled(password.isEmpty || busy)
                    }
                    Button("Start over") { reset() }
                }
            }
            if busy { ProgressView() }
            if let message { Text(message) }
        }
        .navigationTitle("Receive securely")
        .onAppear { generation = wallet.generation }
        .onChange(of: wallet.generation) { _ in reset() }
        .sheet(isPresented: $scanning) {
            BarcodeScannerView { code, format in
                scanning = false
                if format == .qr { receive(code) }
                else { message = BackupError.transfer.localizedDescription }
            }
        }
        .onDisappear { reset() }
    }

    private func receive(_ text: String) {
        guard generation == wallet.generation, wallet.phase == .unlocked else { return }
        do { try input.append(text); message = nil }
        catch { message = (error as? BackupError)?.localizedDescription ?? BackupError.transfer.localizedDescription }
    }

    private func reset() {
        job?.cancel()
        job = nil
        busy = false
        input = BackupTransferCollection()
        staged = nil
        password = ""
        message = nil
    }

    private func review() {
        guard !busy, generation == wallet.generation else { return }
        let secret = password
        let parts = input
        busy = true
        message = nil
        job = Task { @MainActor in
            do {
                let result = try await worker.decode(parts, password: secret)
                try Task.checkCancellation()
                guard generation == wallet.generation, wallet.phase == .unlocked else { return }
                staged = result
                password = ""
            } catch {
                if !Task.isCancelled { message = (error as? BackupError)?.localizedDescription ?? "Kura could not unlock this transfer. Check the codes and password." }
            }
            if !Task.isCancelled { busy = false }
        }
    }

    private func add(_ card: WalletCard) {
        guard let generation, !busy else { return }
        busy = true
        job = Task { @MainActor in
            do {
                try Task.checkCancellation()
                try await wallet.importCards([card], generation: generation)
                guard !Task.isCancelled else { return }
                dismiss()
            } catch {
                if !Task.isCancelled { message = "Kura could not add this item." }
            }
            if !Task.isCancelled { busy = false }
        }
    }
}
