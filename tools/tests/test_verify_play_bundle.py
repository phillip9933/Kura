import runpy
import tempfile
import unittest
import zipfile
from pathlib import Path

CHECKS = runpy.run_path(str(Path(__file__).resolve().parents[1] / "verify-play-bundle.py"))
MANIFEST = """<manifest package="app.kura.wallet.play"
    xmlns:android="http://schemas.android.com/apk/res/android" android:versionCode="123">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
    <application android:allowBackup="false" android:usesCleartextTraffic="false">
        <provider android:authorities="app.kura.wallet.play.files" android:exported="false"/>
        <provider android:authorities="app.kura.wallet.play.attachments" android:exported="false"/>
    </application>
</manifest>"""


class PlayBundleVerifierTest(unittest.TestCase):
    def test_accepts_offline_manifest(self):
        CHECKS["verify_manifest"](MANIFEST)

    def test_rejects_update_permissions_in_both_manifest_forms(self):
        for tag in ("uses-permission", "uses-permission-sdk-23"):
            for permission in ("INTERNET", "ACCESS_NETWORK_STATE", "REQUEST_INSTALL_PACKAGES"):
                with self.subTest(tag=tag, permission=permission), self.assertRaisesRegex(ValueError, permission):
                    entry = f'<{tag} android:name="android.permission.{permission}"/>'
                    CHECKS["verify_manifest"](MANIFEST.replace("<uses-sdk ", entry + "<uses-sdk "))

    def test_rejects_updater_components(self):
        for replacement in (
            '<application android:name=".KuraUpdateApplication"',
            '<application android:debuggable="true"',
        ):
            with self.subTest(replacement=replacement), self.assertRaises(ValueError):
                CHECKS["verify_manifest"](MANIFEST.replace("<application", replacement))
        provider = '<provider android:authorities="app.kura.wallet.android-updates"/>'
        xml = MANIFEST.replace("</application>", provider + "</application>")
        with self.assertRaisesRegex(ValueError, "updater provider"):
            CHECKS["verify_manifest"](xml)

    def test_rejects_non_play_identity_and_target(self):
        for replacement in (
            MANIFEST.replace('package="app.kura.wallet.play"', 'package="app.kura.wallet"'),
            MANIFEST.replace('package="app.kura.wallet.play"', 'package="app.kura.wallet.play.prototype"'),
            MANIFEST.replace('targetSdkVersion="36"', 'targetSdkVersion="35"'),
        ):
            with self.subTest(replacement=replacement), self.assertRaises(ValueError):
                CHECKS["verify_manifest"](replacement)

    def test_provider_authorities_stay_separate_from_github(self):
        for authority in ("app.kura.wallet.files", "app.kura.wallet.play.wrong"):
            with self.subTest(authority=authority), self.assertRaises(ValueError):
                CHECKS["verify_manifest"](MANIFEST.replace("app.kura.wallet.play.files", authority))

    def test_checks_an_expected_version_code(self):
        CHECKS["verify_manifest"](MANIFEST, expected_version_code=123)
        with self.assertRaisesRegex(ValueError, "Expected version code 124"):
            CHECKS["verify_manifest"](MANIFEST, expected_version_code=124)

    def test_requires_16k_alignment(self):
        CHECKS["verify_alignment"]('{"alignment": "PAGE_ALIGNMENT_16K"}')
        with self.assertRaisesRegex(ValueError, "16 KiB"):
            CHECKS["verify_alignment"]('{"alignment": "PAGE_ALIGNMENT_4K"}')

    def test_requires_unsigned_bundle_and_scanner_payload(self):
        files = {
            "base/assets/card-ocr/eng.traineddata", "base/assets/card-ocr/jpn.traineddata",
            "base/lib/arm64-v8a/libscanner.so", "base/lib/x86_64/libscanner.so",
        }
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory) / "candidate.aab"
            for variant, message in (
                (files, None),
                (files | {"META-INF/APP.RSA"}, "unsigned"),
                (files - {"base/assets/card-ocr/jpn.traineddata"}, "models"),
                (files | {"base/lib/armeabi-v7a/libscanner.so"}, "64-bit"),
            ):
                with self.subTest(message=message):
                    with zipfile.ZipFile(bundle, "w") as archive:
                        for name in variant:
                            archive.writestr(name, b"synthetic fixture")
                    if message:
                        with self.assertRaisesRegex(ValueError, message):
                            CHECKS["verify_archive"](bundle)
                    else:
                        CHECKS["verify_archive"](bundle)


if __name__ == "__main__":
    unittest.main()
