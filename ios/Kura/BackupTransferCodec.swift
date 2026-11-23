import Foundation

enum BackupTransferCodec {
    static let chunkSize = 432
    static let maximumChunks = (BackupCodec.maximumTransferBytes + chunkSize - 1) / chunkSize

    static func chunks(for card: WalletCard, password: String) throws -> [String] {
        var item = card
        item.media = WalletMedia()
        item.passStyle?.artwork = [:]
        var data = try JSONEncoder().encode(item)
        defer { data.resetBytes(in: 0..<data.count) }
        let encrypted = try BackupCodec.seal(data, password: password, kind: .item)
        let id = UUID().uuidString
        let total = (encrypted.count + chunkSize - 1) / chunkSize
        return stride(from: 0, to: encrypted.count, by: chunkSize).map { offset in
            let end = min(offset + chunkSize, encrypted.count)
            return "KURA1:\(id):\(offset / chunkSize):\(total):" + encrypted[offset..<end].base64EncodedString()
        }
    }

    static func open(_ encrypted: Data, password: String) throws -> WalletCard {
        var data = try BackupCodec.open(encrypted, password: password, kind: .item)
        defer { data.resetBytes(in: 0..<data.count) }
        var item = try JSONDecoder().decode(WalletCard.self, from: data)
        guard item.media == WalletMedia(), item.passStyle?.artwork.isEmpty != false else { throw BackupError.transfer }
        // A transfer creates a new item, even when the sender also owns this wallet.
        item.id = UUID()
        item.created = Date()
        return item
    }
}

struct BackupTransferCollection {
    private(set) var id: UUID?
    private(set) var total = 0
    private var chunks: [Int: Data] = [:]
    var count: Int { chunks.count }

    mutating func append(_ code: String) throws {
        guard code.utf8.count <= 700 else { throw BackupError.transfer }
        let fields = code.split(separator: ":", omittingEmptySubsequences: false)
        guard fields.count == 5, fields[0] == "KURA1",
              let incomingID = UUID(uuidString: String(fields[1])),
              let index = Int(fields[2]), let count = Int(fields[3]),
              (1...BackupTransferCodec.maximumChunks).contains(count), (0..<count).contains(index),
              let bytes = Data(base64Encoded: String(fields[4])), !bytes.isEmpty,
              bytes.count <= BackupTransferCodec.chunkSize,
              index == count - 1 || bytes.count == BackupTransferCodec.chunkSize
        else { throw BackupError.transfer }
        if let id, id != incomingID || total != count { throw BackupError.transfer }
        if let existing = chunks[index], existing != bytes { throw BackupError.transfer }
        id = incomingID
        total = count
        chunks[index] = bytes
    }

    func assembled() throws -> Data {
        guard total > 0, count == total else { throw BackupError.transfer }
        var data = Data()
        for index in 0..<total {
            guard let chunk = chunks[index] else { throw BackupError.transfer }
            data.append(chunk)
        }
        guard data.count <= BackupCodec.maximumTransferBytes else { throw BackupError.tooLarge }
        return data
    }
}

actor TransferCodecWorker {
    func encode(_ card: WalletCard, password: String) throws -> [String] {
        try BackupTransferCodec.chunks(for: card, password: password)
    }

    func decode(_ collection: BackupTransferCollection, password: String) throws -> WalletCard {
        try BackupTransferCodec.open(collection.assembled(), password: password)
    }
}
