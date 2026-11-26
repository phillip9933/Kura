# Google Play candidate

The Play candidate includes offline card scanning. It does not include the
GitHub APK updater. It uses application ID app.kura.wallet and supports Android
8 (API 26) or later on 64-bit devices. GitHub releases use their separate APK
workflow.

## Build and checks

Run **Unsigned Google Play candidate** in GitHub Actions for the source you
want to check. The workflow uses no signing credentials. It produces an unsigned
Android App Bundle (AAB) and a checksum. It cannot submit an app to Google Play.

For the same local build, use JDK 21, SDK platform 37.0, and build-tools 36.0.0.
Run these commands from the repository root. Get bundletool 1.18.3 from its
[release page](https://github.com/google/bundletool/releases/tag/1.18.3).
Verify its checksum before you run it.

~~~sh
./gradlew -PcardScanner=true -PdirectUpdates=false :app:lintDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease
python3 -m unittest discover -s tools/tests -p 'test_verify_play_bundle.py' -v
python3 tools/verify-native-packaging.py --card-scanner
python3 tools/verify-play-bundle.py --bundletool /path/to/bundletool-all-1.18.3.jar \
  --bundle app/build/outputs/bundle/release/app-release.aab
~~~

The bundle checks do not test the APKs that Google Play generates. Test those
APKs on a device with 16 KiB pages. Use synthetic cards to check vault unlock,
card scanning, and backup and restore. Use **Settings → Privacy Policy** to
open the public policy in your browser.

## Before upload

Use Kura's existing Play Console entry. Check the version codes in all tracks.
Use a code that is higher than all prior Play uploads and the current GitHub APK
if the bundle must update that APK. Use the registered upload certificate to
sign the AAB. The Play app signing certificate is separate. Compare it with the
GitHub APK certificate before you try an in-place upgrade. Do not uninstall an
existing wallet to fix a certificate mismatch.

Kura stores card references and passes. It does not process payments or require
an account. For review, use an Android device with a secure screen lock and
synthetic wallet data. List the [public privacy policy](../PRIVACY.MD) in Play
Console. Complete the declarations for the exact build you upload.
