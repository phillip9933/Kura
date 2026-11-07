# Kura design

This document describes the current Android wallet and its intended interaction
rules. It expands the original card-image specification; it does not assert
that every screen has completed accessibility or usability acceptance. The
owner has reviewed the newer crop and missing-side photo behavior. Whole-app
design review and the verification gaps below remain explicit.

## Product, audience and priorities

Kura keeps cards, passes and identity documents in an encrypted Android wallet.
The owner builds it for personal use and shares it publicly; the README records
that purpose. Primary tasks are finding and presenting an item, adding/importing
one, correcting its details and keeping recoverable encrypted backups. It is not
a payment app. No account, advertising or telemetry is part of these journeys.

Privacy and data safety take precedence over convenience, followed by clear
navigation and reversible editing. The existing phone-unlock-only choice is an
explicit convenience option; app authentication remains the default. Public
distribution does not establish suitability for every device or wallet. Broader
user research and audience-specific usability assumptions have not been
validated; do not invent personas or treat this specification as their approval.

## Screen inventory and navigation

| Surface | Purpose and entry | Back/cancel behavior |
| --- | --- | --- |
| Locked, authenticating and opening | Android biometric/device-credential authorization, then encrypted vault opening | Cancel leaves the vault closed; busy opening hides competing updater actions |
| Cards, Passes and Identity | Swipeable sections with categories, favorites, archive, search and sorting; chosen default section is loaded before first presentation | Search/filter changes do not rewrite records; Back exits temporary modes before leaving the screen |
| Item details and barcode/image viewers | Inspect fields, front/back media and attachments; present a barcode full screen | Close/Back returns to the originating item/list; external sharing is a separate user action |
| Add/import choice and manual editor | Manual entry, system file import or barcode scan; typed fields/categories and media | Form Save commits; cancel discards the owned draft and leaves the stored item unchanged |
| Photo capture, crop and OCR review | System camera/picker, editable crop, then optional existing-field suggestions | Full-photo/retake/manual adjustments remain available; no implicit field replacement or Save |
| File/backup preview and restore | Review bounded parsed content before importing or replacing the active vault | Cancellation keeps the current vault; publication follows validation and confirmation |
| Settings | Data & Security; per-section categories/custom fields; General Display; Expiry Alerts; Navigation & Layout; Barcode & Scanning; About | Child pages return to their parent; nested category/field management returns to the appropriate section settings |
| Backup & Storage | Manual backup/restore, automatic-backup folder/password/retention and recovery | Folder/password setup can be cancelled; setup must be complete before enabling automation |
| App updates, when built in | Settings/About and version route expose manual checking; automatic checks are opt-in | Checking/download/install have distinct state; Android confirmation owns installation; no vault data enters this boundary |

