import CryptoKit
import LocalAuthentication
import SwiftUI

private actor VaultStorage {
    private var file: VaultFile {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return VaultFile(directory: support.appendingPathComponent("Wallet", isDirectory: true))
    }

    func open(context: LAContext, phoneOnly: Bool, passcodeOnly: Bool) throws -> (SymmetricKey, WalletSnapshot?) {
        try Task.checkCancellation()
        try file.finishPendingErase { try VaultKey.eraseAll(context: context) }
        let read = Result { try file.readCiphertext() }
        let isNew: Bool
        if case .success(nil) = read { isNew = try !file.containsPriorState() } else { isNew = false }
        let key: SymmetricKey
        if phoneOnly { key = try PhoneUnlock.read() }
        else if passcodeOnly {
            _ = try PasscodeUnlock.read(context: context)
            key = try VaultKey.load(context: context, allowCreation: false)
        }
        else { key = try VaultKey.load(context: context, allowCreation: isNew) }
        try Task.checkCancellation()
        do {
            guard let data = try read.get() else {
                if !isNew {
                    let generations = try BackupRecovery.entries(in: file.directory)
                    if !generations.isEmpty { throw WalletError.vaultUnavailable }
                }
                let empty = WalletSnapshot()
                try file.write(empty, key: key)
                return (key, empty)
            }
            return (key, try file.decodeSnapshot(data, key: key))
        } catch {
            guard !phoneOnly else { throw WalletError.keyUnavailable }
            return (key, nil)
        }
    }

    func write(_ snapshot: WalletSnapshot, key: SymmetricKey, retainPrevious: Bool, damaged: Bool) throws {
        try Task.checkCancellation()
        try snapshot.validate()
        if damaged { try file.recordDamagedBeforeRestore() }
        else if retainPrevious { try file.recordRecoveryBeforeRestore(key: key) }
        try Task.checkCancellation()
        try file.write(snapshot, key: key)
    }

    func erase(context: LAContext) throws {
        try Task.checkCancellation()
        try file.erase { try VaultKey.eraseAll(context: context) }
    }

    func recovery(_ id: UUID, key: SymmetricKey) throws -> WalletSnapshot {
        try Task.checkCancellation()
        return try file.readRecovery(id: id, key: key)
    }
}

@MainActor
final class WalletSession: ObservableObject {
    enum Phase { case locked, opening, unlocked, recoveryRequired }
    @Published private(set) var phase = Phase.locked
    @Published private(set) var snapshot = WalletSnapshot()
    @Published private(set) var saving = false
    @Published private(set) var unlockMessage: String?
    var cards: [WalletCard] { snapshot.cards }
    private(set) var generation = UUID()
    private let storage = VaultStorage()
    private var key: SymmetricKey?
    private var context: LAContext?
    private var opening: Task<Void, Never>?
    private var writing: Task<Void, Error>?
    private var backgroundSince: TimeInterval?
    private var backgroundLock: Task<Void, Never>?

    func unlock(requirePrompt: Bool = false) {
        guard phase == .locked else { return }
        let phoneOnly = PhoneUnlock.enabled && !requirePrompt
        let passcodeOnly = PasscodeUnlock.enabled && !phoneOnly
        let auth = LAContext()
        context = auth
        let attempt = generation
        phase = .opening
        unlockMessage = nil
        opening = Task {
            do {
                try Task.checkCancellation()
                if !phoneOnly && !passcodeOnly {
                    guard try await auth.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Unlock your Kura wallet.")
                    else { throw WalletError.keyUnavailable }
                }
                let (newKey, records) = try await storage.open(context: auth, phoneOnly: phoneOnly, passcodeOnly: passcodeOnly)
                guard attempt == generation else { return }
                key = newKey
                if let records {
                    snapshot = records
                    phase = .unlocked
                    BackupAutomatic.schedule(snapshot: records, key: newKey)
                } else {
                    phase = .recoveryRequired
                }
            } catch {
                guard attempt == generation else { return }
                lock()
                if let authError = error as? LAError {
                    if authError.code == .passcodeNotSet { unlockMessage = "Set a device passcode in Settings to use Kura." }
                    else if ![.userCancel, .systemCancel, .appCancel].contains(authError.code) {
                        unlockMessage = "Authentication failed. Try unlocking again."
                    }
                } else { unlockMessage = (error as? WalletError ?? .vaultUnavailable).localizedDescription }
            }
        }
    }

