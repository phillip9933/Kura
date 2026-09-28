"""Verify native ELF load alignment and APK permissions without installing release."""
import struct, zipfile, subprocess, pathlib, os
import xml.etree.ElementTree as ET
root = pathlib.Path(__file__).resolve().parent
apk = root / "app/build/outputs/apk/release/app-release-unsigned.apk"
sdk_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
if not sdk_home:
    sdk_home = str(pathlib.Path(os.environ["LOCALAPPDATA"]) / "Android/sdk")
sdk = pathlib.Path(sdk_home) / "build-tools/36.0.0"
exe = ".exe" if os.name == "nt" else ""
subprocess.run([str(sdk / ("zipalign" + exe)), "-c", "-P", "16", "4", str(apk)], check=True)
badging = subprocess.check_output([str(sdk / ("aapt2" + exe)), "dump", "badging", str(apk)], text=True)
assert "android.permission.INTERNET" not in badging
assert "package: name='app.kura.wallet'" in badging
print("Release ID preserved; INTERNET absent")
# Check the merged manifest from this release build, including library-contributed components.
manifest = ET.parse(root / "app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml").getroot()
a = "{http://schemas.android.com/apk/res/android}"
application = manifest.find("application")
assert application is not None
assert application.get(a + "allowBackup") == "false"
assert application.get(a + "debuggable", "false") == "false"
assert application.get(a + "usesCleartextTraffic") == "false"
for component in application:
    if component.tag not in ("activity", "activity-alias", "provider", "service", "receiver"):
        continue
    if component.tag == "provider":
        assert component.get(a + "exported") == "false"
    if component.get(a + "exported") == "true":
        name = component.get(a + "name")
        assert name in ("app.kura.nativeapp.MainActivity", "androidx.profileinstaller.ProfileInstallReceiver"), name
        if name.endswith("ProfileInstallReceiver"):
            assert component.get(a + "permission") == "android.permission.DUMP"
print("Release backup/debugging disabled; exported components restricted")
with zipfile.ZipFile(apk) as archive:
    if (root / "app/src/main/baseline-prof.txt").is_file():
        for profile in ("assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm"):
            assert archive.getinfo(profile).file_size > 0
            print(profile, archive.getinfo(profile).file_size, "bytes")
    for name in archive.namelist():
        if not name.endswith(".so"):
            continue
        data = archive.read(name)
        assert data[:4] == b"\x7fELF"
        endian = "<" if data[5] == 1 else ">"
        wide = data[4] == 2
        offset = struct.unpack_from(endian + ("Q" if wide else "I"), data, 32 if wide else 28)[0]
        size, count = struct.unpack_from(endian + "HH", data, 54 if wide else 42)
        alignments = []
        for i in range(count):
            pos = offset + i * size
            if struct.unpack_from(endian + "I", data, pos)[0] == 1:
                alignment = struct.unpack_from(endian + ("Q" if wide else "I"), data, pos + (48 if wide else 28))[0]
                assert alignment >= 16384, (name, alignment)
                alignments.append(alignment)
        print(name, "PT_LOAD", alignments)
    assert not any(n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC")) for n in archive.namelist())
print("Native libraries are 16 KiB compatible; release has no JAR signature")
