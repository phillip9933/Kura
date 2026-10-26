# Optional direct updates

The owner authorized GitHub APK updates on 2026-10-24. Wallet operations remain local. The default build still has no INTERNET permission and supports API 24. Build with `-PdirectUpdates=true` to include the standalone updater, network/install permissions and an API 26 minimum. This explicit distribution switch preserves the existing offline/store build and its packaging contract; never mix APK outputs from the two invocations.

```
./gradlew -PdirectUpdates=true :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug
python3 tools/verify-native-packaging.py --direct-updates
```

The app identity, signing identity, native key aliases, schemas and backup formats do not change. This development pass does not bump version 2.1.0 / 119 or publish Kura. A future direct release must increment the version code and retain the installed release certificate. Do not uninstall to bypass a signer mismatch.

## Ownership and behavior

`KuraUpdateApplication` registers and owns the public source independently of the Activity; Compose only observes the already-created manager. It also supplies WorkManager.Configuration.Provider with on-demand initialization. First-run checks are disabled before library initialization. Users can check manually or opt into roughly daily checks and Wi-Fi downloads. Application startup does not request installation. Settings → About → App updates and the lock-screen App updates entry expose the same dialog. No authentication or vault reference is passed to the updater. No record, image, backup, password or master key is sent to GitHub.

Configuration accepts public `phillip9933/Kura` tags `vX.Y.Z` and universal APKs `Kura-X.Y.Z.apk`. Checks, downloads and installer handoff have distinct actions. Size/hash (when advertised), package, version, minimum SDK, debug status and installed signer are checked by the library. Android performs final signature/install enforcement. Permission-screen return never automatically opens the installer; the user taps Install update again. Installer return does not prove success. Prototype builds can inspect releases but cannot download/install the production APK through this UI.

Update metadata and APK cache are separate from vault files. The library's nonexported FileProvider is scoped to `cache/updates/`. The direct variant adds WorkManager and normal network/wake-lock permissions; the offline variant contains none of that dependency graph. Locking the vault can discard the open updater UI without stopping independently scheduled public update work.

## Dependency and licensing

The independent AGPL-3.0 updater is vendored as an immutable Maven/source/license bundle under `third_party/android-updater`. Gradle verifies its SHA256SUMS and rejects unlisted Maven files; builds never follow a sibling checkout. The exact public pin is `io.github.phillip9933:android-updater:0.2.0`, from [the standalone release](https://github.com/phillip9933/android-app-updater/releases/tag/v0.2.0). See the bundle README for source revision and archive checksum. Kura's existing LICENSE is AGPL-3.0; earlier README/contribution GPL labels were corrected. Do not replace already distributed artifacts under an existing version.

## Acceptance limits

Library host tests are separate from Kura integration/device evidence. Before a Kura release, run a signed same-ID upgrade with retained synthetic wallet data, wrong-signer/package rejection, permission denial, interrupted download, offline failures, process recreation and physical-device checks. API 24 must remain usable with the offline variant. No signing key was inspected or used by this integration. Neither Kura nor Raiun app publication is implied by publishing the reusable library.

## Historical archive verification correction — 0.1.3

The previous direct distribution pinned the immutable 0.1.3 security prerelease. 0.1.2's PackageManager-only debuggable check was unreliable on Android 15. The corrected library also reads the APK binary manifest, resolves boolean resources and fails closed on missing/unreadable application metadata. All installed-signer/package/version/hash checks remain. Independent source-level native regressions cover API 26 and API 35; Kura's real signed upgrade/retained-data acceptance remains separate. The default offline distribution and vault policy are unchanged.

## New-version popup — 26 October 2026

The application root observes the updater’s offer state on both the locked and unlocked screens. New releases show a “New version available” dialog. “View update” opens the update controls; “Later” dismisses that version persistently while keeping it available from App updates. A newer version can prompt again. No background activity launches a dialog or installer; the popup appears in the foreground. Automatic checks remain opt-in, and the default offline build has no updater. The published 0.2.0 prerelease is checksum-pinned with its source and license; earlier 0.1.3 artifacts are preserved in project outputs.

The Application owns a single update-panel visibility state. The root notice hosts both automatic offers and the manual panel, suppressing a second offer while details are already open. Closing details dismisses the currently displayed version; a later version can offer again.
