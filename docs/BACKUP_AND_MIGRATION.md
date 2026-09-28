# Backup and migration

## Move your existing data into Kura 2.0

1. In the old app, create an encrypted `.wbk` backup and keep its password securely.
2. If the separate test app contains anything worth retaining, export its own backup first.
3. In the new app, open **Settings → Backup & Storage → Restore Backup**, select the old backup and enter its password on the device.
4. Review and confirm replacement, including any missing-image warning. **Restore replaces the active vault; it does not combine two vaults.**
5. Check item counts in Cards, Passes and Identity, custom fields, barcodes, images, attachments and category placement. Reconfigure biometric preferences and automatic-backup folder access where needed.
6. Keep the old app and both backups until the restored data has been checked.

The separate debug installation cannot read the production app's private storage or Keystore. Importing a backup is the only supported transfer path. No personal backup was supplied or imported during repository consolidation; automated tests use synthetic data. Never put a backup password in chat, an issue or a commit.

## Format and safety

New v4 `.wbk` files use Argon2id v19 (128 MiB, 3 iterations, parallelism 4) and AES-256-GCM. Readers retain older PBKDF2 and CBC compatibility; CBC archives lack authenticated integrity. Use backups from a trusted source and replace old backups with newly created v4 files after verifying restore.

The app bounds encoded input to 150 MiB, expanded contents to 100 MiB, JSON to 8 MiB, and an individual media/attachment file to 10 MiB. Low-memory devices can reject Argon2 processing without weakening the KDF. Validation precedes activation; wrong passwords, corrupt archives, invalid images, missing attachments and unsupported records block restoration and leave the current vault active.

Some older Flutter backups contain image paths whose files were never included: their writer omitted pass logos/footers and silently skipped unreadable images. Build 118 can stage those records and the available images, then display the number of missing image references before confirmation. Restoring clears only those unavailable image references; it cannot reconstruct absent files. Cancel leaves the current vault unchanged. The warning also appears if the incomplete staged backup is later selected for recovery. Reimport the original pass or add the missing image afterward if needed. Historical filenames with repeated `.enc` segments are matched using the old writer's naming rule.

Previous encrypted generations remain available through recovery management. Deleting an item does not erase every retained generation or exported backup. Delete inactive recovery generations explicitly when no longer needed; deleting all current app data still cannot erase externally exported files.

## Updating the production installation

Create and verify your backup **before installing 2.0 over the old app**. The update retains `app.kura.wallet`, requires the existing signing identity for that distribution, and increases the version code. It opens a fresh native vault; it does not read the old Flutter databases, preferences or plugin keys. Restore the backup explicitly after unlocking.

Old private files and keys are left untouched, but the old app UI is replaced by the update. Their presence is not a recovery workflow. Keep the exported backup independently. Existing native prototype vaults continue to use their current key envelopes, schemas and files. A correctly signed same-ID installation rehearsal remains required before publication.
