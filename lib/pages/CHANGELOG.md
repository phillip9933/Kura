# Changelog

All notable changes to Kura will be documented in this file.

## [1.0.1] - 2026-08-26

### Fixed
- **Grid Card Layout:** Corrected Payment and Identity card proportions and aligned their starting positions with the Passes grid.
- **Passes Grid Spacing:** Removed unused label space and aligned card spacing with Payments and Identity.
- **Auto-Backup Folder Access:** Restored Android folder selection and persisted write access for automatic backups.

### Changed
- **Version Metadata:** Updated the release version to **1.0.1+101** and linked the About display to app metadata while showing only the external version number.
- **About Repository Link:** Updated the GitHub and issue tracker link to the Kura repository.
- **Universal APK Versioning:** Removed ABI-specific version-code overrides so Android uses the version code from `pubspec.yaml` directly.
- **Auto-Backup Naming:** Automatic backups are now saved as `Kura_autobackup.wbk`.
- **Search Settings:** Clarified the search visibility setting and simplified the search style control to directly switch between Search Bar and Search Button.

### Added
- **Scanned Barcode Formats:** The barcode type now matches the format detected by the live scanner instead of always defaulting to QR Code.
- **Manual Item Reordering:** Added dedicated reorder modes for Payments, Passes, and Identity, with drag-and-drop and up/down movement controls.
- **Archive Storage:** Added an Archive for hiding Payments, Passes, and Identity items from main views, with restore and permanent deletion controls.

## [1.0.0] - 2026-08-25

###  Security & Hardening
- **Encrypted Local Storage:** Migrated to SQLCipher for full AES-256 database encryption at rest.
- **Hardware-Backed Keys:** Integrated Android Keystore via `flutter_secure_storage` to secure database keys.
- **PKPASS Security Hardening:** Added strict bounds, size limitations, and nested depth checks to prevent archive-based attacks (zip bombs).
- **Backup Exclusion:** Set `android:allowBackup="false"` in the manifest to completely block automated cloud backups of sensitive local vaults.
- **Release Sanitization:** Enforced strict `kDebugMode` guards around logging and error traces.

###  Major Features & UI Overhauls
- **Independent Rebrand:** Transitioned project identity to **Kura** (蔵).
- **Dynamic Custom Fields:** Replaced rigid forms with flexible, user-defined schema fields.
- **Grid Layout UI:** Introduced a modernized grid view for faster card and pass browsing.
- **Modular Architecture:** Split logic cleanly across Payments, Passes, and Identity card modules.
