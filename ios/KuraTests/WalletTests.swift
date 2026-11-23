import CryptoKit
import Vision
import XCTest
@testable import Kura

final class WalletTests: XCTestCase {
    func testSavedCardSurvivesReopenAndEdit() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = VaultFile(directory: directory)
        let key = SymmetricKey(size: .bits256)
        let card = try WalletCard(name: " Sample library ", payload: "00001842")
        XCTAssertNil(try file.readCiphertext())
        try file.write(WalletSnapshot(cards: [card]), key: key)
        let ciphertext = try XCTUnwrap(file.readCiphertext())
        XCTAssertEqual(try file.decodeSnapshot(ciphertext, key: key).cards, [card])
        XCTAssertNil(ciphertext.range(of: Data(card.name.utf8)))
        let edited = try WalletCard(id: card.id, name: "Branch library", payload: "00001843")
        try file.write(WalletSnapshot(cards: [edited]), key: key)
        let reopened = VaultFile(directory: directory)
        XCTAssertEqual(try reopened.decodeSnapshot(XCTUnwrap(reopened.readCiphertext()), key: key).cards, [edited])
    }

    func testWrongKeyAndDamagedVaultKeepOriginalBytes() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = VaultFile(directory: directory)
        let key = SymmetricKey(size: .bits256)
        try file.write(WalletSnapshot(cards: [WalletCard(name: "Test", payload: "0001")]), key: key)
        let ciphertext = try XCTUnwrap(file.readCiphertext())
        XCTAssertThrowsError(try file.decodeSnapshot(ciphertext, key: SymmetricKey(size: .bits256)))
        var damaged = ciphertext
        damaged[damaged.count - 1] ^= 1
        XCTAssertThrowsError(try file.decodeSnapshot(damaged, key: key))
        XCTAssertEqual(try file.readCiphertext(), ciphertext)
    }

    func testEmptyExistingFileIsAnError() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data().write(to: directory.appendingPathComponent("cards.kura"))
        XCTAssertThrowsError(try VaultFile(directory: directory).readCiphertext())
    }

    func testCardValidationKeepsSignificantPayloadBytes() throws {
        let card = try WalletCard(name: " Library ", payload: " 0001 ")
        XCTAssertEqual(card.name, "Library")
        XCTAssertEqual(card.payload, " 0001 ")
        XCTAssertThrowsError(try WalletCard(name: "  ", payload: "1"))
        XCTAssertThrowsError(try WalletCard(name: "Library", payload: "  "))
        XCTAssertEqual(try WalletCard(name: "Library", payload: "会員番号").payload, "会員番号")
        XCTAssertThrowsError(try WalletCard(name: "Library", payload: String(repeating: "0", count: 8193)))
    }

    func testSharedQRPayloadsDecodeExactly() async throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "qr-payloads", withExtension: "json"))
        let payloads = try JSONDecoder().decode([String].self, from: Data(contentsOf: url))
        XCTAssertFalse(payloads.isEmpty)
        for payload in payloads {
            let card = try WalletCard(name: "Test card", payload: payload)
            let rendered = await CardQR.shared.image(for: card.payload)
            let image = try XCTUnwrap(rendered)
            let found = try BarcodePhoto.observations(in: image, symbologies: [.qr])
            XCTAssertEqual(found.first?.payloadStringValue, payload)
        }
    }
}
