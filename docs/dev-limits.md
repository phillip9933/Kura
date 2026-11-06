# Development limits and release scope

No active temporary functional limit, mock provider, placeholder workflow or
security relaxation was found in the selected shipping-source scope for the
proposed Android 2.2.0 / 123 distribution. This is a scoped source assessment,
not a universal absence claim or final tagged-artifact acceptance.

## Scope and exclusions

Review the app main, enhanced/basic scanner, direct/offline update source sets,
core libraries, vault UI, build configuration, manifests, packaged assets and
the selected vendored SDK inputs. The distributed candidate selects
`cardScanner=true` and `directUpdates=true`. Test source sets and the separate
benchmark application are not shipped. Vendored runtime artifact/model hashes
are checked by the dependency and packaging verifiers; native internals are not
independently audited here.

The `DEV-LIMIT(` marker scan and review of TODO/FIXME/mock/placeholder matches
found no temporary shipping entry requiring an active `DL-*` record. The search
field's Compose `placeholder` is ordinary interface hint text. Permanent input,
pixel, attachment and memory bounds are product safety limits documented in
[architecture](ARCHITECTURE.md) and [card recognition](CARD-RECOGNITION.md), not
development-only caps.

## Existing prototype-only paths

| Path | Release boundary | Required evidence |
| --- | --- | --- |
| Emulator software-key fallback in `MainActivity` | Requires `BuildConfig.PROTOTYPE` plus emulator hardware; release defines it as false | Inspect final generated release constant and guard call sites; retain hardware-key acceptance separately |
| Synthetic fixture entry and `VaultController.fixture` | UI callback is absent with `PROTOTYPE=false`; the operation also checks that flag | Inspect final release source/configuration and R8 removal evidence |
| Updater installation disabled in prototype builds | Release enables the normal user-confirmed installer flow | Inspect final distribution configuration; do not treat prototype replacement as production-installer acceptance |
| Instrumentation screenshot/fixture helpers | Exist only in test APKs and use disposable synthetic targets | Exclude test APKs from published assets; verify release manifest is not test-only or debuggable |

These are permanent testing controls excluded from the shipped release scope,
not a waiver allowing a release security bypass. No new runtime limit or bypass
was introduced by the two image fixes or version preparation.

## Final release gate

Before publishing, repeat the scope/marker/configuration assessment at the
authorized tag and inspect the actual CI-produced release APK. Record the
generated release flags, manifest/model/ABI/alignment checks and R8 fixture
removal evidence in release validation. Any applicable unresolved limit or
reachable security relaxation blocks publication. The unperformed final tagged
artifact check remains pending; removing a marker would not satisfy this gate.
