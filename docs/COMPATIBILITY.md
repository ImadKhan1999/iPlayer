# Compatibility

iPlayer plays media through **LibVLC 3.7.7** (VideoLAN), the engine used by VLC for Android. It
supports a very wide range of formats, but **no player supports every file**. Actual playback
depends on the device's decoders (for hardware decoding), CPU speed (for software decoding),
DRM (protected streams are not supported) and the file itself.

## What the engine is built to handle

| Area | Formats |
| --- | --- |
| Containers | MKV, MP4/M4V/MOV, AVI, WebM, TS/M2TS, FLV, WMV/ASF, OGG, 3GP, MPEG-PS, VOB |
| Video | H.264, H.265/HEVC, VP8, VP9, AV1 (software decode on most devices), MPEG-2/4, VC-1/WMV, Theora |
| Audio | AAC, MP3, FLAC, ALAC, Opus, Vorbis, WAV/PCM, AC-3, E-AC-3, DTS (decode), TrueHD, APE, WavPack, WMA, DSD (DSF/DFF) |
| Subtitles | Embedded and external SRT, ASS/SSA (libass), WebVTT, MicroDVD/SUB, VobSub, PGS, DVB, TTML |
| Streaming | HTTP(S) progressive, HLS (.m3u8), DASH (.mpd), RTSP, RTMP, MMS, UDP/RTP |
| Network shares | SMB 2/3, FTP/FTPS, SFTP, NFS, UPnP/DLNA browsing |

## Engine limits (by design, shown honestly in the app)

- **Gapless playback and crossfade** are not available in LibVLC 3 for Android, so they are not offered.
- **Frame stepping** is a precise seek by one frame period (no native frame-step API).
- **HDR**: HDR10/HLG streams play through hardware decoders where the device supports them; the
  HDR type is shown as a hint only, because LibVLC 3 doesn't expose the transfer function reliably.
- **Zoom, pan, rotation, mirroring and colour filters** require the TextureView output (the default).
- **Rename** of library files needs Android 11+. External subtitle and lyric auto-discovery is
  limited by scoped storage; the optional "All files access" (direct build) removes that limit.
- **Lyrics** come from sidecar `.lrc`/`.txt` files only. No online lyric or subtitle search
  (those need a provider account and API key).
- **DRM-protected** content (Widevine etc.) is not supported.

## Test record

| Check | Status |
| --- | --- |
| Unit tests (queue, update policy/manifest validation, startup dialogs, parsers, redaction, formatters, engine options) | Run in CI on every push |
| Debug APK build (direct and play flavors) | Built in CI |
| Playback on physical devices / emulator | **Not yet tested.** No device or emulator was available in the build environment. |
| Network shares, PiP, Bluetooth controls, update install flow | **Not yet tested on a device.** |

Please report files that don't play (with the Diagnostics log from Settings › Advanced) on the
ImadLabs Telegram channel: https://t.me/ImadLabs
