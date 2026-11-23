# Kura barcode encoders

The local Swift package builds the Zint 2.16.0 C backend for the iPhone app. It supplies the 21 output formats in the Android wallet, including the postal, add-on, and Telepen formats. Kura calls the in-memory encoder with bounded input and copies a grayscale raster. The build uses no optional PNG library, GS1 Syntax Engine, online service, or external Swift package.

The imported files come from upstream commit `55541e139e62b9209b71cd9b0ba9010cec28b1d9`. The SHA-256 of the official version-tag archive is `e1077fd5846bdd0128fef3ab8c3c28e93e450d2efaf1424d0aa96e0c9e514591`. The OSV commit query returned zero matching advisories on 23 November 2026. That query covers only advisories indexed for this commit. [Zint source](https://github.com/zint/zint/tree/55541e139e62b9209b71cd9b0ba9010cec28b1d9)

For an update, verify the new tag and archive hash. Copy unmodified C sources from the upstream backend CMake source lists with PNG disabled. Copy the adjacent headers and embedded font headers. Retain upstream licensing information and update the included notices. Run the iPhone barcode tests and compare independent decoder results before accepting the update.

## Scan and display

The live camera reads QR, Aztec, PDF417, Code 128, Data Matrix, Code 39, Code 93, EAN-8, EAN-13, UPC-E, interleaved 2 of 5, ITF-14 and Codabar when the device reports support. The photo picker decodes bounded image thumbnails with ZXing-C++ and uses Vision when the first reader finds no supported symbols. It also reads Telepen and reports GS1-128 when the symbol supplies GS1 application identifiers. If a photo contains several distinct supported codes, the user selects one. Imported text keeps significant spaces and leading zeros.

Apple can report UPC-A as EAN-13 with a leading zero. ISBN may also be reported as EAN-13. A scanner can report a GS1 symbol as Code 128. Check the application identifiers and select GS1-128 with parenthesized identifiers when a reader returns only its digits. POSTNET, RM4SCC and standalone EAN supplements can be entered manually for display. The full-screen format selector changes only the current presentation. [Apple capture behavior](https://developer.apple.com/library/archive/technotes/tn2325/_index.html)

On 23 November 2026 an isolated Linux build rendered all 21 supported formats. ZXing-C++ independently decoded 17 synthetic symbols. The four postal and EAN supplement patterns matched the Android OkapiBarcode 0.5.6 output. Seven malformed values were rejected. Check physical scanner acceptance on an iPhone for the selected display size, device, and lighting.

The iOS photo reader uses the unmodified upstream ZXing-C++ Swift package at v3.1.1, commit `287c85df6f961c8efbfb5ffd736cd9457b8b890e`. The official tag archive has SHA-256 `7286b1e6ade66fe82b7c8208b4595deeb55d6486b410834fdc65702f46650542`. Its Swift package builds C++ source and the Objective-C image reader without the embedded Zint encoder or additional package dependencies. The OSV commit lookup returned zero indexed advisories on 23 November 2026. [ZXing-C++ source](https://github.com/zxing-cpp/zxing-cpp/tree/287c85df6f961c8efbfb5ffd736cd9457b8b890e)
