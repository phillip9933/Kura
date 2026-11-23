# Test Kura on your iPhone

Use an active Apple Developer Program membership, an App Store Connect account and a browser. GitHub's macOS runner builds, signs and uploads Kura. Your iPhone receives the build through TestFlight.

## Set up the app in Apple’s portals

1. In the developer account, copy the **Team ID** from Membership details. Have the Account Holder accept any pending agreement shown in App Store Connect.
2. In Certificates, Identifiers & Profiles, register an explicit App ID for iOS. The proposed bundle ID is `app.kura.wallet` if available. Use an existing Kura iOS App ID if your team already owns one. Keep that ID for subsequent builds. Apple manages this registration independently of Android. [Register an App ID](https://developer.apple.com/help/account/identifiers/register-an-app-id/)
3. In App Store Connect → Apps, create the iOS app record with that bundle ID, an available app name, your primary language and an internal SKU such as `KURA-IOS`. An Account Holder, Admin or App Manager can do this. [Create an app record](https://developer.apple.com/help/app-store-connect/create-an-app-record/add-a-new-app/)

## Prepare the signing files on Linux

If you already have a matching Apple Distribution certificate with its private key in a password-protected `.p12`, reuse it and skip to the profile step. For a new certificate, run these commands in a fresh private directory outside Git. Enter a strong key passphrase, your name and email when prompted. Leave the optional CSR challenge password blank. [OpenSSL certificate requests](https://docs.openssl.org/3.5/man1/openssl-req/)

```sh
umask 077
openssl genrsa -aes256 -out distribution.key 2048
openssl req -new -sha256 -key distribution.key -out distribution.certSigningRequest
```

As Account Holder or Admin, choose **Certificates → + → Apple Distribution** in the developer account. Upload `distribution.certSigningRequest` and download the public certificate as `distribution.cer`. Combine it with its private key locally and choose a strong export password at the prompt. Keep that password for GitHub. [Apple certificate requests](https://developer.apple.com/help/account/certificates/create-a-certificate-signing-request/) · [OpenSSL PKCS#12 export](https://docs.openssl.org/3.5/man1/openssl-pkcs12/)

```sh
openssl x509 -inform DER -in distribution.cer -out distribution.pem
openssl pkcs12 -export -inkey distribution.key -in distribution.pem \
  -keypbe PBE-SHA1-3DES -certpbe PBE-SHA1-3DES -macalg sha1 -out distribution.p12
```

In **Profiles → + → Distribution → App Store Connect**, choose Kura’s exact App ID and the same distribution certificate. Generate the profile and save it as `Kura.mobileprovision`. TestFlight handles device authorization. [Create an App Store Connect provisioning profile](https://developer.apple.com/help/account/provisioning-profiles/create-an-app-store-provisioning-profile/)

## Authorize the upload

The Account Holder can request API access in App Store Connect → Users and Access → Integrations. Once enabled, an Account Holder or Admin can create an **App Store Connect API → Team Keys** key with the **Developer** role. Team keys apply across the team's apps. Download the `.p8` once, save it as `AuthKey.p8`, and copy the **Key ID** and **Issuer ID**. Keep protected recovery copies of the signing files and API key, with the passwords stored separately. [App Store Connect API access](https://developer.apple.com/help/app-store-connect/get-started/app-store-connect-api/) · [Build upload roles](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/)

In GitHub → Kura → Settings → Rules → Rulesets, create an **active tag ruleset** for `kura-testflight-*`. Restrict creations, updates and deletions. Give bypass access only to the release maintainer(s). In Settings → Environments, create `testflight`. Under deployment branches and tags, select **Selected branches and tags** and allow only the **tag** pattern `kura-testflight-*`. Set these protections before adding credentials. [GitHub tag rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/creating-rulesets-for-a-repository) · [GitHub environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments)

Set these **environment variables** using the values from Apple.

| Variable | Value |
| --- | --- |
| `APPLE_TEAM_ID` | Developer membership Team ID |
| `IOS_BUNDLE_ID` | Explicit bundle ID on the app record and profile |
| `ASC_KEY_ID` | App Store Connect team API Key ID |
| `ASC_ISSUER_ID` | App Store Connect API Issuer ID |

Set these four **environment secrets** directly from your private directory. The password command prompts for the `.p12` export password. The two binary files use single-line base64. The API key retains its original multiline PEM format. Keep passwords, private files and two-factor codes out of chat, issues and pull requests. [GitHub CLI secret input](https://cli.github.com/manual/gh_secret_set)

```sh
base64 -w0 distribution.p12 | gh secret set APPLE_DISTRIBUTION_P12_BASE64 --env testflight --repo phillip9933/Kura
gh secret set APPLE_DISTRIBUTION_P12_PASSWORD --env testflight --repo phillip9933/Kura
base64 -w0 Kura.mobileprovision | gh secret set APPLE_PROVISIONING_PROFILE_BASE64 --env testflight --repo phillip9933/Kura
gh secret set ASC_PRIVATE_KEY --env testflight --repo phillip9933/Kura < AuthKey.p8
```

## Install the build

1. The pull request rehearses the unsigned device build. When the owner is ready to upload, the release maintainer tags the reviewed commit `kura-testflight-<build number>` and pushes that one tag. The suffix sets the build number and must be an integer from 1 to 9999 without leading zeros. For the first upload of version `0.1.0`, use `git tag kura-testflight-1 REVIEWED_COMMIT_SHA` and `git push origin refs/tags/kura-testflight-1` if build 1 is available. Substitute the exact reviewed commit SHA. Use a higher unused number for each subsequent upload. The tag push starts signing and uploading. Wait for Apple to process the upload in App Store Connect → Kura → TestFlight. [Apple build uploads](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/)
2. If Apple shows **Missing Compliance**, open **Manage** beside the build. The vault and backups use Apple CryptoKit AES-GCM. Password-based backups use CommonCrypto PBKDF2-HMAC-SHA256. Review the shipped app, its dependencies and planned territories when answering Apple's export questionnaire. The Account Holder, Admin or App Manager completes any required declaration. [Apple encryption documentation](https://developer.apple.com/help/app-store-connect/manage-app-information/determine-and-upload-app-encryption-documentation/)
3. In **TestFlight → Internal Testing**, create a group, add the processed build, then invite your own App Store Connect user with access to Kura. On your iPhone, install Apple’s TestFlight app, open the invitation, accept it and tap **Install**. Builds are available for up to 90 days. [Add internal testers](https://developer.apple.com/help/app-store-connect/test-a-beta-version/add-internal-testers/)
