# Architecture

The repository root is the only Android build. There is no Dart runtime or alternative app implementation.

| Module | Responsibility |
| --- | --- |
| `app` | Compose host, authentication, session ownership, camera/picker/SAF integration and attachment grants |
| `feature:vault` | Cards/Passes/Identity presentation, editor, settings, barcode view and UI state |
| `core:model` | Owned secrets, generation-token session state and external-operation lifecycle policy |
| `core:security` | Keystore wrapping and biometric/credential integration |
| `core:database` | Room schemas/DAOs and SQLCipher generation opening/creation |
| `core:storage` | Encrypted media, bounded streams, encrypted spools and verified atomic writes |
| `core:import` | PKPASS/PKPASSES, backups, transfer codes, preferences and vault-generation publication |
| `benchmark` | Independent device journeys and profile/performance capture |

## Vault lifetime

`VaultSessionCoordinator` moves through Locked, Authenticating, Opening, Unlocked and Closing. Generation tokens reject stale work. A session owns its master key, cancellable work and database handles. Lock invalidates the generation, cancels/joins session jobs, closes databases off the UI thread and clears owned mutable secrets. The ViewModel also clears pending image/attachment buffers and invalidates attachment access, independently of Activity visibility.

Camera, picker and SAF operations receive bounded guards (normally 120 seconds). Guards defer ordinary process-background locking, not screen-off or explicit locking. User auto-lock delays are bounded to five minutes. Authentication continuations survive Activity recreation; process death requires fresh authentication.

## Storage

Release identity is `app.kura.wallet`; debug stays `app.kura.wallet.prototype`. The legacy Room-compatible tables retain versions walletbox 8, passes 7 and identities 6. SQLCipher receives the UTF-8 bytes of the Base64 master-key string. Column/media crypto receives the raw 32-byte key.

Vaults are separate UUID generations in the app's no-backup directory. A small atomic pointer chooses the active generation. Restore creates and validates another generation before confirmation and pointer replacement. Existing generations are retained for recovery. Migration from the old app is explicit backup import. Automatic Flutter database copying, preference XML reading and plugin-key extraction have been removed. Historical schema names, backup keys and native Keystore aliases remain compatibility contracts.

New media uses AES-256-GCM with random nonces. Temporary imported pass/backup content is encrypted before being written to staging disk. Camera capture is the documented plaintext-cache exception. Room schemas are committed under `core/database/schemas/`; destructive migration fallback is not enabled.

## Trust boundaries

Untrusted files enter through explicitly launched system document pickers or camera/scanner input. Import code bounds compressed/expanded sizes, path names, entry counts and nesting. SQL table/column identifiers are allowlisted; record values are bound. The release has no network permission. Links and file viewing require user actions and leave the app via Android intents; plaintext recipients can retain copies.

[Security review](SECURITY_REVIEW.md) and [Testing](TESTING.md) distinguish implemented protections from unverified device/release behavior.

## Code map

