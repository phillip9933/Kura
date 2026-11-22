import CryptoKit
import Foundation
import SwiftUI

private struct AutomaticDestination: Codable {
    let id: UUID
    let bookmark: Data
    let password: String
    let retention: Int
}

private struct BackupReceipt: Codable {
    let fileID: UUID
    let digest: Data
}

private struct BackupLedger: Codable {
    var lastSnapshot: Data?
    var files: [BackupReceipt] = []
}

private enum BackupSettingsFile {
    static let directory = BackupRecovery.walletDirectory.appendingPathComponent("Automatic", isDirectory: true)
    static let config = directory.appendingPathComponent("destination")
    static func ledger(_ id: UUID) -> URL {
        directory.appendingPathComponent(id.uuidString + ".ledger")
    }

    static func read<Value: Decodable>(_ type: Value.Type, at url: URL, key: SymmetricKey) throws -> Value {
        let box = try AES.GCM.SealedBox(combined: BackupFiles.read(url, limit: 4 * 1024 * 1024))
        var data = try AES.GCM.open(box, using: key)
        defer { data.resetBytes(in: 0..<data.count) }
        return try JSONDecoder().decode(type, from: data)
    }

    static func write<Value: Encodable>(_ value: Value, to url: URL, key: SymmetricKey) throws {
        let directory = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
                                               attributes: [.protectionKey: FileProtectionType.complete])
        var folder = directory
        var properties = URLResourceValues()
        properties.isExcludedFromBackup = true
        try folder.setResourceValues(properties)
        var data = try JSONEncoder().encode(value)
        defer { data.resetBytes(in: 0..<data.count) }
        guard let sealed = try AES.GCM.seal(data, using: key).combined else { throw BackupError.unavailable }
        try sealed.write(to: url, options: [.atomic, .completeFileProtection])
    }
}

actor AutomaticWriter {
    func run(snapshot: WalletSnapshot, key: SymmetricKey, access: BackupFileAccess) throws {
        try Task.checkCancellation()
        let config = try BackupSettingsFile.read(AutomaticDestination.self, at: BackupSettingsFile.config, key: key)
        var stale = false
        let folder = try URL(resolvingBookmarkData: config.bookmark, options: [],
                             relativeTo: nil, bookmarkDataIsStale: &stale)
        guard !stale else { throw BackupError.unavailable }
        let scoped = folder.startAccessingSecurityScopedResource()
        defer { if scoped { folder.stopAccessingSecurityScopedResource() } }

        try write(snapshot: snapshot, key: key, folder: folder, password: config.password,
                  retention: config.retention, ledgerURL: BackupSettingsFile.ledger(config.id), access: access)
    }

    func write(snapshot: WalletSnapshot, key: SymmetricKey, folder: URL, password: String,
               retention: Int, ledgerURL: URL, access: BackupFileAccess = BackupFileAccess()) throws {
        try Task.checkCancellation()
        guard (1...1000).contains(retention) else { throw BackupError.invalid }
        var ledger: BackupLedger
        do { ledger = try BackupSettingsFile.read(BackupLedger.self, at: ledgerURL, key: key) }
        catch let error as CocoaError where error.code == .fileReadNoSuchFile { ledger = BackupLedger() }

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        var plain = try encoder.encode(snapshot)
        defer { plain.resetBytes(in: 0..<plain.count) }
        let digest = Data(SHA256.hash(data: plain))
        func location(_ id: UUID) -> URL {
            folder.appendingPathComponent("Kura-" + id.uuidString).appendingPathExtension("kurabackup")
        }

        if ledger.lastSnapshot == digest, let last = ledger.files.last, ledger.files.count <= retention,
           FileManager.default.fileExists(atPath: location(last.fileID).path) {
            let lastURL = location(last.fileID)
            let values = try lastURL.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey])
            if values.isRegularFile == true, values.isSymbolicLink != true,
               let size = values.fileSize, size > 0, size <= BackupCodec.maximumFileBytes {
                let saved = try BackupFiles.readPickedFile(lastURL, access: access)
                if Data(SHA256.hash(data: saved)) == last.digest { return }
            }
        }

        let encrypted = try BackupCodec.seal(plain, password: password, kind: .wallet)
        try Task.checkCancellation()
        let id = UUID()
        let expected = Data(SHA256.hash(data: encrypted))
        try Self.coordinateWrite(with: access, at: location(id), options: []) { url in
            try Task.checkCancellation()
            let staged = url.deletingLastPathComponent().appendingPathComponent(UUID().uuidString + ".partial")
            try encrypted.write(to: staged, options: .withoutOverwriting)
            defer { try? FileManager.default.removeItem(at: staged) }
            let stored = try BackupFiles.read(staged, limit: encrypted.count)
            guard Data(SHA256.hash(data: stored)) == expected else { throw BackupError.verifyFailed }
            try Task.checkCancellation()
            // Moving to a unique final name cannot replace an existing document.
            try FileManager.default.moveItem(at: staged, to: url)
            let published = try BackupFiles.read(url, limit: encrypted.count)
            guard Data(SHA256.hash(data: published)) == expected else { throw BackupError.verifyFailed }
        }
        ledger.files.append(BackupReceipt(fileID: id, digest: expected))
        ledger.lastSnapshot = digest
        try BackupSettingsFile.write(ledger, to: ledgerURL, key: key)
        while ledger.files.count > retention {
            try Task.checkCancellation()
            let oldest = ledger.files[0]
            let oldURL = location(oldest.fileID)
            if FileManager.default.fileExists(atPath: oldURL.path) {
                try Self.coordinateWrite(with: access, at: oldURL, options: .forDeleting) { url in
                    try Task.checkCancellation()
                    let properties = try url.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey])
                    // A changed file belongs to the user. Drop its receipt and leave it in place.
                    guard properties.isRegularFile == true, properties.isSymbolicLink != true,
                          let size = properties.fileSize, size > 0, size <= BackupCodec.maximumFileBytes
                    else { return }
                    let current = try BackupFiles.read(url)
                    if Data(SHA256.hash(data: current)) == oldest.digest {
                        try FileManager.default.removeItem(at: url)
                    }
                }
            }
            ledger.files.removeFirst()
            try BackupSettingsFile.write(ledger, to: ledgerURL, key: key)
        }
    }

    private static func coordinateWrite(with access: BackupFileAccess, at url: URL, options: NSFileCoordinator.WritingOptions,
                                        action: @escaping (URL) throws -> Void) throws {
        var error: NSError?
        var result: Result<Void, Error>?
        access.coordinator.coordinate(writingItemAt: url, options: options, error: &error) { target in
            result = Result { try action(target) }
        }
        if let error { throw error }
        guard let result else { throw BackupError.unavailable }
        try result.get()
    }
}

