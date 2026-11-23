import SwiftUI
import UniformTypeIdentifiers

struct EncryptedBackupDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.data]
    let bytes: Data

    init(bytes: Data) { self.bytes = bytes }
    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              data.count <= BackupCodec.maximumFileBytes else { throw BackupError.invalid }
        bytes = data
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: bytes)
    }
}

struct BackupView: View {
    private enum PickerPurpose { case restore, automaticFolder }
    @ObservedObject var wallet: WalletSession
    @ObservedObject private var automatic = BackupAutomatic.shared
    @State private var viewGeneration: UUID?
    @State private var password = ""
    @State private var confirmation = ""
    @State private var autoPassword = ""
    @State private var autoConfirmation = ""
    @State private var retention = 5
    @State private var importPassword = ""
    @State private var pickedBackup: URL?
    @State private var pickerPurpose = PickerPurpose.restore
    @State private var pickerGeneration: UUID?
    @State private var showPicker = false
    @State private var document: EncryptedBackupDocument?
    @State private var showExporter = false
    @State private var staged: WalletSnapshot?
    @State private var previewGeneration: UUID?
    @State private var showReplace = false
    @State private var entries: [BackupRecovery] = []
    @State private var deleteCandidate: BackupRecovery?
    @State private var showDelete = false
    @State private var status: String?
    @State private var job: Task<Void, Never>?
    @State private var busy = false
    private let worker = BackupWorker()

    var body: some View {
        Form {
            if let status { Text(status) }
            if busy { ProgressView("Working…") }
            Section("Export encrypted backup") {
                SecureField("New backup password", text: $password)
                SecureField("Confirm backup password", text: $confirmation)
                Button("Create backup") { export() }
                    .disabled(password.isEmpty || password != confirmation || busy)
            }
            Section("Restore encrypted backup") {
                Button(pickedBackup == nil ? "Choose backup file" : "Choose another backup") {
                    pickerPurpose = .restore
                    pickerGeneration = wallet.generation
                    showPicker = true
                }
                if pickedBackup != nil {
                    SecureField("Backup password", text: $importPassword)
                    Button("Review backup") { previewFile() }
                        .disabled(importPassword.isEmpty || busy)
                }
                if let staged {
                    Text("This backup contains \(staged.cards.count) items. Restoring replaces your items and wallet preferences. A recovery copy keeps the current wallet.")
                    Button("Replace wallet…", role: .destructive) { showReplace = true }
                    Button("Discard preview") { discardPreview() }
                }
            }
            Section {
                Text(automatic.status)
                SecureField("Automatic backup password", text: $autoPassword)
                SecureField("Confirm automatic backup password", text: $autoConfirmation)
                Stepper("Keep \(retention) backups", value: $retention, in: 1...1000)
                Button("Choose folder and enable") {
                    pickerPurpose = .automaticFolder
                    pickerGeneration = wallet.generation
                    showPicker = true
                }.disabled(autoPassword.count < 8 || autoPassword != autoConfirmation || busy)
                if BackupAutomatic.configured {
                    Button("Update current folder settings") {
                        guard viewGeneration == wallet.generation else { return }
                        do {
                            try wallet.updateAutomatic(password: autoPassword.isEmpty ? nil : autoPassword, retention: retention)
                            autoPassword = ""
                            autoConfirmation = ""
                        } catch {
                            status = "Kura could not update automatic backup settings."
                        }
                    }
                    .disabled((!autoPassword.isEmpty && autoPassword.count < 8) || autoPassword != autoConfirmation || busy)
                    Button("Turn off automatic backups") {
                        guard viewGeneration == wallet.generation else { return }
                        do { try wallet.disableAutomatic() }
                        catch { status = "Kura could not disable automatic backups." }
                    }
                }
            } header: {
                Text("Automatic backups")
            } footer: {
                Text("Kura saves changes while your wallet is open. To change only retention, leave the password blank and update the current folder. Existing files keep their original passwords. Choosing a folder starts a new backup history.")
            }
            Section {
                if entries.isEmpty { Text("No recovery copies") }
                ForEach(entries) { entry in
                    VStack(alignment: .leading) {
                        Text(entry.date, format: .dateTime.year().month().day().hour().minute())
                        Text(ByteCountFormatter.string(fromByteCount: Int64(entry.bytes), countStyle: .file))
                            .font(.caption)
                        HStack {
                            Button("Review") { previewRecovery(entry) }
                            Spacer()
                            Button("Delete…", role: .destructive) {
                                deleteCandidate = entry
                                showDelete = true
                            }
                        }.buttonStyle(.borderless)
                    }
                }
            } header: {
                Text("On-device recovery")
            } footer: {
                Text("Kura keeps an encrypted copy before replacing an existing wallet. These copies require this device and its wallet key.")
            }
        }
        .autocorrectionDisabled()
        .navigationTitle("Backup & Storage")
        .onAppear {
            viewGeneration = wallet.generation
            refreshRecovery()
            if BackupAutomatic.configured {
                do { retention = try wallet.automaticRetention() }
                catch { status = "Kura could not read the automatic backup settings." }
            }
        }
        .onDisappear { clearSensitiveState() }
        .onChange(of: wallet.generation) { _ in clearSensitiveState() }
        .fileImporter(isPresented: $showPicker,
                      allowedContentTypes: pickerPurpose == .restore ? [.data] : [.folder]) { result in
            guard case let .success(url) = result else { return }
            guard wallet.phase == .unlocked, pickerGeneration == wallet.generation else { return }
            switch pickerPurpose {
            case .restore:
                discardPreview()
                pickedBackup = url
                status = "File selected. Enter its backup password."
            case .automaticFolder:
                do {
                    try wallet.configureAutomatic(folder: url, password: autoPassword, retention: retention)
                    autoPassword = ""
                    autoConfirmation = ""
                } catch {
                    status = (error as? BackupError)?.localizedDescription ?? "Kura could not configure the backup folder."
                }
            }
        }
        .fileExporter(isPresented: $showExporter, document: document, contentType: .data,
                      defaultFilename: "Kura-" + UUID().uuidString + ".kurabackup") { result in
            document = nil
            switch result {
            case .success: status = "Encrypted backup exported."
            case .failure: status = "The export did not finish."
            }
        }
        .alert("Replace the current wallet?", isPresented: $showReplace) {
            Button("Replace wallet", role: .destructive) { replace() }
            Button("Cancel", role: .cancel) {}
        } message: { Text("This uses the reviewed copy. The current wallet becomes a recovery copy.") }
        .alert("Delete this recovery copy?", isPresented: $showDelete) {
            Button("Delete copy", role: .destructive) {
                guard let entry = deleteCandidate, wallet.phase == .unlocked, viewGeneration == wallet.generation else { return }
                do { try BackupRecovery.remove(entry.id); refreshRecovery() }
                catch { status = "Kura could not delete the recovery copy." }
                deleteCandidate = nil
            }
            Button("Cancel", role: .cancel) { deleteCandidate = nil }
        }
    }

