# Testing

## Published beta 2 qualification — 6 November 2026

[Kura 2.2.0-beta.2 / 121](https://github.com/phillip9933/Kura/releases/tag/v2.2.0-beta.2) is published, APK-only, from bcb9f9a. [Public validation report](https://github.com/phillip9933/Kura/releases/download/v2.2.0-beta.2/release-validation.json) records exact hashes, source scopes, fresh/reused/skipped checks and preserved failures. Application code remains b07ef53; all later changes are test harnesses/docs, with byte-identical debug and optimized application artifacts verified.

| Verification | Actual result and source scope |
| --- | --- |
| Complete enhanced app selection | **87 active passed, 0 failed, 4 intentional skips / 91 total**,1055.771s, at74480f9; complete streamed transcript and installed APK hashes |
| Complete default offline selection | **67 active passed, 0 failed, 4 intentional skips / 71 total**,916.658s, at bcb9f9a; complete transcript and hashes |
| Later enhanced test-only delta | Affected backup/export journey **1/1**,58.128s; ordinary encrypted-wallet prerequisite **1/1**,61.282s; all **12/12** cold-launch phases and phone-only cold prepare/verify **2/2**,9.751s/27.916s, at bcb9f9a. Do not relabel the earlier enhanced full selection as exact-final-head execution. |
| Core instrumentation | **7 security +12 database +29 import =48 passed**, no failures/skips; application/core source unchanged from b07ef53 |
| Optimized/R8 external selection | **4/4**,104.744s, including real authentication, camera denial/cancelled import and locked/Settings/version updater discovery |
| Upgrade/installer | **18 successful phase executions** on synthetic prototype packages, source9d26ed1:119→121 app-authenticated,120→121 phone-only, actual Android121→122 nondebuggable candidate, refusal/interruption/cancellation/cache/permission/recreation/offline paths. Installed bytes match the candidate; UID, encrypted records/media, leading zeros and preferences retained; install permission restored to deny. |
| Real scanner process death | Prepare intentionally force-stopped during active OCR, not a pass; verify **1/1**,6.948s. Four owned workspace files plus abandoned capture existed before relaunch; cleanup publishes no draft. Earlier five-file observations are historical/timing-dependent. |
| Local JVM | **59 fresh executions** at b07ef53:24 model +34 import +1 constrained-heap; no subsequent model/import source change |
| [Final-source CI](https://github.com/phillip9933/Kura/actions/runs/42171805484) | All four distributions pass; **59 fresh JVM tests each /236 total**,0 failures/errors/skips; build/lint/unsigned release/permission/model/16KiB checks and opt-in harness compilation pass. Warnings58/62/62/65;0 lint errors/fatals. |
| Older supported API | Final beta application121 phone-unlock/data/backup/re-enable journey on API26 passes **1/1**,67.356s. Earlier focused scanner/launch/safety **17/17** on each API26 and API37 retain their development-metadata/source scope; no physical/arm64 claim. |

Four default skips per complete app selection are phone cold-process, retained-upgrade, store screenshot and empty-vault timing opt-ins. Relevant cold/upgrade phases ran separately above. Store capture and empty-vault trace were not rerun here; older evidence remains historical. The launch matrix deliberately cleared its wallet, so the first separate phone-cold verification correctly failed its missing sentinel assertion. The final driver reruns the real ordinary encrypted-wallet journey before cold prepare/force-stop/verify, with no clear/uninstall between those phases. That failed setup remains preserved. Existing native/full/fixture failures and environment-interrupted runs are retained in the dated evidence sections below; none are silently counted as passing runs.

The signed APK and all three public assets pass independent byte readback. Its public certificate matches 2.1/beta1; production identity, nondebuggable manifest, model/native payloads, baseline profiles and 16KiB packaging pass. No production-package or personal-phone destructive suite ran. Stage stays **Beta**: required independent review, affected-phone updater validation, real photographed-card/arm64/mobile performance, physical hardware-key and prior production-package upgrades under both unlock choices remain open. Latest stable is2.1.0. The owner-shelved backup report #21 is unconfirmed/not planned, not a claimed fix. AI assisted implementation, test diagnosis, validation and publication.

## Local build and JVM checks

Use JDK 21, SDK platform 37.0 and build-tools 36.0.0. On Windows use `gradlew.bat` in the commands below.

```sh
./gradlew :core:model:test :core:import:testDebugUnitTest :core:import:testLowMemory
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug
python3 tools/verify-native-packaging.py
```

The runner `./tools/run-tests.ps1 -Serial emulator-5580` builds the modules and runs security, database, import/storage and app instrumentation. It rejects non-emulator serials and requires 16 KiB pages. Use **only a disposable emulator**, API 37 x86_64 in the current setup, with synthetic PIN `2468`. Do not change credentials, clear data or run fixtures on a personal device. Supply JDK/SDK locations through `JAVA_HOME` and `ANDROID_HOME`.

UI tests enter a real system credential prompt. They may install synthetic records, alter preferences/font scale and create test documents. Some fixtures temporarily remove screenshot protection on the emulator for synthetic screenshots; this is test code only.

For release-style performance testing without production signing, build
`:app:assembleOptimized`. This non-debuggable, R8-optimized APK updates the existing
debug prototype package using its debug certificate. Use the separate `benchmark`
test driver rather than in-process debug instrumentation against this variant.
`:app:assembleBenchmark` remains the independently installed comparison target.

`tools/capture-profile.ps1` captures four real unlocks plus the broader synthetic
UI journey into `app/src/main/baseline-prof.txt`. After `tools/run-benchmark.ps1`,
`tools/measure-profile.ps1 -Unlock` compares verify-only and packaged-profile
compilation on the same benchmark APK, including authentication-to-draw traces.
The ordinary cold-launch log measures entry to the lock screen, not vault access.
For empty-vault comparisons, provision an empty disposable benchmark installation
first; the normal benchmark journey populates synthetic items. Scripts never target
the production package. Compilation state is explicitly reset in the comparison;
do not compare uncontrolled debug runs against forced-compiled runs as equivalent.

## Verification status

### Updater 0.2.0 and optional app unlock — 2026-10-26

Integrated the published immutable 0.2.0 prerelease (`90367917ac451bb1b065d4a49044df23ed3138a6`) as a checksum-verified Maven/source/license bundle. Public bundle SHA-256: `7b8cd5befbb65142c96fd16cbb84b069c51d029370ca1e198ed6440c11520b1d`. The direct distribution uses the new package/group and a foreground new-version popup with View update/Later; the default distribution remains offline/API 24. Added the device-local Require app unlock choice, default on, retaining the original authenticated wrapper and encrypted wallet formats. Re-enabling verifies the original key inside cancellable background session work before removing the phone-only wrapper.

Final builds on Linux/JDK 21:

| Variant | Checks | Result |
| --- | --- | --- |
| Direct updates | Debug + instrumentation APK, unsigned release APK/AAB, lint | Passed, 1m 2s; 25 tasks executed / 477 up-to-date; lint 0 errors / 61 warnings |
| Default offline | Debug + instrumentation APK, unsigned release APK/AAB, lint | Passed, 1m 26s; 78 tasks executed / 424 up-to-date; lint 0 errors / 57 warnings |
| Both | Packaging/permissions/provider checks, 16 KiB native/ZIP alignment | Passed; release APKs and AABs unsigned |

Model JVM task was UP-TO-DATE: existing 17/17 passing results were reused, not rerun. Library host/native results are upstream evidence rather than new Kura test executions.

On the newly created disposable `kura-features-20261026` API 37 emulator (16 KiB pages, synthetic PIN 2468), **11 selected native acceptance executions passed**: updater identity/manifest/application registration 3/3; popup dismissal, manual access and a newer-version offer 1/1; phone-unlock retained-data/locked-phone rejection/restored app prompt journey 1/1; cold-process preparation and post-force-stop verification 1/1 each; hardware-key regressions including key-mode separation 4/4. Cold-process phases are two executions of one test method, not two independent test cases. Popup metadata was injected synthetically; no live download/installer journey was used. The final unlock journey passed after moving re-authentication into session-owned IO work. The task's emulator was stopped afterward.

An initial unlock test stopped at Android accessibility’s checked-state assertion. It was replaced with a direct default-policy assertion while retaining the actual opt-in dialog and mode-transition checks; the complete journey subsequently passed. The initial failure log is preserved rather than counted as a pass. An initial packaging check still expected the former provider namespace; its expected class was updated to 0.2.0 and both variants then passed.

Evidence and separate direct/offline artifacts: `../outputs/updater-and-unlock-20261026/`, with checksums. Prior 0.1.3 vendor bytes were preserved there before repinning. AI assistance was used for implementation, tests and documentation. No app version bump, commit, push, production signing or release was performed. No private signing material was inspected or activated. Physical hardware, older-API device behavior, signed same-ID retained-data upgrades and full existing device suites remain unverified for these changes.

### Signed optimization release 2.1.0 / 119 — 2026-10-03

The user authorized GitHub publication of the optimization work as a minor release.
The version was increased to 2.1.0 / 119 and the newer remote documentation commit
was preserved. Source revision `8d5c082` contains the application changes; this
validation record is documentation only. Store-console submissions are unchanged.

Local signed APK/AAB, JVM and lint build passed in 3m 28s: **75 tasks executed,
375 up-to-date**. Import JVM **34/34** and constrained-heap **1/1** ran afresh;
model **17/17** results were reused (`UP-TO-DATE`). Lint: **0 errors / 44 warnings**.
The database, restore and optimized-device tests recorded above apply to the same
application changes before the version-only release bump; no new complete device
suite or production-package installation was performed for this signing pass.

APK signature matched the existing release certificate
`1e19598265c5c5920639da46261944463f8ec65793fe0dbd362a558599c6dfd1`.
Production ID/version, offline/component restrictions, baseline profiles and all
16 native-library/ZIP 16 KiB alignment checks passed. Jarsigner verified the AAB;
its certificate matched and bundletool 1.18.3 validated its structure. Jarsigner's
self-signed/untrusted CA-chain, missing timestamp, ignored POSIX attributes and
JarFile/JarInputStream entry-order warnings remain in the local log, as with 2.0.
These do not constitute Play Console acceptance or a physical-device upgrade test.

| Signed artifact | SHA-256 |
| --- | --- |
| Kura-2.1.0.apk | `443a78234032620ee6fd7349f292e83b3471dc05aa0a7131614366657d2dd343` |
| Kura-2.1.0.aab | `4dd44f0e09214649e15c41ff6afa60abb8824b67667957f08076a1a0b3326a32` |

Local files are in `artifacts/releases/2.1.0/`, with `SHA256SUMS.txt`; validation
logs are `verification/release-2.1-*`. Independent GitHub verification for the
application revision is [run 37130857749](https://github.com/phillip9933/Kura/actions/runs/37130857749).
At the time this record was written that run was in progress; its linked status
is authoritative. AI assistance was used for implementation, release preparation
and validation. Private signing inputs were consumed by Gradle, not printed or
committed. GitHub publication does not update Play or F-Droid submissions.

### Optimized prototype and refreshed profile — 2026-10-03

Added the non-debuggable `optimized` build type: release-style R8/resource shrinking,
debug signing and the existing `app.kura.wallet.prototype` identity. No database,
key, backup, authentication or session-policy change is part of this pass. Updated
profile capture to exercise four real credential unlocks before the broader UI
journey; the measured baseline profile now contains 24,914 rules (previously
24,194). The external, opt-in `benchmark.UnlockTimingTest` can drive optimized
apps without making them debuggable. Scripts and the driver restrict targets to
disposable emulators/prototype packages; production is excluded.

Fresh builds:

| Build | Executed / up-to-date tasks | Duration |
| --- | --- | --- |
| Benchmark, profile and external driver | 102 / 230 | 4m 55s |
| Profile capture rebuild | 13 / 266 | 55s |
| Refreshed benchmark and optimized APK | 64 / 216 | 3m 1s |

**9 instrumentation executions passed, 0 failed**: empty-vault provisioning (1),
old-profile comparison (2), profile capture unlock/UI journeys (2), new-profile
comparison (2), final optimized benchmark UI journey (1), and install-over-debug
prototype unlock journey (1). The final update retained the existing emulator vault
without clearing its data and passed four credential unlocks in 27.505 seconds.
Broader optimized UI journeys passed in 54.018 and 49.745 seconds, exercising
synthetic cards, barcode display, scrolling, camera, picker, rotation and relock.
These are repeated executions of the timing/journey tests, not nine distinct cases.

The same empty benchmark vault was used for these callback-to-first-draw observations
(ms; first process opening followed by three reopenings):

| APK/profile | Verify-only compilation | Packaged-profile compilation |
| --- | --- | --- |
| Previous profile | 592 / 557 / 666 / 470 | 449 / 463 / 467 / 432 |
| Refreshed profile | 611 / 591 / 586 / 567 | 566 / 549 / 561 / 563 |

Android reported `speed-profile`, not forced full `speed`, for the compiled runs.
The native database-open mean also varied from about 418 to 520 ms between the two
compiled runs despite unchanged cryptography, so these results do not establish
a speedup from the profile refresh itself. They confirm the packaged profile is
accepted and the optimized unlock path runs successfully. Earlier debug/forced-full-
compilation timings are not equivalent release measurements. Sideloading does not
guarantee Android has finished profile compilation immediately; no Pixel speedup or
fixed percentage gain is claimed.

The final synthetic scrolling sample reported 102/201 janky frames (50.75%; legacy
metric 39/201), with 17 ms median / 29 ms p95 on this headless emulator. Passing the
functional journey does not establish smooth 60/120 fps physical-device rendering.

APK verification passed: matching debug certificate, prototype ID/version 118,
no INTERNET permission, backup/cleartext disabled, non-debuggable manifest and
16 KiB ZIP alignment. The APK includes `baseline.prof` (10,366 bytes) and
`baseline.profm` (1,211 bytes). Artifact size is 11,089,419 bytes versus the previous
debug APK's 33,456,464 bytes. Current local testing artifact:
`artifacts/Kura-2.0.0-dev-118-optimized-20261003.apk`, SHA-256
`feeb1f14be5a1bc03bf6cf9014b03319918e526c4d3963e65cfd59c9f349f844`.

PowerShell syntax and `git diff --check` passed. Full JVM/database/import suites,
full lint, a production-signed release and physical-device tests were not rerun.
Logs/traces are in `verification/profile-*` and `test-results/profile-*`; prior
comparison traces were preserved before the second run. AI assistance was used
for build configuration, profile/test tooling and validation. Nothing was pushed
or published.

### Code-only preload investigation — 2026-10-03

`VaultController` now schedules SQLCipher native/runtime class loading on the I/O
dispatcher when the controller is created, alongside authentication. The opener
awaits its completion only after authentication. `prepareRuntime()` takes no
Context, path or secret and creates no database connection. Locked-state data,
key lifetime, authentication and database derivation settings are unchanged.

The debug/app-test build passed: **14 tasks executed / 155 up-to-date**. Alternating
baseline/preload installations on the empty disposable API 37 / 16 KiB emulator
passed **4 timing-test executions**, four unlocks each. Callback-to-first-draw
durations (ms) were:

| Variant/run | First | Repeat 1 | Repeat 2 | Repeat 3 |
| --- | --- | --- | --- | --- |
| Existing parallel opener / 1 | 1212 | 617 | 630 | 624 |
| Code preload / 1 | 1145 | 649 | 625 | 621 |
| Existing parallel opener / 2 | 1249 | 630 | 606 | 615 |
| Code preload / 2 | 978 | 503 | 502 | 528 |

The preload trace completed before authentication and moved **18–29 ms** of
code loading out of the post-authentication path. Repeat timings also varied
substantially despite this being a once-per-controller preload, so the larger
end-to-end differences cannot be attributed to preloading alone. No reliable
percentage improvement or physical-device benefit is claimed.

A separate diagnostic forced ART `speed` compilation on the emulator prototype
installation: **1 timing execution passed**, with 669 / 522 / 552 / 523 ms.
This is not a release/profile benchmark: full forced compilation is emulator-local
and is not carried in the supplied APK. Kura already ships a baseline profile;
refreshing and measuring its coverage remains a separate optimization opportunity.
The emulator prototype now has forced compilation enabled; reset compilation when
using it for future controlled comparisons.

Lock-time pending-buffer/grant/capture regressions passed **2/2** in 0.817 seconds.
These are **7 passing executions**, covering 3 distinct test methods; earlier full
database/import suite results were not rerun for this code-loading-only change.
No fresh lint, release build, Pixel timing or cryptographic migration was performed.
Raw traces and results are under `verification/preload-*`; AI assistance was used
for the experiment, implementation and validation.

Verified local APK: `artifacts/Kura-2.0.0-dev-118-preload-20261003.apk`, prototype
package / version 118, SHA-256
`cc1fcd5980ac793556d563062387c6d302a2d8d80ae7d5dcc6d9273cc37a77f0`.
Signature verification passed; INTERNET permission remains absent. Nothing was
pushed or published.

Potential larger work requires a separately validated key-handling design.
[SQLCipher's guidance](https://www.zetetic.net/sqlcipher/performance/) permits raw
high-entropy keys without password stretching; applying that to existing vaults
requires compatibility, recovery, authenticated wrapping and crash-safety tests.
No such key change, reduced KDF work, retained unlocked connection or decrypted
preload is part of this patch. [Android's baseline-profile guidance](https://developer.android.com/topic/performance/baselineprofiles/overview)
describes code precompilation without changing authentication or encryption.

### Concurrent database unlock — 2026-10-03

The three existing SQLCipher databases now open concurrently on the I/O dispatcher.
All must pass key/version checks before any Room callback can adopt a database.
Failure joins the openers before closing their handles and wiping their password
copies; cancellation cannot return an owned vault without cleanup. Encryption
parameters, schemas and lock-time key destruction are unchanged. First-time vault
creation still uses the existing sequential creation path.

The initial concurrent candidate passed 9/10 database tests: the corruption test
caught Room metadata being written to a healthy sibling database before another
opener failed. Adding the shared verification checkpoint corrected this without
weakening the assertion. The revised suite passed **12/12** in 27.309 seconds,
including missing-file and cancellation/reopen regressions. Builds before and
after this correction passed (14 executed / 155 up-to-date and 16 / 153).

The opt-in empty-vault timing journey passed **1/1**, exercising four real credential
unlocks in 28.519 seconds on the same disposable API 37 / 16 KiB emulator. Compared
with the sequential single-open baseline immediately below:

| Authentication callback to first vault draw end | Sequential | Concurrent |
| --- | --- | --- |
| First opening after process start | 1488 ms | 989 ms |
| Subsequent opening 1 | 1006 ms | 504 ms |
| Subsequent opening 2 | 992 ms | 492 ms |
| Subsequent opening 3 | 999 ms | 507 ms |

Repeat-opening median improved from 999 to 504 ms (about 50%). These are four-sample
debug emulator observations, not a physical Pixel or release benchmark. Database
opens overlap; their individual durations must not be summed. No reduction in
cryptographic work or authentication requirements is claimed. Initial process/UI
work still contributes to the first opening. Local evidence: `parallel-database-tests.txt`
(failed candidate), `parallel-safe-database-tests.txt`, `parallel-safe-timing-test.txt`,
`parallel-safe-timing.atrace`, and `parallel-safe-spans.json` under `verification/`.
AI assistance was used for implementation, diagnosis and validation.

Final debug APK/lint/import-test build passed in 1m 54s (**49 tasks executed,
229 up-to-date**). Lint reported **0 errors / 44 warnings**; this offline run is not
a fresh dependency-advisory scan. Fresh import/storage instrumentation passed
**29/29** in 91.947 seconds, including staged restore, publication failure,
encrypted media and large backups (`verification/unlock-import-tests.txt`).
APK signature verification passed and its manifest
has no INTERNET permission. Local test artifact:
`artifacts/Kura-2.0.0-dev-118-fast-unlock-20261003.apk`, prototype package,
version `2.0.0-dev` / `118`, SHA-256
`e8c05a6f7a24000cd8754873337850c428eba3ddd7c7690af6fcc328167caf79`.
Prior artifacts were retained. No remote publication, release build, full app
journey suite or physical-device timing was performed in this pass.

### Empty-vault post-authentication timing — 2026-10-03

AI-assisted local tracing added fixed-name Android trace sections (no keys, records,
paths or passwords) for authentication success, unwrap, the three existing-database
opens, settings, backup-secret lookup, refresh and vault drawing. These are local
system traces, not analytics. `UnlockTimingTest` is opt-in via `-e unlockTiming true`,
requires a disposable emulator and uses synthetic PIN 2468; ordinary runs skip it.
Start from an empty prototype installation when reproducing these measurements.

On API 37 / 16 KiB `emulator-5580`, the final debug run measured four empty-vault
reopenings after a process start:

| Stage | Observed duration |
| --- | --- |
| Authentication callback to first vault draw end | 1488, 1006, 992, 999 ms |
| Master-key unwrap | 2.5–9.8 ms |
| Wallet database open | 321–342 ms |
| Pass database open | 288–298 ms |
| Identity database open | 290–297 ms |
| Three opens combined | 899–938 ms |
| Settings | 0.5–1.6 ms |
| Backup-secret lookup | 0.15–0.19 ms |
| Empty-vault refresh | 0.9–12.4 ms |
| First vault draw itself | 3.9–8.2 ms |

Database opening is the dominant measured stage. The callback-to-draw interval also
includes scheduling/composition/layout; draw end is not proof of physical display
presentation. These are debug emulator measurements, not Pixel/StrongBox timings,
and exclude the user's PIN entry and the time before the authentication callback.
No encryption parameters or database formats were changed for this measurement.

The first journey failed before authentication because the emulator had no secure
credential. After configuring the documented synthetic PIN, two runs passed (one
test each, four unlocks per run). The first successful run captured creation plus
three reopenings but missed the first-frame marker due to cached parent drawing;
the marker was moved onto the newly composed vault container. The final run passed
in 30.93 seconds. Local evidence: `verification/unlock-timing-test.txt` (initial
failure), `unlock-timing-retest.txt`, `unlock-timing-final-test.txt`,
`unlock-timing.atrace`, and `unlock-spans.json`.

App/test build passed (17 executed / 152 up-to-date tasks); the test diagnostic
rebuild passed (4 / 147), and the corrected draw-marker app build passed (5 / 132).
No full app suite or release-performance comparison was run, and no new APK was
published or copied over the previously supplied test artifact.

### Unlock database-open regression — 2026-10-03

Existing vaults now check database versions on the same SQLCipher connection used by Room,
before Room can create or migrate tables. This removes the three separate read-only
preflight opens per unlock. Room validation, key cleanup and non-deleting corruption
handling remain in place; cryptographic parameters are unchanged.

Fresh `DatabaseCompatibilityTest` instrumentation on disposable 16 KiB `emulator-5580`:
**10 passed, 0 failed, 0 ignored**, including new rejection tests for version zero,
older/newer versions, wrong keys and corrupt files without file deletion or replacement.
Existing schema, nullable-column, reopen and session-lock checks passed. Evidence:
`verification/unlock-database-tests.txt` (ignored local output).

`:core:database:assembleDebugAndroidTest :app:compileDebugKotlin` passed with
14 tasks executed and 90 up-to-date. Only the database test APK was built/installed;
no new application APK was built or published. Full app journeys, lint and physical-device
latency measurements were not rerun. The reduction in opens is established by the code;
no percentage or millisecond improvement on the Pixel is claimed. AI assistance was used
for implementation and validation.

Fresh validation on 2026-09-29 after relocation to the repository root:

| Suite | Result |
| --- | --- |
| Model JVM | 17 passed |
| Import JVM | 34 passed |
| Low-memory backup JVM | 1 passed |
| Android security | 14 passed |
| Android database/schema | 9 passed |
| Android import/storage | 29 passed |
| Selected app journeys and new security regressions | 7 passed, 1 failed |
| Debug and unsigned release builds | Passed |
| Android lint | 0 errors, 29 warnings |
| Release packaging/manifest checks | Passed; no INTERNET permission, 16 KiB native-library alignment |

**111 passed, 1 failed across 112 selected test cases.** The selected app run covered `PendingSecurityTest`, `LaunchTest`, `BackupJourneyTest`, `CropJourneyTest` and `SettingsMediaJourneyTest`; it was not a full app-suite rerun. Both new regressions passed: vault lock without an Activity clears pending attachment buffers/grants/capture, and startup removes abandoned camera outputs. System-document backup export and confirmed restore passed with synthetic data.

At that checkpoint, `SettingsMediaJourneyTest.settingsFullscreenImagesArchiveAndAutomaticResume` failed with `Missing control: Edit Back image`. Its later archive/resume assertions were not reached. This also failed before consolidation and is not classified as harmless flakiness; investigate the editor and test interaction before calling the app suite clean. The earlier integration established passing evidence for 57/58 distinct app cases through a full run plus focused rechecks; those historical results are not added to the fresh counts above.

The debug emulator launch/scroll sample recorded 186 frames, total-frame p95 51.04 ms, UI-work p95 11.90 ms and PSS 224,932 KiB. These figures do not demonstrate sustained 60/120 fps on a physical device. Import instrumentation recorded a 100 MiB backup exercise at 17,849 ms and peak PSS 331,791 KiB; the Argon2 round trip took 5,312 ms with peak PSS 231,718 KiB. PSS includes native allocations and is not equivalent to the managed heap limit. These are individual emulator samples, not production performance guarantees.

The local testing APK is `artifacts/Kura-2.0.0-dev-116.apk` (package `app.kura.wallet.prototype`). SHA-256: `1ff878805769e0458a5985eaf2e5054e0d31607791595bf96f7be0d9116c4d24`. Artifacts and raw logs are ignored; fresh clones build their own APK. Evidence: `verification/native-root-build.log`, `final-security-build.log`, `release-packaging.txt` and `root-*-tests.txt` / `root-app-security-journeys.txt`.

## Subsequent code-cleanup validation — 2026-09-29

After the native consolidation, the maintainability cleanup passed a clean build, 52 JVM test executions and 22 focused Android cases on API 37 / 16 KiB `emulator-5580`. Debug, instrumentation, benchmark-test and optimized unsigned-release APKs compiled; release packaging checks passed. Expanded lint now includes Android libraries and reports 0 errors / 58 warnings, with no unused-resource warning. See [Code review](CODE_REVIEW.md#validation) for exact classes, warning categories, build evidence and scope.

These are separate runs; do not add their counts to the preceding consolidation results as if every case were distinct. At that cleanup checkpoint, the existing `SettingsMediaJourneyTest` failure remained unresolved and was not included in the selection. The build-117 follow-up below supersedes that status. The previously shared numbered APK and its hash above remain unchanged; cleanup builds are in the normal Gradle output directory.

## Backup-only and dependency refresh — build 117

The current build uses SQLCipher 4.19.0 and Bouncy Castle 1.86, compiled against API 37.0 with AGP 9.1.1, Gradle 9.3.1 and KSP 2.3.12. Minimum API 24 and target API 36 are unchanged. The security XML/key extractor and automatic Flutter database/preference/media adoption were removed. Existing native generations and backup formats retain their compatibility contracts.

Fresh checks on the disposable API 37 x86_64 / 16 KiB emulator:

| Check | Result |
| --- | --- |
| Model JVM | 17 passed |
| Import JVM | 34 passed |
| Separate constrained-heap execution | 1 passed |
| Android security | 6 passed |
| Android database/schema | 8 passed |
| Android import/storage | 27 passed |
| Debug/test, unsigned release APK/AAB, optimized benchmark builds | Passed |
| Optimized-build device smoke journey | 1 passed in 53.637 seconds |
| Expanded Android lint | 0 errors, 56 warnings |
| Release permissions and 16 KiB alignment | Passed |
| Resolved release-runtime Maven advisory scan | 138 coordinates; no OSV matches |

The smaller core Android counts deliberately exclude obsolete extraction/snapshot tests; a new isolation test checks that old private files are ignored and preserved during fresh creation/reset and that native device secrets survive explicit staged restore. GCM/CBC column compatibility, every database schema, nullable fields, media, backup interchange and failed-publication checks remain covered.

The first dependency build correctly failed because SQLCipher requires compile API 37. The first upgraded-toolchain build exposed a nullable recent-task test lookup, which was corrected; its version assertion now uses `BuildConfig.VERSION_CODE` rather than a stale hardcoded 115. Successful build evidence: `verification/readiness-build3.log` (538 executed / 156 up-to-date tasks) and `readiness-test-rebuild.log` (5 executed / 255 up-to-date). JVM XML results were freshly produced in this pass; they are not added to earlier historical runs.

Lint warnings comprise 35 dependency-update suggestions, 12 KTX suggestions, five launcher-icon warnings, two EXIF suggestions, one target-API notice and one Compose modifier-default warning. None were suppressed. OSV metadata matching is not a native binary or build-tool vulnerability audit; scope and the public inventory are in [Security review](SECURITY_REVIEW.md).

The first full app run completed in 951.989 seconds: **59 passed, 1 failed out of 60**. The back-image journey passed. The failure was the headless pending-data regression: the preceding UI journey's delayed process `ON_STOP` raced its synthetic unlock. The corrected test goes home and waits for the lifecycle to settle before creating its controller; it retains every buffer/grant/capture assertion. `lifecycle-process` is now an explicit test-only dependency. Production background-lock behavior was not disabled or changed. The first compilation of that test correction failed on the missing dependency; `readiness-final-test-build2.log` records the successful correction (15 executed / 245 up-to-date tasks).

The corrected, ordered recheck passed **11/11** in 194.852 seconds: `ParityJourneyTest` (3), `PassPresentationTest` (5), `PendingSecurityTest` (2), and `SettingsMediaJourneyTest` (1). It deliberately includes the preceding UI work to exercise the original process-transition condition. All 60 app cases therefore have passing evidence across the full run and recheck, but the first full run was not a clean pass and a second complete 60-case run was not performed. Do not sum overlapping cases as unique coverage. Logs: `verification/readiness-app-tests.txt` and `readiness-app-recheck.txt`.

The previous back-image failure was reproduced in isolation. Real scroll gestures brought the pencil visibly onscreen, while the cached accessibility tree retained pre-scroll bounds. Refreshing `UiAutomation`'s accessibility cache before locating controls resolved the failure. The complete test then exercised encrypted front-image saving, back-image picker cancellation, archive restore, brightness restoration, automatic resume authentication and canceled-auth behavior. No assertion was removed to obtain that result.

Current debug artifact: `artifacts/Kura-2.0.0-dev-117.apk`, SHA-256 `9295ddb5cfee3a42e5b51b4d486021be7306daed195f743c25d4039508a25dc7`. Build 116 was preserved under `artifacts/previous/`; the top-level artifacts folder has one current testing APK. This remains a separate prototype installation, not a production-signed upgrade.

The independently installed optimized benchmark variant passed its authenticated launch, synthetic-fixture, lock and reopen journey. Evidence: `verification/readiness-benchmark.txt`. This uses debug signing and the prototype hardware policy; it is not production certificate or physical StrongBox verification.

The debug scroll sample recorded 192 frames, total-frame p95 36.08 ms, UI-work p95 7.39 ms and PSS 238,889 KiB. It does not establish sustained 60/120 fps or compare controlled physical-device performance.

The import suite took 175.143 seconds. Its large streaming-backup sample took 32,194 ms (reported PSS 53,521 KiB); the Argon2 round trip took 3,662 ms with baseline PSS 52,405 KiB and sampled peak 218,185 KiB. These are individual emulator observations, not physical-device performance guarantees.

## Missing backup images — build 118

A reported `Missing referenced image` error exposed a gap in old-format fixtures. Reviewing the Flutter writer at `72d5a30` confirmed that pass logo/footer paths were serialized without adding their files to the archive, and unreadable images were silently omitted. Its filename rule also removed all `.enc` occurrences rather than only the trailing suffix. No personal backup was opened to investigate this report.

The native importer now matches that historical filename rule. Full-vault restore can stage records and available images with missing ordinary image references cleared, but presents an omission count before user confirmation. The count is encrypted with the staged generation so recovery selection also shows it. Core staging remains strict by default; missing attachments, invalid images and authentication failures still reject restore. Cancellation does not publish the staged generation. This cannot reconstruct absent pictures; it recovers the data that the archive actually contains.

Fresh verification: import JVM **34/34**, separate constrained-heap **1/1**, import/storage instrumentation **29/29** (182.619 seconds), debug/app-test builds successful and expanded lint **0 errors / 56 warnings**. The build executed 59 tasks and reused 256 up-to-date tasks; JVM tests were executed, not cached. Two new import cases cover omitted logo/footer/card/loyalty images, repeated `.enc` filename handling, nullable references, existing-image decryption, persisted omission counts, source-payload immutability, unchanged active generation and continued rejection of missing attachments/corrupt images. Evidence: `verification/backup-image-build.log` and `backup-image-import-tests.txt`.

The app selection passed **4/4** in 101.94 seconds: the new encrypted-stream missing-image warning/cancel journey, normal system-document export/confirmed restore, and both pending-security regressions. Evidence: `verification/backup-image-app-tests.txt`. These are synthetic fixtures on the disposable emulator; the user's backup and Pixel were not inspected.

Current testing APK: `artifacts/Kura-2.0.0-dev-118.apk`, SHA-256 `79078f9e3f2a20def273bd5132ed3323aa94b5d7db555ac019eac406778f688b`. The prior build 117 was preserved under `artifacts/previous/`.

This focused fix does not reuse prior full-app, security, database or optimized-build results as fresh tests. At the time of this focused fix, the prior unsigned-release packaging and optimized-device evidence applied to build 117. The later release-preparation section below records the fresh build-118 packaging run; optimized-device evidence still applies to 117. AI assistance was used for diagnosis, implementation, tests and these notes.

## Device and release checks still needed

The user reported successful real-backup restore on the Pixel 10 Pro with build 118 and then confirmed that everything was good. This is user-reported acceptance, not an assistant-observed checklist run: individual biometrics/PIN, background/resume, screen-off, camera/picker, image-editing, font/rotation/theme and sorting checks were not separately recorded. An emulator cannot establish physical StrongBox behavior or measured device smoothness.

Run `./tools/run-benchmark.ps1 -Serial emulator-5580` for independent optimized journeys and cold launches. `tools/capture-profile.ps1` and `tools/measure-profile.ps1` generate/compare ART profiles on a disposable emulator. Existing profiles are historical measured startup coverage; no fresh physical-device frame benchmark is claimed for this consolidation.

The API 24–29 fallback and a production-signed upgrade remain separate gates. One real historical backup is now user-confirmed; exhaustive historical-version coverage is not claimed. Raw local results/captures live in ignored `test-results/` and `verification/`, not in the repository. Record exact tests, failures and reused results when reporting validation; a completed test is not necessarily a passed test.

## Local release preparation — build 118

On 2026-09-29, `:app:assembleRelease :app:bundleRelease` succeeded in 3m 27s: **237 tasks, 40 executed / 197 up-to-date**. `tools/verify-native-packaging.py` passed on this build's APK, including all 16 packaged native libraries. AAPT independently reported release ID `app.kura.wallet`, version `2.0.0` / `118`, min API 24 and target API 36. Outputs remain unsigned. Logs: `verification/release-118-build.log` and `release-118-packaging.txt`.

| Artifact | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | `7dc52959be97d6949e238d076d700650e185fcaad30e3d5294c4a4e12340a94b` |
| `app/build/outputs/bundle/release/app-release.aab` | `4cdd1b3de07db65c66701cbd7ff63440ad7b0bbad21a4115a14a988f86cae304` |

The new opt-in `StoreScreenshotsTest` compiled successfully: debug/app-test build **169 tasks, 5 executed / 164 up-to-date**, 1m 11s. Capture passed **1/1** in 28.078 seconds, then **1/1** in 38.243 seconds after adjusting only the emulator density to fit more pass content. These are two executions of one capture journey, not two distinct regression tests. Final five captures were visually inspected and checked as 1080 × 1920, 24-bit RGB JPEGs. Text lengths: title 4/30, short description 72/80, full description 1432/4000, changelog 416/500. Capture logs: `verification/store-captures-build.log`, `store-captures-tests.txt`, `store-captures-run.log`; the final run replaces the initial capture log/images.

The capture uses only fictional owned fixtures, authenticates through the real emulator credential UI, restores saved settings and removes owned rows. Screen protection is changed only by instrumentation and restored in `finally`; production screenshot security is unchanged. The emulator display was restored after capture. Ordinary suite runs skip the opt-in capture test.

No production Kotlin, Gradle configuration, schema or crypto behavior changed in this release-preparation pass. Full JVM/device suites, lint, the runtime advisory scan and optimized-device performance journeys were **not rerun**; their earlier evidence above remains scoped to those runs. No private signing material or personal backup was opened. No signed upgrade, F-Droid Linux/server build, new physical-device benchmark or store submission was performed. AI assistance was used for test tooling, assets and documentation.

## First public source verification

Source revision `1d77cb7` was pushed on 2026-09-29, preserving the remotely added privacy policy. Its first GitHub Android verification run (`36514186319`) failed during SDK setup: setup-android's default `tools platform-tools` requested the removed `tools` package. No Gradle tests ran in that attempt. Both Android workflows now explicitly request only `platform-tools` before installing the pinned platform/build-tools. This is a CI bootstrap fix; no application behavior changes.

The follow-up public run [36514363928](https://github.com/phillip9933/Kura/actions/runs/36514363928), on F-Droid-pinned revision `c429d29e6d523a1aee5f76aa969dc0b9b5ac6129`, **passed** on GitHub's Linux runner. Gradle completed in 9m 6s: **473 tasks executed, none reused/up-to-date**. Downloaded XML reports show model **17/17**, import JVM **34/34**, separate constrained-heap **1/1**: **52 executions, 0 failures, 0 errors, 0 skipped**. Lint reported **0 errors / 56 warnings**. Debug/release APK builds and release packaging checks passed, including no INTERNET permission and 16 KiB native/ZIP alignment. This workflow does not run instrumentation, sign binaries or perform an F-Droid build-server run. Raw downloaded reports are retained locally in ignored `verification/github-native-2.0/`.

The final documentation-only record of these already-completed checks uses `[skip ci]` to avoid another identical full build; it changes no application or workflow code. The earlier SDK-setup failure remains visible in GitHub and is not counted as passing. AI assistance was used for source publication, the CI fix and these validation notes.

## Signed 2.0 publication

The user authorized signed APK/AAB publication and requested a simpler public README/changelog. Production application code is unchanged from the verified build 118. Test/profile helpers now live under `tools/`, with their repository-root resolution and workflow/doc references updated. The packaging verifier adds an explicit APK path and expected-certificate option for signed validation.

The signed build succeeded in 1m 31s: **238 tasks, 15 executed / 223 up-to-date**. APK signature verification matched the existing public release certificate, `1e19598265c5c5920639da46261944463f8ec65793fe0dbd362a558599c6dfd1`; identity/version are `app.kura.wallet`, `2.0.0` / `118`, min API 24 and target API 36. Offline manifest, baseline profiles and all 16 native-library/ZIP alignment checks passed on the signed APK. Jarsigner verified the AAB signature and its public certificate matched; Google's bundletool 1.18.3 validated the bundle structure. Bundletool was downloaded from Google's release and checked against its published SHA-256.

Jarsigner additionally reports self-signed/untrusted CA-chain and missing-timestamp warnings, ignored POSIX attributes, and JarFile/JarInputStream differences caused by the bundle's entry ordering. These warnings are retained in the local validation log rather than suppressed. The independent bundletool structural check passed; Play Console acceptance has not been tested. No personal phone or production package was used for destructive device testing, and no new physical-device claim is made.

PowerShell helpers received syntax/root-path checks; the moved packaging verifier ran on both unsigned and signed APKs, including rejection of an incorrect expected certificate. This pass reuses the earlier **52 passing Linux test executions** and **0 errors / 56 lint warnings**; no application change calls for a fresh device suite. Release assets carry a separate SHA256SUMS file. Local evidence is under `verification/signed-2.0-*`; published hashes and assets live on the GitHub release. AI assistance was used for signing orchestration, tool organization, validation and public documentation.

## Optional direct-update integration — 2026-10-24

The owner requested reuse of the standalone updater. Kura now pins the published `dev.rogers:android-updater:0.1.2` Maven/source/AGPL bundle. Its downloaded release ZIP and internal checksums were verified. The independent library repository CI passed 43 host tests with no failures/skips; those are library tests, not Kura coverage. No private signing material was inspected or used. README/contribution GPL labels were corrected to match Kura's already-existing AGPL LICENSE.

The final direct build (`-PdirectUpdates=true`) passed debug/test APKs, unsigned release APK/AAB and lint in 1m 17s: **502 tasks, 27 executed / 475 up-to-date**. Lint reported **0 errors / 61 warnings**. The direct packaging verifier passed identity, exact update-permission mode, restricted exported components, baseline profiles and all native 16 KiB alignment checks. Normal offline debug/release builds and lint also passed in 1m 47s (**465 tasks, 59 executed / 406 up-to-date**) with **0 errors / 57 warnings**, and the default packaging verifier confirmed no update/network permissions. These are separate, overlapping builds; task/test counts are not summed.

On the disposable API 37 / 16 KiB emulator, the focused updater integration selection passed **3/3**, with zero failures. It covers Kura-only release/asset configuration, declared direct-update permissions and nonexported provider scope, first-run opt-out, and Application/WorkManager registration without opening a vault. The first device attempts could not start while Android credential-encrypted storage was locked. Once the emulator was unlocked, instrumentation exposed missing WorkManager initialization. The Application now supplies Configuration.Provider and removes only WorkManager's default startup metadata. Lint initially caught the missing metadata removal; that was corrected without suppressing the error. The final selection passed with the complete configuration. Two earlier build attempts also corrected AGP 9 Kotlin source-set wiring. Failed attempts remain in local logs rather than being counted as passes.

Artifacts and evidence are preserved in `../outputs/updater-integration-20261024/`. This pass did not rerun Kura's complete app/JVM/database/import suites, sign an app, perform a production-package upgrade, verify the recovered signing key, test API 26 or physical hardware, or publish a Kura app release. The signed retained-data upgrade acceptance in [App updates](APP-UPDATES.md) remains required. AI assistance was used for integration, tests, library publication, dependency pinning and documentation.

## Updater debug-APK rejection correction — 0.1.3

Raiun's native acceptance identified that Android 15 can report archive ApplicationInfo.flags=0 even for debuggable=true. The shared library was corrected under a new immutable **0.1.3** pin, reading the compiled manifest and resolving resource-backed booleans. Kura adopted the downloaded public bundle after its outer and per-file SHA-256 checks passed; previous 0.1.2 bytes remain preserved in project outputs. No Raiun integration files were changed by this correction task.

The authoritative shared library passed **43 fresh host tests**, ktlint/detekt, release lint (0 errors / 11 warnings / 1 hint), and **10 native verifier tests on each API 26 and API 35**. Its public quality CI also passed. These are shared-library tests, distinct from Kura app acceptance.

Kura's direct-update debug/test APKs, unsigned release APK/AAB and lint passed in **1m 15s**, **502 tasks: 33 executed / 469 up-to-date**; lint reported **0 errors / 61 warnings**. Direct packaging checks passed, including restricted components and 16 KiB alignment. Logs and APKs are in `../outputs/updater-security-0.1.3/direct/`. The earlier Kura 3/3 integration and default offline build results were not rerun for this dependency-only correction. Full app suites, signed installation/retained-data upgrades, physical hardware and the recovered key remain unverified by this pass. No Kura app release or production signing was performed.

The shared library's final independent native CI [40052660831](https://github.com/phillip9933/android-app-updater/actions/runs/40052660831) subsequently passed **10/10 on API 26 and 10/10 on API 35**; both downloaded XML reports contain zero failures/errors/skips. CI-only setup corrections are documented in the library repository; its 0.1.3 tag and published bytes are unchanged. Raiun's owning task separately verified its published-library pin and synthetic retained-data installer journeys; that does not establish Kura production upgrade acceptance.


## Issue closeout review — 26 October 2026

An isolated linked worktree based on public main `4931b1e` preserves the original local checkout and unpublished edits. The updater is pinned to immutable 0.2.0 (the newest published SDK prerelease checked on this date). The shared-panel change prevents an automatic offer from stacking over manual update details. CI now checks both offline and direct distributions and compiles their instrumentation APKs.

Fresh isolated direct build: **539/539 Gradle tasks executed**, 3m 4s; model **17/17** and import **35/35** JVM test executions passed (the import count includes the low-memory task). Debug/test APKs, unsigned release APK/AAB, lint (**0 errors, 61 warnings**) and direct permissions/export/alignment verification passed. No production signing occurred.

The exact isolated branch's API 37 / 16 KiB synthetic phone-unlock journey passed **1/1** in 60.033 seconds. It verifies cancel-before-opt-in, retained sentinel data after background lock, encrypted backup round-trip and wrong-password refusal, staged restore, device-local policy exclusion from backup, locked-device key refusal, and authenticated restoration of app unlock. The expanded foreground update-popup regression also passed **1/1** in 12.486 seconds, including persistent Later, manual access and suppression of overlapping offers. It injects public synthetic metadata; it does not exercise a production installer.

Earlier API 26 updater integration passed **3/3** in the closeout session. Extended credential-journey attempts first exposed test helpers assuming a modern keypad; helpers now use the real Android 8 credential activity. After interruption/restart the API 26 image had an unavailable credential service and Settings/SystemUI crashes (including missing user_dictionary provider). Restarting its normal credential service allowed a synthetic PIN but did not repair the system UI. These failed/incomplete runs are retained in local logs and **do not establish older-API unlock acceptance**. Initial modern runs before first device unlock failed on credential-encrypted storage; first unlock through the real synthetic PIN restored normal test operation.

Full app regression, API 24, physical hardware, biometrics, prior-production same-ID retained-data upgrades, real installer denial/return journeys, signing continuity and the recovered key are not established by these focused results. Issues #9/#10/#13 retain those acceptance gates. Shared SDK 0.2.0 [quality CI](https://github.com/phillip9933/android-app-updater/actions/runs/40056763702) and [native API 26/35 verifier CI](https://github.com/phillip9933/android-app-updater/actions/runs/40056763707) passed for its exact source; those remain library evidence, not Kura upgrade acceptance.

OCR discovery has separate [synthetic measurements and limitations](CARD-RECOGNITION.md). No scanner or OCR dependency was added to Android. AI assistance was used for review, implementation, experiments, tests and documentation. Local disposable artifacts/logs are under `../outputs/issue-closeout-20261026/`; no personal data or signing material was used.

The separate offline build passed in **1m 53s**, **502 tasks: 78 executed / 424 up-to-date**, including debug/test APKs and unsigned APK/AAB. Offline lint reported **0 errors / 57 warnings**; manifest permission restrictions and 16 KiB packaging passed. The cold-process preparation and post-force-stop verification each passed **1/1** on API 37 (9.960s and 28.012s), preserving the sentinel and restoring app authentication. A combined updater invocation used a misspelled test-class selector and is a runner error; the corrected suite is recorded separately.

The corrected exact-branch API 37 updater integration/popup selection passed **4/4** in 11.667s; hardware-key checks passed **4/4** in 0.042s. Together with the retained-data journey and two cold-process phases, the final focused selection passed **11/11 executions**. These are synthetic emulator results, not production-device acceptance.


## Scanner integration — 5 November 2026

Scanner **0.8.0-beta.1** (`8d9a451c27ed87d20110d25dcedb81001e0f521f`) is the latest published scanner checked on this date. Upstream OCR #1 and beta release #8 are closed; broader real-card/stable accuracy acceptance remains open. Kura vendors the published archive after its SHA-256 match, retains source JARs and all notices, and bundles the approved English/Japanese model hashes only with `-PcardScanner=true`. See [integration/limits](CARD-RECOGNITION.md) and [machine-readable build/test evidence](evidence/scanner-integration-20261105.json).

Fresh JVM XML reports contain **24 model + 35 import/low-memory test executions**, all passing with zero failures/errors/skips. Seven model cases cover allowlisted fields, leading zeros, conflicting identifiers, existing edits, Japanese labels/label spacing, unknown keys and unlabelled text. Import results were rerun against the changed model runtime; these are not merely historical results.

Final serialized build/packaging matrix (debug and instrumentation APK, unsigned release APK, lint):

| Distribution | Gradle result | Tasks |
| --- | --- | --- |
| Enhanced offline/API 26 | 1m 40s | 511: 109 executed / 402 up-to-date |
| Basic offline/API 24 | 1m 34s | 497: 80 executed / 417 up-to-date |
| Basic + direct updates/API 26 | 1m 33s | 496: 74 executed / 422 up-to-date |
| Enhanced + direct updates/API 26 | 1m 34s | 497: 56 executed / 441 up-to-date |

All four passed lint and permission/export/16 KiB ELF+ZIP packaging verification. Enhanced offline also produced an unsigned AAB. Packaging checks verify enhanced-only language models, their hashes and 64-bit ABI set; scanner use adds no network permissions. The default remains API 24 without scanner models; direct updates remain independently opt-in. CI configuration now covers all four combinations, but this dated pass is **local validation**, not a newly executed public CI run. Existing lint warnings remain; no baseline/error suppression was introduced.

On the owned disposable **API 26** emulator, actual native card detection/perspective crop, English/Japanese labelled field recognition and cancellation cleanup passed **3/3** in 1.624s. On owned **API 37 / 16 KiB**, those engine checks, the full optional-import journey and two startup-cleanup checks passed **6/6** in 18.859s. The UI journey verifies cancel leaves fields/images untouched; choosing image-only performs no autofill; review does not mutate the form; selected suggestions update only the draft; media is encrypted; the separate form Save publishes the record. Cleanup tests remove abandoned OCR workspaces while retaining other files and reject scanner-directory links before traversing/deleting outside content. All fixtures are generated nonprivate cards on prototype packages, not personal devices.

Failed attempts are preserved under project outputs: the first native run hit an incorrect canonical-cache-path assumption (2/2 failed); lint then caught direct StateFlow access in composition. Both were corrected without suppression. Japanese recognition initially failed the host mapping on both APIs because the engine inserted spaces inside known Japanese labels. Matching now tolerates those label spaces without changing recognized identifier/name characters; a new JVM regression and actual native Japanese fixture pass. The first packaging check used an incorrect aapt2 key spelling and was corrected. A direct-update build exposed Git CRLF conversion of pinned updater text files; original published bytes were restored, unchanged hashes rechecked, and `.gitattributes` now preserves all dependency/model bytes. None of these failed attempts is presented as passing evidence.

Physical photographed cards, glare/low-contrast/occlusion quality, arm64 runtime/mobile profiling, permission-denial UI, API 26 authentication/import UI, full app regression, production same-ID upgrade and backup/restore acceptance were not rerun by this focused integration. Issue #15 remains open for wider acceptance; upstream beta limitations remain applicable. No production signing, release, app version bump or personal-phone installation occurred. AI assistance was used for implementation, review, tests and documentation. Reproducible logs and development/unsigned artifacts are in `../outputs/scanner-integration-20261105/`.

The validated scanner delta was subsequently applied to the canonical `repo/` checkout after a clean patch check, preserving its pre-existing branch/index/local work. All 210 changed/new files were compared against the validated worktree (exact bytes for dependencies/models; newline-normalized comparison for app source). Git patch application initially converted newly added vendored text/model-license files to CRLF and correctly failed the pin check; only those newly added files were restored to their validated original bytes. Canonical enhanced debug build then passed in **56s**, **137 tasks: 29 executed / 108 up-to-date**. Both dependency bundles' original pins were reverified there. These transfer/setup failures are retained locally; no hashes were weakened.


## 2.2.0-beta.1 release preparation — 5 November 2026

The owner explicitly authorized committing/pushing the accumulated changes and publishing a GitHub release with scanner/OCR/updater enabled. The candidate is **2.2.0-beta.1 (120)**, combined enhanced/direct configuration. It remains a beta while photographed-card, physical hardware and production-package upgrade acceptance are incomplete; no stable gate is waived. Include beta releases is an explicit updater option; stable-only remains the default.

The downloaded prior public 2.1.0 APK matches published SHA-256 `443a78234032620ee6fd7349f292e83b3471dc05aa0a7131614366657d2dd343` and its APK signature verifies. The recovered signing material's public certificate matches the same previously published SHA-256 certificate `1e19598265c5c5920639da46261944463f8ec65793fe0dbd362a558599c6dfd1`. This check used the existing properties/keystore internally without printing credentials or private-key contents.

A generated-wallet **prototype-identity** upgrade from exact tag v2.1.0 to the combined beta passed: seed **1/1** in 7.075s, then `adb install -r` and verification **1/1** in 6.589s. The original native authenticated envelope reopened retained records, the leading-zero member field, an encrypted image (including decrypted dimensions/pixels) and preferences. There was no uninstall/data clear between those phases. This is source/data compatibility evidence using the disposable prototype package/debug signer, not a production-package or physical-device upgrade claim. Generated data was cleared only after successful verification to isolate the subsequent regression suites.

Combined debug/test/core instrumentation builds and lint passed in **1m 32s**, **378 tasks: 142 executed / 236 up-to-date**. Final native suite, independent CI and signed-artifact results are recorded in the GitHub release's `release-validation.json` and linked issue/PR evidence. That report distinguishes fresh/reused JVM results, assumptions/skips, signature/packaging checks and remaining limits. Release/source links refer to [v2.2.0-beta.1](https://github.com/phillip9933/Kura/releases/tag/v2.2.0-beta.1); main retains its required approving-review gate.

The first combined full-app run reported 75 cases: 64 passed, seven failed and four opt-in cases were skipped. The failed journeys exposed stale crop/unlock wording, unguarded scroll assumptions, shared-fixture category expectations, an offscreen menu action and password-button/keyboard synchronization. Test corrections retain restore/data-preservation assertions and the original restore timeout; focused rerun outcomes and any remaining failures belong in the final release validation report. No failing result is replaced with a claimed full-suite pass.

The live camera rerun identified `Dynamic range profile cannot be converted to a DynamicRange object: 8192` in CameraX 1.4.2 on API 37. This is covered by the upstream [CameraX 1.5.2 fix](https://developer.android.com/jetpack/androidx/releases/camera#1.5.2); all three host CameraX modules now use 1.5.3. A focused real-frame regression was added, and temporary diagnostic logging was removed. The AVD advertises both camera lenses, so both emulated cameras must be enabled for CameraX validation. Final post-fix outcomes remain in the release validation report. Backup UI input now waits for and scopes itself to the actual password modal, avoiding background search fields; no app encryption or restore checks were relaxed.

The final upstream release check found updater 0.2.1 stable beyond the previous 0.2.0 pin. Its outer bundle checksum, seven inner pins and source delta were reviewed; Kura now pins 0.2.1. The only runtime source change cancels obsolete queued downloads when no eligible update remains. Results from 0.2.0 remain historical and are distinguished from final 0.2.1 host validation in the release report.

A subsequent full-run attempt stopped after the phone-lock test failed to return to the app and left phone-only access enabled, causing later credential-prompt assumptions to fail. That run was aborted and retained, not reported as passing. The journey now explicitly resumes Kura after phone unlock (retrying the real encrypted wrapper without another credential) and always removes only its optional device wrapper in guarded prototype/emulator cleanup. The authenticated wrapper/data remain intact; opt-in cold-process phases retain their wrapper across invocations. Final clean-run results are recorded separately in release-validation.json.

The isolated phone-wake failure was confirmed as a second app credential prompt (`Authenticating`, no error, SystemUI foreground), not a successful reopen. The host now defers phone-only auto-unlock while keyguard is locked and retries on the system user-present event only in a resumed, locked, idle session with no pending authentication. The receiver is not exported and is unregistered with the Activity; Keystore/device-lock checks and authenticated-wrapper recovery remain unchanged. The test again requires ready UI after the phone PIN without manually resuming or retrying the app. Exact post-fix outcomes are in the release report.

The combined post-fix run reached successful backup staging/restore but its fixture lookup assumed Transit 0 would be initially visible. The backup journey now records deterministic search preferences and searches for the restored synthetic card, retaining the subsequent encrypted pass export/import assertions. The full run and final focused rerun are reported separately rather than presenting a mixed result as one clean full-suite pass.

Further diagnosis of the missing restored sample found the prototype-only fixture loader skipped all sample passes when any retained test pass existed. The loader now inserts missing named sample passes idempotently, retaining its PROTOTYPE guard and existing data. This corrects the shared test setup for both backup and launch journeys; final focused reruns are distinguished from the earlier full-run failures.

The final action-sheet diagnosis confirmed a partially expanded menu clipped Delete below the screen; drag-based test attempts also failed to open Edit reliably. The production context sheet now skips partial expansion. The focused three-section direct Edit/Delete journey passed 1/1 in 64.325s, without swipe workarounds. Final backup/launch/camera focused reruns passed four cases; their preceding five-case run retained one action-sheet failure. Together with the full run (69 passed, three failed, four opt-in skips), all 72 active cases subsequently have passing evidence. This is combined evidence across runs, not one clean full-suite pass; a fresh full run remains part of RC acceptance.


## Stable-readiness rerun — 5 November 2026

Application follow-up [1bbf86e](https://github.com/phillip9933/Kura/commit/1bbf86e9d2e672046bf7641390b59b0d790a5f98) adds a bounded Activity-owned retry when the system user-present event precedes resume/keyguard publication. Foreground, secure/unlocked-phone and pending-authentication checks remain. This is **a partial mitigation**: Android 15 still reproduces a locked/no-error screen after the phone PIN. [Bug #16](https://github.com/phillip9933/Kura/issues/16) stays open; [release #13](https://github.com/phillip9933/Kura/issues/13) remains Beta. Published beta assets/tag are unchanged; stable latest remains 2.1.0.

Test corrections wait for observable Compose controls, enabled password confirmation, fresh accessibility nodes and the reinserted search-position row. Credential helpers now enter the real Settings PIN field on Android 8 and SystemUI PIN text field on Android 15 hardware-keyboard emulators; Android 17 retains its digit-keypad path. No authentication bypass or test master key was added. The external permission journey leaves its guarded prototype target stopped so the next test cannot race a pending background transition.

| Fresh or explicitly reused evidence | Outcome |
| --- | --- |
| Local JVM, forced before the host-only fix | Model 24 + import 34 + constrained heap 1: **59 passed**, no failures/errors/skips; these modules are unchanged |
| Core API 37/16 KiB instrumentation | Security **7/7**, database **12/12**, import/storage **29/29**; unchanged core sources were not rerun after host/test-only edits |
| Final complete combined app run, API 37/16 KiB | AndroidJUnitRunner completed **76 cases, zero failures**: 72 active passes and four default opt-in skips; capture limitation below |
| Complete optimized/R8 external suite, API 37 | **3/3 passed**: fixtures/live camera/lock-reopen, real camera denial + cancelled file import, four authenticated reopens |
| Separate modern opt-in executions | Cold-process prepare/force-stop/verify **2/2**, synthetic screenshot capture **1/1**, empty-vault unlock trace **1/1** |
| API 26 selected native/updater/key/unlock work | Scanner **3/3**, updater **4/4**, keys **3 passes + one API-30-only assumption skip**, complete phone-unlock/backup/restore journey **1/1** |
| API 26 added UI acceptance on a clean profile | Optional crop/OCR review/image-only/encrypted Save **1/1**; real camera denial + cancelled file import **1/1** |
| API 35 final evidence across selected runs | Keys **4/4**, native scanner **3/3**, updater **4/4**, full scanner import **1/1**, permission denial/cancel **1/1**; **phone wake failed 1/1** |
| Fresh [four-configuration CI](https://github.com/phillip9933/Kura/actions/runs/41996003114) at application commit 1bbf86e | **59 JVM executions each, 236 total**, zero failures/errors/skips; build/lint/unsigned-release/packaging passed; lint warnings 57/58/61/61, zero errors |

The final full-run desktop command exited 143 before its buffered ADB output was saved. The independently recovered AndroidJUnitRunner log explicitly reports `76 tests, 0 failed, 0 ignored`. Device log rotation retains 51 individual finishes and three late assumption events; the early cold-process skip follows its first-statement phase assumption with no opt-in argument. All four opt-in branches also have separate execution evidence. This is a real complete-run result with an incomplete transcript, not a fabricated ADB report; [structured evidence](evidence/stable-readiness-20261105.json) preserves the limitation. No app crash is inferred from the desktop command termination.

Prototype/debug-identity upgrade rehearsals retained generated records, leading zeros, encrypted image pixels and preferences: stable-source 119 → published-beta-source 120, then 120 → nonshipping validation metadata 121 with app unlock on/off. Across setup/seed/verification executions **eight passed and one old-beta phone-wake setup failed**. Its data was retained; subsequent phone-only preparation, update, cold reopening and retained-data verification passed. No uninstall/data clear occurred between a scenario's seed and verification. Version 121 is an external build override, not a shipping version bump or production-package upgrade claim.

Failed evidence remains visible: two earlier complete app runs each had 70 passes/two failures/four skips; two other attempts were stopped after observed failures. Focused navigation/visibility attempts, the first optimized-suite cleanup race and a package-private UIAutomator API compilation failure are retained. Initial older-device setup rejected valid `Pin set to` wording; corrected reruns followed. The first API 35 profile failed secure-key prerequisites; a fresh profile passed keys, and inspecting the actual hardware-keyboard PIN field resolved its selector failures. API 26 package-service/control-port setup failures occurred before its added UI tests; the clean profile on a standard emulator port subsequently passed both checks. None of these attempts is counted as a pass.

Read-only verification of the already published beta again passed its public certificate, permissions/models and native 16 KiB alignment. No private signing material was read or activated. No personal phone or production package was used. The owner confirmed no spare phone is available. Physical photographed-card/arm64/hardware-key checks, production-identity retained-data and actual Kura installer/interruption acceptance, dedicated scanner lock/process-death/source-conflict checks, required review and final stable-artifact validation remain open. Raw generated-fixture logs/artifacts are in `<project>/outputs/stable-readiness-20261105/`; AI assistance was used for fixes, tests and evidence.


## Protected system-event delivery correction — 5 November 2026

The preceding failed API 35 wake result is preserved as historical evidence. The next correction receives the system-protected user-present action from SystemUI's separate UID, retaining secure-phone/foreground/idle/authentication guards and the bounded retry. A real app-UID spoof attempt must throw `SecurityException`; locked-phone wrapper release must still fail. See the [security rationale](SECURITY_REVIEW.md#phone-only-wake-event-delivery--5-november-2026).

After this correction, API 35 wake/encrypted-backup/authenticated-re-enable passed **3/3 consecutive executions**, including spoof refusal, and cold-process prepare/force-stop/verify passed **2/2**. A fresh API 26 profile passed the same real-PIN wake/backup/re-enable/spoof-refusal journey **1/1** in 63.566s. Rebuilt optimized/R8 external acceptance passed **3/3** in 77.545s. No keys, schemas, formats, application IDs or signing policy changed.

[Focused source-hash evidence](evidence/protected-wake-20261105.json) records the actual results and older failed attempts. Subsequent complete modern regression and CI outcomes are recorded against this follow-up source in [#16](https://github.com/phillip9933/Kura/issues/16) and [PR #14 checks](https://github.com/phillip9933/Kura/pull/14/checks). The full-run driver now flushes each output line and saves independent progress/results under `<project>/outputs/stable-readiness-20261105/`, avoiding the earlier buffered-output loss. This documentation links the live acceptance record rather than asserting a future result.

API 26 reused-profile warm boots intermittently lost package/install-service readiness before tests could start; those attempts remain setup failures. The new protected-event journey passed on a fresh profile using a standard emulator port. This does not establish older-device reboot/production-upgrade acceptance. Physical-card/arm64/hardware-key checks, production-package upgrade/installer qualification, dedicated scanner lifecycle/source-conflict checks and required review remain release gates. No spare physical phone is available and Stage remains Beta.


The next complete run of application source 0d4a093 captured **71 passes, one failure and four opt-in skips** with a complete streamed transcript. Its sole failure was RefinementJourneyTest's cached Settings click: the saved hierarchy was still the main card grid when General Display was expected. The three remaining copies of the old lookup helper (Refinement/FormReview/UiFix) now use the same idle/cache refresh and bounded missing-control wait already validated in Interaction/Organization. Assertions and app behavior are unchanged; the failed Refinement journey then passed **1/1** in 31.478s. [#16](https://github.com/phillip9933/Kura/issues/16) records the subsequent whole-suite outcome for this test-only follow-up.

A separate earlier current-source attempt failed at its initial app credential callback before phone-only mode was enabled. That real failure remains recorded. A fresh-data focused rerun passed **1/1** in 56.102s after explicitly verifying phone-unlocked/no-pending-biometric state, exact installed APK hashes and idle package broadcasts without an immediate reinstall. The complete rerun uses those same recorded preconditions. This does not claim that the original callback failure's cause was proven; actual production install/upgrade/first-launch qualification remains in #10.

The complete 0bbca54 test-only follow-up run retained a full transcript: **71 passes, one failure and four opt-in skips**. Settings and the monthly date assertions passed; the second date control was clipped at the bottom of the saved hierarchy and its click did not open the picker. UiFixJourneyTest now explicitly scrolls that control into view before clicking, preserving both date-value assertions. The subsequent focused and complete outcomes remain recorded in [#16](https://github.com/phillip9933/Kura/issues/16); this failed attempt is preserved in the task outputs. Application source remains 0d4a093.

The first date-scroll focused attempt stopped earlier at an immediate Backup-page assertion; its cause is not proven by the post-test launcher snapshot. The test now waits for all three expected Backup controls with an in-test failure dump. With that change and the date-control scroll, the complete focused settings/backup/date journey passed **1/1 in 49.550s**, including both date-value assertions.

## UI regression synchronization follow-up — 5 November 2026

The complete f074165 results are preserved: enhanced **69 passed / 3 failed / 4 opt-in skips (76 total)**; default offline **62 passed / 2 failed / 4 skips (68 total)**. Enhanced failures involved encrypted-pass export discovery, category movement and cancellation from reorder; offline failed category movement and version-label visibility. Focused passes alone did not establish stable acceptance.

The affected click helpers waited for accessibility events but did not explicitly wait for Compose layout-frame publication before using screen coordinates. Overflow discovery was also attempted only once before the detail screen was ready. A shared test-only helper now waits for two frame callbacks, accessibility idle and a fresh cache before looking up controls, then settles after the real tap. Overflow discovery is retried while waiting for its destination. Six duplicate loops use this same synchronization. Existing data/persistence/encryption/cancel/version/secure-window assertions remain; category/version failures retain diagnostic hierarchies. No app/core/feature implementation or dependency changed.

The four previously failing journeys passed together **4/4 in 145.761s** with this correction. [Structured evidence](evidence/ui-regression-fixes-20261105.json) retains old failures and scope; [#16](https://github.com/phillip9933/Kura/issues/16), [#17](https://github.com/phillip9933/Kura/issues/17), [#18](https://github.com/phillip9933/Kura/issues/18), [#19](https://github.com/phillip9933/Kura/issues/19) and [PR checks](https://github.com/phillip9933/Kura/pull/14/checks) record subsequent complete-suite results and review. No future pass is asserted here. Tests use only the owned disposable emulator, real synthetic credential and prototype packages; no private signing or personal-phone tests. Stage remains Beta until all release gates pass. AI assistance was used.

The same four-journey selection passed a second consecutive run **4/4 in 144.563s** on the final shared helper: **eight executions, zero failures across the two focused runs**. Full regression and review remain separate gates.

The first corrected enhanced full run at 7af82bf completed with **72 passes / zero failures / four opt-in skips (76 total)** in 888.401s. Its saved complete transcript reports `OK (76 tests)`. The summary parser initially shadowed its expected case count with an APK SHA256; the original false summary is retained and the result was recalculated from the unchanged transcript and exact counts. Fresh security/database/import instrumentation passed **7 + 12 + 29 = 48**; separately enabled cold-process phases, synthetic screenshot and empty-vault trace passed **4/4**. Exact-head CI passed four configurations, **236 JVM executions**, no failures/errors/skips, lint warnings 57/58/61/61 and no errors/fatals.

The offline full run still reproduced the version-subtitle assertion: its captured hierarchy shows the About title clipped at `[158,1847][590,1866]`, with the subtitle below the exposed viewport. Thus frame synchronization alone was insufficient for this case; earlier focused/enhanced passes did not erase it. The scroll-reachability journey now explicitly requires the settings scroll container and a downward scroll to expose the actual version-code subtitle, then retains its visible-version assertion and failure dump. [Issue #18](https://github.com/phillip9933/Kura/issues/18) records subsequent focused/full verification. No production code changed.

That offline run completed **63 passes / one failure / four opt-in skips (68 total)** in 844.781s; the subtitle assertion was the sole failure. An intermediate focused attempt stopped on UiScrollable.scrollIntoView returning false, before the original assertion; it is retained. The direct bounded scroll then passed the complete focused settings/version/secure-window journey **1/1 in 24.901s**. Both complete configurations and optimized acceptance are being reverified; linked issues record final results rather than asserting future passes.


## PR #14 upgrade and installer acceptance — 6 November 2026

The owner approved fixing and publishing the reproduced upstream cache-recovery defect. Kura now pins **android-updater 0.2.2-beta.1**, source `da2c9f30f3a9f4199d0e9cc1428be4519c954aa1`. This is a candidate SDK release; latest stable 0.2.1 and previously published Kura beta bytes remain unchanged. SDK quality CI passed 56 host tests, nine exporter tests and four dependency-rejection checks; native CI passed **15/15 on API 26 and 15/15 on API 35**, no failures/errors/skips. Published bundle/source/AAR checksums and all seven inner pins were independently verified.

Fresh owned API 37 / 16 KiB prototype acceptance passed:

| Check | Result |
| --- | --- |
| Stable-source 119 → current debug 120 | Seed 1/1, retained-data verification 1/1 |
| Actual Android installer 120 → nondebuggable validation 121 | Real Update confirmation; UID retained; post-install wallet verification 1/1 and installed-version/obsolete-ready cleanup 1/1 |
| Published-beta-source 120 phone-only → validation 121 | Seed 1/1, cold retained-data verification 1/1; phone-only choice retained |
| APK refusal/interrupted transfer/active cancellation | 1/1 phase containing five checked subscenarios |
| Corrupted-cache recovery | 1/1 with new SDK; original SDK 0.2.1 STORAGE failure retained |
| Malformed opt-in state, actual process recreation | Seed 1/1, cold verification 1/1 |
| Real offline request | 1/1; actual active-network absence, NETWORK error, no ready/install |
| Ready download/process recreation/FileProvider grant | Seed 1/1, cold verification 1/1 |
| Normal prototype installation prohibition | 1/1, effective disabled ancestor checked |
| Real unknown-source denial/back, grant/return, explicit install and Cancel | 1/1; return never auto-launched installer; real app reauthentication retained |

Each upgrade retained synthetic wallet rows, leading-zero `00009901`, encrypted image decoded dimensions/pixels and currency preferences. No uninstall or data clear occurred between any scenario's seed and verification. Actual installer acceptance used Android's Update button, not ADB installation for that hop. Validation 121 is an external nonshipping metadata override, standard development signer, prototype package and nondebuggable/nonminified profile build. It is not a production-identity, optimized/R8 or physical-device upgrade claim.

Build the additional harness only with `-PdirectUpdates=true -PcardScanner=true -PinstallerAcceptance=true :app:assembleDebugAndroidTest`. `InstallerAcceptanceTest` requires an explicit `installerPhase`, generated fixtures under prototype external files and its package/emulator/16 KiB guards. Never run it on a personal phone or production package. The default suite excludes this source set; CI additionally compiles it. Normal app entry retains the prototype installation prohibition.

Failed attempts remain preserved under `<project>/outputs/pr14-upgrade-installer-20261105/`: incorrect fixture version metadata, cancellation-before-active-job timing, misunderstanding checkNow's attempted-request return value, disabled-text versus disabled-parent semantics, cancellation window dismissal and installer completion-sheet synchronization. An obsolete UID observer aborted before confirmation; its dependent checks are not passes. Upgrade entry initially encountered a legitimate newer-version offer after PIN entry; the helper now chooses Later and waits for the vault. No credential bypass, verifier suppression or weakened data assertion was introduced. A restricted Gradle cache lock prevented one build from starting; the permitted toolchain build succeeded.

The current enhanced full run and exact-head CI are reported in [PR #14](https://github.com/phillip9933/Kura/pull/14/checks) and the [structured evidence](evidence/pr14-upgrade-installer-20261106.json). Prior enhanced 72-pass and offline 64-pass full runs remain historical evidence. Offline runtime/core sources are unchanged; no fresh offline full-run claim is made in this pass. Production-identity retained-data/installer acceptance, physical photographed-card/arm64/hardware-key checks, dedicated scanner lifecycle/source-conflict checks, independent approval and final stable artifacts remain open. No spare phone is available. Stage remains Beta; no Kura release or signing was performed. AI assistance was used.

## APK-only publishing and update discovery — 6 November 2026

The owner retired AAB downloads for 2.0/2.1/2.2-beta.1. Original AAB/checksum files were preserved outside Git, release checksum lists and notes were updated, and all APK digests/tags remained unchanged. The unsigned release-candidate workflow now builds/verifies the combined scanner/direct-update APK only. Historical AAB verification remains historical evidence.

App Version & Trademark now also exposes the existing App updates entry. Enhanced debug/test build and lint passed (52s, 278 tasks:24 executed/254 up-to-date); the added direct-only UI acceptance passed **1/1 in19.810s**, using actual synthetic credential entry, Settings→App updates→Check for updates and version dialog→App updates→Check for updates. The test asserts automatic checks remain off and does not contact the feed. The first emulator setup attempt failed to obtain a hierarchy under locked external storage; reading the actual keypad from device-local temporary storage allowed setup. This is debug UI evidence, not optimized/public-beta reproduction. No fresh full-suite or optimized-runtime result is claimed for this follow-up.

The actual public beta APK SHA2562a05e2d60b4eb3751361d9d27b1ba85f174a6fb4977b48ab2493a8d5abf894ed was inspected: version120, direct-update permissions/Application and updater strings are present. This does not explain the owner-reported missing control; [#22](https://github.com/phillip9933/Kura/issues/22) remains open for optimized reproduction. Owner-approved reports [#21](https://github.com/phillip9933/Kura/issues/21) and [#23](https://github.com/phillip9933/Kura/issues/23) retain the direct-restore and intermittent launch findings. The owner restored through GitHub2.0 and then manually upgraded to2.1/2.2 beta successfully; that is reported evidence, not an agent-observed production-device test. No launch-policy change was made without a controlled reproduction. AI assisted changes, testing and triage.

## Unified card crop dialog — 6 November 2026

Enhanced front/back image editing now uses one Material crop dialog. Detection initializes the same shaded four-corner mask used for drag and labelled fine-tuning; the separate manual-crop route is removed only from this enhanced path. Reset/full-image/rotation/fine-tune actions are outlined; Use crop is the primary action outside the scrollable content. The OCR/image-only/review/save and session-owned cleanup contracts are preserved. Base offline image cropping is unchanged.

Final enhanced debug/test assembly and lint passed in42s (278 tasks,16 executed/262 up-to-date). Fresh API37/16KiB synthetic scanner instrumentation passed **4/4 in29.059s**: English edge detection/OCR, Japanese model, cancellation workspace cleanup, and the full crop/image-only/autofill/encrypted-save journey. The expanded journey verifies collapsed fine-tuning, one-screen controls, full-image/reset, actual handle dragging and its changed-mask feedback, then preserves every original no-save/cancel/leading-zero/encrypted-save assertion. Default offline app/test assembly passed separately; no fresh offline runtime/full-app suite or physical-camera accuracy claim.

The first run had three passes and one UI assertion failure because expanded precision sliders were below the viewport. Bounded dialog scrolling retains the slider assertion; an intermediate four-case run passed in26.183s. The final rerun additionally verifies drag feedback against the exact rebuilt source. Failed and intermediate logs remain in `<project>/outputs/crop-ux-20261106/`. Opt-in screenshot capture uses generated card content only and restores every affected window's original secure flags in finally; production screenshot policy is unchanged. The synthetic preview was visually inspected. No signing, release or personal-device tests. Required review, CI and broader device/font qualification remain separate gates. AI assisted implementation and verification.

## Preview-first photo rotation — 6 November 2026

Rotation now precedes edge detection and crop. The picker previews four presets and a custom 0.1° slider; Cancel retains the original mask, while Apply derives an oriented image from the original photo, updates the crop preview and resets detection. Cropping/OCR use that same oriented raster with no post-crop turn. Owned encoded and bitmap buffers are bounded and cleared; an outer ownership guard also covers cancellation on dispatch back to the caller.

Enhanced app/test build and lint passed in54s (278 tasks:14 executed/264 up-to-date). Fresh disposable API37/16KiB acceptance passed **8/8 in39.986s**, comprising the four prior scanner/OCR journeys and four new rotation cases. Pixel/dimension checks cover all quarter turns, custom bounds, original-input preservation, scanner memory limits and invalid input refusal. The UI journey additionally covers live presets, cancel, applied270° crop view and custom slider, retaining crop/OCR/cancel/encrypted-save assertions. Synthetic picker/crop screenshots were visually inspected, using the guarded opt-in flag-restoring capture helper.

An initial eight-case run passed in42.911s. A subsequent cleanup refinement had a syntax-only build failure before tests; its log is retained. Final corrected source was rebuilt and reran all eight successfully. Prior full-app/CI results remain historical; no full-suite, optimized-runtime or physical-camera qualification is inferred from these focused results. Base offline sources are unchanged and previous offline compilation is reused. Evidence: [rotation](evidence/crop-rotation-20261106.json); remaining closeout plan lives in [release #13](https://github.com/phillip9933/Kura/issues/13). No personal data, production signing or new app release. AI assisted implementation and verification.

## Updater discovery and manual check — 6 November 2026

On the owned API37/16KiB emulator, the external `UpdaterDiscoveryTest` used only public UI and PackageManager against the nondebuggable R8 benchmark prototype. Locked, Settings/About and version-dialog access passed **1/1 before** (26.019s) and **1/1 after** (25.689s) the full-width updater-row change. No historical production-phone disappearance was reproduced.

The explicit `AppUpdateSettingsTest -e checkPublicUpdateSource true` passed **1/1** (17.931s), including a real foreground GitHub metadata check, a newer last-check timestamp, and automatic checks/downloads still off. The ordinary six-case updater integration/popup/settings selection passed **6/6**, zero failures (28.524s). Enhanced optimized/debug build and lint passed in143s:48 executed/496 up-to-date tasks. Final paired full suites and offline qualification are not implied by these focused results.

Initial package-visibility and authentication-cancelled-dialog test setup failures, emulator termination during the manual check, and unstarted restart attempts are preserved in project outputs/issue-closeout-20261106. The benchmark query is restricted to the prototype benchmark package; no production permission or authentication change. [Structured source hashes and limits](evidence/updater-discovery-20261106.json). AI assisted implementation and verification.

## Launch preference ordering correction — 6 November 2026

`LaunchSelectionTest` reproduced #23 before the fix: hold the operation mutex during real credential acceptance, and a fresh Activity/ViewModel publishes Unlocked with Passes while encrypted settings request Cards. The expected behavioral assertion failed in11.720s. `VaultController` now reads and publishes launch presentation while Opening, before Unlocked is observable. Its opener retains failure/cancellation cleanup ownership until returning handles to the session coordinator.

The final strengthened regression passes **1/1 in42.130s**: Cards/Passes/Identity, hidden Cards→Passes fallback, only-Identity fallback, and actual Cards→Identity navigation retained through a default-setting change and refresh. An earlier narrower pass was1/1 in35.170s. Separate real force-stop prepare/verify phases pass **12/12** for all three defaults under app authentication and phone-only unlock. No master-key injection or authentication bypass. The optional test wrapper is removed after each phone-only verification. Enhanced paired debug build/lint passes44s,14 executed/264 up-to-date tasks. Full/older-platform/optimized qualification remains separate. [Exact source hashes and phase results](evidence/launch-selection-20261106.json). AI assisted diagnosis and implementation.

## Scanner closeout and draft ownership — 6 November 2026

Final focused development pair passes **17/17 on API26** (113.387s) and **17/17 on API37/16KiB** (120.660s), zero failures/skips. Selection: LaunchSelectionTest, ImageImportRequestTest, CardScanSafetyTest, CardScannerIntegrationTest and CardRotationTest. Coverage includes all defaults/fallback/navigation, native blur/light/blank and typed-input failure, real system-camera retake/cancel, front/back pixel association and encrypted backup/staging, late correction/barcode preservation, lock during OCR, English/Japanese extraction and crop/rotation budgets. These are synthetic prototype checks, not physical/production acceptance.

Two production defects were reproduced and fixed: preview-wide drag capture swallowed scrolling outside handles, leaving a452px-clipped preview where540px was available; conditional pointer-down ownership restores scrolling and real handle editing on both APIs. A queued encrypted photo could attach to a replacement draft (baseline failed7.078s). Form lifetimes now survive schema/object reuse as distinct generations; draft ownership is published on Main after validation and unadopted files are deleted. The strengthened same-object queue/result selection passes3/3 (7.316s). Image confirmation remains separate from record Save.

Prior intermediate results remain scoped: modern14/14 (79.868s), then14/14 with optical blur/dispatcher ownership (78.973s); typed input/quality2/2 (0.460s); pre-final-lifetime full selections16/16 each (API26 105.799s / API37 117.732s). A real OCR process-stop/relaunch left five temporary files plus a synthetic abandoned capture and passed cleanup/no-publication1/1 (6.387s) before the final lifetime extension; final beta qualification will repeat the opt-in. Its deliberately interrupted prepare phase is not a pass.

All failed setup/fixture runs and actual bug reproductions are retained under project outputs/issue-closeout-20261106: initial immutable-bitmap cleanup, missed fast OCR observation, unsuitable resizing-only blur fixture, Android8 Camera’s location runtime/tagging prompts, Google package versus Android resource namespace, clipped crop-drag assertion, and queued old-draft image attachment. The Camera prompt is declined and tagging disabled; Kura gains no location permission. No assertions were removed to obtain the final results. Enhanced build/lint passes60s,34 executed/244 up-to-date tasks. [Structured source/artifact hashes, exact scope and remaining gates](evidence/scanner-safety-20261106.json). Full paired regression, final beta artifacts, independent review and physical photographed-card/arm64/mobile resource checks remain separate. AI assisted diagnosis, implementation and verification.


### Beta 2 qualification fixture corrections — 6 November 2026

Final beta application source remains b07ef53. The first complete enhanced attempt did not pass: two older fixtures called draft-image persistence without initializing a current form, and a later Activity/Compose dialog transition crashed with BadTokenException. The new stale-draft refusal correctly rejects ownerless work. The saved-logo setup now uses encrypted saved-record metadata; the parity/identity fixtures explicitly initialize and clear their synthetic form lifetime. No production guard or acceptance assertion was removed. The fresh offline emulator's first phone-unlock journey missed the keyguard's first swipe after wake; following authorization failures led to an explicitly aborted run. The test now waits/retries only the real system keyguard gesture before entering the unchanged synthetic PIN and asserting phone-key refusal/automatic reopen. All failed/crashed/aborted attempts remain preserved, not counted as full passes. [Structured record](evidence/beta2-fixture-corrections-20261106.json).

Enhanced and offline corrected app/test compilation both pass (49s each,42 executed/127 up-to-date tasks). Complete reruns and final beta evidence will be recorded separately after they finish. The final API26 retained-data/phone-lock/encrypted-backup/authentication-reenable journey passed1/1 in67.356s; fresh core security7/database12/import29 pass48/48 on the owned API37/16KiB emulator. No production-package or personal-phone test is claimed. AI assisted fixture diagnosis and synchronization.

### Target-owned installer fixtures — 6 November 2026

The final beta2 rehearsal exposed a test-staging failure on the fresh API37/16KiB emulator: adb created the nested installer-fixtures directory as shell with mode0770, so the target app could not read its generated APKs. This failed before SDK verification. The opt-in harness now has an explicit `installerPhase=prepare-fixtures` phase that creates the directory through the target Context, asserts its UID ownership and verifies read/write access. This follows [Android app-specific storage guidance](https://developer.android.com/training/data-storage/app-specific); it adds no storage permission and changes no application APK or verification guard. Compile with `-PcardScanner=true -PdirectUpdates=true -PinstallerAcceptance=true :app:assembleDebugAndroidTest`, run that guarded phase before staging generated APKs, then run the existing installer phases. The preparation phase passes1/1 in0.061s. The earlier EACCES result, fresh-emulator blank-keyguard attempt and host UID-observer format correction remain preserved in project outputs.

Installer acceptance takes optional `baseVersionCode` (defaulting to the host BuildConfig version) and `payloadVersion`. This rehearsal uses host121 and generated candidate122 /2.2.0-validation.1, never the production package. Recovering a corrupt cache and checking a fresh offline failure are separate scenarios: only the checksum-matched synthetic public APK is removed between them, with its digest recorded. Earlier119→121 app-authenticated and120→121 phone-only retained-data phases pass with UID, encrypted image, records, leading zeros and preferences retained. Remaining phases and complete regressions are reported only after they finish. AI assisted test-fixture correction; no security assertion was removed.


### Final beta 2 regression observers — 6 November 2026

Two complete, streamed reruns finished with one failure each: enhanced **86 passed / 1 failed / 4 opt-in skips / 91 total**, 1080.692s; offline **66 passed / 1 failed / 4 skips / 71 total**, 951.800s. These are not passing full suites. Earlier environment-interrupted attempts stopped at 51 and 53 passes without an assertion failure; both host workers/emulators were authoritatively missing, and their partial evidence is preserved. Detached workers now own subsequent runs.

The settings regression clicked a partly visible semantic parent row at `(36,1841)-(1044,1866)`, then searched for its offscreen child title on return. The saved failure hierarchy proves the same parent row retained exactly those bounds. The corrected assertion checks that same parent and exact unchanged bounds, with no corrective scroll or application change. Its stronger diagnostic initially failed with the old child selector; the corrected selection passes. The attachment read failed because its token had been revoked before the AppFuse `onGetSize` callback (`Attachment closed`, ENOENT). The old headless fixture had no foreground vault lifetime; pending Activity/session cleanup can revoke global leases. The fixture now uses real credential authentication and an active foreground vault, then clears/locks in finally. Provider revocation, seekable reads, zeroing and expired-URI refusal remain unchanged. Both focused corrected journeys pass **2/2 in 33.033s**; no security check or application code was changed. [Exact evidence](evidence/beta2-regression-corrections-20261106.json).

The isolated installer selection now passes **18 phase executions**: stable119→121 app-authenticated and beta120→121 phone-only retained upgrades, target-owned fixture preparation, package/signer/debug refusal, interrupted/cancelled transfer, corrupt-cache recovery, malformed opt-in recreation, real offline failure, ready-state recreation, normal prototype installation prohibition, real install-source permission denial/grant/return/cancel, actual Android121→122 update, stale-offer clearing and retained wallet verification after real cold reauthentication. Installed bytes match the generated candidate; UID, encrypted media/records, leading zeros and preferences remain intact. Android showed App updated; Done must be dismissed before the final app authentication check. The premature verification while the installer was still foreground is preserved as a failed attempt. Install permission was restored to deny. These are synthetic prototype phase executions, not production-package/physical acceptance. Final paired regressions and final-source CI will be recorded after they complete. AI assisted test diagnosis and verification.


### Enhanced full pass and final export/cold observers — 6 November 2026

At74480f9, the complete enhanced selection passes **87 active / 0 failed / 4 opt-in skips / 91 total**,1055.771s, with exact installed app/test hashes and a complete transcript. The default offline selection completes with **66 passed / 1 failed / 4 skips / 71 total**,928.976s: encrypted-pass export could not find its action. The saved hierarchy is still the filtered grid, proving its preceding direct raw tap had not opened the detail. That tap now uses the established frame-settled helper and asserts the pass-detail menu exists before export. All backup/password/SAF/restore/data assertions remain. The first cold-process verify also observed default UI memory before Compose initialization, while loaded presentation already correctly requested Cards; the ordinary regression already waited for initialization, but the cold branch did not. It now waits only for first initialization, never for a matching selected value, and retains both immediate presentation-order and exact first-selected-tab assertions.

Corrected offline export passes1/1 in58.124s; cold prepare/force-stop/verify passes2/2 in7.668s/6.903s. Both enhanced/offline pair builds pass (50s/46s respectively). These changes affect two test methods only; application/debug/optimized bytes remain unchanged. Retain the complete enhanced74480f9 pass, repeat its affected backup journey and all cold phases on the final harness, and rerun the complete offline selection. Do not relabel the earlier failed offline run as a pass or claim the enhanced full selection executed at the subsequent test-only commit. [Scoped evidence](evidence/beta2-export-cold-observers-20261106.json). The final real OCR interruption cleanup also passes1/1 in6.948s with four orphaned workspace files plus an abandoned synthetic capture before relaunch; file count varies with interruption timing, and all owned temporary data is removed without publishing a draft. Earlier five-file observations remain historical. AI assisted observation and test synchronization; no application/security change or assertion removal.
