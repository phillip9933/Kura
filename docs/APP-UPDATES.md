# Optional direct updates

The owner authorized GitHub APK updates on 2026-10-24. Wallet operations remain local. The default build still has no INTERNET permission and supports API 24. Build with `-PdirectUpdates=true` to include the standalone updater, network/install permissions and an API 26 minimum. This explicit distribution switch preserves the existing offline/store build and its packaging contract; never mix APK outputs from the two invocations.

```
./gradlew -PdirectUpdates=true :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug
python3 tools/verify-native-packaging.py --direct-updates
```

The app identity, signing identity, native key aliases, schemas and backup formats do not change. The initial development pass kept 2.1.0 / 119. The owner-authorized 2.2.0-beta.1 release uses code 120 and retains the installed release certificate. Subsequent updates must increase the version code. Do not uninstall to bypass a signer mismatch.

## Ownership and behavior

`KuraUpdateApplication` registers and owns the public source independently of the Activity; Compose only observes the already-created manager. It also supplies WorkManager.Configuration.Provider with on-demand initialization. First-run checks are disabled before library initialization. Users can check manually or opt into roughly daily checks and Wi-Fi downloads. Application startup does not request installation. Settings → About → App updates and the lock-screen App updates entry expose the same dialog. No authentication or vault reference is passed to the updater. No record, image, backup, password or master key is sent to GitHub.

Configuration accepts public `phillip9933/Kura` tags `vX.Y.Z` and universal APKs `Kura-X.Y.Z.apk`. Checks, downloads and installer handoff have distinct actions. Size/hash (when advertised), package, version, minimum SDK, debug status and installed signer are checked by the library. Android performs final signature/install enforcement. Permission-screen return never automatically opens the installer; the user taps Install update again. Installer return does not prove success. Prototype builds can inspect releases but cannot download/install the production APK through this UI.

Update metadata and APK cache are separate from vault files. The library's nonexported FileProvider is scoped to `cache/updates/`. The direct variant adds WorkManager and normal network/wake-lock permissions; the offline variant contains none of that dependency graph. Locking the vault can discard the open updater UI without stopping independently scheduled public update work.

## Dependency and licensing

