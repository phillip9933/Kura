# Kura

**Kura** (蔵) is a completely offline, privacy-first digital wallet for your payment cards, passes, and IDs. Named after the traditional Japanese secure storehouse, it's built to keep your data organized and strictly on your device. No accounts, no ads, no analytics, and no cloud dependency.

Kura is a hard fork of [Wallet by Sidhant](https://github.com/sidhant947/Wallet). Huge thanks to Sidhant for building an awesome foundation.

I spun this off to take the app in a more customizable direction and implement major architectural overhauls for security. The main differences are:
- Dynamic custom fields so you choose what data matters for your cards.
- A more customizable grid-based UI for browsing.
- Split architecture for Payments, Passes, and Identity cards.
- Additional Security Hardening against malicious imports and data exfiltration.

Kura is its own independent project now, but it still heavily respects the local-only DNA of the original.

## Security & Hardening

Kura is built with a highly defensive threat model. Every security and privacy claim made about the app's data handling is auditable in the codebase.

### Trust claim audit map

| Kura claim | Where to look |
| :--- | :--- |
| **Pass/Card storage is fully encrypted at rest** | `lib/models/db_helper.dart` — SQLCipher `openDatabase` integration across all three databases. |
| **Encryption keys are hardware-backed** | `lib/services/encryption_service.dart` — `flutter_secure_storage` Keystore implementation. |
| **PKPASS parser is hardened against zip bombs and malicious inputs** | `lib/services/pkpass_service.dart` — Explicit archive size limits, nested depth bounds, and maximum file-count checks before extraction. |
| **Pass data is excluded from Android Auto Backup** | `android/app/src/main/AndroidManifest.xml` — `android:allowBackup="false"` prevents silent Google Drive syncing. |
| **Card deletion purges images from disk and RAM** | `lib/models/db_helper.dart` — `deleteImageFile()` triggers explicit file deletion and calls `PaintingBinding.instance.imageCache.evict()`. |
| **App data never appears in system logs in release mode** | `lib/services/*.dart` — All error handling, stack traces, and debug prints are wrapped in strict `kDebugMode` guards. |
| **100% offline; no internet access** | `android/app/src/main/AndroidManifest.xml` — Total absence of `android.permission.INTERNET`. |

## Permissions

**Why it asks for permissions:**
- **Camera:** For taking pictures of the front and back of your cards.
- **Modify System Settings (WRITE_SETTINGS):** This is purely so the app can temporarily max out your screen brightness when you pull up a fullscreen barcode to scan at a register. It drops right back to your system default when you close it. If you don't toggle this feature on in settings, you don't need to grant the permission.

## Features

- Store payment card information, passes, and identity cards. (NOT capable of making actual NFC payments)
- Build dynamic forms with user-configurable custom field schemas
- Customize pass categories and grid layouts
- Crop and align card front/back images internally
- Display fullscreen barcodes with optional maximum brightness control
- Secure clipboard that auto-clears sensitive copied values
- Export and import fully encrypted `.wbk` backups
- Protect the vault with local biometric/PIN authentication
- 100% offline by design

## How it's built

Kura is developed using AI-assisted workflows. AI assistance does not replace engineering judgment: changes are human-reviewed and understood to hold the project’s local-first privacy model, existing architecture, and device-level behavior.

## License

Kura is licensed under the [GNU General Public License v3.0](LICENSE).