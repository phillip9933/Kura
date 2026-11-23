import CryptoKit
import SwiftUI
import UniformTypeIdentifiers

extension VaultFile {
    /// Preserve the exact bytes without trying to decrypt a damaged generation.
    /// Failing to copy or verify prevents the caller from replacing the active file.
    func recordDamagedBeforeRestore() throws {
        let source = ciphertextURL
        let values = try source.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey])
        guard values.isRegularFile == true, values.isSymbolicLink != true else { throw BackupError.unavailable }
        let input = try FileHandle(forReadingFrom: source)
        defer { try? input.close() }
        let length = try input.seekToEnd()
        guard length <= UInt64(BackupCodec.maximumFileBytes) else { throw BackupError.tooLarge }
        try input.seek(toOffset: 0)

        var folder = directory.appendingPathComponent("Damaged", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true,
                                               attributes: [.protectionKey: FileProtectionType.complete])
        var properties = URLResourceValues()
        properties.isExcludedFromBackup = true
        try folder.setResourceValues(properties)
        let base = folder.appendingPathComponent(UUID().uuidString)
        let staging = base.appendingPathExtension("partial")
        let destination = base.appendingPathExtension("ciphertext")
        try Data().write(to: staging, options: [.withoutOverwriting, .completeFileProtection])
        defer { try? FileManager.default.removeItem(at: staging) }
        let output = try FileHandle(forWritingTo: staging)
        defer { try? output.close() }
        let digest = try Self.digest(input, length: length, copyingTo: output)
        try output.synchronize()
        try Task.checkCancellation()
        try FileManager.default.moveItem(at: staging, to: destination)
        let check = try FileHandle(forReadingFrom: destination)
        defer { try? check.close() }
        guard try Self.digest(check, length: length, copyingTo: nil) == digest else { throw BackupError.verifyFailed }
    }

    private static func digest(_ handle: FileHandle, length: UInt64, copyingTo output: FileHandle?) throws -> SHA256.Digest {
        var total: UInt64 = 0
        var hash = SHA256()
        while true {
            try Task.checkCancellation()
            guard let bytes = try handle.read(upToCount: 256 * 1024), !bytes.isEmpty else { break }
            total += UInt64(bytes.count)
            guard total <= length else { throw BackupError.verifyFailed }
            try output?.write(contentsOf: bytes)
            hash.update(data: bytes)
        }
        guard total == length else { throw BackupError.verifyFailed }
        return hash.finalize()
    }
}

struct BackupRescueView: View {
    @ObservedObject var wallet: WalletSession
    @State private var generation: UUID?
    @State private var copies: [BackupRecovery] = []
    @State private var password = ""
    @State private var backupURL: URL?
    @State private var choosing = false
    @State private var preview: WalletSnapshot?
    @State private var replacing = false
    @State private var working = false
    @State private var message: String?
    @State private var task: Task<Void, Never>?
    private let worker = BackupWorker()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Kura unlocked the device key but could not read the current wallet. Choose an earlier copy to review.")
                    Button("Choose encrypted backup") { choosing = true }
                    if backupURL != nil {
                        SecureField("Backup password", text: $password)
                        Button("Review backup") { reviewFile() }.disabled(password.isEmpty || working)
                    }
                }
                Section("On-device recovery copies") {
                    if copies.isEmpty { Text("No recovery copies available") }
                    ForEach(copies) { copy in
                        Button { reviewCopy(copy) } label: {
                            VStack(alignment: .leading) {
                                Text(copy.date, format: .dateTime.year().month().day().hour().minute())
                                Text(ByteCountFormatter.string(fromByteCount: Int64(copy.bytes), countStyle: .file))
                                    .font(.caption)
                            }
                        }
                    }
                }
                if let preview {
                    Section("Review replacement") {
                        Text("The selected copy has \(preview.cards.count) items. Its wallet preferences will also be restored.")
                        Button("Replace damaged wallet…", role: .destructive) { replacing = true }
                        Button("Discard preview") { self.preview = nil }
                    }
                }
                if working { ProgressView() }
                if let message { Text(message) }
            }
            .disabled(working)
            .navigationTitle("Recover wallet")
            .toolbar { Button("Lock", action: wallet.lock) }
        }
        .onAppear {
            generation = wallet.generation
            do { copies = try BackupRecovery.entries() }
            catch { message = "Kura could not list on-device copies. You can still choose an exported backup." }
        }
        .onDisappear { clear() }
        .onChange(of: wallet.generation) { _ in clear() }
        .fileImporter(isPresented: $choosing, allowedContentTypes: [.data]) { result in
            guard wallet.generation == generation, wallet.phase == .recoveryRequired else { return }
            if case let .success(url) = result {
                backupURL = url
                password = ""
                preview = nil
                message = nil
            }
        }
        .alert("Replace the damaged wallet?", isPresented: $replacing) {
            Button("Replace wallet", role: .destructive) { restore() }
            Button("Cancel", role: .cancel) {}
        } message: { Text("Kura must preserve and verify a copy of the damaged file before it can replace your wallet.") }
    }

    private func reviewFile() {
        guard let url = backupURL else { return }
        let secret = password
        preview = nil
        run { token in
            let access = BackupFileAccess()
            let result = try await withTaskCancellationHandler {
                try await worker.preview(url, password: secret, access: access)
            } onCancel: { access.cancel() }
            try check(token)
            preview = result
            password = ""
            backupURL = nil
        }
    }

    private func reviewCopy(_ copy: BackupRecovery) {
        preview = nil
        run { token in
            let result = try await wallet.recoverySnapshot(copy.id, generation: token)
            try check(token)
            preview = result
            backupURL = nil
            password = ""
        }
    }

    private func restore() {
        guard let snapshot = preview else { return }
        run { token in
            try check(token)
            try await wallet.replaceForRestore(snapshot, generation: token)
        }
    }

    private func run(_ action: @escaping @MainActor (UUID) async throws -> Void) {
        guard let token = generation, wallet.phase == .recoveryRequired, token == wallet.generation, !working else { return }
        working = true
        message = nil
        task = Task { @MainActor in
            defer { if !Task.isCancelled { working = false } }
            do { try await action(token) }
            catch {
                if !Task.isCancelled, token == wallet.generation {
                    message = (error as? BackupError)?.localizedDescription ?? "Recovery did not finish. The current wallet has been kept."
                }
            }
        }
    }

    private func check(_ token: UUID) throws {
        try Task.checkCancellation()
        guard token == wallet.generation, wallet.phase == .recoveryRequired else { throw BackupError.expired }
    }

    private func clear() {
        task?.cancel()
        generation = nil
        preview = nil
        backupURL = nil
        password = ""
        working = false
    }
}
