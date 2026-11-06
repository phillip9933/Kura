# Dependency and security baseline — 6 November 2026

Source: `08d3f9e`, with the same tree as remote main `6ed466a`. This baseline
records declarations, resolved configurations, bounded integrity checks and a
current public-advisory lookup. It is **not an independent security audit or
shipped-artifact SBOM**.
The older [September review](SECURITY_REVIEW.md) and
[138-package OSV result](security-dependencies.json) retain their original date
and scope; they are not current evidence for the scanner/updater release.

## Inventory and evidence

[Machine-readable inventory](evidence/dependency-inventory-20261106.json)
contains 62 dependency declarations / 37 distinct coordinate strings, their
configuration/source locations, seven vendored POMs and actual native entries
in the retained public Beta 3 APK. The separate [resolved baseline](evidence/dependency-resolution-20261106.json)
records the selected versions, which can differ from declarations. Upstream sample
inventories and unused vendored camera/UI/export modules are not Kura's graph.

| Area | Declared inputs | Current evidence and limits |
| --- | --- | --- |
| Android host and UI | CameraX 1.5.3, Compose BOM 2025.12.01, Activity 1.10.1, Lifecycle 2.9.2, Core 1.16.0, ProfileInstaller 1.4.1 | All four supported flag combinations resolved; exact selections are in the resolved baseline. |
| Storage/security | SQLCipher 4.19.0, Room 2.7.2, Bouncy Castle 1.86, Biometric 1.1.0 | Current Maven selections queried against OSV; no runtime match returned. Native/unknown vulnerabilities remain outside that conclusion. |
| Other runtime code | Coroutines 1.10.2, ZXing 3.5.3, OkapiBarcode 0.5.6, desugaring 2.1.5 | Current selected transitives recorded separately; declarations alone do not establish selection. |
| Optional updater | `android-updater:0.2.2-beta.1`, WorkManager 2.11.2; published POM declares OkHttp 4.12.0 and serialization 1.8.1 | All 7 listed vendor SHA-256 pins match. Selected updater runtime graph is recorded separately from the POM declarations. |
| Optional scanner | `scanner-core`, `scanner-processing-opencv`, `scanner-ocr-tesseract` 0.8.0-beta.1 | All 185 listed vendor pins match. Processing POM declares ONNX Runtime Android 1.24.1 and EXIF 1.4.1. |
| Native scanner code | OpenCV 4.12.0, Tesseract 5.5.3 plus documented parser patches, Leptonica 1.85.0, libjpeg 9f, libpng 1.6.59, NDK runtime 28.2.13676358 | Pinned source/build/notice metadata inspected; eight OCR binary hashes match the public APK. Targeted upstream advisory/patch review is separate from an independent rebuild or exhaustive native scan. |
| Models | Pinned DocQuad inference artifact and English/Japanese tessdata_fast revision `87416418657359cb625c412a48b6e1d6d41c29bd` | Model/asset pins retained; English/Japanese hashes pass the public-APK packaging smoke. Not a model/parser-safety audit. |
| Build/toolchain | Gradle 9.3.1 with distribution SHA-256, AGP 9.1.1, JVM/Compose Kotlin plugins 2.2.20, KSP 2.3.12, JDK 21, SDK 37.0/build-tools 36.0.0 | 121 build-plugin Maven selections collected; current lookup found build-scoped debt. SDK/JDK/Gradle embedded-library coverage remains incomplete. |
| Test code | JUnit 4.13.2, AndroidX runner 1.6.2, extension JUnit 1.2.1, UIAutomator 2.3.0, JSON 20250517, opt-in OkHttp harness | Test/runtime selections are recorded by configuration; they are not automatically shipped app dependencies. |
| CI dependencies | Four action commits matching a passing prior run | [Pin and permission review](CI-VERIFICATION.md); no new GitHub workflow execution for this local diff. |

The retained public APK has 24 native ZIP entries across arm64-v8a/x86_64.
Its checksum, manifest policy, model hashes, profiles, signature/public identity
and 16 KiB alignment pass a read-only smoke check. This identifies a published
artifact, not a fresh build or a complete SBOM. Android system libraries and
OS security updates remain platform dependencies.

## Existing findings versus verification gaps

- The APK manifest-authority defect is existing verifier debt, corrected in this
  local batch with a failing baseline and real compiled-fixture regressions.
  It does not establish that the historical release APK had unsafe flags.
- The September dependency finding and upstream OCR patch descriptions remain
  historical dispositions. Current Maven advisory coverage is
  recorded below. Pinned upstream patch code and binary correspondence were
  inspected, but native fixes were not independently rebuilt or exhaustively tested.
- Full Gradle dependency locking and dependency-verification metadata are not
  tracked. Vendored SDK/model pins and the Gradle distribution pin cover a
  narrower boundary; they do not authenticate every remotely resolved artifact.
