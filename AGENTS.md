# Working on Kura

Kura is an offline Kotlin/Jetpack Compose Android wallet. Read [README](README.md) for setup, [Architecture](docs/ARCHITECTURE.md) for ownership and [Contributing](CONTRIBUTING.md) for the change/test map. This file is the shared agent entry point; do not create competing copies of these instructions.

## Boundaries

- Source publication and removal of old GitHub release downloads were explicitly authorized on 2026-09-29. Store submission, signed binary publication, new release tags and messages on the user's behalf still require task-specific authorization. Never assume source-push approval authorizes store publication.
- Release application ID is `app.kura.wallet`; debug is `app.kura.wallet.prototype`. Preserve signing identity, database schemas, backup formats and Keystore aliases. Historical `Flutter`/`payments` strings can be compatibility keys, not dead code.
- Never add INTERNET permission, networking, analytics or telemetry. Never log keys, passwords, personal records or image contents.
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
