# Privacy Policy for Kura

**Updated:** September 29, 2026 — native Kura 2.0

Kura is an offline Android wallet for cards, passes and identity documents. It has no account service, advertising, analytics or telemetry, and does not request Android's INTERNET permission.

## Local data and authentication

Records are stored in encrypted SQLCipher databases. New imported media and attachments are encrypted in the app's private storage. The Android operating system handles biometrics and device credentials; Kura does not receive raw biometric data or your device PIN. Android Keystore protects access to the vault key.

Camera capture can temporarily create a plaintext file in private cache while a photo is processed. Kura cleans its capture files on cancellation, locking and subsequent startup; a camera application can retain its own copies.

## Backups and sharing you choose

Kura does not send vault data to a developer-operated server. You can create password-encrypted backups or enable automatic backups to a folder you select. If that folder belongs to a cloud-backed document provider, that provider controls synchronization and its own privacy practices.

Sharing a pass, exporting or opening an attachment, following a web link, or calling a phone number hands information to the selected external app. Exported attachments and ordinary .pkpass files are not password-encrypted. External apps and recipients may retain copies; their privacy practices apply after handoff.

## Deletion and retained copies

Deleting a record removes it from the active wallet, but retained recovery generations, shared media, backups and exported copies may still contain data. Uninstalling removes Android-managed private app data; it does not delete backups or copies stored elsewhere. File deletion is not a guarantee of secure erasure from flash storage. Keep and delete external backups according to your needs.

## Third-party services

Kura includes no advertising or tracking SDKs. Android services and external apps you explicitly use operate under their own policies. The repository hosting and app stores also have their own policies.

## Contact

For general privacy questions, use the [Kura repository](https://github.com/phillip9933/Kura). Do not post personal records, backups or passwords in public issues. See the [security policy](SECURITY.md) for vulnerability reporting and implementation limits.
