"""Check an unsigned Kura Play candidate bundle without signing or installing it."""

import argparse
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
FORBIDDEN_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.REQUEST_INSTALL_PACKAGES",
}
SCANNER_ABIS = {"arm64-v8a", "x86_64"}


def verify_manifest(xml, expected_version_code=None):
    manifest = ET.fromstring(xml)
    if manifest.get("package") != "app.kura.wallet":
        raise ValueError("Unexpected Play application ID")
    sdk = manifest.find("uses-sdk")
    if (
        sdk is None
        or sdk.get(ANDROID + "minSdkVersion") != "26"
        or int(sdk.get(ANDROID + "targetSdkVersion", "0")) < 36
    ):
        raise ValueError("Expected minSdk 26 and targetSdk 36 or higher")
    code = int(manifest.get(ANDROID + "versionCode", "0"))
    if code < 1:
        raise ValueError("Missing Android version code")
    if expected_version_code is not None and code != expected_version_code:
        raise ValueError(f"Expected version code {expected_version_code}, got {code}")
    for item in manifest:
        if item.tag.startswith("uses-permission"):
            name = item.get(ANDROID + "name")
            if name in FORBIDDEN_PERMISSIONS:
                raise ValueError(f"Forbidden Play candidate permission: {name}")
    application = manifest.find("application")
    if (
        application is None
        or application.get(ANDROID + "allowBackup") != "false"
        or application.get(ANDROID + "usesCleartextTraffic") != "false"
        or application.get(ANDROID + "debuggable", "false") != "false"
    ):
        raise ValueError("Unexpected release application flags")
    if application.get(ANDROID + "name", "").endswith("KuraUpdateApplication"):
        raise ValueError("GitHub updater application is not allowed in the Play bundle")
    for provider in application.findall("provider"):
        if (
            provider.get(ANDROID + "name", "").endswith("UpdateFileProvider")
            or provider.get(ANDROID + "authorities") == "app.kura.wallet.android-updates"
        ):
            raise ValueError("GitHub updater provider is not allowed in the Play bundle")
    print(f"Offline release package verified, version code {manifest.get(ANDROID + 'versionCode')}")


def verify_archive(bundle):
    with zipfile.ZipFile(bundle) as archive:
        files = set(archive.namelist())
    signatures = {name for name in files
                  if name.startswith("META-INF/") and name.upper().endswith((".RSA", ".DSA", ".EC", ".SF"))}
    if signatures:
        raise ValueError("Candidate bundle must be unsigned")
    models = {f"base/assets/card-ocr/{language}.traineddata" for language in ("eng", "jpn")}
    if not models.issubset(files):
        raise ValueError("Offline OCR models are missing from the bundle")
    libraries = [name.split("/") for name in files if name.endswith(".so")]
    abis = {parts[2] for parts in libraries if len(parts) == 4 and parts[1] == "lib"}
    if abis != SCANNER_ABIS:
        raise ValueError(f"Expected only the scanner's 64-bit ABIs, got {sorted(abis)}")


def verify_alignment(config):
    if not re.search(r"\bPAGE_ALIGNMENT_16K\b", config):
        raise ValueError("Bundle does not request 16 KiB APK alignment")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundletool", required=True, type=Path)
    parser.add_argument("--bundle", required=True, type=Path)
    parser.add_argument("--expected-version-code", type=int)
    args = parser.parse_args()
    run = ["java", "-jar", str(args.bundletool)]
    bundle = f"--bundle={args.bundle}"
    subprocess.run([*run, "validate", bundle], check=True)
    verify_manifest(subprocess.check_output(
        [*run, "dump", "manifest", bundle, "--module=base"], text=True
    ), args.expected_version_code)
    verify_alignment(subprocess.check_output([*run, "dump", "config", bundle], text=True))
    verify_archive(args.bundle)
    print("Unsigned enhanced-scanner bundle and 16 KiB packaging request verified")


if __name__ == "__main__":
    main()
