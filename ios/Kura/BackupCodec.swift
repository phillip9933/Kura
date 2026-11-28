import CommonCrypto
import CryptoKit
import Foundation

enum BackupError: LocalizedError {
    case password, invalid, tooLarge, unavailable, expired, verifyFailed, transfer, transferExpired

    var errorDescription: String? {
        switch self {
        case .password: return "Use a password of at least 8 characters and at most 1,024 UTF-8 bytes."
        case .invalid: return "Kura could not open this file. Check the password and file."
        case .tooLarge: return "The file exceeds the supported size."
        case .unavailable: return "Kura could not access the file or folder. Choose it again in Files."
        case .expired: return "Unlock the wallet and try again."
        case .verifyFailed: return "Kura could not verify the saved backup. Earlier backups were kept."
        case .transferExpired: return "This transfer expired. Start over and scan all the codes within five minutes."
        case .transfer: return "This code does not belong to the current transfer or exceeds its limits."
        }
    }
}

enum BackupCodec {
    enum Kind: UInt8 { case wallet = 1, item = 2, passFile = 3 }

    private static let magic = Data("Kura-iOS".utf8)
    private static let saltLength = 16
    private static let rounds: UInt32 = 600_000
    static let maximumFileBytes = 100 * 1024 * 1024
    static let maximumTransferBytes = 64 * 1024

    static func checkNewPassword(_ password: String) throws {
        guard password.count >= 8, password.utf8.count <= 1024 else { throw BackupError.password }
    }

    private static func deriveKey(password: String, salt: Data) throws -> SymmetricKey {
        guard !password.isEmpty, password.utf8.count <= 1024 else { throw BackupError.invalid }
        var bytes = Data(password.utf8)
        var output = Data(count: 32)
        defer {
            bytes.resetBytes(in: 0..<bytes.count)
            output.resetBytes(in: 0..<output.count)
        }
        let status = output.withUnsafeMutableBytes { destination in
            salt.withUnsafeBytes { saltBuffer in
                bytes.withUnsafeBytes { input in
                    guard let pass = input.bindMemory(to: Int8.self).baseAddress,
                          let salt = saltBuffer.bindMemory(to: UInt8.self).baseAddress,
                          let key = destination.bindMemory(to: UInt8.self).baseAddress
                    else { return Int32(kCCParamError) }
                    return CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2),
                        pass, input.count, salt, saltBuffer.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), rounds, key, destination.count)
                }
            }
        }
        guard status == kCCSuccess else { throw BackupError.invalid }
        return SymmetricKey(data: output)
    }

    private static func prefix(for kind: Kind) -> Data {
        // Version 1 fixes the KDF, work factor, and cipher. Inputs cannot request more work.
        magic + Data([1, kind.rawValue])
    }

    static func seal(_ plaintext: Data, password: String, kind: Kind) throws -> Data {
        try checkNewPassword(password)
        try Task.checkCancellation()
        let limit = kind == .item ? maximumTransferBytes : maximumFileBytes
        let salt = SymmetricKey(size: .bits128).withUnsafeBytes { Data($0) }
        let header = prefix(for: kind) + salt
        guard plaintext.count <= limit - header.count - 28 else { throw BackupError.tooLarge }
        let key = try deriveKey(password: password, salt: salt)
        try Task.checkCancellation()
        let box = try AES.GCM.seal(plaintext, using: key, authenticating: header)
        guard let encrypted = box.combined else { throw BackupError.invalid }
        return header + encrypted
    }

    static func open(_ data: Data, password: String, kind: Kind) throws -> Data {
        try Task.checkCancellation()
        let expectedPrefix = prefix(for: kind)
        let offset = expectedPrefix.count + saltLength
        let limit = kind == .item ? maximumTransferBytes : maximumFileBytes
        guard data.count <= limit else { throw BackupError.tooLarge }
        guard data.count >= offset + 28, data.starts(with: expectedPrefix) else { throw BackupError.invalid }
        let header = Data(data.prefix(offset))
        let key = try deriveKey(password: password, salt: Data(header.suffix(saltLength)))
        try Task.checkCancellation()
        do {
            let box = try AES.GCM.SealedBox(combined: data.dropFirst(offset))
            return try AES.GCM.open(box, using: key, authenticating: header)
        } catch { throw BackupError.invalid }
    }

    static func encode(_ snapshot: WalletSnapshot, password: String) throws -> Data {
        var json = try JSONEncoder().encode(snapshot)
        defer { json.resetBytes(in: 0..<json.count) }
        return try seal(json, password: password, kind: .wallet)
    }

    static func openJSON<T: Decodable>(_ type: T.Type, from data: Data, password: String, kind: Kind) throws -> T {
        var json = try open(data, password: password, kind: kind)
        defer { json.resetBytes(in: 0..<json.count) }
        guard ImportedJSON.hasSupportedDepth(json) else { throw BackupError.invalid }
        return try JSONDecoder().decode(type, from: json)
    }

    static func decode(_ data: Data, password: String) throws -> WalletSnapshot {
        let snapshot = try openJSON(WalletSnapshot.self, from: data, password: password, kind: .wallet)
        try snapshot.validate()
        return snapshot
    }
}

// Each external operation has one coordinator. Cancellation can interrupt a pending
// provider handoff without waiting for the storage actor or the main thread.
final class BackupFileAccess: @unchecked Sendable {
    let coordinator = NSFileCoordinator()
    func cancel() { coordinator.cancel() }
}

enum BackupFiles {
    static func read(_ url: URL, limit: Int = BackupCodec.maximumFileBytes) throws -> Data {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        let size = try handle.seekToEnd()
        guard size > 0, size <= UInt64(limit) else {
            throw size == 0 ? BackupError.invalid : BackupError.tooLarge
        }
        try handle.seek(toOffset: 0)
        var data = Data()
        while data.count <= limit {
            try Task.checkCancellation()
            let requested = min(256 * 1024, limit + 1 - data.count)
            guard let chunk = try handle.read(upToCount: requested), !chunk.isEmpty else { break }
            data.append(chunk)
        }
        guard data.count == Int(size) else { throw BackupError.invalid }
        return data
    }

    static func readPickedFile(_ url: URL, access: BackupFileAccess) throws -> Data {
        try Task.checkCancellation()
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        var coordinationError: NSError?
        var result: Result<Data, Error>?
        access.coordinator.coordinate(readingItemAt: url, options: [], error: &coordinationError) { coordinated in
            result = Result { try read(coordinated) }
        }
        if let error = coordinationError { throw error }
        guard let result else { throw BackupError.unavailable }
        return try result.get()
    }
}

actor BackupWorker {
    func export(_ snapshot: WalletSnapshot, password: String) throws -> Data {
        try BackupCodec.encode(snapshot, password: password)
    }

    func preview(_ url: URL, password: String, access: BackupFileAccess) throws -> WalletSnapshot {
        try BackupCodec.decode(BackupFiles.readPickedFile(url, access: access), password: password)
    }
}
