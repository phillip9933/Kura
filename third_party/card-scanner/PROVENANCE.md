# Pinned scanner 0.8.0

Published [stable SDK](https://github.com/phillip9933/open-android-doc-scanner/releases/tag/v0.8.0), source `e74d8cd8efd838f38820ee8ca48bc7ea765b2a8c`.

Published SDK ZIP SHA-256: `1f350dd8442ee8a2959d48d6ef31918f717a6aeb88a04945438d587eed094ae1`.
Kura verified that outer release checksum and all 23 supplied SHA-256 sidecars.
The upstream ZIP does not contain an inner SHA256SUMS or source-revision file;
Kura generates the adjacent manifest and revision marker after reviewing the
archive and exact tag. The manifest covers every retained file except itself.
All upstream Maven source JARs, docs, licenses, native source/build metadata and
notices are retained. Builds reject unlisted Maven files and never follow a
sibling checkout.

All five AARs are byte-identical to the prior 0.8.0-beta.1 SDK. The core JAR has
a different ZIP hash but identical entry payloads; source JARs are identical.
Maven metadata changes versions and corresponding core-JAR archive hashes only.
See the host [pin review](../../docs/evidence/stable-sdk-pins-20261107.json).

English/Japanese tessdata_fast models remain at revision
`87416418657359cb625c412a48b6e1d6d41c29bd`; their Apache-2.0 license remains
retained. Models are bundled only in the optional enhanced build, with no
runtime downloads. Upstream physical acceptance is not Kura host acceptance.
