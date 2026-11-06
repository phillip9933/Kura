# CI dependencies and permissions

Both checked-in workflows declare `permissions: contents: read`; unspecified
token permissions are not requested. Checkout disables credential persistence.
Validation uses GitHub-hosted runners, not a signing runner. Neither workflow
uses `pull_request_target`, requests OIDC/write permissions, uses production
signing credentials, or publishes a release. Prototype debug/test APKs use the
runner's disposable debug signing identity. The manual candidate workflow uploads only an unsigned
APK. Artifact upload uses the Actions artifact service, not release credentials.

## Reviewed action references

The immutable references below match the actions resolved in the passing
[four-configuration run at b11d4f5](https://github.com/phillip9933/Kura/actions/runs/42203474146).
Their major-tag resolutions were independently read from the publishers on
6 November 2026. This identifies tested source inputs; it is not an audit of
every bundled action dependency.

| Action | Release tag | Commit |
| --- | --- | --- |
| `actions/checkout` | v4.4.0 | `11d5960a326750d5838078e36cf38b85af677262` |
| `actions/setup-java` | v4.9.1 | `cf277c60eb25467037889841efdb72551f06f6c3` |
| `android-actions/setup-android` | v3.2.2 | `9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407` |
| `actions/upload-artifact` | v4.6.2 | `ea165f8d65b6e75b540449e92b4886f43607fa02` |

A targeted OSV query of these four exact public action release coordinates
returned no matches on 6 November 2026. It does not cover every bundled action
dependency or prove that the actions are vulnerability-free.

Android setup's command-line archive identifier `12266719` is explicit; it was
the pinned action's previous default, not a toolchain upgrade. That action
installs the command-line tools and adds their `bin` directory to PATH. Both
workflows check `apkanalyzer` availability before building. The verifier also
supports the ordinary SDK `cmdline-tools/latest/bin` location.

Update action pins in a separate reviewed change: verify the publisher and
release/commit, inspect the action definition and dependency/runtime changes,
then run the existing validation matrix. Do not replace pins automatically
with floating tags. No update bot or account integration is enabled here.

## Validation scope

The four existing matrix combinations, job names, triggers, Gradle tasks and
report uploads are retained. The eight packaging verifier regressions run only
in the default combination, immediately after SDK setup and before the Gradle
build. They add Python packaging checks without replacing the Gradle JVM suites.
Current test counts come from the XML evidence in [Testing](TESTING.md).
Android instrumentation is compiled, not run, in these jobs.

The hardening changes merged in [PR #27](https://github.com/phillip9933/Kura/pull/27)
after [four successful configurations](https://github.com/phillip9933/Kura/actions/runs/42228987241)
at `960f65f`. The later [crop implementation run](https://github.com/phillip9933/Kura/actions/runs/42245999018)
at `8d97adc` also passes all four; downloaded XML records 63 fresh JVM tests per
configuration, 252 total, with zero failures/errors/skips. Those runs compile
instrumentation rather than execute it. Runtime results retain separate source
and artifact scopes in Testing.

No measured runtime budget or required check is introduced here. Account/branch
rules and security-service grants are outside these workflow files; no setting
change or effective merge-gate enforcement is claimed.

## Manual prototype runtime export

The verification workflow accepts `export-prototype-apks`, default false. Only
a manual dispatch selecting it and the combined scanner/updater configuration
builds the core instrumentation, R8-optimized prototype and external benchmark
driver APKs. It uploads these with the prototype debug app/default test pair. That job's ordinary validation and packaging checks
must succeed first; the complete four-way matrix remains part of qualification. Export runs before opt-in installer-harness compilation so
the saved app test APK is the default suite. No production signing key, release
APK or signing material enters this artifact; retention is seven days.

This supports exact-head runtime qualification, including the Kotlin update, on
a credential-free disposable emulator without installing a new global compiler.
Record the dispatch head, downloaded artifact hashes and actual device results.
APK export/compilation is not a runtime pass. Keep the prototype-package and
emulator guards; never install this pair over a personal or production wallet.
These validation artifacts are not inputs to privileged release signing or
publishing. Production builds must follow the separate tagged-source release
workflow. No branch/account settings, token grants or signing configuration
change is included.


## Resolved dependency evidence

Each flag combination runs `tools/export-dependency-resolution.gradle` after
normal build/packaging validation. It records app debug/release compile and
runtime scopes, default Android-test compile/runtime, core model/import JVM
test runtime, and the root buildscript classpath: nine named scopes per
configuration. The 6 November export resolved sixteen configurations per
combination; the new collector explicitly covers the nine scopes listed above.
Each export retains its own scope definitions and date.

Reports under `build/reports/dependencies/resolution.json` contain exact selected
Maven coordinates and external artifact hashes, source revision, flags and
toolchain versions. They are included in the ordinary report artifact. The
collector does not change resolution attributes; it filters project outputs
from artifact hashing because Android publishes several secondary variants.
Project source is covered separately by source/artifact qualification. No
credentials, repository URLs or absolute file paths are exported.

Resolution is not a shipped/R8 SBOM. Platform/BOM metadata may carry no code;
compile/test/build-tool matches must remain separate from runtime applicability.
Opt-in installer-test dependencies, supplemental processor/lint configurations,
embedded Gradle/JDK/SDK libraries and native components are not exhaustively
covered. The collector API was exercised against real Gradle configurations in
a credential-free offline probe; the final Kotlin graph requires its own CI
report and advisory reconciliation.


### External optimized driver target

The exported optimized app uses `app.kura.wallet.prototype`, while the external
benchmark driver historically defaults to `.prototype.benchmark`. Supply
`-e targetPackage app.kura.wallet.prototype` when testing the exported optimized
APK. All four external journeys accept that explicit synthetic identity; the
production identity remains excluded. The external test APK targets API 37, so its
manifest explicitly declares visibility for the three allowed prototype targets
before PackageManager checks. Updater discovery and optimized fixture
journeys require a nondebuggable app, and the fixture journey explicitly checks
emulator hardware. This selects an existing release-style prototype build; it
does not change application code, signing or authentication behavior.


### Recorded qualification

[Final manual run 42268577334](https://github.com/phillip9933/Kura/actions/runs/42268577334)
and [PR run 42268543563](https://github.com/phillip9933/Kura/actions/runs/42268543563)
at `1f544dd` pass all four configurations. Downloaded manual reports confirm
252 fresh JVM passes, no failures/errors/skips and no lint errors/fatals.
The seven exported APKs were inspected for package, signer and instrumentation
targets; the external driver declares exactly the three prototype query targets.
Runtime uses the source-correspondent `3c50ed1` app pair and the final `1f544dd`
self-targeted driver, with signatures recorded separately. Four optimized
external journeys pass. [Complete scoped evidence](evidence/kotlin-runtime-20261107.json).
