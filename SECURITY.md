# Security policy

Kura's release app is offline and requests no INTERNET permission. That does not prevent a user-selected external app, browser, keyboard, document provider or camera from communicating independently.

## Reporting

Do not post real vault contents, passwords, keys or personal barcodes publicly. Report vulnerabilities privately to **security@rogers.ltd**, the repository's existing maintainer contact. If the repository's private vulnerability-reporting facility is enabled, it can also be used. Do not include real vault contents in an initial report.

Provide an affected version, reproducible synthetic example, impact and the relevant code path. Do not test against another person's vault or a production signing key.

## Security model

The intended protections cover a locked app, casual device access and malicious import files. Android's sandbox, device credential, Keystore and cryptographic providers are trusted. A rooted/compromised OS, malicious authorized keyboard/accessibility service, camera watching the screen, or recipient app that already read an export is outside the guarantees.

Release keys must be authentication-bound and backed by TEE or StrongBox. Emulator-only software-key allowances are confined to debug/profile/benchmark variants. API 24–29 credential fallback uses a short Keystore authentication window; API 30+ uses an authenticated CryptoObject. Physical-device verification across these API bands remains necessary.

Mutable owned buffers are overwritten and session work/database handles are cancelled/closed on lock. This is not complete heap erasure: immutable strings, provider internals, Compose text, bitmaps already rendered and OS copies cannot all be reliably wiped. Temporary camera output exists in private cache during capture; cleanup occurs on completion, lock and the next session startup after process death.

Backups require a strong password. Legacy CBC imports do not provide modern authenticated integrity. PKPASS manifests do not prove issuer identity, and CMS issuer signatures are not currently verified. Never treat a displayed pass as proof of authenticity.

Record deletion can retain shared encrypted media and recovery generations. Use the documented recovery cleanup/Delete All Data options and manage exported backups separately. Flash storage and external copies prevent a secure-erasure promise.

See [the dated review](docs/SECURITY_REVIEW.md) for findings, fixes, scope and remaining work. Development validation is not an independent audit or a certification.
