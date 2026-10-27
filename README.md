# iPlayer

**iPlayer** is a video and music player for Android by **ImadLabs**.

- Plays local and network media with **LibVLC** (VideoLAN), with automatic hardware/software decoding and fallback
- Video: gestures (seek, brightness, volume, double-tap, pinch zoom, hold for speed), screen lock,
  speed 0.25×–4×, aspect modes, rotate/mirror, brightness/contrast/saturation, A–B repeat,
  frame step, seek preview, chapters, media info, picture-in-picture, background audio
- Subtitles: embedded and external (SRT, ASS/SSA, VTT and more), auto-matching files, delay,
  style, encoding, preferred languages
- Music: songs, albums, artists, genres, folders, recently played/added, favorites, most played,
  downloads, recordings, playlists, queue, 10-band equalizer, ReplayGain, normalizer, synced lyrics
  (.lrc), lock-screen/notification/Bluetooth controls via MediaSession
- Library: MediaStore-backed with a local Room database for history, resume points, playlists,
  hidden folders and TV-episode grouping with manual corrections
- Network: HTTP(S), HLS, DASH, RTSP, SMB, FTP/FTPS, SFTP, NFS, UPnP discovery; saved sources with
  passwords encrypted by the Android Keystore
- Updates: optional launch check against a signed GitHub release manifest, SHA-256 and signing
  certificate verification, user-confirmed install only ([docs/UPDATES.md](docs/UPDATES.md))
- No analytics, ads or tracking

Official channel: [t.me/ImadLabs](https://t.me/ImadLabs)

## Building

Requirements: JDK 17+ (CI uses 21), Android SDK with platform 37. Then

```
./gradlew assembleDirectDebug      # GitHub/sideload build with self-update
./gradlew assemblePlayDebug        # store build (no APK downloads)
./gradlew testDirectDebugUnitTest  # unit tests
```

APKs land in `app/build/outputs/apk/<flavor>/<buildType>/` (per-ABI splits plus a universal APK).
Release signing reads `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)
or the `IPLAYER_KEYSTORE_*`/`IPLAYER_KEY_*` environment variables. No signing material is committed.

CI (`.github/workflows/ci.yml`) builds both flavors, runs the unit tests and uploads debug APKs as
workflow artifacts. Tagging `v<versionName>` runs `.github/workflows/release.yml`.

## Project layout

```
app/src/main/java/com/imadlabs/iplayer/
  playback/   LibVLC engine owner (PlaybackManager), queue, Media3 session bridge, service, media info
  data/       Room database, DataStore settings, MediaStore library, network sources, lyrics, parsers
  update/     manifest parsing/policy, checker, verified downloader and installer
  startup/    welcome reminder policy, dialog priority, Telegram launcher
  ui/         Compose screens (home, videos, music, browse, library, settings, about, player)
```

## Licensing

iPlayer's own code © ImadLabs. Third-party components: LibVLC (LGPL-2.1+, dynamically linked,
replaceable), AndroidX/Media3/Room/DataStore/Compose, Kotlin libraries and Coil (Apache-2.0),
Plus Jakarta Sans, Inter and JetBrains Mono fonts (SIL OFL 1.1). License texts ship in
`app/src/main/assets/licenses/` and are shown in **About › Open-source licenses**.

See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) for supported formats, engine limits and what has been tested.
