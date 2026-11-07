# Release tooling

## Android 2.2.0 preparation — unpublished

The proposed stable source version is **2.2.0 / 123**, following published
Beta 3 / 122. This is preparation only: no stable tag, tagged CI release build,
publication, latest promotion or beta cleanup has occurred. Preserve existing
beta assets, tags and recovery histories until replacement verification and
the separately authorized cleanup decision.

The newer horizontal ID-1 crop default and independent missing-side photo
actions are covered by focused local regressions in [Testing](TESTING.md).
The owner reported physical testing of these two UI changes using the local
Beta 3 / 122 build. The local APK SHA-256 is
`325e948720d0d74285c488f4814b70096d456990e71650394cfd17eae8aaf153`.
Its source inputs match the candidate before the intentional version-only
change to 2.2.0 / 123; UI behavior is unchanged. This is owner-reported UI
testing, not complete hardware-key, encrypted-data, production upgrade or
screen-reader acceptance. Independent AI patch review found no blocking issue;
it is not independent human approval.

Current executable qualification passes 94 active app tests (four existing
opt-in skips), four optimized external journeys and 252 parsed four-way CI JVM
executions. Strict-lock qualification retains byte-identical app/test/release/
optimized/driver APKs. Pinned private-report source/secret checks, their controls
and remaining scope are recorded in [Testing](TESTING.md) and
[structured evidence](evidence/production-preparation-20261107.json).

The owner requires verified completion of every open issue before stable:
#29 has crop review evidence; #9/#10/#15 retain physical/hardware, production
retained-upgrade and representative camera acceptance. All prepublication gates
must pass before authorizing publication; actual verified publication then
completes #13's Stable gate. No waiver/deferral is substituted for verification.
Recovery-canary verification is approved but not complete; metadata inventory is
recorded privately. Production signing remains a separate, unexecuted step.

Publishable APK bytes must come from CI at the final
authorized tag; the locally built testing APK must not be republished as the
production release. Retain the APK-only distribution, existing signing identity,
checksums, verification evidence and opt-in update policy.

## Software qualification follow-up — 6 November 2026

