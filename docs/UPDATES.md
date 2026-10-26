# Update checking

iPlayer checks for a newer version each time it is opened or brought back to the foreground
(at most once every two minutes), in the background, and only if
**Settings › Updates › Check for updates on launch** is on. When a newer version exists it shows
the update popup with the changelog; **Update** downloads the APK inside the app with progress. Nothing is downloaded or installed
without the user tapping **Update**, and Android always shows its own install confirmation.

## Endpoint

The app reads a JSON manifest from `BuildConfig.UPDATE_MANIFEST_URL`. The default is

```
https://github.com/ImadKhan1999/iPlayer/releases/latest/download/update-manifest.json
```

which is the asset that `.github/workflows/release.yml` attaches to every GitHub release.
To use another HTTPS server, build with `-PupdateManifestUrl=https://your.host/path.json` **and**
add the host to `UpdateRepository.TRUSTED_HOSTS` (manifest, APK and redirect hosts must all be on
that list). Plain HTTP and URLs with credentials are always rejected.

> Live checking only works once a release containing `update-manifest.json` exists. Until then
> the check fails quietly on launch (a manual check in Settings says "No published release was found at the update address yet") and is retried on a later launch.

## Manifest format (schema 1)

```json
{
  "schema": 1,
  "channel": "stable",
  "latest": {
    "versionName": "1.0.1",
    "versionCode": 2,
    "releaseDate": "2026-11-01",
    "changelog": ["Fixed subtitle timing on some MKV files"],
    "minSupportedVersionCode": 0,
    "mandatory": false,
    "releasePage": "https://github.com/ImadKhan1999/iPlayer/releases/tag/v1.0.1",
    "apks": [
      { "abi": "arm64-v8a", "url": "https://github.com/…/iPlayer-1.0.1-arm64-v8a-release.apk",
        "sha256": "<64 hex chars>", "sizeBytes": 41234567 },
      { "abi": "universal", "url": "https://github.com/…/iPlayer-1.0.1-universal-release.apk",
        "sha256": "<64 hex chars>", "sizeBytes": 98765432 }
    ]
  }
}
```

Rules enforced by `ManifestParser` (see `UpdatePolicyTest`):

- Only `versionCode` is compared. An equal or lower code is never offered, so downgrades are impossible.
- `mandatory: true`, or an installed code below `minSupportedVersionCode`, makes the dialog
  non-skippable (the user can still choose "Not now"; the app never blocks or self-installs).
- Every URL must be HTTPS on a trusted host; checksums must be 64 hex characters; sizes must be
  positive and at most 400 MB; changelog is capped at 50 lines of 500 characters.
- The response is capped at 64 KB, the request times out after 5 s connect / 8 s read, and the
  last good result is kept and shown when the device is offline.

## Verification before install (direct build)

1. The APK for the device's ABI is downloaded (falling back to `universal`), resumably, to app cache.
2. Its SHA-256 must match the manifest.
3. Its signing certificate must match the installed app's certificate. An APK signed with a
   different key is rejected, even if the checksum matches.
4. It is handed to Android's `PackageInstaller`, which asks the user to confirm. The app needs the
   user to allow "Install unknown apps" for iPlayer once; it links to that setting.

The `play` flavor never downloads APKs; "Update" opens the store listing.

## Publishing a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `docs/changelog/v<versionName>.txt` (one change per line).
3. Configure repository secrets once: `IPLAYER_KEYSTORE_BASE64` (base64 of the .jks),
   `IPLAYER_KEYSTORE_PASSWORD`, `IPLAYER_KEY_ALIAS`, `IPLAYER_KEY_PASSWORD`.
   Keep the keystore safe: every future update must be signed with the same key.
4. Push tag `v<versionName>`, or create branch `release/v<versionName>` (the GitHub web UI can do
   this), in which case the workflow creates the tag on that commit. The workflow refuses to run without signing secrets or if the tag
   doesn't match `versionName`, then publishes the APKs, `SHA256SUMS.txt` and `update-manifest.json`.