The independent AGPL-3.0 updater is vendored as an immutable Maven/source/license bundle under `third_party/android-updater`. Gradle verifies its SHA256SUMS and rejects unlisted Maven files; builds never follow a sibling checkout. The current source pin is the explicit candidate `io.github.phillip9933:android-updater:0.2.2-beta.1`, from [the standalone prerelease](https://github.com/phillip9933/android-app-updater/releases/tag/v0.2.2-beta.1). See the bundle README for source revision and archive checksum. Kura's existing LICENSE is AGPL-3.0; earlier README/contribution GPL labels were corrected. Do not replace already distributed artifacts under an existing version.

## Acceptance limits

Library host tests are separate from Kura integration/device evidence. Before a Kura release, run a signed same-ID upgrade with retained synthetic wallet data, wrong-signer/package rejection, permission denial, interrupted download, offline failures, process recreation and physical-device checks. API 24 must remain usable with the offline variant. No signing key was inspected or used by this integration. Neither Kura nor Raiun app publication is implied by publishing the reusable library.

## Historical archive verification correction — 0.1.3

The previous direct distribution pinned the immutable 0.1.3 security prerelease. 0.1.2's PackageManager-only debuggable check was unreliable on Android 15. The corrected library also reads the APK binary manifest, resolves boolean resources and fails closed on missing/unreadable application metadata. All installed-signer/package/version/hash checks remain. Independent source-level native regressions cover API 26 and API 35; Kura's real signed upgrade/retained-data acceptance remains separate. The default offline distribution and vault policy are unchanged.

## New-version popup — 26 October 2026

The application root observes the updater’s offer state on both the locked and unlocked screens. New releases show a “New version available” dialog. “View update” opens the update controls; “Later” dismisses that version persistently while keeping it available from App updates. A newer version can prompt again. No background activity launches a dialog or installer; the popup appears in the foreground. Automatic checks remain opt-in, and the default offline build has no updater. The published 0.2.1 stable SDK is checksum-pinned with its source and license; earlier 0.1.3 artifacts are preserved in project outputs.

The Application owns a single update-panel visibility state. The root notice hosts both automatic offers and the manual panel, suppressing a second offer while details are already open. Closing details dismisses the currently displayed version; a later version can offer again.

## Beta-release selection

App updates includes an explicit Include beta releases checkbox. It selects the SDK BETA channel and persists that choice; clearing it selects STABLE. Stable-only remains the default. Automatic checks and Wi-Fi downloads remain independent opt-ins. Beta asset names retain their full version, for example `Kura-2.2.0-beta.1.apk`. Changing channels clears the prior offer/ready state through the SDK, so an APK from the old selection is not silently reused.

The initially published 2.2.0-beta.1 APK uses updater 0.2.1, source `60b8fb82aa14ab0fc115ee9f9f96d3cc2c60f5fc`. Its reviewed runtime delta cancels obsolete queued downloads when no eligible release remains; public API/runtime dependency declarations are unchanged from 0.2.0. Outer archive and all seven inner pins were verified before vendoring. Upstream tests do not replace Kura's host/popup and retained-data acceptance.

## Cache recovery and startup opt-in — 6 November 2026

PR #14 review reproduced a 0.2.1 cache-recovery defect: a valid fresh download could not replace a corrupted cached APK. The new immutable 0.2.2-beta.1 bundle fixes that condition after all normal verification succeeds; invalid new bytes cannot replace an existing valid candidate. Source, remote digests, seven inner pins and corresponding source were verified; the AAR matches independent API26/35-tested CI bytes. SDK physical acceptance remains pending, so it is explicitly a prerelease. Existing app beta artifacts still contain 0.2.1.

Kura startup now normalizes a missing or wrong-typed automatic-check preference to false, retaining only a saved Boolean true as opt-in. The guarded opt-in instrumentation host renders the same dialog controls for synthetic same-package APKs; the normal app entry still prohibits prototype production-update installation. Fixture transport does not bypass size/hash/package/version/debug/signer checks. See TESTING.md and issue #20 for exact Kura upgrade/installer evidence and limits.

## Updater discoverability — 6 November 2026

The optional entry now uses the same full-width icon/title/subtitle/chevron row as other settings, with “Check for updates” visible beneath its title. It remains available while locked, under Settings → About and inside App Version & Trademark. An available version changes the subtitle, while the entry keeps a stable accessible name.

An external test against the nondebuggable R8 prototype passed all three routes both before and after this change. The historical missing entry on the owner’s beta phone was **not reproduced**; this is a discoverability improvement, not proof of an R8/source-set defect. The explicit manual GitHub check passed with automatic checks/downloads still disabled. Popup Later/reoffer and first-run/provider/settings acceptance passed 6/6. See [exact evidence](evidence/updater-discovery-20261106.json); independent review and affected-phone validation remain separate.

The owner-requested beta.2 candidate uses0.2.2-beta.1, versionCode121. Include beta releases remains an independent opt-in. The installer-acceptance harness accepts `-e baseVersionCode N` (default the compiled host code) and expects its generated candidate at N+1, so fixture versions are explicit rather than tied permanently to120→121. Neither this development harness nor its transport enters the release APK.

## Loading-screen follow-up — 6 November 2026

The owner manually confirmed that Settings → App updates → Check for updates is visible and opens in published beta.2 (121). This verifies affected-phone discoverability; it does not establish the cause of the earlier beta1 disappearance or verify a new APK installation.

The owner then reported that the loading background also showed the update entry and allowed a quick tap to open its dialog. The shared non-unlocked layout rendered the entry during authentication/opening, and the root hosted dialogs without a vault-transition gate. The follow-up suppresses both the entry and dialogs until the vault is open or the locked screen is idle with no automatic unlock pending. The automatic-unlock flag is observable so the first startup frame and lifecycle changes follow the same rule. A requested panel or available offer can appear once the transition settles; update access still requires no vault access. Networking and installation remain opt-in and user-confirmed.

This is an unreleased change after beta.2. The published APK and validation report remain unchanged. Exact focused verification and the preserved failing pre-fix regression are recorded in [Testing](TESTING.md).
