# Pinned Android updater 1.0.0

Kura's direct-update build uses `io.github.phillip9933:android-updater:1.0.0`.
The Maven files, source archive, license, revision and checksum manifest come
from the [1.0.0 release bundle](https://github.com/phillip9933/android-app-updater/releases/tag/v1.0.0).

| Release input | Verified value |
| --- | --- |
| Source revision | `0613457c079d675dffcb4b5181e37959628cbbc4` |
| Bundle SHA-256 | `13b0fa5df7524091191e063c9b25bdb4d222866053228754d98ea3f1d1e72763` |
| AAR SHA-256 | `5be67e6bc30c370347631d5a1f3208f11b6f807f3a1aef84f6a1d615cb414c70` |
| Source archive SHA-256 | `9968c05e86b69065464d233c84fd7d66e63d7288c3006fb3ec386967406dd9dc` |

Gradle checks all seven entries in `SHA256SUMS` and rejects unlisted Maven files.
The default offline build omits the updater.
