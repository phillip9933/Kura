import CryptoKit
import LocalAuthentication
import Security

enum PhoneUnlock {
    // The flag controls the UI. Only a successful protected Keychain read unlocks the vault.
    private static let flag = "phone-unlock-enabled"
    static var enabled: Bool { UserDefaults.standard.bool(forKey: flag) }
    private static var query: [CFString: Any] {
        [kSecClass: kSecClassGenericPassword, kSecAttrService: "app.kura.iphone.vault", kSecAttrAccount: "phone-unlock-v1"]
    }

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
            guard status == errSecSuccess || status == errSecDuplicateItem else { throw WalletError.keyUnavailable }
            guard try read() == verifiedKey else { throw WalletError.keyUnavailable }
        } else {
            let status = SecItemDelete(query as CFDictionary)
            guard status == errSecSuccess || status == errSecItemNotFound else { throw WalletError.keyUnavailable }
        }
        UserDefaults.standard.set(value, forKey: flag)
    }
}

enum PasscodeUnlock {
    static var enabled: Bool { UserDefaults.standard.bool(forKey: "passcode-unlock-only") }
    private static var identity: [CFString: Any] {
        [kSecClass: kSecClassGenericPassword, kSecAttrService: "app.kura.iphone.vault", kSecAttrAccount: "passcode-unlock-v1"]
    }

    static func read(context: LAContext) throws -> SymmetricKey {
        var query = identity
        query[kSecReturnData] = true
        query[kSecUseAuthenticationContext] = context
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let bytes = result as? Data, bytes.count == 32 else { throw WalletError.keyUnavailable }
        return SymmetricKey(data: bytes)
    }

    static func configure(_ enabled: Bool, verifiedKey: SymmetricKey) throws {
        if enabled {
            guard let control = SecAccessControlCreateWithFlags(nil, kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly, .devicePasscode, nil)
            else { throw WalletError.keyUnavailable }
            var query = identity
            query[kSecAttrAccessControl] = control
            query[kSecValueData] = verifiedKey.withUnsafeBytes { Data($0) }
            let status = SecItemAdd(query as CFDictionary, nil)
            guard status == errSecSuccess || status == errSecDuplicateItem else { throw WalletError.keyUnavailable }
        } else {
            let status = SecItemDelete(identity as CFDictionary)
            guard status == errSecSuccess || status == errSecItemNotFound else { throw WalletError.keyUnavailable }
        }
        UserDefaults.standard.set(enabled, forKey: "passcode-unlock-only")
    }
}
