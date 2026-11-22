import CryptoKit
import Foundation

struct BackupRecovery: Identifiable {
    let id: UUID
    let date: Date
    let bytes: Int

    static var walletDirectory: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Wallet", isDirectory: true)
    }

    private static func folder(in directory: URL) -> URL {
        directory.appendingPathComponent("Recovery", isDirectory: true)
    }

    static func location(_ id: UUID, in directory: URL) -> URL {
        folder(in: directory).appendingPathComponent(id.uuidString).appendingPathExtension("kura")
    }

    static func entries(in directory: URL = walletDirectory) throws -> [BackupRecovery] {
        let urls: [URL]
        do {
            urls = try FileManager.default.contentsOfDirectory(at: folder(in: directory),
                includingPropertiesForKeys: [.isRegularFileKey, .contentModificationDateKey, .fileSizeKey])
        } catch let error as CocoaError where error.code == .fileReadNoSuchFile {
            return []
        }
        return try urls.compactMap { url in
            guard url.pathExtension == "kura",
                  let id = UUID(uuidString: url.deletingPathExtension().lastPathComponent) else { return nil }
            let values = try url.resourceValues(forKeys: [.isRegularFileKey, .contentModificationDateKey, .fileSizeKey])
            guard values.isRegularFile == true, let date = values.contentModificationDate else { return nil }
            return BackupRecovery(id: id, date: date, bytes: values.fileSize ?? 0)
        }.sorted { $0.date > $1.date }
    }

    static func remove(_ id: UUID, in directory: URL = walletDirectory) throws {
        try FileManager.default.removeItem(at: location(id, in: directory))
    }
}

extension VaultFile {
    func recordRecoveryBeforeRestore(key: SymmetricKey) throws {
        guard let bytes = try readCiphertext() else { return }
        _ = try decodeSnapshot(bytes, key: key)
        try Task.checkCancellation()
        let url = BackupRecovery.location(UUID(), in: directory)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true,
                                               attributes: [.protectionKey: FileProtectionType.complete])
        try bytes.write(to: url, options: [.atomic, .completeFileProtection])
        guard try BackupFiles.read(url, limit: bytes.count) == bytes else { throw BackupError.verifyFailed }
    }

    func readRecovery(id: UUID, key: SymmetricKey) throws -> WalletSnapshot {
        try decodeSnapshot(BackupFiles.read(BackupRecovery.location(id, in: directory)), key: key)
    }
}
