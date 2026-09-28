# Security review — 2026-09-29

Scope: local Kura 2.0.0 development source, version code 117, after native-only consolidation. This is a manual engineering review with automated regression validation, **not an independent audit, penetration test or certification**. No production key, user vault or real personal backup was used.

## Findings and disposition

| ID | Priority | Finding | Disposition |
| --- | --- | --- | --- |
| SEC-01 | High | Vault lock cleared many UI fields but left pending attachment export bytes and published attachment-provider content alive until later owner destruction/expiry. A recipient could continue accessing published content after lock, and owned plaintext buffers remained resident. | Fixed: `VaultController` observes non-unlocked states and clears pending sensitive content; `PendingOperations` wipes export/crop buffers, invalidates provider entries/descriptors and removes pending capture files while preserving authentication continuations. Added a regression that locks without an Activity. |
| SEC-02 | Medium | Process death during camera capture could leave an unencrypted private-cache image without a subsequent cleanup path. | Fixed for the next session: confined cleanup of abandoned `scan-*.jpg` files is awaited before unlock. Completion/lock cleanup remains. A process that is dead cannot erase its cache immediately; this exposure window and the external camera's own copies remain limitations. Added a startup-cleanup regression. |
| SEC-03 | Medium / dependency maintenance | SQLCipher 4.10.0 and Bouncy Castle 1.81 required refresh and advisory review. | Updated to SQLCipher 4.19.0 and Bouncy Castle provider 1.86. OSV returned no known matches for 138 resolved release-runtime Maven coordinates on 2026-09-29. This is not a complete native/toolchain supply-chain audit; see validation and vendor notes below. |
| SEC-04 | Medium / migration limitation | Automatic adoption could copy old plaintext media. Record deletion also retains shared media and recovery generations. | Automatic adoption removed: migration is explicit backup import, which encrypts staged media. Old private files/keys are not read or deleted. Earlier native generations can still contain previously copied plaintext media; this cleanup does not rewrite or erase retained data. Recovery-generation and exported-copy retention remains a documented limitation, not a secure-erasure guarantee. |
| SEC-05 | Informational | Earlier README claims promised universal hardware protection, complete deletion and log/memory guarantees beyond what the implementation proves. | Replaced with scoped claims, platform trust assumptions, variant/API distinctions, export boundaries and retention limits. |

The new regression results and exact build evidence are recorded in [Testing](TESTING.md). The back-image journey now passes after correcting stale accessibility-cache handling in the test. The full-run and targeted follow-up evidence, including the headless test setup race, are recorded there explicitly.

## Code paths examined

- [Session coordinator](../core/model/src/main/kotlin/app/kura/nativecore/Session.kt), [lifecycle policy](../core/model/src/main/kotlin/app/kura/nativecore/Lifecycle.kt), [Android lifecycle binding](../core/security/src/main/kotlin/app/kura/nativecore/AndroidVaultLifecycle.kt): generation validation, cancellation, bounded intent guards, screen-off locking and handle/key cleanup.
- [Keystore wrapper](../core/security/src/main/kotlin/app/kura/nativecore/HardwareVaultKey.kt), [authentication host](../app/src/main/kotlin/app/kura/nativeapp/MainActivity.kt): authenticated cipher use, TEE/StrongBox enforcement, old-API credential windows, alias continuity and fail-closed native key handling.
- [Vault store](../core/import/src/main/kotlin/app/kura/nativecore/VaultStore.kt), [database opening](../core/database/src/main/kotlin/app/kura/nativecore/VaultDatabases.kt), [media storage](../core/storage/src/main/kotlin/app/kura/nativecore/MediaStorage.kt) and [atomic writes](../core/storage/src/main/kotlin/app/kura/nativecore/VerifiedAtomicWrite.kt): allowlisted SQL identifiers/bound values, schema validation, copied generations, path confinement and activation boundaries.
- [Bounded ZIP](../core/import/src/main/kotlin/app/kura/nativecore/BoundedZip.kt), [PKPASS parser](../core/import/src/main/kotlin/app/kura/nativecore/PkpassParser.kt), [streaming backup](../core/import/src/main/kotlin/app/kura/nativecore/StreamingBackup.kt), [stream crypto](../core/storage/src/main/kotlin/app/kura/nativecore/StreamingCrypto.kt), [backup KDF](../core/import/src/main/kotlin/app/kura/nativecore/BackupCodec.kt), [transfer codec](../core/import/src/main/kotlin/app/kura/nativecore/TransferCodec.kt) and [automatic backups](../core/import/src/main/kotlin/app/kura/nativecore/AutomaticBackup.kt): input budgets, traversal/duplicate checks, authentication before GCM payload parsing, staged restoration and verified backup publication.
- [Attachment provider](../app/src/main/kotlin/app/kura/nativeapp/AttachmentContent.kt), [pending state](../app/src/main/kotlin/app/kura/nativeapp/PendingOperations.kt), [capture cleanup](../app/src/main/kotlin/app/kura/nativeapp/CaptureFiles.kt), [manifest](../app/src/main/AndroidManifest.xml), backup rules, clipboard helper, external intent dispatch and release build configuration.

