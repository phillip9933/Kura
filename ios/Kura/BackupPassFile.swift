import SwiftUI
import UniformTypeIdentifiers

enum BackupPassFile {
    static func encode(_ card: WalletCard, password: String) throws -> Data {
        try card.validate()
        var plain = try JSONEncoder().encode(card)
        defer { plain.resetBytes(in: 0..<plain.count) }
        return try BackupCodec.seal(plain, password: password, kind: .passFile)
    }

    static func decode(_ bytes: Data, password: String) throws -> WalletCard {
        var card = try BackupCodec.openJSON(WalletCard.self, from: bytes, password: password, kind: .passFile)
        try card.validate()
        card.id = UUID()
        card.created = Date()
        card.archived = false
        return card
    }
}

private actor PassFileWorker {
    func export(_ card: WalletCard, password: String) throws -> Data {
        try BackupPassFile.encode(card, password: password)
    }

    func preview(_ url: URL, password: String, access: BackupFileAccess) throws -> WalletCard {
        try BackupPassFile.decode(BackupFiles.readPickedFile(url, access: access), password: password)
    }
}

struct BackupPassExportView: View {
    @ObservedObject var wallet: WalletSession
    let card: WalletCard
    @State private var password = ""
    @State private var confirmation = ""
    @State private var document: EncryptedBackupDocument?
    @State private var showingFile = false
    @State private var status: String?
    @State private var generation: UUID?
    @State private var task: Task<Void, Never>?
    @State private var working = false
    private let worker = PassFileWorker()

    var body: some View {
        Form {
            Section {
                Text(card.name)
                SecureField("Password", text: $password)
                SecureField("Confirm password", text: $confirmation)
                Button("Export encrypted item") { export() }
                    .disabled(working || password.count < 8 || password != confirmation)
                if working { ProgressView("Encrypting item") }
            } footer: {
                Text("This file includes the item’s details, photos, and attachments. Give the password to the recipient separately.")
            }
            if let status { Text(status) }
        }
        .navigationTitle("Export encrypted item")
        .onAppear { generation = wallet.generation }
        .onDisappear { clear() }
        .onChange(of: wallet.generation) { _ in clear() }
        .fileExporter(isPresented: $showingFile, document: document, contentType: .data,
                      defaultFilename: "Kura-item-" + UUID().uuidString + ".kurapass") { result in
            document = nil
            guard generation == wallet.generation else { return }
            switch result {
            case .success: status = "Encrypted item exported."
            case .failure: status = "The export did not finish."
            }
        }
    }

    private func export() {
        guard let generation, generation == wallet.generation, wallet.phase == .unlocked, !working else { return }
        let secret = password
        working = true
        status = nil
        task = Task { @MainActor in
            defer { if !Task.isCancelled { working = false } }
            do {
                let bytes = try await worker.export(card, password: secret)
                try Task.checkCancellation()
                guard generation == wallet.generation, wallet.phase == .unlocked else { return }
                document = EncryptedBackupDocument(bytes: bytes)
                password = ""
                confirmation = ""
                showingFile = true
            } catch {
                if !Task.isCancelled { status = (error as? BackupError)?.localizedDescription ?? "Kura could not export this item." }
            }
        }
    }

    private func clear() {
        task?.cancel()
        generation = nil
        working = false
        password = ""
        confirmation = ""
        document = nil
    }
}

struct BackupPassImportView: View {
    @ObservedObject var wallet: WalletSession
    @Environment(\.dismiss) private var dismiss
    @State private var generation: UUID?
    @State private var selectingFile = false
    @State private var url: URL?
    @State private var pending: WalletCard?
    @State private var password = ""
    @State private var task: Task<Void, Never>?
    @State private var working = false
    @State private var status: String?
    private let worker = PassFileWorker()

    var body: some View {
        Form {
            Button("Choose encrypted item file") { selectingFile = true }.disabled(working)
            if url != nil {
                SecureField("Item password", text: $password)
                Button("Review item") { preview() }.disabled(working || password.isEmpty)
            }
            if let pending {
                Section("Review imported item") {
                    LabeledContent("Name", value: pending.name)
                    LabeledContent("Section", value: pending.section.rawValue)
                    LabeledContent("Attachments", value: String(pending.media.attachments.count))
                    Button("Add to wallet") { add(pending) }.disabled(working)
                    Button("Discard preview", role: .cancel) { self.pending = nil }
                }
            }
            if working { ProgressView() }
            if let status { Text(status) }
        }
        .navigationTitle("Import encrypted item")
        .onAppear { generation = wallet.generation }
        .onDisappear { clear() }
        .onChange(of: wallet.generation) { _ in clear() }
        .fileImporter(isPresented: $selectingFile, allowedContentTypes: [.data]) { result in
            guard generation == wallet.generation, wallet.phase == .unlocked else { return }
            if case let .success(url) = result {
                self.url = url
                pending = nil
                password = ""
                status = nil
            }
        }
    }

    private func preview() {
        guard let url, let generation, generation == wallet.generation,
              wallet.phase == .unlocked, !working else { return }
        let secret = password
        working = true
        status = nil
        task = Task { @MainActor in
            defer { if !Task.isCancelled { working = false } }
            do {
                let access = BackupFileAccess()
                let item = try await withTaskCancellationHandler {
                    try await worker.preview(url, password: secret, access: access)
                } onCancel: { access.cancel() }
                try Task.checkCancellation()
                guard wallet.generation == generation, wallet.phase == .unlocked else { return }
                pending = item
                self.url = nil
                password = ""
            } catch {
                if !Task.isCancelled { status = (error as? BackupError)?.localizedDescription ?? "Kura could not read this item file." }
            }
        }
    }

    private func add(_ item: WalletCard) {
        guard let generation, generation == wallet.generation, !working else { return }
        working = true
        task = Task { @MainActor in
            defer { if !Task.isCancelled { working = false } }
            do {
                try Task.checkCancellation()
                try await wallet.importCards([item], generation: generation)
                guard !Task.isCancelled else { return }
                dismiss()
            } catch {
                if !Task.isCancelled { status = "Kura could not save this item. Your existing items are intact." }
            }
        }
    }

    private func clear() {
        task?.cancel()
        generation = nil
        working = false
        url = nil
        pending = nil
        password = ""
    }
}
