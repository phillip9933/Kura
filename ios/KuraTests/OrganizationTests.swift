import XCTest
import CryptoKit
@testable import Kura

final class OrganizationTests: XCTestCase {
    func testVersionOneVaultUpgradesWithoutLosingQRTextOrID() throws {
        let id = UUID()
        let legacy = try JSONSerialization.data(withJSONObject: ["version": 1, "cards": [
            ["id": id.uuidString, "name": "Library", "payload": " 001 "]
        ]])
        let key = SymmetricKey(size: .bits256)
        let encrypted = try XCTUnwrap(AES.GCM.seal(legacy, using: key).combined)
        let file = VaultFile(directory: FileManager.default.temporaryDirectory)
        let wallet = try file.decodeSnapshot(encrypted, key: key)
        let first = try XCTUnwrap(wallet.cards.first)
        XCTAssertEqual(first.id, id)
        XCTAssertEqual(first.payload, " 001 ")
        XCTAssertEqual(first.section, .cards)
        XCTAssertEqual(first.format, .qr)
        XCTAssertEqual(wallet.preferences.startSection, .cards)
    }

    func testSearchFavoriteArchiveAndCategoryStayInSection() throws {
        var library = try WalletCard(name: "Library", payload: "0012", category: "Library", fields: ["Branch": "West"])
        library.favorite = true
        var expired = try WalletCard(name: "Library old", payload: "", category: "Library")
        expired.archived = true
        let boarding = try WalletCard(name: "Westbound", payload: "", section: .passes, category: "Transit")
        let snapshot = WalletSnapshot(cards: [boarding, library, expired])
        XCTAssertEqual(snapshot.visible(section: .cards, archived: false, favoritesOnly: true, category: "Library", query: "west"), [library])
        XCTAssertEqual(snapshot.visible(section: .cards, archived: true, favoritesOnly: false, category: nil, query: ""), [expired])
        XCTAssertEqual(snapshot.visible(section: .passes, archived: false, favoritesOnly: false, category: nil, query: ""), [boarding])
    }

    func testSortAndFavoritePinDoNotChangeStoredOrder() throws {
        var zebra = try WalletCard(name: "Zebra", payload: "")
        zebra.favorite = true
        let alpha = try WalletCard(name: "Alpha", payload: "")
        var wallet = WalletSnapshot(cards: [zebra, alpha])
        wallet.preferences.sorting["Cards"] = .ascending
        XCTAssertEqual(wallet.visible(section: .cards, archived: false, favoritesOnly: false, category: nil, query: ""), [alpha, zebra])
        wallet.preferences.favoritesFirst = true
        XCTAssertEqual(wallet.visible(section: .cards, archived: false, favoritesOnly: false, category: nil, query: ""), [zebra, alpha])
        XCTAssertEqual(wallet.cards, [zebra, alpha])
    }

    func testInvalidSchemasAndDuplicateItemIDsAreRejected() throws {
        var prefs = WalletPreferences()
        prefs.categories["Cards"] = ["Library", "library"]
        XCTAssertThrowsError(try prefs.validate())
        let card = try WalletCard(name: "Fixture", payload: "")
        XCTAssertThrowsError(try WalletSnapshot(cards: [card, card]).validate())
        XCTAssertFalse(FieldDefinition(name: "Issued", kind: .date).accepts("2025-02-29"))
        XCTAssertTrue(FieldDefinition(name: "Issued", kind: .date).accepts("2024-02-29"))
    }

    @MainActor
    func testDateFormatsAndBillingDayPreserveUserValues() throws {
        let field = FieldDefinition(name: "Renewal", kind: .date, dateFormat: .month)
        XCTAssertTrue(field.accepts("02/29"))
        XCTAssertFalse(field.accepts("13/29"))
        XCTAssertEqual(field.display("2024-02-29", currency: "JPY"), "02/24")
        XCTAssertFalse(FieldDefinition(name: "Issued", kind: .date, dateFormat: .short).accepts("31/02/27"))
        let billing = FieldDefinition(name: "Billing day", kind: .number)
        XCTAssertTrue(billing.accepts("31"))
        XCTAssertFalse(billing.accepts("0"))
        XCTAssertFalse(billing.accepts("1.5"))
        let february = try XCTUnwrap(WalletDateFormat.iso.parse("2024-02-06"))
        XCTAssertEqual(WalletDateFormat.iso.formatter.string(from: MonthSelection.lastDay(of: february)), "2024-02-29")
    }

    func testLayoutKeepsAccessibleSectionsAndValidGridColumns() throws {
        var prefs = WalletPreferences()
        prefs.layout.visible = [.passes]
        XCTAssertThrowsError(try prefs.validate())
        prefs.startSection = .passes
        prefs.layout.sectionColumns = ["Passes": 3]
        XCTAssertNoThrow(try prefs.validate())
        XCTAssertEqual(prefs.layout.columns(for: .passes), 3)
        XCTAssertEqual(prefs.layout.columns(for: .cards), 2)
        prefs.layout.sectionColumns = ["Passes": 0]
        XCTAssertThrowsError(try prefs.validate())
    }

    func testPendingEraseRetriesCredentialCleanupBeforeOpeningAnotherWallet() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let vault = VaultFile(directory: root.appendingPathComponent("Wallet"))
        try vault.write(WalletSnapshot(), key: SymmetricKey(size: .bits256))
        var attempts = 0
        XCTAssertThrowsError(try vault.erase {
            attempts += 1
            throw WalletError.keyUnavailable
        })
        XCTAssertNil(try vault.readCiphertext())
        try vault.finishPendingErase { attempts += 1 }
        try vault.finishPendingErase { XCTFail("Key cleanup should run only while an erase is pending") }
        XCTAssertEqual(attempts, 2)
    }
}
