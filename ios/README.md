# Kura for iPhone

This development build starts with an empty wallet. Unlock it, enter a card name and its QR code text, then tap **Save card**. Open the card to display its QR code. Use synthetic cards during testing. The build has no backup or restore feature.

## Build and run

1. On a Mac with Xcode and an iPhone simulator runtime, open `ios/Kura.xcodeproj`.
2. Select the **Kura** scheme and a disposable iPhone simulator with iOS 16 or later.
3. Choose **Product → Test** to run the host tests. Choose **Product → Run** to open the app.
4. For the unlock journey, use a disposable test device or simulator that supports device-owner authentication. Set a test passcode. Kura requires authentication each time it opens the wallet.

The app uses Apple frameworks. It has no third-party packages, online account, or camera permission. Its development bundle ID is `app.kura.iphone.development`.

From the repository root, use the ID of an installed simulator:

```sh
xcodebuild -project ios/Kura.xcodeproj -scheme Kura -showdestinations
xcodebuild -project ios/Kura.xcodeproj -scheme Kura \
  -destination "platform=iOS Simulator,id=$SIMULATOR_ID" test
```

## Storage and privacy

The app stores names and QR values in a versioned file in Application Support. CryptoKit encrypts each write with AES-GCM and a fresh nonce. Kura reads the 256-bit key from a device-only Keychain item that requires a passcode and user presence. Each save replaces the encrypted file atomically. Kura requests complete file protection and excludes the wallet directory from device backup. Removing the device passcode can make the wallet inaccessible. Removing the app can delete the wallet file.

The locked screen hides card data. Locking drops the session key and the list of cards. The app also locks an open wallet when the scene becomes inactive or the system withdraws protected data. An unlock attempt that completes after the wallet locks cannot open it. A save that has started its atomic write can finish on disk. Its result cannot repopulate a locked screen. The app presents errors without logging card data.

The first version accepts printable ASCII QR values with a maximum of 512 bytes. It preserves spaces and leading zeros. It does not read Android backups. See [platform ownership](../docs/ARCHITECTURE.md) and [wallet design](../docs/design.md).

## Acceptance on Apple hardware

Use synthetic data for these checks. Record the device, OS, and actual result.

- Cancel the unlock prompt. Check that the wallet stays locked. Unlock with a device passcode and with the available biometric method.
- Save and edit a test card with leading zeros. Close the app, reopen it, and check the name and QR text. Read the displayed code with a second scanner.
- Lock from the list, switch apps from the card editor, and lock the device during unlock. Check the app switcher for exposed card data. Confirm that a new session has no unsaved draft.
- Review the locked, empty, editing, and detail screens in light and dark appearance, in portrait and landscape, with large text and VoiceOver.
