# Release tooling

## Kura 2.0

Version **2.0.0**, code **118**, is distributed as a signed APK and Android App Bundle on [GitHub Releases](https://github.com/phillip9933/Kura/releases/tag/v2.0.0). The APK is for direct installation; the AAB is for submission through Google Play Console. GitHub publication does not submit either store listing. The user is updating the pending F-Droid request.

Release identity is `app.kura.wallet`; debug is `app.kura.wallet.prototype`. Preserve the signing identity, native Keystore aliases and storage contracts. Historical backup support is documented in [Backup and migration](BACKUP_AND_MIGRATION.md), rather than in the public release notes.

## Build unsigned artifacts

Use JDK 21, SDK platform 37.0 and build-tools 36.0.0 from the repository root:

```sh
./gradlew :app:assembleRelease :app:bundleRelease
python3 tools/verify-native-packaging.py
```

Default release builds are unsigned. The manual GitHub release-candidate workflow also produces unsigned artifacts; it never publishes a GitHub release or submits to a store.

## Build and verify signed artifacts

Supply the existing private Java-properties file with `keyAlias`, `keyPassword`, `storePassword` and `storeFile`. Do not put passwords on the command line or commit signing material.

```sh
./gradlew :app:assembleRelease :app:bundleRelease -PreleaseSigningProperties=/absolute/private/key.properties -PreleaseKeystore=/absolute/private/existing-key.jks
python3 tools/verify-native-packaging.py --apk app/build/outputs/apk/release/app-release.apk --expected-certificate 1e19598265c5c5920639da46261944463f8ec65793fe0dbd362a558599c6dfd1
```

Without the optional keystore override, a relative `storeFile` is resolved against the properties file's directory. The APK verifier checks identity, offline permissions, component restrictions, native/ZIP 16 KiB alignment and baseline profiles. In signed mode it also verifies the signature and expected public certificate.

Validate the AAB with Google's `bundletool validate --bundle=...` and `jarsigner -verify`, and inspect its public certificate using `keytool -printcert -jarfile ...`. A self-signed Android release certificate does not form a public CA chain; that warning is distinct from signature verification failure. Record any additional validation warnings in [Testing](TESTING.md).

The expected certificate above was verified against the existing public GitHub APK and both new signed artifacts. It does not independently establish which certificate the Play Console registered. Play can distinguish an upload key from its app-signing key; see [Android signing](https://developer.android.com/studio/publish/app-signing). Check the registered upload certificate and highest uploaded version code in the console before submission. F-Droid can use its own signing route.

## Store handoff

- The [F-Droid guide](FDROID.md) identifies the native source tag, root build and required toolchain. Its external build-server/source-policy checks are not replaced by GitHub CI.
- Store descriptions, release notes and five fictional-data screenshots are in `fastlane/metadata/android/en-US/`.
- Review the existing Play feature graphic, listing and data-safety declarations in the console.
- Remaining hardware/platform validation limits are in [Testing](TESTING.md) and [Security review](SECURITY_REVIEW.md). No independent security certification or exhaustive physical-device coverage is claimed.

GitHub APK/AAB publication and the 2.0 tag were explicitly authorized on September 29, 2026. Store-console submission requires account access and a separate execution step. Older GitHub installer assets were withdrawn; historical tags and source archives remain.

## Refresh screenshots

On the documented disposable emulator with synthetic PIN 2468:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
./tools/capture-store-screenshots.ps1 -Serial emulator-5580
```

The opt-in test captures real production views with fictional fixtures. It restores screenshot-protection flags and preferences, removes owned rows, and restores the emulator display size/density. Ordinary suites skip this capture case. Inspect all images before uploading. The existing captures are 1080 × 1920 RGB JPEGs; see [Play asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151) and [F-Droid metadata layout](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/).
