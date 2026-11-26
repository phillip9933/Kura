# Google Play candidate

The Play candidate includes offline card scanning. It does not include the
GitHub APK updater. It uses application ID `app.kura.wallet.play` and supports
Android 8 (API 26) or later on 64-bit devices. The GitHub APK keeps application
ID `app.kura.wallet` and its existing signing requirements.

The two packages install separately and cannot share private files or
Android Keystore keys. To move a wallet, use the
[encrypted backup procedure](BACKUP_AND_MIGRATION.md#move-from-the-github-apk-to-the-play-app).
Do not uninstall the GitHub app to move to Play.

## Build and checks

**Unsigned Google Play candidate** runs on pull requests that change Android
build inputs. It uses no signing credentials. It produces an unsigned Android
App Bundle (AAB) and a checksum. It cannot submit an app to Google Play. After
the workflow is on the default branch, run it manually for an approved source.

For the same local build, use JDK 21, SDK platform 37.0, and build-tools 36.0.0.
Run these commands from the repository root. Get bundletool 1.18.3 from its
[release page](https://github.com/google/bundletool/releases/tag/1.18.3).
Verify its checksum before you run it.

~~~sh
./gradlew -PplayDistribution=true -PcardScanner=true -PdirectUpdates=false :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleRelease :app:bundleRelease
python3 -m unittest discover -s tools/tests -p 'test_verify_play_bundle.py' -v
python3 tools/verify-native-packaging.py --play --card-scanner
python3 tools/verify-play-bundle.py --bundletool /path/to/bundletool-all-1.18.3.jar \
  --bundle app/build/outputs/bundle/release/app-release.aab
~~~

The bundle checks do not test the APKs that Google Play generates. Test those
APKs on a device with 16 KiB pages. Use synthetic cards to check vault unlock,
card scanning, and backup and restore. Use **Settings → Privacy Policy** to
open the public policy in your browser.

## Before upload

Confirm that Play Console accepts `app.kura.wallet.play` for the new entry.
Do not use the old, deleted Play entry for `app.kura.wallet`. Check the new
entry's code history. Use the source code if it is unused and higher than all
previous codes for the new Play entry. If you need a higher code, enter it in
the manual workflow's **play-version-code** input. For a local build, use the
Gradle property `playVersionCode`. The override works only with
`playDistribution=true` and must be at most 2100000000.

Use the registered upload certificate to sign the AAB. The Play app signing
certificate is separate. Record the certificates for the new Play entry. Continue to verify the original release certificate for GitHub
APK updates. The different package IDs do not support an in-place upgrade from
the GitHub app to the Play app, even if they use the same certificate.

Kura stores card references and passes. It does not process payments or require
an account. For review, use an Android device with a secure screen lock and
synthetic wallet data. List the [public privacy policy](../PRIVACY.MD) in Play
Console. Complete the declarations for the exact build you upload.
