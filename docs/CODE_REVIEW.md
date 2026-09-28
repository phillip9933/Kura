# Native code and maintainability review — 2026-09-29

Scope: the repository after native-only consolidation, beginning at local commit `a1250e6`. This is a repository-wide engineering review of use, ownership, build configuration and maintainability, complementing the [security review](SECURITY_REVIEW.md). It is not a formal proof of reachability, an independent audit, or a measured performance certification.

## Method and coverage

The initial consolidation inventory contained 60 production Kotlin files and 42 Kotlin test files across eight modules. The review covered the tracked source/test inventory, Gradle modules, manifests, resources, scripts and documentation. Remaining file-by-file formatting and localization debt is listed below; this review does not claim every line was refactored. Search declarations and callers across production/tests, inspect candidate chains manually, and compare resource references with lint. Follow host/controller/session, presentation/editing/ordering, schema/storage, codec/staging and platform-provider boundaries. Compile debug/test/benchmark and optimized release variants, run relevant tests, and include Android library modules in app lint. The framework-free JVM `core:model` is covered by compiler/JVM tests; Android lint treats it as external.

Reference counts only identify candidates. Android callbacks, providers, generated Room entry points, baseline profiles and serializer/schema contracts have implicit consumers. Public test seams are not automatically dead production code. The review preserves compatibility readers and data identifiers; historical words alone are not grounds for removal. No dedicated whole-program dead-code analysis or full dependency-use plugin was run.

## Findings and changes

| ID | Priority | Finding and disposition |
| --- | --- | --- |
| CLEAN-01 | Medium | `RecordEditor` and `PassFieldEditor` had no callers after the typed editor/read-only imported-pass changes. Removed both and the generic editor's private scroll helper. Retained structured-field conversion/validation because the active editor and compatibility tests still use them. |
| CLEAN-02 | Medium | The old reorder chain remained wired through callbacks that detail screens never invoke. Removed unused `onLock`, `onAdd`, `onMove`, `onReorder` screen plumbing; controller `add`, `move`, `reorder`; and store `moveRecord`. The sole test of the old reorder method now checks the live `saveOrder` path, including persistence after refresh. Existing sort/reorder UI still saves through `saveOrder`. |
| CLEAN-03 | Low | Unreferenced `kura_logo.png` occupied 2,533,401 source bytes; the app uses `kura_mark.xml`. Removed it after checking source, XML and dynamic lookup usage. Release resource shrinking may already have excluded it; no measured release-APK saving is claimed. Removed the unused Compose tooling-preview dependency and 23 unused explicit imports. |
| CLEAN-04 | Low | `VaultItem` was embedded in the large screen file, obscuring its shared role. Moved it unchanged to its own file. Cached section filtering/sorting with all relevant inputs so unrelated recompositions need not repeat this work. No quantified frame-time improvement is claimed. |
| CLEAN-05 | Medium | App-only lint omitted local library findings. Enabled `checkDependencies` for app lint. Added concise agent instructions, a documentation index, ownership/change maps, editor defaults and AI-assistance disclosure guidance. No duplicate vendor-specific agent instruction files were added. |
| CLEAN-06 | Low | Import confirmation still told users to edit imported-pass sections although imported passes are read-only. Corrected the message. |
| CLEAN-07 | Low | Retained generated caches referenced the former `native/` path; deleting an obsolete generated Compose class exposed a dex-transform failure. A clean root build regenerates the outputs. This was a local generated-state issue, not a source path to retain. |
| CLEAN-08 | Low | Resolved the currency formatter's nullable receiver warning by retaining the validated currency locally, and switched sort icons to their supported auto-mirrored variants. |

## What remains used

