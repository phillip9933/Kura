# Changelog

## 2.0.0 — unreleased

- Replaced the app implementation with Kotlin, Jetpack Compose and Material 3.
- Moved the standalone prototype into the repository-root Android project and removed the old build/tooling from the working tree.
- Added authenticated vault sessions, bounded external-operation guards, hardware-bound key wrapping, SQLCipher/Room storage and encrypted media.
- Added bounded pass import, compatible encrypted backup/restore, typed manual fields, images, attachments, favorites and ordering.
- Preserved the release application ID and separate debug installation ID.
- Centralized sensitive pending-state cleanup on vault lock and added startup cleanup of abandoned camera captures.
- Replaced obsolete docs and release automation with current Android guidance and unsigned verification workflows.
- Added explicit recovery confirmation for backups with omitted images and compatibility with older image filenames. Missing attachments and corrupt images still block restoration.
- Made migration backup-only; removed automatic Flutter database, XML preference and plugin-key extraction. Existing native vaults and older backup formats stay readable.
- Updated SQLCipher to 4.19.0 and Bouncy Castle to 1.86; moved compilation to API 37.0 with compatible Gradle/AGP/KSP tooling.
- Removed obsolete editors/reorder plumbing and unused assets; added contributor/agent guidance and expanded library lint coverage.

Source version code: 118. This is not a published release. Consult [Testing](docs/TESTING.md) and [Security review](docs/SECURITY_REVIEW.md) for remaining gates.

Earlier released history remains in Git and existing release tags. Historical prototype iteration reports are outside the current source tree in the local recovery archive.
