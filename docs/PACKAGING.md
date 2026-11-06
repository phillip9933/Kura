# APK verification

`tools/verify-native-packaging.py --apk <file>` inspects the supplied APK. Its
manifest policy covers the package identity, distribution-specific permissions
and updater provider, disabled backup/debugging/cleartext, and the allowlist of
exported components. A generated manifest from the checkout is never a fallback.
Failure to decode the packaged manifest fails verification.

The verifier needs Python 3, JDK 21 and the Android SDK's existing Command-Line
Tools (`apkanalyzer` on PATH or under `cmdline-tools/latest`), plus build-tools
36.0.0. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT`. Android's [apkanalyzer manifest print](https://developer.android.com/tools/apkanalyzer)
decodes the packaged binary manifest to XML; this avoids maintaining a separate
binary XML parser. The verifier does not download or install tools.

The same APK is checked for ZIP/native 16 KiB alignment, optional bundled OCR
models and ABIs, baseline profiles, and the requested signing-certificate policy.
Choose `--direct-updates`, `--card-scanner` and `--expected-certificate` to match
the artifact being examined. Public identity matching does not prove that a
private signing backup can be recovered. This tool does not install an APK or
replace runtime, upgrade, device or security-scanner acceptance.

## Regressions

In an approved disposable execution environment with the tools above and SDK
platform 37.0 available:

```sh
python3 -m unittest discover -s tools/tests -p 'test_verify_native_packaging.py' -v
```

The fixture uses real `aapt2` and `zipalign` to create unsigned synthetic APKs.
It runs the verifier CLI in a synthetic checkout containing an absent or
deliberately contradictory generated manifest. Cases cover application flags,
exported services/providers, the updater provider, and malformed APK rejection.
The fixture's nonempty profile bytes exercise the existing packaging check;
they are not valid ART profiles or installable application fixtures.

For before/after evidence, set `KURA_PACKAGING_VERIFIER` to a preserved baseline
script before running the same suite. It selects only the script under test;
the Android tools and manifest path are not mocked. Missing prerequisites fail
the suite rather than silently skipping it.

The default CI configuration runs these eight tests once. The four existing
Android verification jobs, their Gradle checks and instrumentation compilation
remain in place. CI does not execute the compiled Android device tests.
