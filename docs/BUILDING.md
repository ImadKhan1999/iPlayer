# Building iPlayer

## Requirements

- JDK 17 or newer (CI uses 21)
- Android SDK with platform 37 (`compileSdk 37`, `targetSdk 36`, `minSdk 26`)

## Build variants

iPlayer has two product flavors:

| Flavor | Use | Differences |
| --- | --- | --- |
| `direct` | GitHub releases and sideloading | In-app update check, optional "All files access" |
| `play` | Store builds | No APK downloads or install permission |

```
./gradlew assembleDirectDebug      # GitHub/sideload build with self-update
./gradlew assemblePlayDebug        # store build (no APK downloads)
./gradlew testDirectDebugUnitTest  # unit tests
```

APKs land in `app/build/outputs/apk/<flavor>/<buildType>/`: one per ABI (`arm64-v8a`,
`armeabi-v7a`, `x86_64`, `x86`) plus a universal APK.

## Release signing

Release builds read `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)
or the `IPLAYER_KEYSTORE_*` / `IPLAYER_KEY_*` environment variables. No signing material is
committed to this repository.

## CI and releases

- `.github/workflows/ci.yml` builds both flavors, runs the unit tests and uploads debug APKs as
  workflow artifacts on every push and pull request.
- `.github/workflows/release.yml` runs when a `v<versionName>` tag is pushed. It builds signed
  `direct` APKs, writes `SHA256SUMS.txt` and the signed update manifest, and publishes the GitHub
  release with the notes from `docs/changelog/v<versionName>.txt`.

See [UPDATES.md](UPDATES.md) for how the update manifest is produced and verified.

## Project layout

```
app/src/main/java/com/imadlabs/iplayer/
  playback/   LibVLC engine owner (PlaybackManager), queue, Media3 session bridge, service, media info
  data/       Room database, DataStore settings, MediaStore library, network sources, lyrics, parsers
  update/     manifest parsing/policy, checker, verified downloader and installer
  startup/    welcome reminder policy, dialog priority, Telegram launcher
  ui/         Compose screens (home, videos, music, browse, library, settings, about, player)
```
