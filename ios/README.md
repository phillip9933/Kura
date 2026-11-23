# Kura for iPhone

Kura is an offline SwiftUI wallet for iPhone running iOS 16 or later. A new wallet opens empty. Unlock it, choose Cards, Passes or Identity, then choose **Add item**. The app keeps wallet data on the device. The development bundle ID is `app.kura.iphone.development`.

## Wallet features

| Android wallet journey | On iPhone |
| --- | --- |
| Create and organize items | Add, edit, favorite, search, filter, sort, reorder, archive, restore from the archive, or delete. Manage categories, typed fields, visible sections, list/grid appearance, upcoming passes and expiry reminders in Settings. |
| Present and scan barcodes | Render all 21 Android output formats, choose a full-screen format and orientation, and optionally raise brightness. Scan supported symbols with the camera or choose an image. The [barcode guide](BarcodeKit/README.md) lists reader capabilities and format-specific behavior. |
| Photograph cards and recognize details | Add front, back or logo images, adjust the crop and orientation, and review on-device text recognition suggestions before applying them to empty fields. Add, preview and export attachments. |
| Create or import passes | Edit manual pass layouts and ordered field groups. Preview a .pkpass file or .pkpasses bundle, select individual passes and review duplicates. Import preserves supported issuer fields, images and formatting. Kura checks the archive manifest when present. It does not authenticate the issuer signature. |
| Export or send an item | Export an unsigned .pkpass for compatible readers, share item text through password-encrypted QR frames, or export an encrypted item file with media. Apple Wallet requires an issuer-signed pass. |
| Back up and recover | Create password-encrypted backups in Files. Configure automatic backups to a selected folder, change the password and retention, review a backup before replacing a wallet, or recover a previous local generation. Store the backup password separately. |
| Protect or erase a wallet | Device-owner authentication is the default. Settings supports device-passcode authentication, phone-unlock-only access, a background lock delay, and authenticated erasure. Files previously exported to another location remain there. |

Both platforms use the same synthetic QR fixtures. The iPhone app owns its Keychain items, vault, backup and encrypted transfer formats. Android/iPhone wallet migration is deferred. Android's standalone packages and direct APK updater apply to Android only.

## Build and test

Open `ios/Kura.xcodeproj` in Xcode, choose the **Kura** scheme and a disposable iPhone simulator, then choose **Product → Test**. The [iPhone workflow](../.github/workflows/ios.yml) runs the same scheme on a hosted Mac. To run from the repository root with the UUID of an installed simulator:

```sh
xcodebuild -project ios/Kura.xcodeproj -scheme Kura -showdestinations
xcodebuild -project ios/Kura.xcodeproj -scheme Kura \
  -destination "platform=iOS Simulator,id=$SIMULATOR_ID" \
  -parallel-testing-enabled NO test
```

Swift Package Manager resolves ZIPFoundation 0.9.20 and the ZXing-C++ 3.1.1 photo decoder at revisions pinned in the project lockfile. The app includes the licenses for [ZIPFoundation](Kura/LICENSE-ZIPFoundation.txt), [ZXing-C++](Kura/LICENSE-ZXingCpp.txt) and the [vendored Zint encoder](BarcodeKit/Notices.txt).

## Storage and authentication

The versioned wallet in Application Support uses CryptoKit AES-GCM with a fresh nonce per atomic write. It includes records, preferences, photos and attachments. The original iPhone QR-only vault can be opened and saved in the newer format. The encrypted file limit is 96 MiB. The vault directory uses complete file protection and is excluded from device backup.

The default 256-bit Keychain key is device-only, requires a device passcode and is gated by user presence. Optional phone-unlock-only mode uses a separately protected copy available while the device is unlocked. Switching access modes requires authentication. Removing the passcode can make the wallet unrecoverable without a password-encrypted export.

An opaque cover over the wallet window protects app-switcher snapshots and active screen recording or mirroring. Ordinary user screenshots of a visible wallet are possible. [Apple capture behavior](https://developer.apple.com/documentation/uikit/uiapplication/userdidtakescreenshotnotification)

Locking drops the session's records and key and invalidates pending editors. A successful restore also discards older editors. Kura keeps a previous encrypted copy before replacing a readable vault. Recovery from damaged ciphertext preserves that file before replacement. Password-encrypted backups use PBKDF2-HMAC-SHA256 with a random salt and AES-GCM. Choose a Files destination appropriate for the backup, including an on-device location if you want it to stay local.

## Acceptance on an iPhone

Use synthetic records and a disposable device. Record the device, OS and actual results in the [testing record](../docs/TESTING.md).

- Enroll biometrics, cancel an unlock, unlock with biometrics and device passcode, try phone-only mode and the background lock delay. Inspect the app-switcher snapshot over an open editor and a document picker.
- Capture front and back images, crop and rotate them, review English and Japanese recognition on representative cards, scan a separate display and read the iPhone's barcodes with another device.
- Export and restore a synthetic wallet through the intended Files provider. Reopen it, verify photos and preferences, then verify that a wrong password preserves the active wallet.
- Check the locked, list, grid, editor, pass, barcode, backup and recovery screens using light and dark appearance, portrait and landscape, large text and VoiceOver. Check focus order, system sheet dismissal and the return to the wallet.