The owner explicitly deferred physical-device testing and will supply results later. [PR #26](https://github.com/phillip9933/Kura/pull/26) corrects only an instrumentation fixture and records the completed software follow-up: enhanced full suite 88 active passes / 4 opt-in skips, offline full suite 67 active passes / 4 skips, affected corrected refinement class 4/4 in each build, 12 default-screen cold-launch phases and the separate phone-only prerequisite/cold-process phases. All four instrumentation-source CI configurations pass with 236 fresh JVM executions and no failures/errors/skips. [Testing](TESTING.md#full-beta-3-regression-follow-up--6-november-2026) retains exact source scopes and earlier failures.

Application source and APK bytes are unchanged. Published Beta 3 (122), its tag, validation report and assets are unchanged; no new signing or release occurred. No implementation issue remains beyond the acceptance recorded in #9/#10/#15; those device/production-package checks are pending under the owner's deferral. #13 remains Beta, and stable promotion still requires the remaining acceptance and separate owner authorization. Latest stable remains 2.1.0.

## Owner-approved source closeout — 6 November 2026

The owner chose personal sign-off and removed the required-review rule. [PR #14](https://github.com/phillip9933/Kura/pull/14) merged as a5dd32d after all four [PR-head CI configurations](https://github.com/phillip9933/Kura/actions/runs/42192279428) passed. No independent approving review is claimed. Issues #8, #16, #17, #18, #19, #20, #22, #23 and #24 are completed/closed and marked Done in the public Project.

Four issues remain: physical unlock/data-safety acceptance #9/#10, representative physical scanner qualification #15, and RC/stable promotion #13. Stage remains Beta, latest testing release is 2.2.0-beta.3 (122), and latest stable remains 2.1.0. No stable publication or new signing occurred in this closeout. Existing tags, release assets and historical verification below are unchanged. Earlier review-gate statements below describe the policy at those publication steps; [maintainer approval](../CONTRIBUTING.md#maintainer-approval) records the owner's current workflow.

## Kura 2.2 beta.3 — published 6 November 2026

The owner-authorized [2.2.0-beta.3 / 122](https://github.com/phillip9933/Kura/releases/tag/v2.2.0-beta.3) is published from tag/source 6fe858f on the PR branch. It includes the loading-screen updater fix from 7f1b257: update controls/dialogs wait for an open vault or an idle locked screen with no automatic unlock pending. Scanner 0.8.0-beta.1 and updater 0.2.2-beta.1 remain pinned. Enable **Include beta releases** in App updates, then **Check for updates**, to receive this testing build.

Nine focused Android checks pass on version 122, including startup suppression, updater routes/popups, encrypted phone-only access and external optimized/R8 discovery. All four [release-source CI configurations](https://github.com/phillip9933/Kura/actions/runs/42189858072) pass with 236 fresh JVM checks and 0 failures/errors/skips; lint has 0 errors. [Exact validation](https://github.com/phillip9933/Kura/releases/download/v2.2.0-beta.3/release-validation.json) separates these results from historical beta.2 full-suite/installer/API26 evidence. All three public assets were downloaded and matched byte-for-byte; the public APK signature, models/permissions/providers and native/ZIP 16 KiB packaging pass. The certificate matches checksum-verified public beta.2. APK SHA-256: `827b8dcd70a9fd41c3e146692eed9edbe218159603fc888c4a96dbcdfd6cb910`.

Distribution remains APK-only/API 26+ with arm64-v8a/x86_64. Stable latest remains 2.1.0; prior releases/assets are unchanged. Independent approving review/merge and physical-camera/arm64/hardware-key/production-package data-safety acceptance still gate closeout/stable. The owner confirmed beta.2 Settings update access; beta.3 phone testing is not yet recorded. Existing signing inputs were consumed only for this explicitly authorized beta; no secrets were displayed/committed or globally configured. No production-package or personal-phone test suite ran.

## Kura 2.2 beta.2 — published 6 November 2026

The owner-authorized [2.2.0-beta.2 / 121](https://github.com/phillip9933/Kura/releases/tag/v2.2.0-beta.2) is published from validated source tag bcb9f9a on the PR branch. It combines `-PcardScanner=true -PdirectUpdates=true`, scanner 0.8.0-beta.1 and updater 0.2.2-beta.1. Distribution is APK-only: signed APK, SHA256SUMS.txt and [exact validation report](https://github.com/phillip9933/Kura/releases/download/v2.2.0-beta.2/release-validation.json). All public assets were downloaded and matched byte-for-byte; signature, models/permissions and native/ZIP 16 KiB packaging passed. The certificate matches checksum-verified public 2.1 and beta1 APKs. No AAB or store submission occurred.

APK SHA-256: `b2f2a561ce629c2bcea4a3b2e690fc920b0e2cea6d11ac59fa0892a1547d3e1c`. Application code remains b07ef53; subsequent changes through the release tag are test harnesses/documentation only, with unchanged debug/optimized application bytes verified. [Testing](TESTING.md#published-beta-2-qualification--6-november-2026) separates the complete enhanced run at74480f9, final offline run at bcb9f9a and subsequent affected enhanced test-only deltas. Earlier failures and environment interruptions remain preserved.

Latest stable remains 2.1.0; this is not RC/stable promotion. Main protection and required independent approving review remain intact. Affected-phone updater discovery, representative photographed-card/arm64/mobile resource qualification and physical/production-package data-safety acceptance remain open. The unconfirmed legacy-backup report #21 is owner-shelved as not planned, not fixed. Enable Include beta releases in App updates to follow beta builds. Existing private signing inputs were consumed only for this authorized beta; no credentials were printed, committed or globally configured.

## Kura 2.2 beta.1 — historical

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
