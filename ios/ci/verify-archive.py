"""Verify the device archive produced by Xcode before distribution."""

import plistlib
import re
import subprocess
import sys
from pathlib import Path


def read_plist(path):
    with path.open("rb") as source:
        return plistlib.load(source)


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def output(*command):
    return subprocess.check_output(command, text=True).strip()


def main(archive, bundle_id, build_number):
    metadata = read_plist(archive / "Info.plist")
    app = archive / "Products" / metadata["ApplicationProperties"]["ApplicationPath"]
    info = read_plist(app / "Info.plist")
    for key, expected in {
        "CFBundleIdentifier": bundle_id,
        "CFBundleVersion": build_number,
        "CFBundlePackageType": "APPL",
        "CFBundleSupportedPlatforms": ["iPhoneOS"],
        "UIDeviceFamily": [1],
        "CFBundleExecutable": "Kura",
    }.items():
        require(info.get(key) == expected, f"Unexpected {key}: {info.get(key)!r}")
    require(re.fullmatch(r"16(?:\.0){0,2}", info.get("MinimumOSVersion", "")),
            "Expected the supported minimum of iOS 16")
    require(info.get("CFBundleShortVersionString"), "Missing marketing version")
    sdk = re.fullmatch(r"iphoneos(\d+)(?:\.\d+)*", info.get("DTSDKName", ""))
    require(sdk and int(sdk[1]) >= 26 and int(info["DTXcode"]) >= 2600,
            "Expected Xcode 26 or later and iOS 26 SDK or later")
    require(info.get("CFBundleIcons", {}).get("CFBundlePrimaryIcon", {}).get("CFBundleIconName"),
            "Missing compiled iPhone app icon")
    read_plist(app / "PrivacyInfo.xcprivacy")
    executable = app / info["CFBundleExecutable"]
    require(output("lipo", "-archs", executable) == "arm64", "Expected arm64 device executable")
    load_commands = output("xcrun", "vtool", "-show-build", executable)
    require(re.search(r"^\s*platform\s+IOS$", load_commands, re.MULTILINE),
            "Expected the iOS device platform")
    symbols = archive / "dSYMs" / (app.name + ".dSYM")
    binary_uuid = output("xcrun", "dwarfdump", "--uuid", executable).split()
    symbol_uuid = output("xcrun", "dwarfdump", "--uuid", symbols).split()
    require(binary_uuid[:1] == ["UUID:"] and symbol_uuid[:3] == binary_uuid[:3],
            "Missing matching device debug symbols")
    print(f"Verified {bundle_id} {info['CFBundleShortVersionString']} ({build_number}), "
          f"arm64, {info['DTSDKName']}, minimum iOS {info['MinimumOSVersion']}")


if __name__ == "__main__":
    main(Path(sys.argv[1]), sys.argv[2], sys.argv[3])
