import CryptoKit
import XCTest
@testable import Kura

final class BackupTests: XCTestCase {
    private let password = "synthetic-pass-007"

    private func sample(_ title: String = "Library") throws -> WalletSnapshot {
        var card = try WalletCard(name: title, payload: "000260", section: .passes)
        card.notes = "Synthetic detail"
        card.fields = ["Code": "001"]
        try card.media.add(WalletAttachment(name: "test.txt", bytes: Data("fixture".utf8)))
        var preferences = WalletPreferences()
        preferences.startSection = .identities
        return WalletSnapshot(cards: [card], preferences: preferences)
    }

    func testVersionOneOpensIndependentCiphertextVector() throws {
        // OpenSSL PBKDF2-SHA256 and AES-GCM, salt 00…0f, nonce 00…0b.
        let encoded = "S3VyYS1pT1MBAQABAgMEBQYHCAkKCwwNDg8AAQIDBAUGBwgJCgvIv5qLbNSnFapGvClGENmRy9uM2KDEJHTpZ4XSd49Hj//zjLLmPzrutQ=="
        let data = try XCTUnwrap(Data(base64Encoded: encoded))
        let plaintext = try BackupCodec.open(data, password: "Kura synthetic vector", kind: .wallet)
        XCTAssertEqual(plaintext, Data("Kura backup test vector 1".utf8))
    }

    func testEncryptedBackupRoundTripsSettingsAndAttachments() throws {
        let snapshot = try sample()
        let data = try BackupCodec.encode(snapshot, password: password)
        XCTAssertNil(data.range(of: Data("Synthetic detail".utf8)))
        XCTAssertEqual(try BackupCodec.decode(data, password: password), snapshot)
        XCTAssertNotEqual(try BackupCodec.encode(snapshot, password: password), data)
        XCTAssertThrowsError(try BackupCodec.decode(data, password: "wrong"))
        XCTAssertThrowsError(try BackupCodec.open(data, password: password, kind: .item))
        var damaged = data
        damaged[damaged.count - 1] ^= 1
        XCTAssertThrowsError(try BackupCodec.decode(damaged, password: password))
        XCTAssertThrowsError(try BackupCodec.encode(snapshot, password: "weak"))
    }

    func testInvalidSnapshotDoesNotProduceRestorePreview() throws {
        var snapshot = try sample()
        snapshot.cards.append(snapshot.cards[0])
        let json = try JSONEncoder().encode(snapshot)
        let data = try BackupCodec.seal(json, password: password, kind: .wallet)
        XCTAssertThrowsError(try BackupCodec.decode(data, password: password))
    }

    func testRecoveryPreservesOriginalAndDeleteLeavesActiveWalletAlone() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = VaultFile(directory: directory)
        let key = SymmetricKey(size: .bits256)
        let first = try sample("Original")
        let second = try sample("Replacement")
        try file.write(first, key: key)
        try file.recordRecoveryBeforeRestore(key: key)
        try file.write(second, key: key)
        let copies = try BackupRecovery.entries(in: directory)
        XCTAssertEqual(copies.count, 1)
        let id = try XCTUnwrap(copies.first?.id)
        XCTAssertEqual(try file.readRecovery(id: id, key: key), first)
        XCTAssertThrowsError(try file.readRecovery(id: id, key: SymmetricKey(size: .bits256)))
        try BackupRecovery.remove(id, in: directory)
        XCTAssertTrue(try BackupRecovery.entries(in: directory).isEmpty)
        XCTAssertEqual(try file.decodeSnapshot(XCTUnwrap(file.readCiphertext()), key: key), second)
    }

    func testAutomaticRetentionLeavesUnrelatedAndChangedFilesAlone() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let folder = directory.appendingPathComponent("External")
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let ledger = directory.appendingPathComponent("Private/ledger")
        let foreign = folder.appendingPathComponent("Kura-" + UUID().uuidString + ".kurabackup")
        try Data("unrelated".utf8).write(to: foreign)
        let key = SymmetricKey(size: .bits256)
        let writer = AutomaticWriter()
        let first = try sample("First")
        try await writer.write(snapshot: first, key: key, folder: folder, password: password,
                               retention: 1, ledgerURL: ledger)
        let files = try FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)
        let managed = try XCTUnwrap(files.first { $0.lastPathComponent != foreign.lastPathComponent })
        try await writer.write(snapshot: first, key: key, folder: folder, password: password,
                               retention: 1, ledgerURL: ledger)
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: folder.path).count, 2)
        let replacement = Data("user replacement".utf8)
        try replacement.write(to: managed)
        try await writer.write(snapshot: first, key: key, folder: folder, password: password,
                               retention: 1, ledgerURL: ledger)
        let latest = try sample("Third")
        try await writer.write(snapshot: latest, key: key, folder: folder, password: password,
                               retention: 1, ledgerURL: ledger)
        XCTAssertEqual(try Data(contentsOf: managed), replacement)
        XCTAssertEqual(try Data(contentsOf: foreign), Data("unrelated".utf8))
        let remaining = try FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)
        XCTAssertEqual(remaining.count, 3)
        let active = try XCTUnwrap(remaining.first {
            $0.lastPathComponent != foreign.lastPathComponent && $0.lastPathComponent != managed.lastPathComponent
        })
        XCTAssertEqual(try BackupCodec.decode(BackupFiles.read(active), password: password), latest)
    }

    func testSingleItemFileKeepsMediaAndCannotReplaceAnEntireWallet() throws {
        let source = try XCTUnwrap(sample().cards.first)
        let bytes = try BackupPassFile.encode(source, password: password)
        let copy = try BackupPassFile.decode(bytes, password: password)
        XCTAssertNotEqual(copy.id, source.id)
        XCTAssertEqual(copy.name, source.name)
        XCTAssertEqual(copy.media, source.media)
        XCTAssertEqual(copy.fields, source.fields)
        XCTAssertEqual(copy.section, source.section)
        XCTAssertThrowsError(try BackupCodec.decode(bytes, password: password))
        XCTAssertThrowsError(try BackupTransferCodec.open(bytes, password: password))
        XCTAssertThrowsError(try BackupPassFile.decode(bytes, password: "incorrect"))
    }

    func testTransferHandlesUnorderedDuplicateAndMixedCodesAndOmitsMedia() throws {
        var card = try XCTUnwrap(sample().cards.first)
        card.notes = String(repeating: "synthetic", count: 200)
        let parts = try BackupTransferCodec.chunks(for: card, password: password)
        XCTAssertGreaterThan(parts.count, 1)
        var input = BackupTransferCollection()
        try input.append(parts[1])
        try input.append(parts[1])
        XCTAssertEqual(input.count, 1)
        XCTAssertThrowsError(try input.assembled())
        let foreign = parts[1].split(separator: ":").enumerated().map {
            $0.offset == 1 ? UUID().uuidString : String($0.element)
        }.joined(separator: ":")
        XCTAssertThrowsError(try input.append(foreign))
        for part in parts.reversed() { try input.append(part) }
        let encrypted = try input.assembled()
        XCTAssertThrowsError(try BackupTransferCodec.open(encrypted, password: "incorrect"))
        let copy = try BackupTransferCodec.open(encrypted, password: password)
        XCTAssertNotEqual(copy.id, card.id)
        XCTAssertEqual(copy.notes, card.notes)
        XCTAssertEqual(copy.payload, card.payload)
        XCTAssertEqual(copy.media, WalletMedia())
    }
}
