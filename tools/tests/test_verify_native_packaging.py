"""Exercise the verifier CLI against real compiled APK manifests, without Gradle.

Requires JDK and the documented Android SDK tools. Run only in an approved
disposable environment. KURA_PACKAGING_VERIFIER selects a baseline script for
before/after regression evidence; no verifier behavior is mocked.
"""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile


ANDROID = "{http://schemas.android.com/apk/res/android}"
ET.register_namespace("android", ANDROID[1:-1])


def manifest(*, direct_updates=False):
    document = ET.Element("manifest", {"package": "app.kura.wallet"})
    ET.SubElement(document, "uses-sdk", {
        ANDROID + "minSdkVersion": "26" if direct_updates else "24",
        ANDROID + "targetSdkVersion": "36",
    })
    if direct_updates:
        for permission in ("INTERNET", "ACCESS_NETWORK_STATE", "REQUEST_INSTALL_PACKAGES"):
            ET.SubElement(document, "uses-permission", {
                ANDROID + "name": "android.permission." + permission,
            })
    application = ET.SubElement(document, "application", {
        ANDROID + "allowBackup": "false",
        ANDROID + "debuggable": "false",
        ANDROID + "usesCleartextTraffic": "false",
        ANDROID + "label": "Synthetic verifier fixture",
    })
    ET.SubElement(application, "activity", {
        ANDROID + "name": "app.kura.nativeapp.MainActivity",
        ANDROID + "exported": "true",
    })
    if direct_updates:
        ET.SubElement(application, "provider", {
            ANDROID + "name": "io.github.phillip9933.appupdater.UpdateFileProvider",
            ANDROID + "authorities": "app.kura.wallet.android-updates",
            ANDROID + "exported": "false",
        })
    return document


class PackagedManifestTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk_root:
            raise RuntimeError("Set ANDROID_HOME to the SDK inside the approved disposable environment")
        cls.sdk = Path(sdk_root)
        suffix = ".exe" if os.name == "nt" else ""
        cls.aapt = cls.sdk / "build-tools/36.0.0" / ("aapt2" + suffix)
        cls.zipalign = cls.sdk / "build-tools/36.0.0" / ("zipalign" + suffix)
        cls.platform = cls.sdk / "platforms/android-37.0/android.jar"
        analyzer_name = "apkanalyzer.bat" if os.name == "nt" else "apkanalyzer"
        cls.analyzer = Path(shutil.which(analyzer_name) or cls.sdk / "cmdline-tools/latest/bin" / analyzer_name)
        for required in (cls.aapt, cls.zipalign, cls.platform, cls.analyzer):
            if not required.is_file():
                raise RuntimeError("Missing required SDK fixture tool: " + str(required))
        cls.verifier = Path(os.environ.get(
            "KURA_PACKAGING_VERIFIER",
            str(Path(__file__).resolve().parents[1] / "verify-native-packaging.py"),
        ))

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="kura-apk-verifier-")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.checkout = self.directory / "checkout"
        tools = self.checkout / "tools"
        tools.mkdir(parents=True)
        shutil.copyfile(self.verifier, tools / "verify-native-packaging.py")
        self.local_manifest = self.checkout / (
            "app/build/intermediates/merged_manifests/release/"
            "processReleaseManifest/AndroidManifest.xml")
        self.local_manifest.parent.mkdir(parents=True)
        profile = self.checkout / "app/src/main/baseline-prof.txt"
        profile.parent.mkdir(parents=True)
        profile.write_text("Synthetic profile fixture\n", encoding="utf-8")

    def verify(self, packaged, local, *, direct_updates=False):
        if local is not None:
            ET.ElementTree(local).write(self.local_manifest, encoding="utf-8")
        source = self.directory / "AndroidManifest.xml"
        ET.ElementTree(packaged).write(source, encoding="utf-8")
        unaligned = self.directory / "unaligned.apk"
        apk = self.directory / "supplied.apk"
        subprocess.run([
            str(self.aapt), "link", "-I", str(self.platform),
            "--manifest", str(source), "-o", str(unaligned),
        ], check=True, capture_output=True, text=True)
        # These bytes satisfy the unchanged nonempty-profile packaging check.
        # The fixture does not claim to be an installable app or valid ART profile.
        with zipfile.ZipFile(unaligned, "a") as archive:
            archive.writestr("assets/dexopt/baseline.prof", b"synthetic profile")
            archive.writestr("assets/dexopt/baseline.profm", b"synthetic metadata")
        subprocess.run([
            str(self.zipalign), "-f", "-P", "16", "4", str(unaligned), str(apk),
        ], check=True, capture_output=True, text=True)
        command = [sys.executable, str(self.checkout / "tools/verify-native-packaging.py"), "--apk", str(apk)]
        if direct_updates:
            command.append("--direct-updates")
        return subprocess.run(command, capture_output=True, text=True, timeout=60)

    def assert_accepted(self, result):
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Release backup/debugging disabled; exported components restricted", result.stdout)

    def test_safe_apk_does_not_need_a_checkout_manifest(self):
        self.assert_accepted(self.verify(manifest(), None))

    def test_unsafe_checkout_manifest_cannot_reject_safe_apk(self):
        local = manifest()
        local.find("application").set(ANDROID + "allowBackup", "true")
        self.assert_accepted(self.verify(manifest(), local))

    def test_benign_checkout_cannot_hide_unsafe_application_flags(self):
        for attribute in ("allowBackup", "debuggable", "usesCleartextTraffic"):
            with self.subTest(attribute=attribute):
                packaged = manifest()
                packaged.find("application").set(ANDROID + attribute, "true")
                result = self.verify(packaged, manifest())
                self.assertNotEqual(0, result.returncode, attribute + ": " + result.stdout + result.stderr)
                self.assertIn("AssertionError", result.stderr)

    def test_benign_checkout_cannot_hide_exported_component(self):
        packaged = manifest()
        ET.SubElement(packaged.find("application"), "service", {
            ANDROID + "name": "app.kura.synthetic.UnexpectedService",
            ANDROID + "exported": "true",
        })
        result = self.verify(packaged, manifest())
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("UnexpectedService", result.stderr)

    def test_benign_checkout_cannot_hide_exported_provider(self):
        packaged = manifest()
        ET.SubElement(packaged.find("application"), "provider", {
            ANDROID + "name": "app.kura.synthetic.UnexpectedProvider",
            ANDROID + "authorities": "app.kura.synthetic.provider",
            ANDROID + "exported": "true",
        })
        result = self.verify(packaged, manifest())
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("AssertionError", result.stderr)

    def test_direct_update_provider_comes_from_supplied_apk(self):
        self.assert_accepted(self.verify(manifest(direct_updates=True), manifest(), direct_updates=True))

    def test_checkout_update_provider_cannot_hide_missing_packaged_provider(self):
        packaged = manifest(direct_updates=True)
        application = packaged.find("application")
        application.remove(application.find("provider"))
        result = self.verify(packaged, manifest(direct_updates=True), direct_updates=True)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("AssertionError", result.stderr)

    def test_packaged_xml_failure_cannot_fall_back_to_checkout(self):
        # A valid ZIP can still lack the compiled manifest. zipalign will accept
        # this input; the Android manifest reader must fail closed.
        ET.ElementTree(manifest()).write(self.local_manifest, encoding="utf-8")
        apk = self.directory / "missing-manifest.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            archive.writestr("unrelated.txt", "synthetic")
        result = subprocess.run([
            sys.executable, str(self.checkout / "tools/verify-native-packaging.py"), "--apk", str(apk),
        ], capture_output=True, text=True, timeout=60)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
