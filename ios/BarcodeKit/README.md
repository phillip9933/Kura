# Kura barcode encoders

The local Swift package builds the Zint 2.16.0 C backend for the iPhone app. It supplies the 21 output formats in the Android wallet, including the postal, add-on, and Telepen formats. Kura calls the in-memory encoder with bounded input and copies a grayscale raster. The build uses no optional PNG library, GS1 Syntax Engine, online service, or external Swift package.

The imported files come from upstream commit `55541e139e62b9209b71cd9b0ba9010cec28b1d9`. The SHA-256 of the official version-tag archive is `e1077fd5846bdd0128fef3ab8c3c28e93e450d2efaf1424d0aa96e0c9e514591`. The OSV commit query returned zero matching advisories on 23 November 2026. That query covers only advisories indexed for this commit. [Zint source](https://github.com/zint/zint/tree/55541e139e62b9209b71cd9b0ba9010cec28b1d9)

For an update, verify the new tag and archive hash. Copy unmodified C sources from the upstream backend CMake source lists with PNG disabled. Copy the adjacent headers and embedded font headers. Retain upstream licensing information and update the included notices. Run the iPhone barcode tests and compare independent decoder results before accepting the update.