@MainActor
final class BackupAutomatic: ObservableObject {
    static let shared = BackupAutomatic()
    private let writer = AutomaticWriter()
    private var job: Task<Void, Never>?
    @Published private(set) var status = "Automatic backups are off."
    static var configured: Bool { FileManager.default.fileExists(atPath: BackupSettingsFile.config.path) }

    static func configure(folder: URL, password: String, retention: Int, key: SymmetricKey) throws {
        try BackupCodec.checkNewPassword(password)
        guard (1...1000).contains(retention) else { throw BackupError.invalid }
        let scoped = folder.startAccessingSecurityScopedResource()
        defer { if scoped { folder.stopAccessingSecurityScopedResource() } }
        guard try folder.resourceValues(forKeys: [.isDirectoryKey]).isDirectory == true else { throw BackupError.unavailable }
        let bookmark = try folder.bookmarkData(options: .minimalBookmark,
            includingResourceValuesForKeys: nil, relativeTo: nil)
        cancel()
        let config = AutomaticDestination(id: UUID(), bookmark: bookmark, password: password, retention: retention)
        try BackupSettingsFile.write(config, to: BackupSettingsFile.config, key: key)
        shared.status = "Automatic backups are enabled."
    }

    static func disable() throws {
        cancel()
        if configured { try FileManager.default.removeItem(at: BackupSettingsFile.config) }
        shared.status = "Automatic backups are off."
    }

    static func cancel() {
        shared.job?.cancel()
        shared.job = nil
    }

    static func schedule(snapshot: WalletSnapshot, key: SymmetricKey) {
        cancel()
        guard configured else { return }
        shared.job = Task {
            do {
                try await Task.sleep(nanoseconds: 700_000_000)
                shared.status = "Checking automatic backup…"
                let access = BackupFileAccess()
                try await withTaskCancellationHandler {
                    try await shared.writer.run(snapshot: snapshot, key: key, access: access)
                } onCancel: { access.cancel() }
                try Task.checkCancellation()
                shared.status = "Automatic backup is up to date."
            } catch is CancellationError {
                return
            } catch {
                if !Task.isCancelled { shared.status = "Automatic backup failed. Check the folder and available space." }
            }
        }
    }
}
