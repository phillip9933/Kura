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
        try file.write([card], key: key)
        let ciphertext = try XCTUnwrap(file.readCiphertext())
        XCTAssertEqual(try file.decode(ciphertext, key: key), [card])
        XCTAssertNil(ciphertext.range(of: Data(card.name.utf8)))
        let edited = try WalletCard(id: card.id, name: "Branch library", payload: "00001843")
        try file.write([edited], key: key)
        let reopened = VaultFile(directory: directory)
        XCTAssertEqual(try reopened.decode(XCTUnwrap(reopened.readCiphertext()), key: key), [edited])
    }

    func testWrongKeyAndDamagedVaultKeepOriginalBytes() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = VaultFile(directory: directory)
        let key = SymmetricKey(size: .bits256)
        try file.write([WalletCard(name: "Test", payload: "0001")], key: key)
        let ciphertext = try XCTUnwrap(file.readCiphertext())
        XCTAssertThrowsError(try file.decode(ciphertext, key: SymmetricKey(size: .bits256)))
        var damaged = ciphertext
        damaged[damaged.count - 1] ^= 1
        XCTAssertThrowsError(try file.decode(damaged, key: key))
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
        XCTAssertThrowsError(try WalletCard(name: "Library", payload: "A\nB"))
        XCTAssertThrowsError(try WalletCard(name: "Library", payload: String(repeating: "0", count: 513)))
    }

    func testSharedQRPayloadsDecodeExactly() async throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "qr-payloads", withExtension: "json"))
        let payloads = try JSONDecoder().decode([String].self, from: Data(contentsOf: url))
        XCTAssertFalse(payloads.isEmpty)
        for payload in payloads {
            let card = try WalletCard(name: "Test card", payload: payload)
            let rendered = await CardQR.shared.image(for: card.payload)
            let image = try XCTUnwrap(rendered)
            let request = VNDetectBarcodesRequest()
            request.symbologies = [.qr]
            try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
            XCTAssertEqual(request.results?.first?.payloadStringValue, payload)
        }
    }
}