| Task | Entry points |
| --- | --- |
| Understand the Android host | [MainActivity](../app/src/main/kotlin/app/kura/nativeapp/MainActivity.kt), [VaultController](../app/src/main/kotlin/app/kura/nativeapp/VaultController.kt), [PendingOperations](../app/src/main/kotlin/app/kura/nativeapp/PendingOperations.kt) |
| Lock/unlock or external intent policy | [Session](../core/model/src/main/kotlin/app/kura/nativecore/Session.kt), [Lifecycle](../core/model/src/main/kotlin/app/kura/nativecore/Lifecycle.kt), [Android binding](../core/security/src/main/kotlin/app/kura/nativecore/AndroidVaultLifecycle.kt), [HardwareVaultKey](../core/security/src/main/kotlin/app/kura/nativecore/HardwareVaultKey.kt) |
| Change section filtering/sorting | [VaultItem](../feature/vault/src/main/kotlin/app/kura/feature/VaultItem.kt), [ItemClassification](../feature/vault/src/main/kotlin/app/kura/feature/ItemClassification.kt), [VaultOrdering](../feature/vault/src/main/kotlin/app/kura/feature/VaultOrdering.kt), [VaultScreen](../feature/vault/src/main/kotlin/app/kura/feature/VaultScreen.kt) |
| Change card/pass appearance | [PassPresentation](../feature/vault/src/main/kotlin/app/kura/feature/PassPresentation.kt), [PassScreens](../feature/vault/src/main/kotlin/app/kura/feature/PassScreens.kt), [CardMedia](../feature/vault/src/main/kotlin/app/kura/feature/CardMedia.kt) |
| Add an editable field/category | [FormFields](../feature/vault/src/main/kotlin/app/kura/feature/FormFields.kt), [ItemScreens](../feature/vault/src/main/kotlin/app/kura/feature/ItemScreens.kt), [ConfiguredFields](../feature/vault/src/main/kotlin/app/kura/feature/ConfiguredFields.kt), [VaultPreferences](../feature/vault/src/main/kotlin/app/kura/feature/VaultPreferences.kt) |
| Change settings | [SettingsScreen](../feature/vault/src/main/kotlin/app/kura/feature/SettingsScreen.kt), [PresentationSettings](../app/src/main/kotlin/app/kura/nativeapp/PresentationSettings.kt); persist through `VaultController.preference` |
| Diagnose imports/restores | [PkpassParser](../core/import/src/main/kotlin/app/kura/nativecore/PkpassParser.kt), [PassStagingArea](../core/import/src/main/kotlin/app/kura/nativecore/PassStagingArea.kt), [StreamingBackup](../core/import/src/main/kotlin/app/kura/nativecore/StreamingBackup.kt), [VaultStore](../core/import/src/main/kotlin/app/kura/nativecore/VaultStore.kt) |
| Diagnose media/file grants | [MediaStorage](../core/storage/src/main/kotlin/app/kura/nativecore/MediaStorage.kt), [ItemMedia](../core/import/src/main/kotlin/app/kura/nativecore/ItemMedia.kt), [AttachmentContent](../app/src/main/kotlin/app/kura/nativeapp/AttachmentContent.kt), [CapturedImageScanner](../app/src/main/kotlin/app/kura/nativeapp/CapturedImageScanner.kt) |

## Dependency direction

Declared project edges are `app → feature:vault, core:import`; `feature:vault → core:model, core:import`; `core:import → core:storage, core:database`; `core:storage → core:security`; `core:database → core:security`; and `core:security → core:model`. There is no project-dependency cycle. `benchmark` is a separate test APK that drives app variants through Android UI automation.

Several dependencies currently use Gradle `api`, so downstream code can see more than the ideal boundary. All core modules also share the Kotlin package `app.kura.nativecore`. These are existing design tradeoffs, not a claim of strict domain isolation. Tightening exports or splitting packages should be a separately validated refactor, preserving manifest components, generated Room contracts and profile references.

## Flow and ownership rules

UI events go to `VaultController`, which serializes session work with `work` and a mutex. `VaultStore` owns schema-compatible mutations and generation publication. `refresh` produces immutable `VaultItem` snapshots; the UI caches their classification/presentation and derived section ordering. Barcodes and image decoding run outside recomposition on background dispatchers.

The UI's section (`Cards`, `Passes`, `Identity`) is distinct from a physical legacy database table. A membership can be stored in `passes` while displayed in Cards; use stable `table:id` keys and classification helpers rather than moving records between databases to change their appearance. Order/favorites/category overlays live in encrypted settings; tests must include mixed-table sections.

`VaultUiMemory` owns editable drafts for the current session, not process-persistent state. `PendingOperations` owns activity-result guards, temporary image bytes and attachment exports. A caller transferring a mutable buffer must define who wipes it; a caller returning an owned bitmap must define disposal. Framework callbacks, Room entities/DAOs and manifest providers can be used without direct source callers.

Room definitions validate existing schemas and handle database opening; much of `VaultStore` uses bound SQL over the opened database for compatibility operations. Do not delete generated/schema declarations just because row operations also exist as SQL.
