# Changelog

## Unreleased

- Pin the published Android App Updater 0.2.0 prerelease and show a dismissible new-version popup in the optional direct-update build.
- Add Require app unlock in Data & Security. Users may opt into relying on the phone lock while retaining encrypted wallet storage and the original authenticated recovery wrapper.
- AI assistance was used for implementation, tests and documentation; validation is recorded in docs/TESTING.md. No app release or signing is part of this work.

- Keep manual update details and automatic offers in one panel to prevent overlapping dialogs.
- Verify offline and direct-update distributions independently in CI.

- Optional direct-update build with opt-in GitHub release checks, verified APK downloads and user-confirmed Android installation. The default offline build is retained.
- Corrected GPL labels in project documentation to match the existing AGPL-3.0 license.

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
