import CryptoKit
import LocalAuthentication
import Security

enum VaultKey {
    private static let account = "cards-v1"

    static func identity(for account: String) -> [CFString: Any] {
        [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: "app.kura.iphone.vault",
            kSecAttrAccount: account
        ]
    }

    static func load(context: LAContext, allowCreation: Bool) throws -> SymmetricKey {
        let identity = Self.identity(for: account)
        var query = identity
        query[kSecReturnData] = true
        query[kSecUseAuthenticationContext] = context
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecSuccess, let bytes = result as? Data, bytes.count == 32 {
            return SymmetricKey(data: bytes)
        }
        guard status == errSecItemNotFound, allowCreation,
              let access = SecAccessControlCreateWithFlags(nil,
                  kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly, .userPresence, nil)
        else { throw WalletError.keyUnavailable }

        let key = SymmetricKey(size: .bits256)
        var item = identity
        item[kSecAttrAccessControl] = access
        item[kSecValueData] = key.withUnsafeBytes { Data($0) }
        let added = SecItemAdd(item as CFDictionary, nil)
        guard added == errSecSuccess || added == errSecDuplicateItem else { throw WalletError.keyUnavailable }
        // Read through the access control on first use as well as on later unlocks.
        return try load(context: context, allowCreation: false)
    }

    // The caller retains the on-disk pending-erase marker until every key has been removed.
    static func eraseAll(context: LAContext? = nil) throws {
        try PhoneUnlock.remove(context: context)
        try PasscodeUnlock.remove(context: context)
        try remove(account: account, context: context)
    }

    static func remove(account: String, context: LAContext?) throws {
        var query = identity(for: account)
        if let context { query[kSecUseAuthenticationContext] = context }
        else { query[kSecUseAuthenticationUI] = kSecUseAuthenticationUIFail }
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw WalletError.keyUnavailable }
    }
}
