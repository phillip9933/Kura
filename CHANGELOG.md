# Changelog

All notable changes to Kura will be documented in this file.

## [1.1.0] - 2026-08-27

### Features
- Added universal APK version-code support for broader Android device compatibility.
- Added expiry dates for passes and identities, optional expiry date pickers, expiry dates in item details, expiry indicators in main grids, configurable expiry notifications, and startup expiry alerts.
- Added Apple Wallet `.pkpass` importing, sharing support, improved pass scanning, and responsive Apple Wallet-inspired pass rendering.
- Added offline-safe PDF document import and rendering support.
- Added archive storage, archive and delete actions on edit screens, and item reorder mode.
- Added automatic-backup retention settings and restored access to the auto-backup folder.
- Added barcode type selection based on scanned formats and clearer guidance for unrecognized barcode formats.
- Added settings for bottom-navigation visibility, search-bar position, control-row position, and gesture navigation.
- Added a Buy Me a Coffee link.

### Improvements
- Refactored and reorganized Settings into focused submenus for general display, navigation and search, barcode, backup, auto-backup, and expiry alerts.
- Consolidated add actions and clarified search display settings.
- Updated default item categories and renamed automatic-backup files.
- Linked the About version to application metadata.
- Improved payment and identity card ratios and aligned pass-grid spacing.
- Hardened file imports and resolved analyzer issues.

### Documentation
- Added community guidelines and issue templates.

## [1.0.0] - 2026-08-25

### Security & Hardening
- **Encrypted Local Storage:** Migrated to SQLCipher for full AES-256 database encryption at rest.
- **Hardware-Backed Keys:** Integrated Android Keystore via `flutter_secure_storage` to secure database keys.
- **PKPASS Security Hardening:** Added strict bounds, size limitations, and nested depth checks to prevent archive-based attacks (zip bombs).
- **Backup Exclusion:** Set `android:allowBackup="false"` in the manifest to completely block automated cloud backups of sensitive local vaults.
- **Release Sanitization:** Enforced strict `kDebugMode` guards around logging and error traces.

### Major Features & UI Overhauls
- **Independent Rebrand:** Transitioned project identity to **Kura** (蔵).
- **Dynamic Custom Fields:** Replaced rigid forms with flexible, user-defined schema fields.
- **Grid Layout UI:** Introduced a modernized grid view for faster card and pass browsing.
- **Modular Architecture:** Split logic cleanly across Payments, Passes, and Identity card modules.
