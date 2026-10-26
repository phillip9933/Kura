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

Only the explicit direct-update build may use networking for public GitHub app updates; never pass vault data to it. Do not add other network access or telemetry. Treat imports, document-provider streams, image metadata and backup contents as untrusted. Keep SQL identifiers allowlisted and values bound. Preserve application ID, signing continuity and legacy data compatibility unless a separately reviewed migration changes them.

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

Contributions are licensed under [AGPL-3.0](LICENSE). Follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Public roadmap and issue management

Follow the [public roadmap](https://github.com/users/phillip9933/projects/2) for release plans and the work board for individual tasks. This is the shared convention across maintained repositories. Issues, milestones and Project fields are the live source of truth; do not duplicate the live task list in ROADMAP.md or other Markdown backlogs.

### Issue type and priority

Every issue has exactly one purpose label: `type:bug` (existing behavior is broken), `type:feature` (new or changed user-facing behavior), `type:maintenance` (refactoring, dependencies, CI, performance or debt), `type:docs` (documentation only), or `type:release` (release tracking). Choose the purpose: a bug requiring refactoring is still a bug.

After triage, use exactly one priority label: `priority:urgent` (immediate action, such as active data loss or serious security impact), `priority:high` (next work; significant impact), `priority:normal` (default accepted work), or `priority:low` (worthwhile but not currently important). Priority means work order, not a promised date. Keep suspected security vulnerabilities out of public issues until safe disclosure; use the repository’s private security-reporting channel.

### Work status

Use the Project’s **Status** field, never status labels: **Inbox → Ready → In progress → Done**, with **Blocked** available when work cannot proceed. Inbox needs assessment; Ready is accepted and sufficiently defined; In progress is active work. A Blocked issue records its dependency and what unblocks it. Done means its acceptance criteria are met with verification evidence, not necessarily shipped. Close rejected or duplicate work with the appropriate reason; do not mark it delivered or automatically move every closed issue to Done.

### Release targets

Repository milestones identify intended stable versions, with a platform prefix where useful (for example, Android or Desktop). Assign a milestone only when inclusion is intended; unscheduled ideas have none. Alpha, beta and RC builds stay under the intended stable-version milestone. Cross-repository release trackers link the relevant repository milestones. Estimated start/target dates are planning aids, not commitments; leave unknown dates unset.

Multi-platform repositories use `platform:android`, `platform:desktop` or `platform:shared`. Do not add redundant platform labels to single-platform repositories.

### Release maturity and promotion gates

Only `type:release` issues use the Project’s separate **Stage** field: **Planned → Alpha → Beta → RC → Stable**. Each release has one named owner.

- **Planned → Alpha:** record scope, acceptance criteria and supported platforms; an installable build exists (a library supplies a consumable bundle and installable test host).
- **Alpha → Beta:** intended release features are implemented, core workflows pass, and remaining limitations are documented.
- **Beta → RC:** release-blocking bugs are resolved; required regression, upgrade/migration and data-safety tests pass; freeze scope.
- **RC → Stable:** validate the final candidate; prepare release notes and known limitations; verify signed/checksummed artifacts where applicable; publish the release.

For wallets and cloud clients, explicitly test relevant encryption, storage, backup/restore and sync behavior. Promote on evidence, never because a date arrived. Stable means the release was actually published. If new feature scope enters after RC, return the release to Beta and repeat affected checks. GitHub displays these fields and checklists; it does not automatically enforce release gates.

### Issue contents and public views

Every issue records its goal/problem, scope and explicit exclusions, a **Done when** acceptance checklist, dependencies (or None), and verification results/links before closure. New reports may say verification is pending. Release trackers additionally name the target version, one owner, included issues, promotion-gate checklists, remaining blockers and build/release links.

The **Roadmap** view contains only `type:release` items on a timeline, with Stage and estimated start/target dates. The **Work board** contains bugs, features, maintenance and docs grouped by Status. Keep statuses and stages current as work changes. Preserve report authors’ original details when adding triage structure, and disclose material AI assistance in implementation and verification summaries.
