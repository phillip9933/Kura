# Card scanning and recognition

## Pinned integration

The optional enhanced-scanning build pins [scanner 0.8.0](https://github.com/phillip9933/open-android-doc-scanner/releases/tag/v0.8.0), revision `e74d8cd8efd838f38820ee8ca48bc7ea765b2a8c`. This stable SDK retains the tested beta's AAR bytes; the core JAR's archive hash changes while all entry payloads remain identical. Upstream promotion does not establish Kura's photographed-card accuracy or host data-safety acceptance. Editable/manual recovery remains necessary. See the [pin review](evidence/stable-sdk-pins-20261107.json).

Build with `./gradlew -PcardScanner=true :app:assembleDebug`. This explicitly selects API 26+, arm64-v8a/x86_64, processing and OCR dependencies and bundled pinned English/Japanese models. The base build remains API 24 without scanner dependencies/models. `-PdirectUpdates=true` is independent and may be combined; scanning adds no network permissions or runtime downloads. SDK Maven artifacts, source JARs, checksums, provenance, licenses and notices are in `third_party/card-scanner/`; model hashes are checked at build time, packaging verification and before OCR. Use `python3 tools/verify-native-packaging.py --card-scanner` after the enhanced unsigned release build (add `--direct-updates` when selected).

### Import flow

In a card form, add a front/back image from the existing camera or photo picker. Automatic edge detection proposes a perspective-aligned photo. A stationary mask shows the saved frame: move, zoom and rotate the photo underneath it, with the preview always visible. Labelled movement buttons, zoom and fine-angle sliders/buttons, and quarter-turn presets complement pan/pinch gestures. **Auto fit** restores the automatic proposal; **Full photo** restores the original capture. Confirm the crop before recognition. Front/back image targets retain their existing meaning; each side is imported separately. Logos and barcode scanning keep their existing flows.

After crop confirmation, **Autofill card details?** offers **Review autofill** or **Use image only**. OCR runs only after that opt-in. Whole-card English/Japanese sparse recognition maps explicitly labelled values to empty basic/custom fields already displayed by the current form; it creates no field definitions, guesses no card type and never writes arbitrary OCR keys. Unlabelled text and competing values are left for manual entry. Identifiers remain strings; no O/0, I/1 or 8/3 substitution is performed. Date/custom-field validation applies before selected suggestions can be used.

The review shows editable, individually selectable suggestions and a cropped preview. Existing filled values are excluded and checked again at application time. **Apply selected details and use image** encrypts the image into the existing draft-media path and updates only allowed empty draft fields. The form's separate **Save** publishes the record. Cancelling the import changes neither fields nor images. Existing-record image replacement uses image-only confirmation; OCR autofill is available in an editable form. Declining or failed recognition retains image/manual entry. Barcode values are deliberately excluded from OCR suggestions to avoid confusing a printed identifier with encoded content.

### Ownership and limits

`CardScanEngine` owns bounded private operation workspaces for input/output rasters and verified model/job copies. It uses the published processing/OCR API on IO workers inside the current vault session; callbacks cannot write after lock/session cancellation. Native OCR is blocking: cancellation cleanup can wait until the current native call returns. Operation directories are deleted in `finally`; abandoned scanner cache is removed before the next vault unlock. Bitmaps and owned mutable image buffers are cleared on disposal. OCR strings/drafts remain session memory only and are never logged or saved to Android state.

Input is at most 10 MiB/16 MP through the existing capture normalizer; processing/OCR output is capped at 2048 pixels/4 MP. Scan mode uses Original rendering to retain colored/light card details. The SDK adds native memory overhead. Blur/glare feedback is currently instructional with crop review/retake, not a calibrated automatic quality score. No duplicate-card warning, barcode/OCR reconciliation, automatic capture/video edge overlay, unknown-layout inference or selected-field region UI is implemented in this first integration. Japanese models are bundled, but Kura-specific Japanese real-camera quality and physical-device acceptance still need evidence. See [Testing](TESTING.md).

## Historical discovery

Decision for discovery issue [#11](https://github.com/phillip9933/Kura/issues/11), 26 October 2026. This is a reviewed integration direction and measured research prototype, not a shipped scanner/OCR feature or a commitment to include it in the updater/unlock release.

## Ownership

Use the independent scanner for camera/import, page detection, perspective crop, rotation, image enhancement and the optional offline OCR engine. Keep OCR generic: text, word confidence and image-relative bounds, without wallet models or field inference. Kura owns card-field suggestions, validation, user correction/confirmation, record selection, encrypted persistence and backup policy. Do not duplicate an OCR engine inside Kura or let a scanner automatically create wallet records.

At discovery time, published [scanner v0.7.0](https://github.com/phillip9933/open-android-doc-scanner/releases/tag/v0.7.0) is Apache-2.0 and provides scanning/export, not OCR/card extraction. Its [host contract](https://github.com/phillip9933/open-android-doc-scanner/blob/v0.7.0/docs/INTEGRATION.md) supplies completed/cancelled/failed outcomes and host-owned output files. Optional English/Japanese OCR is tracked upstream in [scanner #1](https://github.com/phillip9933/open-android-doc-scanner/issues/1); the then-local SNAPSHOT was not a published dependency or a reason to follow a sibling checkout. Audit the exact released OCR engine, models and notices when a consumable version exists.

## Intended user flow and data contract

1. The user explicitly starts card capture/import. Existing manual and barcode entry remain available.
2. Scanner returns an ordered front/back capture or cancellation/failure. Kura owns the confirmed output lifetime; cancel/failure publishes no wallet record.
3. Optional on-device recognition returns text/positions/confidence. A Kura adapter bounds input and derives editable **suggestions**, preserving identifier strings and leading zeros. Multiple competing values require a user choice; low confidence never silently replaces a field.
4. A session-owned draft presents original/cropped image and suggestions. The user edits/corrects values, chooses an existing/new record and explicitly saves through current VaultController/VaultStore APIs. No database/filesystem operations occur in Composables.
5. Encrypt confirmed images/records using existing storage, then remove only owned temporary capture/OCR files. Lock, cancellation and process death discard the draft and clear owned buffers. Never store decrypted drafts in saved state or include OCR text/images in logs.

The adapter must expose completion, cancellation and typed failure exactly once, with a bounded image/text budget and cancellation token. Do not publish names of upstream OCR APIs until the actual released contract is verified. The host must reject callbacks from an expired vault generation. Camera/import and recognition cancellation remain distinct from updater/network work.

## Initial scope and exclusions

First target: printed membership/loyalty cards with a member/reference identifier and explicit expiry. Optional business-card contact extraction needs separate fixtures and field mapping before inclusion. English labels and Latin identifiers/numerals are the measured feasibility scope. Japanese is an intended capability of the upstream optional OCR module, not verified by this Kura prototype; manual correction/fallback remains mandatory for Japanese, mixed scripts, handwriting and unsupported layouts until measured.

No payment-card verification, NFC payment, government-ID inference, automatic record publication, cloud OCR, model downloads during scanning, or promise of real-world accuracy. Existing encrypted backups, record schemas and manual/barcode workflows stay unchanged.

## Synthetic prototype and measurements

Run `python3 tools/card-recognition-prototype.py --output NEW_EMPTY_DIRECTORY` on Linux with existing Pillow, DejaVu Sans and Tesseract English data. This desktop research tool is **not** an Android dependency, production engine selection or license/model bundle for Kura. It generates its own nonprivate 1200×760 card fixtures, runs local OCR, proposes fields, abstains on ambiguous identifiers, and kills the process group when cancelled. It never opens a wallet or writes a wallet record.

[Recorded measurements](evidence/card-recognition-prototype-20261026.json): Tesseract 5.5.3 read **8/8 expected fields across four generated cards**, including leading zeros and a 2° rotated fixture. Zero manual corrections were needed for these clean fixtures; a deliberately conflicting two-identifier input required an explicit choice. End-to-end OCR subprocess time was **113.99–125.34 ms** and OS-reported child peak RSS **89,844–90,356 KiB**. Cancellation returned no recognition result. A copy/edit simulation demonstrates isolated suggestions; it is not Android UI verification.

These four generated cards are not a representative benchmark. No real-camera accuracy, Japanese quality, mobile latency/thermal/memory, confidence calibration or Android scanner-host integration was measured. The initial experiment could not run because GNU time was absent; the final script uses Python's child-resource accounting without installing software. The failed attempt is preserved in local output evidence.

## Historical implementation follow-up

Before adding a dependency, obtain an immutable published OCR-capable scanner bundle with source, checksums, models and notices. Create an API-26+ enhanced-scanning adapter with bounded session ownership, editable suggestions and explicit save/cancel tests. Kura currently supports API 24 in its base offline build, while the scanner requires API 26 and 64-bit ABIs: **do not silently raise the existing floor or couple scanning to network permission**. The maintainer must choose the enhanced-build/distribution policy before assigning a release milestone; preserve the base manual/barcode fallback.

Acceptance must cover synthetic English/Japanese cards; incorrect/ambiguous/leading-zero fields; manual corrections and no-save cancellation; image ordering and encryption; stale callbacks/lock/process death; permission denial; offline operation; representative physical-camera/device memory and latency; and dependency/ABI/manifest/16 KiB packaging checks. Keep scanner/OCR implementation outside the current updater/unlock release scope until separately accepted.


## Historical corner editor — 6 November 2026

In the enhanced build, front/back card photos use one Material dialog after camera capture or image selection. Edge detection initializes the editable four-corner mask; dragging or fine-tuning edits that same mask. Reset edges restores the detected result, Use full image expands the mask, and rotation updates the source photo before detection and cropping. Fine-tune exposes labelled corner selectors and horizontal/vertical adjustments without leaving the editor. There is no separate manual-crop screen in this path.

The photo has a shaded outside region and visible corner handles. Use crop is the filled primary action, Cancel import stays available, and adjustment actions use outlined buttons. Dialog content scrolls while confirmation stays outside the scroll area. Optional on-device OCR review follows confirmation; image-only import, explicit form Save and existing-field/empty-value checks remain unchanged. Capture still uses the system camera; no live edge overlay or silent image save is introduced. The base offline scanner retains its existing manual crop UI.

## Historical rotation picker

Rotate opens a picker with 0°,90°,180°,270° presets and a custom -180° to180° slider (0.1° steps). The picker previews the selected angle live; Cancel preserves the current photo and mask. Apply rotation creates a bounded oriented raster from the original input, updates the main photo preview and detects edges again. The picker explains that applying rotation resets the mask. Users can then edit the detected mask normally. Cropping and OCR consume that same oriented raster; there is no additional post-crop rotation.

Each change starts from the original photo to avoid cumulative resampling. Nonzero working rotations are bounded to2048 pixels per dimension and4 million pixels, use a white expanded canvas, and release owned bitmap/encoded buffers on cancellation or disposal. Zero degrees preserves the original encoded photo. Preset orientation and custom bounds have pixel-level synthetic acceptance coverage; physical-card and broader platform qualification remain separate.

## Photo guidance and lifecycle ownership — 6 November 2026

The crop dialog now offers Retake photo and nonblocking native brightness/focus advisories plus a warning when an adjusted card boundary reaches the photo edge. Focus warnings require detail in at least two document tiles; blank cards are not labelled blurry. These are heuristic prompts to inspect the image, not calibrated glare/cutoff detection or an accuracy guarantee. Users can adjust the frame, restore the full photo, retake or cancel. Native invalid-image/resource/storage failures give safe recovery messages without exposing native paths or recognized text. The byte/pixel/render budgets and offline model pins are unchanged.

In the previous corner editor, only touches beginning near a corner claimed a crop drag. Gestures elsewhere on that preview scrolled the surrounding dialog. Android8 acceptance exposed the former preview-wide gesture capture: the scrolled canvas stayed clipped and its handle could not be reached. The fix restores photo-area scrolling before corner editing. It uses the pinned Compose gesture API; [reference](https://developer.android.com/reference/kotlin/androidx/compose/foundation/gestures/package-summary).

Camera, image-picker and permission results carry the originating session generation, form identity/object/lifetime, destination, bounded deadline and operation guard. Lock drops form references, invalidates outstanding requests and deletes owned captures. Invalidated metadata remains until Android returns that outstanding result, so a launcher cannot be reused for a different request first. A late result cannot complete another operation’s guard. This metadata is memory-only; process restart has no draft/result ownership to restore. Returned crop bytes remain owned across dispatcher cancellation until the caller adopts them. Draft image writes capture the form lifetime before waiting for serialized vault work, publish ownership on the main dispatcher, and delete rejected/unadopted ciphertext. Reusing the same mutable form/schema cannot revive an old write; its in-memory lifetime changes on reset. Cancel remains available during recognition; the confirmed image-persistence step is kept atomic while lock still cancels session work.

Front/back association, preservation of populated barcode/name fields and a correction made after OCR review begins, separate form Save, and a current-format encrypted backup/staging round-trip are exercised with synthetic cards. An actual process stop during OCR leaves five temporary files and an abandoned capture fixture; real reauthentication/relaunch removes them without publishing the draft. This is separate from the owner-shelved, unconfirmed legacy-backup report #21.

A duplicate-card warning was considered and is excluded from this release: recognition alone is not a reliable merge key. No automatic merge, replacement or extra fields are introduced. The owner approved and merged the implementation in PR #14; independent review is no longer required. Physical photographed-card/arm64 quality, mobile resource qualification and remaining data-safety acceptance still gate #15/stable; the owner deferred physical testing for now and will supply results later. [Evidence](evidence/scanner-safety-20261106.json).

## Stationary card mask — 7 November 2026

The replacement crop editor keeps the photo fixed above an independently
scrollable adjustment pane in portrait, and beside it in landscape. Zoom, Move
and Rotate stay pinned outside that scroller. Switching modes reveals its
controls from the top. The primary **Use crop**, outlined **Retake photo** and
labelled close action stay available outside the adjustment pane. Large-text
rotation presets use two columns. See the scoped [design](design.md).

There are no editable document corners or separate rotation dialog. Automatic
perspective alignment remains a starting proposal, not a confidence guarantee.
New imports and Auto fit use a horizontal standard credit-card mask
(85.60/53.98 width/height), independent of capture/detection orientation. Rotation
turns the photo under that mask. Photo proportions, portrait-card and square are
explicit alternatives. Full photo restores the original capture and its
proportions, and also works when detection finds no edges.
Returning from the OCR offer preserves shape, quarter/fine angle, zoom and image
position. Neither option creates fields or saves the card automatically.

The pure `core:model/CardCrop` geometry is shared by the Canvas preview and
native perspective renderer. It inscribes the frame in the selected source,
clamps movement/zoom, and uses exact quarter-turn transforms to preserve boundary
pixels. Arbitrary rotations crop inside the source instead of adding an expanded
white canvas. Each change samples the original or automatic proposal, avoiding
cumulative resampling. Photo proportions with no other edits can retain the
complete automatic JPEG without another lossy encode. Standard card framing
crops to the same ratio shown in the preview. Preview decode stays bounded to
1536 pixels; working/output budgets
and session-owned encrypted persistence remain unchanged. The rounded mask is
visual: saved JPEGs retain their rectangular bounds.

This revision is under review; published Beta 3 still uses the historical editor.
Final-source regression evidence belongs in [Testing](TESTING.md). Physical
photographed-card/arm64 and production-package acceptance remain deferred, not
passed.
