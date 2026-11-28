import CryptoKit
import LocalAuthentication
import Security

enum PhoneUnlock {
    // The flag controls the UI. Only a successful protected Keychain read unlocks the vault.
    private static let flag = "phone-unlock-enabled"
    static var enabled: Bool { UserDefaults.standard.bool(forKey: flag) }
    private static let account = "phone-unlock-v1"
    private static var query: [CFString: Any] { VaultKey.identity(for: account) }

    static func read() throws -> SymmetricKey {
        var request = query
        request[kSecReturnData] = true
        request[kSecUseAuthenticationUI] = kSecUseAuthenticationUIFail
        var result: CFTypeRef?
        guard SecItemCopyMatching(request as CFDictionary, &result) == errSecSuccess,
              let bytes = result as? Data, bytes.count == 32 else { throw WalletError.keyUnavailable }
        return SymmetricKey(data: bytes)
    }

    static func setEnabled(_ value: Bool, verifiedKey: SymmetricKey) throws {
        if value {
            var request = query
            request[kSecAttrAccessible] = kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
            request[kSecValueData] = verifiedKey.withUnsafeBytes { Data($0) }
            let status = SecItemAdd(request as CFDictionary, nil)
            if status == errSecDuplicateItem {
                let attributes: [CFString: Any] = [kSecValueData: verifiedKey.withUnsafeBytes { Data($0) }]
                guard SecItemUpdate(query as CFDictionary, attributes as CFDictionary) == errSecSuccess else {
                    throw WalletError.keyUnavailable
                }
            } else if status != errSecSuccess { throw WalletError.keyUnavailable }
            do {
                guard try read() == verifiedKey else { throw WalletError.keyUnavailable }
            } catch {
                if status == errSecSuccess { try remove() }
                throw error
            }
        } else {
            try remove()
            return
        }
        UserDefaults.standard.set(true, forKey: flag)
    }

    static func remove(context: LAContext? = nil) throws {
        try VaultKey.remove(account: account, context: context)
        UserDefaults.standard.removeObject(forKey: flag)
    }
}

enum PasscodeUnlock {
    private static let flag = "passcode-unlock-only"
    static var enabled: Bool { UserDefaults.standard.bool(forKey: flag) }
    private static let account = "passcode-unlock-v1"
    private static var identity: [CFString: Any] { VaultKey.identity(for: account) }

    static func read(context: LAContext) throws -> SymmetricKey {
        var query = identity
        query[kSecReturnData] = true
        query[kSecUseAuthenticationContext] = context
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let bytes = result as? Data, bytes.count == 32 else { throw WalletError.keyUnavailable }
        return SymmetricKey(data: bytes)
    }

    static func configure(_ enabled: Bool, verifiedKey: SymmetricKey, context: LAContext = LAContext()) throws {
        if enabled {
            guard let control = SecAccessControlCreateWithFlags(nil, kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly, .devicePasscode, nil)
            else { throw WalletError.keyUnavailable }
            var query = identity
            query[kSecAttrAccessControl] = control
            query[kSecValueData] = verifiedKey.withUnsafeBytes { Data($0) }
            let status = SecItemAdd(query as CFDictionary, nil)
            if status == errSecDuplicateItem {
                var match = identity
                match[kSecUseAuthenticationContext] = context
                let attributes: [CFString: Any] = [kSecValueData: verifiedKey.withUnsafeBytes { Data($0) }]
                guard SecItemUpdate(match as CFDictionary, attributes as CFDictionary) == errSecSuccess else {
                    throw WalletError.keyUnavailable
                }
            } else if status != errSecSuccess { throw WalletError.keyUnavailable }
            // Verify the device passcode can actually open this key before selecting this unlock mode.
            do {
                guard try read(context: context) == verifiedKey else { throw WalletError.keyUnavailable }
            } catch {
                if status == errSecSuccess { try remove(context: context) }
                throw error
            }
        } else {
            try remove(context: context)
            return
        }
        UserDefaults.standard.set(true, forKey: flag)
    }

    static func remove(context: LAContext? = nil) throws {
        try VaultKey.remove(account: account, context: context)
        UserDefaults.standard.removeObject(forKey: flag)
    }
}
