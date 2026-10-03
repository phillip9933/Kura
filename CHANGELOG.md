# Changelog

## 2.1.0 — October 3, 2026

### Faster access to your wallet

- Reduced the delay after authentication by removing duplicate database opens and opening the three encrypted databases concurrently.
- Prepared database engine code while authentication is displayed and refreshed Android's compilation profile for startup, unlocking and common interactions.
- Improved backup folder labels to show the folder's display name while retaining its existing access permission.

Encryption, authentication requirements, database formats and backups are unchanged. Actual speed improvements depend on the device; further optimization work is ongoing.

## 2.0.0 — September 29, 2026

### Built for Android, from the ground up

Kura 2.0 moves from Flutter to **Kotlin and Jetpack Compose**. The goal is a wallet that starts promptly, unlocks reliably and behaves predictably when you switch apps, scan a barcode or choose a file.

- **A leaner startup path.** Removing the Flutter engine avoids its warm-up overhead and gives Kura direct control over initialization.
- **More reliable unlocking and resume.** Authentication and vault lifetime now follow an explicit native session model, designed to prevent overlapping unlocks and lifecycle races.
- **Smoother camera and file-picker handoffs.** External operations are tracked so a temporary switch to another activity does not prematurely close the vault during an import.
- **Direct Android security integration.** Keystore-backed cryptography and Android biometric prompts replace plugin bridges, with clearer ownership and cleanup of sensitive session data.
- **An Android-native interface.** Jetpack Compose and Material 3 provide the foundation for Cards, Passes and Identity, with consistent editing, navigation and barcode views.

Kura remains offline, private and account-free. This release changes the foundation so future improvements can build on Android's own tools and lifecycle.
