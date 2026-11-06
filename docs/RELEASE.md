# Release tooling

## Kura 2.2 beta

The owner authorized source commit/push and GitHub publication on 5 November 2026. Version **2.2.0-beta.1**, code **120**, combines `-PcardScanner=true -PdirectUpdates=true`: API 26+, arm64-v8a/x86_64, offline English/Japanese OCR and opt-in GitHub updates. Base source builds remain offline/API 24. The beta retains known physical-device/photographed-card and production-upgrade acceptance gaps; it does not promote those gates to passed.

Build with `./gradlew -PcardScanner=true -PdirectUpdates=true :app:assembleRelease` plus the private signing properties/keystore overrides below. Verify with `python3 tools/verify-native-packaging.py --card-scanner --direct-updates --apk app/build/outputs/apk/release/app-release.apk --expected-certificate 1e19598265c5c5920639da46261944463f8ec65793fe0dbd362a558599c6dfd1`. Keep private material outside Git and passwords off command lines/logs.

Publish the beta tag from its validated source branch if main still awaits the required approving review; preserve branch protection. GitHub beta releases do not replace the current stable latest release. Enable Include beta releases in App updates to receive subsequent beta builds; stable-only remains the default. No store submission is authorized by this GitHub publication.

Stable-readiness follow-up remains **Beta**. The protected SystemUI event-delivery correction passes focused Android 8/15 wake/security acceptance and optimized 3/3; [#16](https://github.com/phillip9933/Kura/issues/16) records final regression/review acceptance. See [Testing](TESTING.md#protected-system-event-delivery-correction--5-november-2026) and [focused evidence](evidence/protected-wake-20261105.json). Physical/production qualification still gates stable; no production signing or new release occurred.

## Kura 2.1

Version **2.1.0**, code **119**, is the optimization release, originally published with a signed APK and Android App Bundle on [GitHub Releases](https://github.com/phillip9933/Kura/releases/tag/v2.1.0). The owner retired AAB downloads on 6 November 2026. Current and future GitHub releases provide APKs only; no Google Play submission is planned. Historical validation records are preserved.

Release identity is `app.kura.wallet`; debug is `app.kura.wallet.prototype`. Preserve the signing identity, native Keystore aliases and storage contracts. Historical backup support is documented in [Backup and migration](BACKUP_AND_MIGRATION.md), rather than in the public release notes.

## Build unsigned artifacts

Use JDK 21, SDK platform 37.0 and build-tools 36.0.0 from the repository root:

```sh
./gradlew :app:assembleRelease
python3 tools/verify-native-packaging.py
```

Default release builds are unsigned. The manual GitHub release-candidate workflow also produces unsigned artifacts; it never publishes a GitHub release or submits to a store.

## Build and verify signed artifacts

Supply the existing private Java-properties file with `keyAlias`, `keyPassword`, `storePassword` and `storeFile`. Do not put passwords on the command line or commit signing material.

```sh
./gradlew :app:assembleRelease -PreleaseSigningProperties=/absolute/private/key.properties -PreleaseKeystore=/absolute/private/existing-key.jks
python3 tools/verify-native-packaging.py --apk app/build/outputs/apk/release/app-release.apk --expected-certificate 1e19598265c5c5920639da46261944463f8ec65793fe0dbd362a558599c6dfd1
```

Without the optional keystore override, a relative `storeFile` is resolved against the properties file's directory. The APK verifier checks identity, offline permissions, component restrictions, native/ZIP 16 KiB alignment and baseline profiles. In signed mode it also verifies the signature and expected public certificate.

Publish the verified APK and its checksum. Do not build, upload or advertise AAB files in the GitHub release process. Preserve historical AAB verification evidence as history.

The expected certificate above was verified against the existing public GitHub APK and both new signed artifacts. It does not independently establish which certificate the Play Console registered. Play can distinguish an upload key from its app-signing key; see [Android signing](https://developer.android.com/studio/publish/app-signing). Check the registered upload certificate and highest uploaded version code in the console before submission. F-Droid can use its own signing route.

## Store handoff

- The [F-Droid guide](FDROID.md) identifies the native source tag, root build and required toolchain. Its external build-server/source-policy checks are not replaced by GitHub CI.
- Store descriptions, release notes and five fictional-data screenshots are in `fastlane/metadata/android/en-US/`.
- Google Play publishing is retired; no AAB or Play Console handoff is part of this release process.
- Remaining hardware/platform validation limits are in [Testing](TESTING.md) and [Security review](SECURITY_REVIEW.md). No independent security certification or exhaustive physical-device coverage is claimed.

GitHub APK/AAB publication and the 2.0 tag were explicitly authorized on September 29, 2026; the 2.1.0 optimization release was authorized on October 3. Store-console submission requires account access and a separate execution step. Pre-2.0 GitHub installer assets were withdrawn; historical tags and source archives remain.

## Refresh screenshots

On the documented disposable emulator with synthetic PIN 2468:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
./tools/capture-store-screenshots.ps1 -Serial emulator-5580
```

The opt-in test captures real production views with fictional fixtures. It restores screenshot-protection flags and preferences, removes owned rows, and restores the emulator display size/density. Ordinary suites skip this capture case. Inspect all images before uploading. The existing captures are 1080 × 1920 RGB JPEGs; see [Play asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151) and [F-Droid metadata layout](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/).

## Optional direct-update distribution

The owner authorized updater integration on 2026-10-24. The default build remains offline/API 24. Build with `-PdirectUpdates=true` for API 26+ GitHub updates, then verify with `python3 tools/verify-native-packaging.py --direct-updates`. Do not confuse outputs from the two invocations: they share package identity and output paths. The first integration retained 2.1.0 / 119 for local development. The 2.2 beta increments both version and code; any subsequent APK update must increase the version code again.

The public updater library is independently versioned and pinned; see [App updates](APP-UPDATES.md). Publishing that library does not authorize publishing Kura. Signed same-ID retained-data upgrade acceptance remains required.

## APK-only distribution — 6 November 2026

The manual unsigned-candidate workflow builds the combined scanner/direct-update APK, verifies its matching permissions and 16 KiB packaging, and uploads only that APK. Automatic checks remain opt-in. In installed 2.2 beta, use Settings → About → App updates, then Check for updates; App Version & Trademark is a separate information dialog. The next source revision also exposes App updates from that dialog. Kura 2.1 has no built-in updater.
