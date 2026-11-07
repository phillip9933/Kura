# Changelog

## 2.2.0 — November 7, 2026

### Breaking changes
- The GitHub APK now requires Android 8.0 (API 26) or later and an arm64-v8a or x86_64 device.

### Added
- Automatic card-edge detection with an editable crop mask, rotation preview and photo-quality guidance.
- Optional offline English/Japanese OCR with editable suggestions for existing empty fields. Review the suggestions and save the card to apply them.
- GitHub update checks, a foreground new-version notification and optional automatic checks/downloads.
- A Require app unlock setting for relying on the phone's screen lock while keeping wallet storage encrypted.

### Changed
- Keep automatic and manual cropping in one screen; default to horizontal credit-card proportions.
- Use scanner 0.8.0 and updater 0.2.2.

### Removed
- Donation link and settings row.
- Android App Bundle distribution; GitHub releases provide an APK.

### Fixed
- Keep the add-photo action available for each missing card side in card details.
- Honor the saved startup section and improve phone-only wake behavior.
- Keep update controls off the loading screen and make manual update checks easy to find in Settings.
- Recover from corrupted cached update downloads and reject stale camera/picker results.
- Keep crop controls reachable on small screens and with large text.
- Report failed atomic file commits instead of acknowledging uncommitted writes.

### Build information
- Use Kotlin JVM/Compose 2.4.21 with the existing AGP, Gradle and KSP toolchain.
- Version 2.2.0, Android version code 123; enhanced scanning and direct GitHub updates enabled.
- The APK was built by [tagged CI](https://github.com/phillip9933/Kura/actions/runs/42343310292) from [10f17a9](https://github.com/phillip9933/Kura/commit/10f17a93c99b67cbe63b17417111b0e8a7ab34f1), then signed without rebuilding using the existing GitHub release identity.
- See `SHA256SUMS.txt` for download integrity and `release-validation.json` for verification scopes and limitations. APK certificate SHA-256: `1e19598265c5c5920639da46261944463f8ec65793fe0dbd362a558599c6dfd1`.
- OCR suggestions and photo-quality guidance can be inaccurate; review the image and details before saving.

## 2.2.0-beta.3 — November 6, 2026

- Keep update buttons and dialogs off the startup/loading background; show them after opening finishes or on the settled lock screen.

## 2.2.0-beta.2 — November 6, 2026

- Upgrade the pinned updater to 0.2.2-beta.1, including recovery from a corrupted cached download and safe first-run opt-in handling.
- Correct phone-only wake event delivery; automatic GitHub checks remain opt-in.
- Publish APKs only; Google Play/AAB distribution is retired.

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