Presentation sections differ from legacy storage tables. A membership can be
stored in `passes` and displayed in Cards; navigation uses stable record identity
rather than moving data. See the [architecture](ARCHITECTURE.md#flow-and-ownership-rules)
and the screen owners in its code map.

## Main journeys and recovery

- **Open/find/present:** authorize, load the chosen start section, filter/search,
  open an item and show its barcode or image. Clear search and Back recover the
  original list. Expiry alerts provide context without moving/deleting records.
- **Add/correct:** choose a type, enter labelled existing fields, add either image
  independently, optionally review OCR, then explicitly Save. Recognized values
  are suggestions; preserve existing values, leading zeros and late corrections.
  Imported pass images remain read-only in details; manual empty slots stay
  independently addable. Replacement/removal uses Edit card.
- **Import:** launch Android's picker/camera only from an explicit action, return
  to the originating form/destination and show safe recovery after denial or
  invalid input. Scanner quality advisories offer manual/retake alternatives;
  they do not certify recognition correctness or create new fields.
- **Organize:** choose section/category/favorite/archive and sorting. Reorder is
  an explicit temporary mode; Save persists the intended order and Cancel
  restores its previous state. Destructive delete requires its confirmation.
- **Back up/restore:** choose destination and password, create encrypted output,
  or inspect a restore before publishing a validated new generation. Wrong
  passwords, interrupted operations and unavailable providers leave the active
  data intact. Recovery generations are not a secure-erasure guarantee.
- **Change authentication:** disabling app unlock asks for confirmation; cancel
  retains the default. Re-enabling authenticates the original key before removing
  phone-only access. Device lock always constrains key release; restored backups
  do not import this device-local choice.
- **Update:** discover a release, review the available version and confirm its
  download/install. Distinguish offline/error, no update, progress, verification
  and installation states. Trust checks must not be weakened for convenience.

## Shared visual and state rules

Use Material 3 typography, colors, shapes, dialogs, sheets, switches and sliders.
The visual targets are documented Android Material and adaptive-layout patterns,
plus familiar Android Settings grouping; copy their interaction conventions, not
another app's branding. The wallet uses a restrained hierarchy of section title,
item content and actions. Shared spacing, 48 dp touch targets and theme colors
come from the existing platform/component choices. Avoid new arbitrary tokens
or a global restyle for one local correction.

Light, dark and system themes are supported. Lists/grid controls, search and
navigation remain adaptable; landscape and large text must not hide the primary
action or currently edited content. Long names, empty sections, missing media,
large values, validation errors and disabled/busy states belong in visual checks.
Use text/selection semantics as well as color. Native component focus treatment
and explicit labels should make actions discoverable without precise gestures.

| State | Intended feedback and recovery |
| --- | --- |
| Loading/authentication/opening | Keep the startup surface inert to competing actions; expose cancellation where supported without leaking wallet details |
| Empty section/no search result | Preserve navigation and Add/filter recovery; distinguish absent data from a failed import |
| Missing media | Label front/back independently and retain the permitted add action; existing media opens a viewer |
| Invalid input/provider failure | Give a safe actionable error; retain recoverable form state and offer retry/cancel without dumping file contents or secrets |
| Successful edit/import | Return to the saved item/list after explicit confirmation; show the stored result rather than an unsaved draft |
| Busy save/import/update | Disable competing work and repeated submission; ownership/cancellation remains session-bound |
| Lock/screen off | Invalidate stale operations and clear decrypted drafts/viewers; a later result cannot populate another session or form |

## Accessibility and supported layouts

Target WCAG 2.2 AA mapped to native Android, rather than claiming certification.
Check labelled controls and roles, selected/disabled state and meaningful
traversal order (1.3.1, 4.1.2); keyboard/input alternatives and visible,
unobscured focus (2.1.1, 2.4.3, 2.4.7, 2.4.11); contrast and non-color state
communication (1.4.1, 1.4.3, 1.4.11); readable 200% text/adaptive reflow
(1.4.4, 1.4.10); touch target usability (2.5.8 and native 48 dp guidance);
clear errors/status and accessible authentication (3.3.1, 3.3.2, 3.3.8, 4.1.3).
These are review criteria, not evidence that every screen passes them.

Portrait/landscape, narrow supported phones, light/dark themes, long text and
200% font scale are the current layout matrix. Prefer labelled buttons/sliders
and native authentication over gesture-only controls or app-specific puzzles.
Keep cancellation and error recovery reachable. Visual baseline comparison
shows change, not quality; inspect the captures. Native accessibility automation,
keyboard operation and TalkBack navigation/spoken feedback are separate checks.

Current crop/large-text visual evidence and keyboard checks are recorded below
and in [Testing](TESTING.md). Owner phone feedback covers the two latest image
UI changes. The instrumented TalkBack input path did not establish reliable
reader navigation, and spoken feedback remains unverified. Complete main-journey
assistive-technology, contrast/focus and whole-app visual/usability acceptance
still need evidence; no broad AA compliance is inferred from a passing app suite.

## Privacy, platform handoffs and meaningful choices

The [architecture trust boundaries](ARCHITECTURE.md#trust-boundaries),
[security review](SECURITY_REVIEW.md), [scanner](CARD-RECOGNITION.md) and
[updater](APP-UPDATES.md) own the detailed data-flow/security contracts. OCR runs
locally and asks before suggestions. Camera capture is the documented plaintext
cache exception; other apps/providers receiving an explicitly shared/opened file
can retain copies. Accessibility services are a platform/user trust decision;
do not promise that decrypted on-screen content is invisible to them.

Users can choose presentation settings, backup location/password/retention,
biometric versus credential prompts, and the explicit phone-only option. Build
switches independently select scanner/updater capability; they are not an
undisclosed runtime provider fallback. The offline build has no INTERNET
permission; the optional updater contacts GitHub only. A secure device lock and
supported hardware-backed key storage remain product constraints.

## UX rationale and maintenance

Three familiar sections and grouped settings support recognition and predictable
navigation (Jakob's Law). Keeping preview and adjustment controls together reduces
context switching; labelled alternatives and native target sizing reduce precise
touch demands (Fitts's Law). Separate crop, optional suggestions and explicit Save
limit consequential choices at each step (Hick's Law) while preserving recovery.
Group each image with its own label/action so one populated side does not hide the
other side's available operation (proximity).

Update this document with reusable interaction changes, link regression/visual
results and retain failed attempts in Testing. Owner review establishes intended
behavior; automated checks and AI review support it but do not substitute for
physical acceptance or usability research. The outstanding acceptance criteria
remain in the live issues and release tracker, not a duplicated roadmap here.

## Card image interaction specification

### Purpose and journey

The owner wants to frame front/back card photos without moving document corners
or losing sight of the photo while adjusting it. After camera/picker capture,
Kura proposes a card alignment locally. A stationary mask shows the final frame;
the user moves, zooms and rotates the photo underneath it, then chooses **Use
crop**. Optional OCR still asks separately and only suggests existing empty
fields. The form's explicit Save owns persistence. Cancel/retake preserve the
existing form and front/back destination.

Automatic perspective correction remains a starting proposal. **Full photo**
restores the original capture and its proportions when detection chose the wrong
region. New imports and **Auto fit** start with a horizontal credit-card frame,
using the [ID-1 dimensions of 85.60 × 53.98 mm](https://www.epo.org/boards-of-appeal/decisions/pdf/t140876eu1.pdf)
(width/height ≈ 1.586). The mask stays horizontal while the photo rotates beneath
it. Photo proportions, portrait card and square remain explicit alternatives.
Differing documents are supported; a standard card ratio
is a convenience, not a validity rule. Users inspect the visible frame before
cropping. No OCR confidence or detected edge is treated as verified content.

### Layout and controls

Use Material 3 colors, typography and standard buttons/slider semantics, following
[Android adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive)
and [accessible Compose components](https://developer.android.com/develop/ui/compose/accessibility).
The crop dialog uses the available safe window area. Portrait places a fixed
preview above a separately scrollable adjustment pane; landscape places them
side by side. Scrolling controls never scrolls the photo. Cancel and crop actions
remain outside that pane. Large text may require scrolling controls, with the
preview still visible. The subsequent OCR offer/review uses a wider Material
dialog in landscape so large text does not stack actions and collapse its
scrolling region.

Zoom, Move and Rotate stay pinned above the adjustment scroller. Selecting a
mode scrolls its controls into view; shape and recovery controls follow them.
Rotation presets use two columns at large text sizes. These choices keep common
actions reachable without hiding the preview. Pinch and
drag are shortcuts; zoom/angle sliders, labelled increment buttons, rotation
presets and directional movement buttons provide alternatives to precise
gestures. Targets use native 48 dp minimum touch sizing. The selected tab, current
value and button state use text/semantics as well as color. Disabled/busy states
block competing transformations; loading/error messages preserve cancellation
and usable recovery. Keep text readable in light/dark themes and at 200% scale.

### Image correctness and privacy

Preview and crop output share one normalized affine mapping. Clamp every angle,
zoom and translation so the frame lies inside the source, avoiding empty output
edges. Quarter-turns with photo proportions preserve the full image; custom-angle
and standard-ratio framing visibly select a smaller region. Always sample the
chosen original/auto-aligned image, never repeatedly resample previous edits.
Round mask corners are visual framing; the saved JPEG retains its rectangular
bounding area. Encrypted media, backup/database formats, session cancellation and
owned-buffer wiping stay under the existing [architecture](ARCHITECTURE.md).

### Review and evidence

Reproduce the old photo/control visibility failure on a small mobile viewport.
Check portrait/landscape, light/dark and 200% text with synthetic cards. Verify
landmark pixels against the preview mapping, rotation/pan/zoom boundaries,
alternate proportions, full-photo/reset, cancel/retake/re-entry, front/back and
optional OCR/save journeys. Record actual screenshots/results and failures in
Testing; compile success is not visual validation or owner sign-off. The owner
must review this crop revision before publication. Physical-device and stable
release acceptance remain separate. The owner accepted the horizontal crop's
appearance in the local card-mask-fix testing build on 7 November 2026; that
feedback does not constitute hardware-key, production-upgrade, complete accessibility or stable-release acceptance. The owner also reported physical testing of the independent missing-side photo actions; no broader acceptance is inferred.

### Card-detail image slots

Manual cards keep both labelled front/back slots visible on their details
screen. Each missing side independently offers the existing edit icon with
Choose photo and Take photo, even when the other side already has an image.
Cancelling capture/selection leaves the record unchanged. An existing image
opens its viewer; replacement/removal continues through Edit card. Imported
pass images retain their read-only details presentation and do not gain empty
front/back placeholders. These controls reuse the existing Material image menu
and image-import ownership/confirmation path.

### Visual review gallery

These are unedited captures of synthetic cards from a disposable emulator.
The older editor loses the photo when its corner controls are reached. The
replacement keeps the preview and actions fixed; rotation edits that same
preview. The 200% landscape capture checks readable pinned modes.

| Prior editor | Stationary mask |
| --- | --- |
| ![Photo hidden above corner controls](images/card-crop/before.png) | ![Photo and move controls remain together](images/card-crop/after.png) |

[Rotation and retained framing](images/card-crop/rotation.png) ·
[Landscape at 200% text, dark theme](images/card-crop/large-text-landscape.png).
Exact source scopes and image hashes are in the [test evidence](evidence/card-mask-20261107.json).
