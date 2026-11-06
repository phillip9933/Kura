# Working on Kura

Kura is an offline Kotlin/Jetpack Compose Android wallet. Read [README](README.md) for setup, [Architecture](docs/ARCHITECTURE.md) for ownership and [Contributing](CONTRIBUTING.md) for the change/test map. This file is the shared agent entry point; do not create competing copies of these instructions.

## Boundaries

- Source publication, a signed 2.0 GitHub release (APK/AAB), its version tag and retirement of old downloads were explicitly authorized on 2026-09-29. Store-console submission and messages on the user's behalf remain separate actions requiring authorization and access.
- Release application ID is `app.kura.wallet`; debug is `app.kura.wallet.prototype`. Preserve signing identity, database schemas, backup formats and Keystore aliases. Historical `Flutter`/`payments` strings can be compatibility keys, not dead code.
- The default offline build must not request INTERNET. The owner authorized an optional direct-update build on 2026-10-24: GitHub release metadata/APK traffic only, isolated from vault data, with opt-in checks and foreground user-confirmed installation. Never add analytics or telemetry or log keys, passwords, personal records or image contents. See docs/APP-UPDATES.md.
- Never read or commit private signing material/personal backups to aid a code review. Use synthetic fixtures. Ignored files are not permission to delete them.
- Device suites are destructive to test data. Use only a disposable emulator, never a personal phone or production package. The documented runner expects 16 KiB pages and a synthetic credential; see [Testing](docs/TESTING.md).

## Find the owner before changing code

- `app`: Android host, activity results, authentication, session-owned orchestration.
- `feature/vault`: Compose presentation, typed editors, classification, sorting and preferences.
- `core/model`: framework-free session state, generations, secret ownership and lifecycle policy.
- `core/security`, `core/database`, `core/storage`: platform security, schema-compatible persistence and encrypted I/O.
- `core/import`: bounded codecs, staged restore/publication and vault operations.
- `benchmark`: disposable-device performance/profile harness, not application code.

Use the [code map](docs/ARCHITECTURE.md#code-map) for entry points. Search tracked source first; avoid dumping generated build outputs, large profiles or historical verification logs into context unless the task needs them. Do not add database/filesystem operations to Composables or generate barcodes during recomposition. Keep cancellation and secret ownership explicit: rethrow cancellation, wipe owned mutable arrays in `finally`, and never persist decrypted drafts in saved state. Do not acquire vault locks from inside session-owned work.

## Change and verify

1. Inspect callers, tests, manifests/resources, generated Room contracts and compatibility paths before deleting apparently unused code. Single-reference scans are candidates, not proof; callbacks and generated entry points have implicit callers.
2. Make a focused change. Avoid unrelated mass formatting, dependency upgrades or schema migrations. Prefer named arguments for long callback APIs. Follow `.editorconfig`; compressed older code should be expanded when substantively edited.
3. Run the relevant checks from [Contributing](CONTRIBUTING.md#choose-tests-by-change). Build from the root with JDK 21/SDK 37.0; use `gradlew.bat` on Windows. Serialize Gradle runs to keep outputs and device state reliable.
4. Report exact passed/failed counts, cached/reused results, checks not run and remaining risks. Do not suppress failing tests or report instrumentation completion as success.
5. Update the applicable canonical doc rather than duplicating policy. [Testing](docs/TESTING.md) records historical evidence; [Code review](docs/CODE_REVIEW.md) tracks cleanup findings. Existing failures must remain visible.

Use no secret or personal-data fixtures in agent prompts, screenshots, committed logs or issue reports. Disclose AI assistance and what was actually validated in contribution summaries.

## Project workspace layout

Before working, read the [project guidance](../AGENTS.md) and [shared guidance](../../AGENTS.md) when accessible. If either is unavailable in your environment, report that limitation and follow the local rules below. Preserve this repository's existing instructions and any applicable nested instructions; historical release permissions are not current task authorization.

This file's directory (`.`) is the authoritative working root, `<project>/repo/`. Run source commands here. Keep versioned documentation in `docs/` when consistent with existing conventions. Put task deliverables in `../outputs/`; consult `../reference/` for supporting material and `../archive/` for recovery material. Use `../worktrees/` only for real linked worktrees. Create these directories only when needed, and retain conventional build-output paths required by tools. Global caches and app configuration remain global.

Keep signing material outside Git in `../private/signing/`. Do not read, print, commit, publish or activate credentials; signing configuration or use requires explicit user authorization. Do not initialize Git in the project container (`..`) or Projects directory (`../..`). Inspect status before edits, preserve user changes and ignored source, run relevant checks, and report limitations. Keep external backups unchanged. Instructions guide behavior; they do not technically guarantee compliance.
