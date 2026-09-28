package app.kura.nativecore

/** Logical privacy scope, independent of the legacy SQLCipher table used to store a record. */
enum class VaultSection(val key: String) {
    CARDS("wallets"), PASSES("passes"), IDENTITY("identities")
}
