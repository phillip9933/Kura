# Pinned Android updater 0.2.2

Immutable `io.github.phillip9933:android-updater:0.2.2` from the
[stable release](https://github.com/phillip9933/android-app-updater/releases/tag/v0.2.2).

Source: `e98076dcc9cc980afef32b41949a6f529bc833c6`.
Outer bundle SHA-256: `3e057a18afd0e681a49fb6aadd852f9678f825610267ba6552683fb1aa181e4e`.
Source ZIP SHA-256: `29b57bb4fe5168219b480d9fd16deef49880edacb64b93cc2548a9970d669359`.

All seven supplied inner pins and the source archive match. AAR and Maven source
JAR are byte-identical to the prior 0.2.2-beta.1 candidate; Maven metadata changes
the version only. The corrupt-cache recovery fix is retained. Gradle verifies
the original inner pins and rejects unlisted Maven files. Consumers never follow
a sibling checkout. Kura host/production acceptance remains separate from the
upstream release. See the [host pin review](../../docs/evidence/stable-sdk-pins-20261107.json).