- No current secret-scan or source-security-analysis result is included. Build
  lint is not an equivalent security scan. The scoped public dependency lookup
  below does not replace either check.
- Checked-in workflows contain Android verification and unsigned candidate
  builds, not a dedicated security scan. Platform alert/default-scan state is
  not verified. The absence of a scanner workflow does not establish that
  platform-managed scanning is disabled.

There is no current clean-scan conclusion. New exploitability cannot be inferred
from version age, and a pinned hash is not a vulnerability verdict. Existing debt
and unverified scope must remain visible when reviewing future dependency changes.

## Current resolved and advisory evidence

A disposable, credential-free Linux environment resolved sixteen selected
configurations in each supported combination, without network access:

| Direct updates | Card scanner | Release-runtime Maven selections | Unresolved modules |
| --- | --- | --- | --- |
| false | false | 139 | 0 |
| true | false | 149 | 0 |
| false | true | 143 | 0 |
| true | true | 153 | 0 |

The source export preserves the original versions, flags, constraints and
exclusive vendored repositories. Remote public repositories were redirected to
a read-only mirror of selected public cache files and explicitly fetched public
POM/module metadata. No opaque global cache indexes, URL caches, user init files,
authentication or signing inputs were exposed. This establishes resolution under
that stated mirror; it does not authenticate/rebuild every cached binary or prove
byte-for-byte equivalence with a newly built release.

OSV was queried on 6 November for **319 unique selected Maven tuples plus four
exact action releases**, **323 queries**, with no truncated results. It returned
**11 advisory records across seven build-plugin package versions**. No matches
were returned for the collected release-runtime, compile, test or action-release
targets. Presence is not demonstrated reachability/exploitability, and zero
matches is not proof against unknown, unindexed or native vulnerabilities.
Existing build-tool debt requires maintainer triage; none was silently suppressed
or changed in this baseline. Complete source/secret scanning and native/toolchain
coverage remain gaps.

The root build-plugin graph has 121 selections, including compiler/tooling and
AGP transitives; it is distinct from the APK runtime. The upstream OCR build
script at the pinned scanner revision contains the documented backports, and
all eight OCR native hashes match the retained public APK. That correspondence
supports provenance, not independent native recovery/rebuild or universal parser
safety. Targeted native advisory review remains narrower than full native SCA.

## Remaining evidence

1. Triage build-tool advisories in their actual dependency paths and supported
   toolchain context before selecting upgrades or classpath overrides. Keep
   existing debt distinct from new regressions and preserve the validation matrix.
2. Review dependency locking/verification and artifact provenance separately.
   Do not blindly accept generated checksums, and do not substitute an upstream
   sample inventory for the four Kura variants.
3. Establish current source/secret scanner and platform security-service evidence,
   with reviewed scope, configuration, redaction, installation and data handling.
   Keep an alert owner and bounded suppression/risk review process.
4. Extend native, bundled-tool and compiler-specific configuration coverage as
   applicable. Installer-acceptance opt-in and every hidden compiler classpath
   were not independently resolved in this pass.

Before a scan, identify its exact network/data boundary. The OSV batch requests
sent public package ecosystem/name/version values to `api.osv.dev`, not
source, vault data or credentials. The isolated
Gradle process had no network route. A separate restricted client fetched only
public metadata from Google Maven and Maven Central; it mounted no repository
code, user home or credentials. Other resolution/execution modes require their
own network/data assessment.
GitHub security services can process source and may need extra grants; neither
is enabled by this baseline. Review tools and integrations through the normal
maintainer workflow.

Evidence covers declarations, scoped offline resolution/advisory lookup,
retained public artifacts and existing CI references. It does not establish
platform security-service scan status or signing recovery. Sensitive findings and credential-location inventories belong
in the maintainer's private records, outside this repository.


## Stable SDK pin follow-up — 7 November 2026

The source now selects scanner 0.8.0 and updater 0.2.2. The dated inventory and
advisory lookup above retain their original candidate-version scope; they are
not relabelled as a new scan. [Pin review and binary comparison](evidence/stable-sdk-pins-20261107.json)
verifies both published outer archives, exact tag/source revisions, all 185
scanner host pins and seven supplied updater pins, and exact Maven coverage.
The scanner archive has no inner manifest: Kura generates its own after checking
the outer digest and all 23 supplied SHA-256 sidecars, retaining provenance.

All five scanner AARs and the updater AAR match the previous pins byte-for-byte.
The scanner core JAR has identical entry payloads despite a changed ZIP hash;
Maven source JARs are identical. POMs change release versions only; Gradle module
metadata also updates the core-JAR archive hashes. Licenses, models and native
notices are retained; native/transitive versions are unchanged. This is bounded
artifact correspondence, not a new native rebuild, security audit or Kura
physical acceptance. Host build/runtime qualification is recorded separately
before merging; the original build-scoped debt and scan gaps remain visible.
