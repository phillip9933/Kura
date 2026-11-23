import CryptoKit
import LocalAuthentication
import Security
import XCTest
import UIKit
@testable import Kura

final class SessionTests: XCTestCase {
    @MainActor
    func testPrivacyCoverFollowsPhaseAndDetachesFromExistingWindow() throws {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 320, height: 480))
        let anchor = PrivacyShield.ShieldAnchor(frame: .zero)
        window.addSubview(anchor)
        let count = window.subviews.count
        anchor.update(from: .inactive)
        let cover = try XCTUnwrap(window.subviews.first { $0.subviews.contains { $0.accessibilityIdentifier == "privacy.inactive" } })
        XCTAssertEqual(cover.frame, window.bounds)
        anchor.update(from: .active)
        XCTAssertNil(cover.superview)
        anchor.update(from: .background)
        XCTAssertTrue(cover.superview === window)
        anchor.removeFromSuperview()
        XCTAssertNil(cover.superview)
        XCTAssertLessThanOrEqual(window.subviews.count, count)
    }

    @MainActor
    func testLockInvalidatesPendingUnlockAndRejectsWritesFromThatGeneration() async throws {
        let wallet = WalletSession()
        let original = wallet.generation
        wallet.unlock(requirePrompt: true)
        XCTAssertEqual(wallet.phase, .opening)
        // Both calls are on the main actor. The auth task cannot display a prompt before cancellation.
        wallet.lock()
        XCTAssertNotEqual(wallet.generation, original)
        XCTAssertEqual(wallet.phase, .locked)
        let item = try WalletCard(name: "Synthetic card", payload: "0001")
        await expectCancellation { try await wallet.save(item, generation: original, replacing: nil) }
        await expectCancellation { try await wallet.importCards([item], generation: original) }
        await expectCancellation { try await wallet.replaceForRestore(WalletSnapshot(cards: [item]), generation: original) }
        await expectCancellation { _ = try await wallet.recoverySnapshot(UUID(), generation: original) }
        await Task.yield()
        XCTAssertTrue(wallet.cards.isEmpty)
        XCTAssertEqual(wallet.phase, .locked)
        XCTAssertFalse(wallet.saving)
        XCTAssertNil(wallet.unlockMessage)
    }

    @MainActor
    func testLockedSessionRejectsCurrentGenerationMutationAndKeyConfiguration() async throws {
        let wallet = WalletSession()
        let token = wallet.generation
        let card = try WalletCard(name: "Synthetic pass", payload: "0002")
        await expectCancellation { try await wallet.importCards([card], generation: token) }
        await expectCancellation { try await wallet.preferences(WalletPreferences(), generation: token) }
        XCTAssertThrowsError(try wallet.configureAutomatic(folder: FileManager.default.temporaryDirectory,
                                                            password: "synthetic-password", retention: 1))
        XCTAssertThrowsError(try wallet.disableAutomatic())
        XCTAssertEqual(wallet.snapshot, WalletSnapshot())
    }

    @MainActor
    private func expectCancellation(_ action: () async throws -> Void,
                                    file: StaticString = #filePath, line: UInt = #line) async {
        do {
            try await action()
            XCTFail("Locked operation unexpectedly succeeded", file: file, line: line)
        } catch is CancellationError {
            return
        } catch {
            XCTFail("Expected cancellation from the session boundary", file: file, line: line)
        }
    }

    func testKeychainReadWithInvalidatedContextCannotProvisionOrUnlock() {
        let context = LAContext()
        context.interactionNotAllowed = true
        context.invalidate()
        XCTAssertThrowsError(try VaultKey.load(context: context, allowCreation: false))
    }

    func testKeychainDeletionIsExactAndSafeToRetry() throws {
        let first = "unit-" + UUID().uuidString
        let second = "unit-" + UUID().uuidString
        defer {
            try? VaultKey.remove(account: first, context: nil)
            try? VaultKey.remove(account: second, context: nil)
        }
        for account in [first, second] {
            var item = VaultKey.identity(for: account)
            item[kSecAttrAccessible] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            item[kSecValueData] = Data("temporary synthetic fixture".utf8)
            XCTAssertEqual(SecItemAdd(item as CFDictionary, nil), errSecSuccess)
        }
        try VaultKey.remove(account: first, context: nil)
        try VaultKey.remove(account: first, context: nil)
        XCTAssertEqual(SecItemCopyMatching(VaultKey.identity(for: first) as CFDictionary, nil), errSecItemNotFound)
        XCTAssertEqual(SecItemCopyMatching(VaultKey.identity(for: second) as CFDictionary, nil), errSecSuccess)
    }

    func testInvalidCandidateAndDamagedActiveVaultCannotEraseLastGoodCopy() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = VaultFile(directory: directory)
        let key = SymmetricKey(size: .bits256)
        let original = WalletSnapshot(cards: [try WalletCard(name: "Synthetic card", payload: "0001")])
        try file.write(original, key: key)
        let initial = try XCTUnwrap(file.readCiphertext())
        var invalid = original
        invalid.cards.append(original.cards[0])
        XCTAssertThrowsError(try file.write(invalid, key: key))
        XCTAssertEqual(try file.readCiphertext(), initial)
        XCTAssertThrowsError(try file.recordRecoveryBeforeRestore(key: SymmetricKey(size: .bits256)))
        XCTAssertTrue(try BackupRecovery.entries(in: directory).isEmpty)

        try file.recordRecoveryBeforeRestore(key: key)
        let id = try XCTUnwrap(try BackupRecovery.entries(in: directory).first?.id)
        var damaged = initial
        damaged[damaged.count - 1] ^= 1
        try damaged.write(to: directory.appendingPathComponent("cards.kura"), options: [.atomic, .completeFileProtection])
        XCTAssertThrowsError(try file.recordRecoveryBeforeRestore(key: key))
        XCTAssertEqual(try BackupRecovery.entries(in: directory).map(\.id), [id])
        XCTAssertEqual(try file.readRecovery(id: id, key: key), original)
    }

    func testRestoreDamagedActiveFileAfterVerifiedPreservation() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = VaultFile(directory: directory)
        let key = SymmetricKey(size: .bits256)
        let original = WalletSnapshot(cards: [try WalletCard(name: "Test original", payload: "01")])
        try file.write(original, key: key)
        let password = "test backup password"
        let backup = try BackupCodec.encode(original, password: password)
        var damaged = try XCTUnwrap(file.readCiphertext())
        damaged[damaged.count - 1] ^= 1
        try damaged.write(to: file.ciphertextURL, options: [.atomic, .completeFileProtection])
        // A failed preservation cannot touch the active file.
        let blocked = directory.appendingPathComponent("Damaged")
        try Data("test obstructing file".utf8).write(to: blocked)
        XCTAssertThrowsError(try file.recordDamagedBeforeRestore())
        XCTAssertEqual(try file.readCiphertext(), damaged)
        try FileManager.default.removeItem(at: blocked)

        let preview = try BackupCodec.decode(backup, password: password)
        try file.recordDamagedBeforeRestore()
        try file.write(preview, key: key)
        let savedFiles = try FileManager.default.contentsOfDirectory(at: blocked, includingPropertiesForKeys: nil)
        XCTAssertEqual(savedFiles.count, 1)
        XCTAssertEqual(try Data(contentsOf: XCTUnwrap(savedFiles.first)), damaged)
        XCTAssertEqual(try file.decodeSnapshot(XCTUnwrap(file.readCiphertext()), key: key), original)
        XCTAssertTrue(try BackupRecovery.entries(in: directory).isEmpty)
    }
}
