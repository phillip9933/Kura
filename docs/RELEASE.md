# Release preparation

## Current status

2.0.0 / version code 118 is an **unreleased development candidate**. Source publication to GitHub `main` and withdrawal of older release installers were explicitly authorized on 2026-09-29. Signed 2.0 binaries and store publication remain pending; the user is updating the original F-Droid submission. The Android project now builds from the repository root.

Release ID stays `app.kura.wallet`; debug stays `app.kura.wallet.prototype`. Code 118 is higher than the previous source release's 104, but check the actual highest uploaded code in each store before publication. Do not change production identity or replace a signing key to work around an installation failure.

## Local push readiness versus publication

The native source is prepared locally with backup-only migration, updated security libraries, current contributor documentation and scoped dependency-scan evidence. Root builds, packaging, JVM/core-device tests and the optimized-build device smoke test passed. App coverage includes a 59/60 full run followed by an 11/11 ordered recheck that resolves its test-setup race; see [Testing](TESTING.md) for exact limits. No known test failure remains unresolved, but this is not a claim that every publication gate below is complete.

Pushing source and publishing a store release are separate actions. Source push and old-download removal are now authorized. Store publication is separate. Remote `main` gained privacy-policy commit `c337f75` during local development; the native squash was rebased onto it so publication preserves that change and needs no forced push or rewrite of published history.

## Unsigned verification

```sh
./gradlew :app:assembleRelease :app:bundleRelease :app:lintDebug
python3 verify-native-packaging.py
```

The verifier requires Android build-tools 36.0.0. It checks release identity, absence of INTERNET permission, APK alignment and ELF load-segment alignment for 16 KiB devices, packaged baseline profiles and absence of a JAR signature. It is not a signature-continuity check.

The manual GitHub release-candidate workflow produces **unsigned** APK/AAB artifacts. It does not use signing secrets, create releases, create tags or publish to stores. The test workflow runs JVM/lint/build/packaging checks. A push to `main` triggers the verification workflow. Inspect that run for the published revision; earlier local results do not prove remote CI success.

## Explicit local signing

Default release builds are unsigned. Supply an ignored/private Java-properties file containing the existing `keyAlias`, `keyPassword`, `storePassword` and `storeFile`:

```sh
./gradlew :app:bundleRelease -PreleaseSigningProperties=/absolute/private/key.properties
```

Relative `storeFile` paths resolve against the properties file's directory. An explicit `-PreleaseKeystore=/absolute/private/existing-key.jks` overrides only the file location. Never place passwords on the command line. Never commit signing properties or keystores.

The consolidation preserved the original signing files, byte-for-byte, in the external local recovery folder; it did not generate or use a release key. When using that archived properties file, supply the correct absolute keystore override because its original relative path was interpreted against the old app directory. Verify certificate continuity independently before signing a release. F-Droid and Google Play may use different signing/distribution paths; do not assume their APKs can update one another.

## Publication gates

- Review the remaining platform/retention limits in [Security review](SECURITY_REVIEW.md). SQLCipher/Bouncy Castle have been refreshed and the scoped runtime advisory scan is recorded there.
- The user confirmed successful backup restore and satisfactory testing of build 118 on Pixel 10 Pro. See [Testing](TESTING.md) for the scope; this is not a measured hardware-security or frame-performance result.
- Export a backup from the old app **before** a same-ID update, then restore and verify counts, fields and media. Migration is backup-only; the new app does not automatically read old private storage. Rehearse the correctly signed installation on disposable data.
- Verify Keystore/biometric/device-credential behavior on API 24–29 and API 30+ physical devices.
- Review final merged release permissions, signing certificate, version code, R8 behavior and 16 KiB packaging on the exact release artifact.
- Current store text, code-118 release notes and five synthetic screenshots are prepared under `fastlane/metadata/android/en-US/`. Review their appearance in each store listing before submitting. The existing icon is retained; a Play feature graphic and console-specific listing/data-safety declarations still need review against the existing listing.
- Apply and validate the [F-Droid build handoff](FDROID.md) after selecting a public revision. No external recipe, build-server run or pending submission was modified here.
- Obtain separate authorization before publishing signed 2.0 binaries or submitting to a store. The user will update the pending F-Droid request.

The original release history and tags remain intact. The rewrite/consolidation is one commit on top of the preserved remote privacy-policy commit. A local recovery branch preserves the pre-squash prototype for rollback.

## Prepared locally — 2026-09-29

The exact unsigned release APK reports `app.kura.wallet`, version `2.0.0` / `118`, minimum API 24 and target API 36. Release APK/AAB generation succeeded; the APK passed the no-INTERNET, non-debuggable/no-backup, exported-component, baseline-profile and 16 KiB ELF/ZIP checks. Exact counts and hashes are recorded in [Testing](TESTING.md#local-release-preparation--build-118). Neither artifact is signed or installable as an update yet.

Outputs are `app/build/outputs/apk/release/app-release-unsigned.apk` and `app/build/outputs/bundle/release/app-release.aab`. No production application code changed during this preparation; the user's tested debug build 118 remains current.

Before signing, use the Play Console's **app signing certificate** fingerprint for installed Play APK continuity, and its **upload certificate** for the AAB upload identity. They can differ; see [Android app signing](https://developer.android.com/studio/publish/app-signing). Check the separately distributed F-Droid APK certificate for that channel; do not compare it to a debug certificate. Public APK certificates can be inspected without opening a private keystore:

```sh
apksigner verify --print-certs previous-production.apk
apksigner verify --print-certs candidate-signed.apk
```

Record the highest uploaded version code from all Play tracks (including drafts/internal tracks) and the relevant F-Droid entry. Code 118 is only verified against source history, not those consoles. No private signing material was opened for this checklist. The retired public GitHub v1.1.2 universal APK passed `apksigner verify`; its certificate SHA-256 is `1e19598265c5c5920639da46261944463f8ec65793fe0dbd362a558599c6dfd1`. This identifies that GitHub artifact only, not the Play/F-Droid signing keys. Candidate certificate continuity, a signed same-ID upgrade and highest uploaded codes remain unverified.

## Store screenshots

The five JPEGs in `fastlane/metadata/android/en-US/images/phoneScreenshots/` show actual build-118 views: Passes, a boarding pass, its fullscreen barcode, and Cards in dark/light themes. All names and barcode payloads are fictional. They were captured from the prototype using the same production presentation code; no layout was fabricated or retouched. Each is 1080 × 1920, 24-bit RGB. Store text lengths were checked against title/short/full/changelog limits.

To reproduce on the documented disposable emulator with PIN 2468:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
./capture-store-screenshots.ps1 -Serial emulator-5580
```

The opt-in instrumentation case temporarily clears screenshot protection only in the test process and restores window flags in `finally`. It removes its owned fictional rows and restores saved preferences. The runner restores the emulator's previous display size/density. Ordinary device-suite runs skip this capture case unless `storeScreenshots=true` is supplied. Always visually inspect recaptured images before using them.

Asset layout follows [F-Droid's metadata guide](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/); screenshot dimensions/formats were checked against [Google Play's preview-asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151). This does not upload anything or establish listing approval. AI assistance was used for the capture harness, store copy and release documentation.

## Old release downloads

The 2026-09-29 transition withdraws seven APKs and one AAB from GitHub releases v1.0.0, v1.1.0, v1.1.1 and v1.1.2. Release notes are marked retired. Historical tags and source archives remain for provenance; source archives are not installable APKs. Copies already downloaded or distributed by other stores cannot be recalled by removing GitHub assets. This action does not modify the pending Play/F-Droid submissions.
