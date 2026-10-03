# Testing

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
