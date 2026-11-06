# Kura

## Roadmap and work

[Release roadmap](https://github.com/users/phillip9933/projects/2/views/2) · [Work board](https://github.com/users/phillip9933/projects/2/views/1) · [Issue conventions](CONTRIBUTING.md#public-roadmap-and-issue-management)

Follow release scope and maturity separately from individual work. Dates are estimates; unscheduled ideas have no release milestone.

**Privacy first pass wallet.**

Kura (蔵) keeps your cards, passes and identity documents in an encrypted, offline Android wallet. No accounts, advertising or telemetry. The default build has no Internet permission. An optional direct-update build can check GitHub for updates; see [app updates](docs/APP-UPDATES.md).

[Stable download](https://github.com/phillip9933/Kura/releases/latest) · [2.2 beta](https://github.com/phillip9933/Kura/releases/tag/v2.2.0-beta.1) · [Changelog](CHANGELOG.md) · [Privacy](PRIVACY.MD)

## About this project

I build projects to solve problems I run into in my own life. I share them because I believe in open source and hope others can learn from them, adapt them or find them useful.

Making it public does not mean it is a polished production product or suitable for every setup. Please read the documented limitations and decide whether it fits your needs. I'm happy to help where I can, but I can't promise a support schedule.

## Your wallet, organized

- **Cards, Passes and Identity** — three separate, swipeable sections.
- Import `.pkpass` files and `.pkpasses` bundles, scan barcodes or add items manually.
- Add logos, card images, attachments and custom fields.
- Find what you need with categories, favorites, search, sorting and archive.
- Show barcodes full screen when it is time to scan.
- Unlock with biometrics or your device credential, or choose phone-unlock-only access in Settings. Wallet data remains encrypted and sessions close on lock.
- Keep password-encrypted backups, optionally saved automatically to a folder you choose.

## Native Android

Kura 2.0 is rebuilt in **Kotlin and Jetpack Compose**, using Material 3. The rewrite removes the Flutter engine and plugin bridges so startup, authentication and camera/file-picker handoffs can be managed directly through Android's lifecycle and security APIs.

Requires Android 7.0 or later, a secure device lock and supported hardware-backed key storage. Download the **APK** to install Kura. GitHub releases are APK-only; Google Play publication is no longer planned.

Kura stores card references and barcodes; it does not make NFC payments. Imported pass issuer signatures are not verified. Sharing files or opening links hands data to another app. Read the [security and privacy details](SECURITY.md).

## Contribute

Start with [Contributing](CONTRIBUTING.md) for setup and checks. The [documentation index](docs/README.md) covers architecture, development and release tooling. Test and profiling helpers live in `tools/`.

Kura originated as a fork of [Wallet by Sidhant](https://github.com/sidhant947/Wallet).

## AI usage

I use AI tools to help with development, including analysis, code, tests and documentation. I care about security, privacy and protecting people's data, and I try to reflect that in how I build these projects.

I document validation and known limitations so you can assess the evidence for yourself. Contributions should disclose material AI assistance and distinguish checks actually run from checks still needed.

See [testing evidence](docs/TESTING.md), [security review](docs/SECURITY_REVIEW.md) and [contribution guidance](CONTRIBUTING.md#ai-usage).

## License

Licensed under [AGPL-3.0](LICENSE).

## Optional card scanning

The enhanced-scanning development build (`-PcardScanner=true`) adds automatic card-edge cropping and optional offline English/Japanese OCR into existing empty form fields, with editable review before saving. It uses scanner 0.8.0-beta.1 and requires Android API 26+ on 64-bit devices. The base build retains API 24 support. See [card scanning](docs/CARD-RECOGNITION.md) for controls, privacy, build flags and known limitations.
