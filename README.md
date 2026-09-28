# Kura

**Privacy first pass wallet.** Kura (蔵) is an offline Android wallet for cards, passes and identity documents. It has no accounts, advertising or telemetry and does not request the Android INTERNET permission. It stores card references and barcodes; it does not make NFC payments.

Kura 2.0 is written in **Kotlin and Jetpack Compose with Material 3**. This repository contains only the native Android build. Older backup formats remain readable through Kotlin compatibility code.

**2.0 source is available; signed 2.0 downloads are not published yet.** Older GitHub installer downloads are retired. Before upgrading from 1.x, export and keep a backup; 2.0 migration is by explicit backup restore. See [Release preparation](docs/RELEASE.md) and the [F-Droid handoff](docs/FDROID.md).

## What it does

- Three separate swipeable sections: **Cards**, **Passes** (default), and **Identity**.
- Categories, favorites, search, archive, grid/list layouts and custom ordering.
- `.pkpass` / `.pkpasses` import, barcode scanning and fullscreen barcode display.
- Manual items with category-specific fields, typed custom fields, logos, front/back images and attachments.
- Device credential or biometric unlock, automatic locking, and encrypted databases/media.
- Password-encrypted `.wbk` backup and restore, with optional automatic backups to a selected folder.

## Build and test

Use **JDK 21**, Android SDK platform **37.0**, build-tools **36.0.0**, and the included Gradle wrapper. Minimum supported Android API is **24**; target API is **36**. Set `ANDROID_HOME` or configure an ignored `local.properties` file.

```sh
./gradlew :app:assembleDebug
./gradlew :core:model:test :core:import:testDebugUnitTest :core:import:testLowMemory :app:lintDebug
```

On Windows use `./gradlew.bat`. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. It installs separately as **app.kura.wallet.prototype** so existing test vaults remain accessible. Release keeps **app.kura.wallet** and is unsigned unless signing is explicitly configured. Current source version: **2.0.0**, code **118**; debug displays **2.0.0-dev**.

For device suites, emulator setup and exact results, see [Testing](docs/TESTING.md). [Release preparation](docs/RELEASE.md) covers signing, packaging and store gates. Nothing in this local consolidation publishes a release.

## Privacy and limits

The vault uses SQLCipher with Room, AES-256-GCM for new media/backups, and an Android Keystore key bound to authentication. Release requires hardware-backed key storage. Backups use Argon2id with fixed parameters; older CBC backups have weaker integrity guarantees. Pass manifests are checked when present, but **issuer signatures are not verified**.

Opening an attachment or link hands it to another app. Exported attachments and `.pkpass` files are plaintext; `.wbk` files are password-encrypted. Deletion is not a secure-erasure guarantee: backups, retained recovery generations and shared media can retain copies. Screenshots are restricted, but a compromised OS or an authorized external app is outside that protection.

See [Privacy policy](PRIVACY.MD), [Security policy](SECURITY.md), the [security review](docs/SECURITY_REVIEW.md), and [Backup and migration](docs/BACKUP_AND_MIGRATION.md). These describe the implementation and its limits, not an independent security certification.

## Project documentation

The [documentation index](docs/README.md) maps each guide to its purpose. Contributors start with [Contributing](CONTRIBUTING.md); coding agents start with [AGENTS.md](AGENTS.md).

- [Architecture](docs/ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)
- [Testing and remaining validation](docs/TESTING.md)
- [Backup and migration](docs/BACKUP_AND_MIGRATION.md)
- [Release preparation](docs/RELEASE.md)

Kura originated as a fork of [Wallet by Sidhant](https://github.com/sidhant947/Wallet). The native rewrite retains that project's attribution and local-first intent. Development uses AI assistance; review findings and validation limits are documented explicitly.

Licensed under [GPL-3.0](LICENSE). See the [Code of Conduct](CODE_OF_CONDUCT.md) for community expectations.