## Protections observed

The release manifest omits INTERNET, disables platform backup/transfer of app data, and has only the launcher as an unrestricted exported app component. The merged manifest also contains AndroidX ProfileInstallReceiver protected by android.permission.DUMP; providers/services are non-exported, and file sharing requires explicit URI grants. The camera FileProvider is scoped to its capture directory. Screen capture protection is enabled; external URLs are limited to http/https/tel and require a user action.

New column/media encryption uses random-nonce AES-256-GCM. Hardware keys require user authentication and release rejects software-only protection. Owned master/SQLCipher buffers are cleared and handles close on lock. API 24–29 credential fallback still relies on Keystore enforcement after the system prompt; success alone is not used as a substitute for decryption.

Pass input is bounded to 10 MiB expanded, 500 KiB pass JSON/localization, 128 entries and depth 3. Path traversal, duplicate entries and manifest mismatches are rejected. Backups have separate encoded, expanded, JSON and per-file limits, and GCM authentication is completed before ZIP/JSON parsing. Restore publishes a validated separate generation; it does not destructively replace active tables while parsing an archive.

## Dependency notes

Vendor sources checked on 2026-09-29:

- [SQLCipher 4.19.0 security maintenance advisory](https://www.zetetic.net/blog/2026/09/08/sqlcipher-4.19.0-release/) describes two low-risk issues in older versions involving export aliases and invalid `hexkey` URI parameters. The inspected Kura code does not call `sqlcipher_export` or use that URI parameter, so those particular triggers were not found. This is not a blanket conclusion about all SQLite/SQLCipher vulnerabilities. Keep legacy-format/schema tests when evaluating a stable 4.x update.
- [SQLCipher 5 beta notice](https://www.zetetic.net/blog/2026/09/15/sqlcipher-5.0.0-beta/) warns of default compatibility changes. Do not silently jump major database formats as part of cleanup.
- [Bouncy Castle's current downloads](https://www.bouncycastle.org/download/bouncy-castle-java/) and [advisory index](https://www.bouncycastle.org/vulnerability-advisory.html) show releases/advisories newer than the former 1.81 pin. The provider is now pinned to 1.86 (the separately versioned TLS library is not used). Kura uses AES/GCM, Argon2id, PBKDF2 and Base64 paths.

The [resolved dependency inventory and OSV result](security-dependencies.json) covers 138 public Maven coordinates from `:app:releaseRuntimeClasspath`, queried through the [OSV batch API](https://google.github.io/osv.dev/post-v1-querybatch/) on 2026-09-29, with no matches or truncated pages. Only public package names/versions were sent. This does not cover unindexed vulnerabilities, vendored native components, build plugins, CI actions or binary provenance. Native SQLCipher vendor release notes were reviewed separately. Other dependency-update lint suggestions are maintenance candidates, not demonstrated security vulnerabilities.

The Gradle 9.3.1 distribution checksum is pinned from its official checksum endpoint. AGP 9.1.1 supports the API 37.0 compilation required by SQLCipher; Android modules use built-in Kotlin and KSP 2.3.12. Minimum API 24 and target API 36 remain unchanged. See [AGP compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes) and [built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin). No exhaustive binary SBOM assessment or dependency-verification metadata was added.

## Remaining limits and release work

PKPASS issuer/CMS signatures are **not verified**; a valid manifest only checks consistency. Legacy CBC formats lack authenticated integrity. Biometric/StrongBox behavior on physical hardware, API 24–29 fallback, the final production signing identity and real historical backup restoration remain unverified release gates.

Mutable owned buffers can be overwritten; immutable strings, framework/provider internals, GPU/OS copies and already-shared exports cannot all be recalled or securely erased. Clipboard clearing is a best-effort delayed ownership check, subject to Android restrictions. File deletion on flash storage is not secure erasure. A compromised OS or authorized recipient can bypass the intended app-level privacy boundary.

This pass did not perform extensive parser fuzzing, whole-heap analysis, rooted-device attack simulation, independent cryptographic review or a remote CI/store run. Complete the open findings and [release gates](RELEASE.md) before describing 2.0 as production-ready.
