# F-Droid native build handoff

This handoff accompanies the published 2.0 source; it does not update fdroiddata or a pending submission. Preserve the existing 1.x build entries and signing/distribution policy. The user will edit the original pending F-Droid submission; no external request is modified by the source push.

## What changes for 2.0

The repository root is now a Gradle Android project. Remove Flutter/Dart bootstrap, pub commands and the old `android/` build subdirectory from the **new build entry only**. Release output is `app/build/outputs/apk/release/app-release-unsigned.apk`; its package is `app.kura.wallet`, version name `2.0.0`, code `118`. There are no product flavors. Do not use the debug, profile or benchmark APKs for distribution.

The tested local toolchain is JDK 21, Android SDK `platforms;android-37.0` and `build-tools;36.0.0`, Gradle 9.3.1, AGP 9.1.1, Kotlin 2.2.20 and KSP 2.3.12. Minimum API is 24; target API is 36. F-Droid's selected build-server image must supply compatible tooling. Local Windows success is not a Linux build-server result.

## Build entry draft

Append a disabled entry like this to the existing `Builds` list in the external `metadata/app.kura.wallet.yml`. The entry below pins the public native source and CI-bootstrap revision. Check the actual highest store code and remove `disable` only after maintainer verification. If application code changes again, update the pin to that reviewed revision. This deliberately is not an executable publishing configuration in this repository.

```yaml
  - versionName: 2.0.0
    versionCode: 118
    disable: Native rewrite pending F-Droid build-server verification
    commit: c429d29e6d523a1aee5f76aa969dc0b9b5ac6129
    gradle:
      - yes
    output: app/build/outputs/apk/release/app-release-unsigned.apk
```

Do not carry `subdir: android` into this entry. Keep the existing repository identity, author information, license and old build history. Review update detection against `app/build.gradle.kts`; do not advance `CurrentVersionCode` or auto-update policy until the maintainer has verified the candidate. Do not pass signing properties; F-Droid's existing signing workflow owns signing.

## Maintainer verification

In a separate fdroiddata checkout with the intended server image/toolchain, review and enable the new entry, then run:

```sh
fdroid rewritemeta app.kura.wallet
fdroid lint app.kura.wallet
fdroid build --server app.kura.wallet:118
```

Inspect the source scan and resulting manifest, APK version and signing/distribution route. Do not add broad `scanignore`, `scandelete` or `novcheck` exceptions to bypass findings. SQLCipher 4.19.0 includes native libraries in its Maven AAR; confirm the maintainer's accepted source provenance/build policy for this dependency. A passing local ELF-alignment check does not establish F-Droid source-policy approval or reproducibility. Keep the declared toolchain pins; coordinate server support rather than silently downgrading the security library.

Store text and real synthetic-data screenshots are under `fastlane/metadata/android/en-US/`. Existing text/assets in fdroiddata can override source metadata, so reconcile those when the external submission is updated. See [Release preparation](RELEASE.md) for the authorization and signing gates.

## Sources and evidence

Reviewed 2026-09-29: [F-Droid build metadata reference](https://f-droid.org/docs/Build_Metadata_Reference/), [F-Droid descriptions and screenshots](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/). The build entry is a proposal based on this repository's structure; it has not been run through fdroidserver, and no external recipe was retrieved or changed. AI assistance was used to prepare this handoff.
