#!/usr/bin/env python3
"""Builds update-manifest.json (schema 1) for iPlayer from release APKs.

Usage:
  make_update_manifest.py --apk-dir DIR --version-name 1.0.0 --version-code 1 \
      --base-url https://github.com/ImadKhan1999/iPlayer/releases/download/v1.0.0 \
      --release-page https://github.com/ImadKhan1999/iPlayer/releases/tag/v1.0.0 \
      --changelog CHANGES.txt [--min-supported 0] [--mandatory] > update-manifest.json

APK file names must end with -<abi>-release.apk or -universal-release.apk (AGP split naming).
"""
import argparse, datetime, hashlib, json, os, re, sys

ABI = re.compile(r"-(arm64-v8a|armeabi-v7a|x86_64|x86|universal)-release\.apk$")

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--apk-dir", required=True)
    p.add_argument("--version-name", required=True)
    p.add_argument("--version-code", type=int, required=True)
    p.add_argument("--base-url", required=True)
    p.add_argument("--release-page", required=True)
    p.add_argument("--changelog", required=True)
    p.add_argument("--min-supported", type=int, default=0)
    p.add_argument("--mandatory", action="store_true")
    a = p.parse_args()
    if not a.base_url.startswith("https://") or not a.release_page.startswith("https://"):
        sys.exit("URLs must be HTTPS")
    apks = []
    for name in sorted(os.listdir(a.apk_dir)):
        m = ABI.search(name)
        if not m:
            continue
        path = os.path.join(a.apk_dir, name)
        h = hashlib.sha256()
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        apks.append({"abi": m.group(1), "url": f"{a.base_url.rstrip('/')}/{name}",
                     "sha256": h.hexdigest(), "sizeBytes": os.path.getsize(path)})
    if not apks:
        sys.exit("No release APKs found in " + a.apk_dir)
    with open(a.changelog, encoding="utf-8") as f:
        changes = [l.strip().lstrip("-*• ").strip() for l in f if l.strip()][:50]
    manifest = {"schema": 1, "channel": "stable", "latest": {
        "versionName": a.version_name, "versionCode": a.version_code,
        "releaseDate": datetime.date.today().isoformat(), "changelog": [c[:500] for c in changes],
        "minSupportedVersionCode": a.min_supported, "mandatory": a.mandatory,
        "releasePage": a.release_page, "apks": apks}}
    json.dump(manifest, sys.stdout, indent=2)
    print()

if __name__ == "__main__":
    main()
