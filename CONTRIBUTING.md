# Contributing to Kura

Kura is a Kotlin/Compose Android app. Start with [README](README.md), [Architecture](docs/ARCHITECTURE.md) and [Security](SECURITY.md).

## Development

1. Install JDK 21 and Android SDK platform 37.0/build-tools 36.0.0.
2. Set `ANDROID_HOME` or an ignored `local.properties` file.
3. Open the repository root in Android Studio or run `./gradlew :app:assembleDebug` (`gradlew.bat` on Windows).
4. Run the relevant checks in [Testing](docs/TESTING.md). Never run destructive fixtures against a personal device or production package.

I prioritize data safety, security, correctness and privacy, followed by usability, maintainability, simplicity, compatibility, extensibility, performance and architectural purity. Prefer small, understandable changes with clear ownership. Explain exceptions to defaults and preserve existing data and public contracts. Consider accessibility and localization when changing UI. These are review priorities, not a claim that every current implementation meets them.

Use small, focused changes. Follow Kotlin conventions and the surrounding code. Prefer standard Material 3 components, lifecycle-aware state, bounded background work and explicit ownership of sensitive buffers. No formatter is currently enforced by the build; do not run unrelated bulk formatting.

For performance testing, build `:app:assembleOptimized`. It uses release-style R8
optimization and the debug signing identity, with the same `app.kura.wallet.prototype`
package as debug, so it updates the local test installation. It is not debuggable
and is not a production release. Keep debug for debugger/in-process instrumentation
work; use the external `benchmark` driver on a disposable emulator for optimized
app journeys. Packaged baseline profiles guide Android's compilation; sideloading
an APK does not guarantee that compilation has completed immediately.

Do not add network access or telemetry. Treat imports, document-provider streams, image metadata and backup contents as untrusted. Keep SQL identifiers allowlisted and values bound. Preserve application ID, signing continuity and legacy data compatibility unless a separately reviewed migration changes them.

## Choose tests by change

Build from the repository root. Commands below use the Unix wrapper; substitute `./gradlew.bat` on Windows.

| Change | Start here | Validation |
| --- | --- | --- |
| Lock/session/background policy | `core/model/Session.kt`, `Lifecycle.kt`; Android binding in `core/security` | `:core:model:test`, security instrumentation, relevant app resume/authentication journeys |
| Room/SQLCipher/schema | `core/database` and its committed `schemas/` | Database compatibility instrumentation and staged-restore tests |
| PKPASS, backup or transfer | `core/import`; encrypted streams in `core/storage` | `:core:import:testDebugUnitTest :core:import:testLowMemory`, import instrumentation for media/restore changes |
| Cards, pass display, editors, sorting | `feature/vault`; Android callbacks in `app` | `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`, relevant app journeys |
| Permissions, dependencies, signing/build | Root/module Gradle, app manifests | Debug and unsigned release builds, lint, `python3 tools/verify-native-packaging.py`; inspect merged manifest |
| Documentation | Relevant canonical doc in `docs/` | Check commands/paths/links against source; no app suite needed for prose alone |

The paths in the table identify modules/classes, not shell file paths; the [architecture code map](docs/ARCHITECTURE.md#code-map) links to exact sources. [Testing](docs/TESTING.md) explains the full disposable-emulator runner and known failures. Tests should protect behavior or a meaningful failure boundary; do not add tests that merely duplicate an implementation.

## Structure and code style

Keep UI rendering in `feature/vault`, Android launchers in `app`, and import/storage behavior in the core modules. Module dependencies are currently acyclic. Do not expose a new implementation dependency with `api` merely to make a downstream compile error disappear; declare the actual owner/dependency deliberately. Package names and compatibility keys are stable even when they contain historical terminology.

Follow `.editorconfig` and normal Kotlin formatting for new or substantially edited code. Prefer small named functions and named arguments at long callback call sites. Avoid unrelated bulk reformatting of the older compact source. Android lint includes local library modules; no third-party formatter or dead-code analyzer is currently enforced. The compiler, reference searches and shrinker/lint each catch different things; none proves every unreferenced-looking declaration is removable.

For a larger structural change, describe the affected owner, retained behavior and test plan first. Keep behavior-changing work separate from mechanical movement where practical. Do not generate a new framework or abstraction for a single caller without a concrete benefit.

## AI usage

Disclose material AI assistance in the PR/commit summary: whether it was used for analysis, documentation, code or tests, and which checks you personally verified. You remain responsible for understanding the submitted change. Do not present generated test claims as observed results or share private vault data with an assistant. Coding agents should start with [AGENTS.md](AGENTS.md), then follow links only for the area being changed.

## Build troubleshooting

After relocating a checkout, Gradle incremental outputs can retain old absolute paths. If a dex transform reports a file outside the current module root, run `./gradlew clean` from this root and rebuild. Do not delete personal data, signing material or the Gradle wrapper to fix a generated-cache problem.

## Reports and pull requests

Report the app version/code, Android version, device, font scale, theme and steps to reproduce. Use synthetic cards/passes and redact personal data from screenshots and logs. Never attach passwords, vault backups or signing keys to public issues. See [Security](SECURITY.md) for vulnerability reporting.

PRs should explain the problem, final behavior, tests run and remaining limits. Update affected docs. CI runs JVM tests, lint and unsigned build checks; it does not replace emulator, physical-device or upgrade testing. The manual release-candidate workflow only uploads unsigned artifacts and does not publish.

The 2.0 source and signed GitHub release are authorized for publication. Store-console submissions remain separate; the user is updating the pending F-Droid submission.

Contributions are licensed under [GPL-3.0](LICENSE). Follow the [Code of Conduct](CODE_OF_CONDUCT.md).
