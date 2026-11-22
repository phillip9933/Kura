import CryptoKit
import Foundation

struct VaultFile {
    let directory: URL
    private var file: URL { directory.appendingPathComponent("cards.kura") }
    private static let maximumBytes = 4 * 1024 * 1024

    private struct Contents: Codable {
        let version: Int
        let cards: [WalletCard]
    }

    func readCiphertext() throws -> Data? {
        let handle: FileHandle
        do {
            handle = try FileHandle(forReadingFrom: file)
        } catch let error as CocoaError where error.code == .fileReadNoSuchFile {
            return nil
        }
        defer { try? handle.close() }
        let size = try handle.seekToEnd()
        guard size <= UInt64(Self.maximumBytes) else { throw WalletError.tooLarge }
        try handle.seek(toOffset: 0)
        let data = try handle.read(upToCount: Int(size) + 1) ?? Data()
        guard size > 0, data.count == Int(size) else { throw WalletError.vaultUnavailable }
        return data
    }

    func decode(_ ciphertext: Data, key: SymmetricKey) throws -> [WalletCard] {
        let box = try AES.GCM.SealedBox(combined: ciphertext)
        let data = try AES.GCM.open(box, using: key)
        let contents = try JSONDecoder().decode(Contents.self, from: data)
        guard contents.version == 1, Set(contents.cards.map(\.id)).count == contents.cards.count
        else { throw WalletError.vaultUnavailable }
        return contents.cards
    }

    func write(_ cards: [WalletCard], key: SymmetricKey) throws {
        let data = try JSONEncoder().encode(Contents(version: 1, cards: cards))
        guard let encrypted = try AES.GCM.seal(data, using: key).combined,
              encrypted.count <= Self.maximumBytes else { throw WalletError.tooLarge }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
                                                attributes: [.protectionKey: FileProtectionType.complete])
        var folder = directory
        var attributes = URLResourceValues()
        attributes.isExcludedFromBackup = true
        try folder.setResourceValues(attributes)
        try encrypted.write(to: file, options: [.atomic, .completeFileProtection])
    }
}