    func changePhoneUnlock(_ enabled: Bool, generation token: UUID) async throws {
        guard generation == token, phase == .unlocked, let key else { throw CancellationError() }
        let auth = LAContext()
        defer { auth.invalidate() }
        guard try await auth.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Change how Kura unlocks.") else { throw WalletError.keyUnavailable }
        guard generation == token, phase == .unlocked else { throw CancellationError() }
        let original = try VaultKey.load(context: auth, allowCreation: false)
        guard original == key else { throw WalletError.keyUnavailable }
        try PhoneUnlock.setEnabled(enabled, verifiedKey: original)
        objectWillChange.send()
    }

    func changePasscodeOnly(_ enabled: Bool, generation token: UUID) async throws {
        guard generation == token, phase == .unlocked, let key else { throw CancellationError() }
        let auth = LAContext()
        defer { auth.invalidate() }
        guard try await auth.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Change how Kura authenticates.") else {
            throw WalletError.keyUnavailable
        }
        guard generation == token, phase == .unlocked, try VaultKey.load(context: auth, allowCreation: false) == key else {
            throw WalletError.keyUnavailable
        }
        try PasscodeUnlock.configure(enabled, verifiedKey: key, context: auth)
        objectWillChange.send()
    }

    func background() {
        guard phase == .unlocked, snapshot.preferences.lockDelay > 0 else { lock(); return }
        backgroundSince = ProcessInfo.processInfo.systemUptime
        backgroundLock?.cancel()
        let delay = UInt64(snapshot.preferences.lockDelay)
        backgroundLock = Task {
            try? await Task.sleep(nanoseconds: delay * 1_000_000_000)
            if !Task.isCancelled { lock() }
        }
    }

    func foreground() {
        if let start = backgroundSince, (ProcessInfo.processInfo.systemUptime - start) >= Double(snapshot.preferences.lockDelay) { lock() }
        backgroundSince = nil
        backgroundLock?.cancel()
        backgroundLock = nil
    }

    func lock() {
        generation = UUID()
        backgroundLock?.cancel()
        backgroundLock = nil
        backgroundSince = nil
        opening?.cancel()
        opening = nil
        writing?.cancel()
        writing = nil
        context?.invalidate()
        context = nil
        BackupAutomatic.cancel()
        key = nil
        snapshot = WalletSnapshot()
        saving = false
        unlockMessage = nil
        phase = .locked
    }

    func save(_ card: WalletCard, generation token: UUID, replacing original: WalletCard?) async throws {
        var next = snapshot
        if let index = cards.firstIndex(where: { $0.id == card.id }) {
            guard original == cards[index] else { throw WalletError.recordChanged }
            next.cards[index] = card
        } else {
            guard original == nil else { throw WalletError.recordChanged }
            next.cards.append(card)
        }
        try await commit(next, generation: token)
    }

    func delete(_ card: WalletCard, generation token: UUID) async throws {
        guard let index = cards.firstIndex(of: card) else { throw WalletError.recordChanged }
        var next = snapshot
        next.cards.remove(at: index)
        try await commit(next, generation: token)
    }

    func deleteArchived(_ selected: [WalletCard], generation token: UUID) async throws {
        guard selected == cards.filter(\.archived) else { throw WalletError.recordChanged }
        var next = snapshot
        next.cards.removeAll(where: \.archived)
        try await commit(next, generation: token)
    }

    func importCards(_ items: [WalletCard], generation token: UUID) async throws {
        var next = snapshot
        next.cards.append(contentsOf: items)
        try await commit(next, generation: token)
    }

    func preferences(_ value: WalletPreferences, generation token: UUID, renames: [CategoryChange] = [],
                     replacing original: WalletPreferences? = nil) async throws {
        if let original, !snapshot.preferences.hasSameSettings(as: original) { throw WalletError.recordChanged }
        var next = snapshot
        next.preferences = value
        for change in renames {
            for index in next.cards.indices where next.cards[index].section == change.section && next.cards[index].category == change.old {
                next.cards[index].category = change.new
            }
        }
        try await commit(next, generation: token)
    }

    func reorder(_ ids: [UUID], section: WalletSection, generation token: UUID) async throws {
        let set = Set(ids)
        let indexed = Dictionary(uniqueKeysWithValues: cards.map { ($0.id, $0) })
        guard set.count == ids.count, set.allSatisfy({ indexed[$0] != nil }) else { throw WalletError.recordChanged }
        var ordered = ids.compactMap { indexed[$0] }.makeIterator()
        var next = snapshot
        next.cards = cards.map { set.contains($0.id) ? (ordered.next() ?? $0) : $0 }
        next.preferences.sorting[section.rawValue] = .custom
        try await commit(next, generation: token)
    }

    func replaceForRestore(_ restored: WalletSnapshot, generation token: UUID) async throws {
        try await commit(restored, generation: token, retainPrevious: true)
        // Discard editors and import previews that belonged to the replaced wallet.
        generation = UUID()
        objectWillChange.send()
    }

    func recoverySnapshot(_ id: UUID, generation token: UUID) async throws -> WalletSnapshot {
        guard (phase == .unlocked || phase == .recoveryRequired), generation == token, let key else { throw CancellationError() }
        let result = try await storage.recovery(id, key: key)
        guard generation == token else { throw CancellationError() }
        return result
    }

    func eraseWallet(generation token: UUID) async throws {
        guard generation == token, phase == .unlocked, !saving, let key else { throw CancellationError() }
        let auth = LAContext()
        defer { auth.invalidate() }
        guard try await auth.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Erase your local Kura wallet.") else { throw WalletError.keyUnavailable }
        guard generation == token, phase == .unlocked, !saving else { throw CancellationError() }
        let verified = try VaultKey.load(context: auth, allowCreation: false)
        guard verified == key else { throw WalletError.keyUnavailable }
        saving = true
        defer { if generation == token { lock() } }
        BackupAutomatic.cancel()
        let job = Task { try await storage.erase(context: auth) }
        writing = job
        try await job.value
    }

    func automaticBookmark(generation token: UUID) throws -> Data? {
        try Task.checkCancellation()
        guard generation == token, phase == .unlocked, let key else { throw CancellationError() }
        return try BackupAutomatic.destination(key: key)?.bookmark
    }

    func automaticRetention() throws -> Int {
        guard phase == .unlocked, let key else { throw CancellationError() }
        return try BackupAutomatic.retention(key: key)
    }

    func updateAutomatic(password: String?, retention: Int) throws {
        guard phase == .unlocked, let key else { throw CancellationError() }
        try BackupAutomatic.update(password: password, retention: retention, key: key)
        BackupAutomatic.schedule(snapshot: snapshot, key: key)
    }

    func configureAutomatic(folder: PreparedBackupFolder, password: String, retention: Int, generation token: UUID) throws {
        try Task.checkCancellation()
        guard generation == token, phase == .unlocked, let key else { throw CancellationError() }
        try BackupAutomatic.configure(folder: folder, password: password, retention: retention, key: key)
        BackupAutomatic.schedule(snapshot: snapshot, key: key)
    }

    func disableAutomatic() throws {
        guard phase == .unlocked else { throw CancellationError() }
        try BackupAutomatic.disable()
    }

    private func commit(_ next: WalletSnapshot, generation token: UUID, retainPrevious: Bool = false) async throws {
        let rescuing = phase == .recoveryRequired && retainPrevious
        guard (phase == .unlocked || rescuing), let key, token == generation, !saving else { throw CancellationError() }
        saving = true
        let task = Task { try await storage.write(next, key: key, retainPrevious: retainPrevious, damaged: rescuing) }
        writing = task
        defer { if generation == token { saving = false; writing = nil } }
        try await task.value
        guard generation == token else { throw CancellationError() }
        snapshot = next
        phase = .unlocked
        BackupAutomatic.schedule(snapshot: next, key: key)
    }
}
