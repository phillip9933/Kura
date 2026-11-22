import CryptoKit
import LocalAuthentication
import SwiftUI

private actor VaultStorage {
    private var file: VaultFile {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return VaultFile(directory: support.appendingPathComponent("Wallet", isDirectory: true))
    }

    func open(context: LAContext) throws -> (SymmetricKey, [WalletCard]) {
        try Task.checkCancellation()
        let data = try file.readCiphertext()
        let key = try VaultKey.load(context: context, allowCreation: data == nil)
        try Task.checkCancellation()
        return (key, try data.map { try file.decode($0, key: key) } ?? [])
    }

    func write(_ cards: [WalletCard], key: SymmetricKey) throws {
        try Task.checkCancellation()
        try file.write(cards, key: key)
    }
}

@MainActor
final class WalletSession: ObservableObject {
    enum Phase { case locked, opening, unlocked }
    @Published private(set) var phase = Phase.locked
    @Published private(set) var cards: [WalletCard] = []
    @Published private(set) var saving = false
    @Published private(set) var unlockMessage: String?

    private let storage = VaultStorage()
    private var key: SymmetricKey?
    private var context: LAContext?
    private var opening: Task<Void, Never>?
    private var writing: Task<Void, Error>?
    private var generation = UUID()

    func unlock() {
        guard phase == .locked else { return }
        let auth = LAContext()
        context = auth
        let attempt = generation
        phase = .opening
        unlockMessage = nil
        opening = Task {
            do {
                try Task.checkCancellation()
                guard try await auth.evaluatePolicy(.deviceOwnerAuthentication,
                          localizedReason: "Unlock your Kura wallet.") else { throw WalletError.keyUnavailable }
                let (newKey, records) = try await storage.open(context: auth)
                guard attempt == generation else { return }
                key = newKey
                cards = records
                phase = .unlocked
            } catch {
                guard attempt == generation else { return }
                lock()
                if let authError = error as? LAError {
                    if authError.code == .passcodeNotSet {
                        unlockMessage = "Set a device passcode in Settings to use Kura."
                    } else if ![.userCancel, .systemCancel, .appCancel].contains(authError.code) {
                        unlockMessage = "Authentication failed. Try unlocking again."
                    }
                } else {
                    unlockMessage = (error as? WalletError ?? .vaultUnavailable).localizedDescription
                }
            }
        }
    }

    func lock() {
        generation = UUID()
        opening?.cancel()
        opening = nil
        writing?.cancel()
        writing = nil
        context?.invalidate()
        context = nil
        key = nil
        cards.removeAll()
        saving = false
        unlockMessage = nil
        phase = .locked
    }

    func save(_ card: WalletCard) async throws {
        guard phase == .unlocked, let key, !saving else { throw CancellationError() }
        let attempt = generation
        var updated = cards
        if let index = updated.firstIndex(where: { $0.id == card.id }) {
            updated[index] = card
        } else {
            updated.append(card)
        }
        saving = true
        let task = Task { try await storage.write(updated, key: key) }
        writing = task
        defer {
            if generation == attempt {
                saving = false
                writing = nil
            }
        }
        try await task.value
        guard generation == attempt else { throw CancellationError() }
        cards = updated
    }
}