    private func export() {
        let snapshot = wallet.snapshot
        let secret = password
        perform { generation in
            let bytes = try await worker.export(snapshot, password: secret)
            try check(generation)
            document = EncryptedBackupDocument(bytes: bytes)
            password = ""
            confirmation = ""
            showExporter = true
        }
    }

    private func previewFile() {
        guard let url = pickedBackup else { return }
        let secret = importPassword
        discardPreview()
        perform { generation in
            let access = BackupFileAccess()
            let preview = try await withTaskCancellationHandler {
                try await worker.preview(url, password: secret, access: access)
            } onCancel: { access.cancel() }
            try check(generation)
            staged = preview
            previewGeneration = generation
            pickedBackup = nil
            importPassword = ""
            status = "Backup ready for review."
        }
    }

    private func previewRecovery(_ entry: BackupRecovery) {
        discardPreview()
        perform { generation in
            let preview = try await wallet.recoverySnapshot(entry.id, generation: generation)
            try check(generation)
            staged = preview
            previewGeneration = generation
            status = "Recovery copy ready for review."
        }
    }

    private func replace() {
        guard let snapshot = staged, let generation = previewGeneration else { return }
        perform { _ in
            try check(generation)
            try await wallet.replaceForRestore(snapshot, generation: generation)
            try check(generation)
            discardPreview()
            refreshRecovery()
            status = "Wallet restored."
        }
    }

    private func refreshRecovery() {
        do { entries = try BackupRecovery.entries() }
        catch { entries = []; status = "Kura could not list recovery copies." }
    }

    private func perform(_ action: @escaping @MainActor (UUID) async throws -> Void) {
        guard wallet.phase == .unlocked, viewGeneration == wallet.generation, !busy else { return }
        let generation = wallet.generation
        busy = true
        status = nil
        job = Task { @MainActor in
            defer { if !Task.isCancelled { busy = false } }
            do { try await action(generation) }
            catch {
                if !Task.isCancelled, generation == wallet.generation {
                    status = (error as? BackupError)?.localizedDescription ?? "Kura could not complete the operation. Your current wallet is intact."
                }
            }
        }
    }

    private func check(_ generation: UUID) throws {
        try Task.checkCancellation()
        guard wallet.generation == generation, wallet.phase == .unlocked else { throw BackupError.expired }
    }

    private func discardPreview() {
        staged = nil
        previewGeneration = nil
    }

    private func clearSensitiveState() {
        job?.cancel()
        job = nil
        busy = false
        discardPreview()
        pickedBackup = nil
        pickerGeneration = nil
        viewGeneration = nil
        document = nil
        password = ""
        confirmation = ""
        autoPassword = ""
        autoConfirmation = ""
        importPassword = ""
    }
}
