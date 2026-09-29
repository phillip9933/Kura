# Kura

**Privacy first pass wallet.**

Kura (蔵) keeps your cards, passes and identity documents in an encrypted, offline Android wallet. No accounts, advertising or telemetry. No Internet permission.

[Download Kura](https://github.com/phillip9933/Kura/releases/latest) · [Changelog](CHANGELOG.md) · [Privacy](PRIVACY.MD)

## Your wallet, organized

- **Cards, Passes and Identity** — three separate, swipeable sections.
- Import `.pkpass` files and `.pkpasses` bundles, scan barcodes or add items manually.
- Add logos, card images, attachments and custom fields.
- Find what you need with categories, favorites, search, sorting and archive.
- Show barcodes full screen when it is time to scan.
- Unlock with biometrics or your device credential, with automatic vault locking.
- Keep password-encrypted backups, optionally saved automatically to a folder you choose.

## Native Android

Kura 2.0 is rebuilt in **Kotlin and Jetpack Compose**, using Material 3. The rewrite removes the Flutter engine and plugin bridges so startup, authentication and camera/file-picker handoffs can be managed directly through Android's lifecycle and security APIs.

Requires Android 7.0 or later, a secure device lock and supported hardware-backed key storage. Download the **APK** to install Kura; the **AAB** is the package for Google Play submission. Store listings are pending.

Kura stores card references and barcodes; it does not make NFC payments. Imported pass issuer signatures are not verified. Sharing files or opening links hands data to another app. Read the [security and privacy details](SECURITY.md).

## Contribute

Start with [Contributing](CONTRIBUTING.md) for setup and checks. The [documentation index](docs/README.md) covers architecture, development and release tooling. Test and profiling helpers live in `tools/`.

Kura originated as a fork of [Wallet by Sidhant](https://github.com/sidhant947/Wallet). Development uses AI assistance, with validation and limitations documented in the repository.

Licensed under [GPL-3.0](LICENSE).