- Every declared module participates in app build, compatibility/testing or performance tooling. The project dependency graph has no cycles; see [Architecture](ARCHITECTURE.md#dependency-direction).
- Legacy crypto, secure-storage aliases, XML preferences and `app_flutter` filenames remain reachable from migration paths and tests. `payments` settings keys remain serialized compatibility keys. They are not a Flutter runtime or build dependency.
- Room entities/DAOs, committed schema JSON, manifest callbacks/providers and launcher/profile resources remain contracts even when ordinary source references are sparse.
- Structured JSON helpers preserve imported/older fields, including unknown metadata. Removing the old editing UI does not make these helpers redundant.
- The debug fixture entry point is gated by `BuildConfig.PROTOTYPE`; benchmark/profile variants and profiles remain deliberate tooling.
- The production declaration scan after removals found only framework callbacks/comment matches among single-occurrence function/class candidates. This is a heuristic result, not a guarantee that no dead parameters or paths remain.

## Maintainability follow-ups

These are open findings, not completed refactors or reasons to change data formats during cleanup.

1. **Medium — host orchestration remains concentrated.** `MainActivity` still combines authentication, activity-result registration, imports, password prompts and crop presentation. Extract crop/dialog presentation and then import-flow owners in separate behavior-preserving changes. Keep launcher registration and authentication continuations attached to their lifecycle owner. The wide `VaultScreen` callback interface should become a few cohesive event/state interfaces as those owners stabilize; do not introduce one opaque global service.
2. **Medium — module boundaries are broader than ideal.** Core modules share `app.kura.nativecore`, and several Gradle dependencies export transitive implementations via `api`. `core:import` also owns the vault repository and preference migration. Tighten boundaries when changing these owners, with explicit dependency declarations and schema/restore validation. A package-wide rename now would also affect generated contracts and profiles.
3. **Medium — device-test helpers are duplicated.** Authentication, scrolling, screenshots and fixture cleanup repeat across app journeys. Consolidate these after diagnosing the existing `SettingsMediaJourneyTest` failure; do not mask it by weakening assertions or removing the journey. Explicitly separate pure model/format assertions from device interaction as test infrastructure matures. The lower-level `VaultStore.deletePass` is still used by fixture/restore tests; prefer the live `deleteRecord` path when consolidating cleanup helpers, then reassess removing that duplicate API.
4. **Low — formatting is inconsistent.** Much of the prototype uses semicolon-heavy compact Kotlin/Gradle and wildcard imports. `.editorconfig` establishes editor defaults, not automated formatting enforcement. Expand touched functions and introduce a formatter in a dedicated mechanical change if desired, without mixing it with cryptographic/lifecycle changes.
5. **Medium — dependency maintenance remains outstanding.** Version declarations repeat across modules; a version catalog would reduce drift. Refresh security-sensitive dependencies and review advisories separately, as recorded in [Security review](SECURITY_REVIEW.md). This cleanup changes no cryptographic dependency version or database format.
6. **Low — localization/accessibility still need a focused pass.** Much UI text is hardcoded in Compose. Centralize user-facing strings before adding languages; validate large fonts, semantics, RTL and contrast with device checks. Do not equate successful compilation with accessibility coverage.

The app still retains recovery generations/shared media intentionally, and loads vault rows into session memory. Large-vault performance and a deliberate retention policy need measured product decisions; cleanup should not silently delete recovery data or promise complete memory erasure.

## Validation

Observed validation for the final cleanup source:

| Check | Result |
| --- | --- |
| Clean root build | Passed; 4m 56s, regenerated relocated outputs |
| Final incremental build after currency/icon warning fixes | Passed; 2m 8s |
| JVM suites | 17 model + 34 import + 1 constrained-heap execution passed; no failures/skips |
| Focused API 37 / 16 KiB emulator instrumentation | 22/22 passed in 323.397 seconds |
| Android lint including library modules | 0 errors, 58 warnings; no unused-resource warning |
| Optimized unsigned release and packaging verifier | Passed; release identity/offline manifest and 16 KiB alignment preserved |
| Benchmark instrumentation APK | Compiled in clean build; benchmark journeys not executed |
| Local Markdown links / diff whitespace | Passed |

The 22 Android cases are `NativePresentationTest` (3), `BarcodeParityTest` (3), `ParityJourneyTest` (3), `OrganizationJourneyTest` (11) and `PendingSecurityTest` (2). They cover live order persistence, reversible name/date sorting, drag/tap/cancel behavior, favorites, editor/custom fields, images/attachments, automatic backup retention, barcode decoding/encoding and lock cleanup. This is a focused selection, not a full app-suite rerun.

The warning count increased from the previous 29 because lint now includes Android library modules. The final 58 comprise 37 dependency-update suggestions, 12 KTX suggestions, five launcher-icon warnings, two platform EXIF suggestions, one target-API notice and one Compose modifier-default warning. No warnings were globally suppressed. The JVM-only model module is not analyzed by Android lint.

Evidence is in ignored `verification/code-review-{clean-build,final-build}.log`, `code-review-app-tests.txt` and `code-review-packaging.txt`, plus module JUnit XML and `app/build/reports/lint-results-debug.xml`. The first incremental attempt failed on a relocated dex cache; the successful clean build above supersedes it. No new performance benchmark or physical-device result is claimed.

The earlier `SettingsMediaJourneyTest` failure at `Edit Back image` was not rerun or resolved in this pass. Keep that and the dependency/device/signing release gates visible in [Testing](TESTING.md) and [Security review](SECURITY_REVIEW.md). No remote CI, push, release signing or publication was performed. The previously shared numbered APK was not overwritten; this review's debug build is under `app/build/outputs/apk/debug/`.

## Contributor guidance reference

The separation of a human contributor guide, clear verification expectations and an AI-assistance disclosure section follows the useful pattern in [RomM's contribution guide](https://github.com/rommapp/romm/blob/master/CONTRIBUTING.md). Kura's actual build, security and agent instructions are written for this repository; no RomM-specific tooling or policies were copied into the Android build.

## Backup-only cleanup and dependency follow-up

Automatic Flutter adoption, plugin-key/XML extraction, plaintext media copying and their obsolete tests were removed after backup import became the supported migration path. `VaultDatabases` now owns reopening native generations; Room schemas and database/backup compatibility remain unchanged. Column-format, schema, media and backup interoperability tests remain. `NativeVaultIsolationTest` covers ignoring old storage on fresh open/reset and preserving native secrets during explicit staged restore. Tests for removed XML/snapshot features are intentionally not counted as current coverage.

SQLCipher and Bouncy Castle updates require compile API 37.0, AGP 9.1.1, Gradle 9.3.1 and KSP 2.3.12. Android modules now use AGP's built-in Kotlin; minimum API 24 and target API 36 remain unchanged. Obsolete profile rules referencing the removed database opener were removed; this does not claim a regenerated performance profile. Follow-up evidence is recorded in [Testing](TESTING.md).

The follow-up passed 52 JVM executions, 6 security, 8 database and 27 import/storage device tests. The app full run was 59/60; its headless controller setup was corrected and the ordered 11-case recheck passed, including the previously failing media journey. These overlapping runs are documented separately in [Testing](TESTING.md). Final lint reports 0 errors / 56 warnings. No production lifecycle bypass or weakened assertion was introduced to obtain passing results.

The build-118 backup-image follow-up adds explicit incomplete-image recovery confirmation after the old writer's omitted pass assets were reproduced with synthetic fixtures. It retains strict staging by default and attachment/image integrity checks. See [Testing](TESTING.md#missing-backup-images--build-118) for the focused results and [Backup and migration](BACKUP_AND_MIGRATION.md) for user-visible recovery limits.
