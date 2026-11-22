import CryptoKit
import LocalAuthentication
import Security

enum VaultKey {
    static func load(context: LAContext, allowCreation: Bool) throws -> SymmetricKey {
        let identity: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: "app.kura.iphone.vault",
            kSecAttrAccount: "cards-v1"
        ]
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
}
