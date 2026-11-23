#!/bin/bash
# Run from the protected TestFlight job on a disposable macOS runner.
set +x
set -euo pipefail
umask 077

fail() { echo "$1" >&2; exit 1; }
[[ $# == 0 && ${GITHUB_ACTIONS:-} == true && ${RUNNER_OS:-} == macOS ]] ||
    fail "Run this script from the TestFlight workflow."
for name in APPLE_DISTRIBUTION_P12_BASE64 APPLE_DISTRIBUTION_P12_PASSWORD \
    APPLE_PROVISIONING_PROFILE_BASE64 ASC_PRIVATE_KEY APPLE_TEAM_ID IOS_BUNDLE_ID \
    ASC_KEY_ID ASC_ISSUER_ID TESTFLIGHT_BUILD_NUMBER RUNNER_TEMP; do
    [[ -n ${!name:-} ]] || fail "Missing $name."
done

ci_dir=$(cd "$(dirname "$0")" && pwd)
project="$ci_dir/../Kura.xcodeproj"
temp=$(mktemp -d "$RUNNER_TEMP/kura-testflight.XXXXXX")
keychain="$temp/signing.keychain-db"
installed_profile=""
search_list_changed=false
original_keychains=()

cleanup() {
    status=$?
    set +e
    if [[ $search_list_changed == true ]]; then
        security list-keychains -d user -s "${original_keychains[@]}" >/dev/null || status=1
    fi
    if [[ -f $keychain ]]; then security delete-keychain "$keychain" >/dev/null || status=1; fi
    if [[ -n $installed_profile ]]; then rm -f "$installed_profile" || status=1; fi
    rm -rf "$temp" || status=1
    exit "$status"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

python3 - "$temp" <<'PY'
import base64
import os
import re
import sys
from pathlib import Path

directory = Path(sys.argv[1])
patterns = {
    "APPLE_TEAM_ID": r"[A-Z0-9]{10}",
    "ASC_KEY_ID": r"[A-Z0-9]{10}",
    "ASC_ISSUER_ID": r"[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}",
    "IOS_BUNDLE_ID": r"[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+",
    "TESTFLIGHT_BUILD_NUMBER": r"[1-9][0-9]{0,3}",
}
for name, pattern in patterns.items():
    if not re.fullmatch(pattern, os.environ[name]):
        sys.exit(f"Invalid {name}.")
for name, filename in (
    ("APPLE_DISTRIBUTION_P12_BASE64", "identity.p12"),
    ("APPLE_PROVISIONING_PROFILE_BASE64", "store.mobileprovision"),
):
    try:
        contents = base64.b64decode("".join(os.environ[name].split()), validate=True)
    except ValueError:
        sys.exit(f"Invalid base64 in {name}.")
    (directory / filename).write_bytes(contents)
(directory / ("AuthKey_" + os.environ["ASC_KEY_ID"] + ".p8")).write_text(os.environ["ASC_PRIVATE_KEY"].strip() + "\n")
PY
unset APPLE_DISTRIBUTION_P12_BASE64 APPLE_PROVISIONING_PROFILE_BASE64 ASC_PRIVATE_KEY
upload_key="$temp/AuthKey_${ASC_KEY_ID}.p8"
openssl pkey -in "$upload_key" -passin pass: -noout 2>/dev/null ||
    fail "ASC_PRIVATE_KEY must contain the original App Store Connect private key in PEM format."

security list-keychains -d user > "$temp/search-list"
while IFS= read -r path; do
    original_keychains+=("$path")
done < <(sed -E 's/^[[:space:]]*"//;s/"[[:space:]]*$//' "$temp/search-list")
[[ -n ${original_keychains[0]:-} ]] || fail "Cannot read the runner's keychain search list."
password=$(openssl rand -hex 32)
search_list_changed=true
security create-keychain -p "$password" "$keychain"
security set-keychain-settings -lut 3600 "$keychain"
security unlock-keychain -p "$password" "$keychain"
security list-keychains -d user -s "$keychain" "${original_keychains[@]}"
security import "$temp/identity.p12" -k "$keychain" -f pkcs12 \
    -P "$APPLE_DISTRIBUTION_P12_PASSWORD" -T /usr/bin/codesign >/dev/null 2>&1 ||
    fail "Cannot import the distribution identity. Check the PKCS#12 file and its password."
unset APPLE_DISTRIBUTION_P12_PASSWORD
security set-key-partition-list -S apple-tool:,apple: -s -k "$password" "$keychain" >/dev/null
unset password
rm "$temp/identity.p12"
security find-identity -v -p codesigning "$keychain" > "$temp/identities"
security cms -D -i "$temp/store.mobileprovision" -o "$temp/profile.plist" 2>/dev/null ||
    fail "Cannot decode the App Store provisioning profile."

python3 - "$temp" <<'PY'
import datetime
import hashlib
import os
import plistlib
import re
import sys
import uuid
from pathlib import Path

directory = Path(sys.argv[1])
profile = plistlib.loads((directory / "profile.plist").read_bytes())
team, bundle = os.environ["APPLE_TEAM_ID"], os.environ["IOS_BUNDLE_ID"]
entitlements = profile["Entitlements"]
if (team not in profile["TeamIdentifier"] or entitlements.get("com.apple.developer.team-identifier") != team
        or entitlements.get("application-identifier") not in {
            prefix + "." + bundle for prefix in profile["ApplicationIdentifierPrefix"]
        }):
    sys.exit("The provisioning profile does not match the selected team and explicit bundle ID.")
if ("iOS" not in profile["Platform"] or "ProvisionedDevices" in profile or profile.get("ProvisionsAllDevices")
        or entitlements.get("get-task-allow") is not False or entitlements.get("beta-reports-active") is not True):
    sys.exit("Provide an App Store Connect distribution profile for iOS.")
if profile["ExpirationDate"].replace(tzinfo=datetime.timezone.utc) <= datetime.datetime.now(datetime.timezone.utc):
    sys.exit("The App Store provisioning profile has expired.")
allowed = {hashlib.sha1(cert).hexdigest().upper() for cert in profile["DeveloperCertificates"]}
available = set(re.findall(r'^\s*\d+\) ([A-F0-9]{40}) "Apple Distribution:',
                           (directory / "identities").read_text(), re.MULTILINE))
matches = allowed & available
if len(matches) != 1:
    sys.exit("Provide one valid Apple Distribution identity listed in this profile, including its private key.")
identity = matches.pop()
profile_id = str(uuid.UUID(profile["UUID"])).upper()
(directory / "identity").write_text(identity)
(directory / "profile-id").write_text(profile_id)
(directory / "export.plist").write_bytes(plistlib.dumps({
    "method": "app-store-connect",
    "destination": "upload",
    "testFlightInternalTestingOnly": False,
    "manageAppVersionAndBuildNumber": False,
    "signingStyle": "manual",
    "signingCertificate": identity,
    "teamID": team,
    "provisioningProfiles": {bundle: profile_id},
}))
PY

profile_id=$(cat "$temp/profile-id")
identity=$(cat "$temp/identity")
profiles="$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"
mkdir -p "$profiles"
# Create an empty file exclusively before copying so cleanup owns only this run's profile.
(set -C; : > "$profiles/$profile_id.mobileprovision") ||
    fail "Cannot create a new provisioning profile on the disposable runner."
installed_profile="$profiles/$profile_id.mobileprovision"
cp "$temp/store.mobileprovision" "$installed_profile"
archive="$temp/Kura.xcarchive"
xcodebuild -quiet -project "$project" -scheme Kura -configuration Release \
    -destination 'generic/platform=iOS' -archivePath "$archive" -derivedDataPath "$temp/build" \
    -onlyUsePackageVersionsFromResolvedFile KURA_SIGNING_STYLE=Manual \
    "KURA_SIGNING_TEAM=$APPLE_TEAM_ID" "KURA_SIGNING_IDENTITY=$identity" \
    "KURA_SIGNING_PROFILE=$profile_id" "KURA_BUNDLE_ID=$IOS_BUNDLE_ID" \
    "KURA_BUILD_NUMBER=$TESTFLIGHT_BUILD_NUMBER" archive > "$temp/archive.log" 2>&1 ||
    fail "The signed archive failed. Verify the distribution certificate, profile and Release configuration."

python3 "$ci_dir/verify-archive.py" "$archive" "$IOS_BUNDLE_ID" "$TESTFLIGHT_BUILD_NUMBER"
app="$archive/Products/Applications/Kura.app"
codesign --verify --deep --strict "$app"
(cd "$temp" && codesign -d --extract-certificates "$app" 2>/dev/null)
actual_identity=$(shasum -a 1 "$temp/codesign0" | awk '{print toupper($1)}')
[[ $actual_identity == "$identity" ]] || fail "Unexpected signing identity in archive."
cmp -s "$app/embedded.mobileprovision" "$installed_profile" || fail "Unexpected embedded provisioning profile."

echo "Uploading the verified archive to App Store Connect for TestFlight."
if ! xcodebuild -exportArchive -archivePath "$archive" -exportPath "$temp/export" \
    -exportOptionsPlist "$temp/export.plist" -authenticationKeyPath "$upload_key" \
    -authenticationKeyID "$ASC_KEY_ID" -authenticationKeyIssuerID "$ASC_ISSUER_ID" \
    > "$temp/upload.log" 2>&1; then
    # Apple's full logs may include the account name. Expose only diagnostic codes in public CI.
    grep -Eo '(ITMS|STATE_ERROR)-[0-9]+' "$temp/upload.log" | sort -u >&2 || true
    fail "Upload failed. Check the App Store Connect app record, key permissions, build number and agreements."
fi
echo "Upload accepted. TestFlight processing continues in App Store Connect."
