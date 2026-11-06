# Changelog

## Unreleased

- Add retake and advisory photo-quality feedback; preserve front/back destinations and user corrections during OCR review.
- Reject expired camera/picker/permission results and clean interrupted scanner work without publishing a draft.
- Let gestures outside crop handles scroll the dialog so the full preview remains reachable.

- Honor the saved launch section before displaying the unlocked wallet, including cold starts and phone-only unlock.

- Make App updates a prominent settings row and expose update controls from the app-version dialog.

- Rotate the photo before cropping, with live preset/custom-angle previews and edge detection on the oriented image.

- Combine automatic edge detection and manual corner editing in one crop dialog, with a shaded mask, expandable fine-tuning and consistent primary/secondary actions.

## 2.2.0-beta.1 — November 5, 2026

- Automatic card-edge detection and editable perspective crop in the enhanced-scanning build.
- Optional offline English/Japanese OCR with editable suggestions for existing empty card fields. Confirm the form before saving; leading zeros and original recognized characters are retained.
- Updater 0.2.1 cancels obsolete queued downloads after host upgrades.
- GitHub update checks and a foreground new-version popup, with opt-in automatic checks/downloads and an explicit beta-release option.
- Optional phone-unlock-only access while retaining encrypted wallet storage and the original authenticated wrapper; wait for phone unlock when waking instead of requesting a second app PIN.
- Keep card action menus fully expanded so Edit/Delete are visible.
- Updated CameraX to 1.5.3 to handle newer Android camera dynamic-range profiles.
- Removed the donation row/link from settings and its unused icon profile entries.
- Pinned SDK/model integrity checks, line-ending protection and a four-distribution verification matrix.

The GitHub beta combines enhanced scanning and direct updates (Android API 26+, arm64-v8a/x86_64). The source default retains offline/API 24 support. Scanner 0.8.0-beta.1 remains subject to photographed-card accuracy limits; verify OCR suggestions before saving. Physical-device/full release acceptance remains open. AI assistance and exact validation are recorded in docs/TESTING.md.

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
