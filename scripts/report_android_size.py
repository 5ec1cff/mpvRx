#!/usr/bin/env python3
"""Report built APK/AAB sizes, including native ABI costs and the ART profile budget."""

import argparse
from collections import defaultdict
import json
from pathlib import Path
import sys
import zipfile

PROFILE_LIMIT_BYTES = 1_500_000


def native_abi(name):
    parts = name.split("/")
    index = 0 if parts[0] == "lib" else 1 if len(parts) > 1 and parts[1] == "lib" else None
    return parts[index + 1] if index is not None and len(parts) > index + 2 else None


def category(name):
    if name.endswith(("baseline.prof", "baseline.profm")):
        return "ART profiles"
    if name.endswith(".dex"):
        return "DEX"
    if native_abi(name):
        return "Native libraries"
    if name.startswith("res/font/") or "/res/font/" in name:
        return "Fonts"
    if name.startswith("assets/") or "/assets/" in name:
        return "Assets"
    if name.startswith("res/") or "/res/" in name or name.endswith("resources.arsc"):
        return "Resources"
    return "Other"


def analyze(path):
    totals = defaultdict(lambda: {"compressed_bytes": 0, "uncompressed_bytes": 0})
    native_abis = defaultdict(int)
    profiles = []
    with zipfile.ZipFile(path) as archive:
        files = [info for info in archive.infolist() if not info.is_dir()]
        for info in files:
            group = totals[category(info.filename)]
            group["compressed_bytes"] += info.compress_size
            group["uncompressed_bytes"] += info.file_size
            abi = native_abi(info.filename)
            if abi:
                native_abis[abi] += info.file_size
            if info.filename.endswith("baseline.prof"):
                profiles.append({"path": info.filename, "bytes": info.file_size})
        largest = sorted(files, key=lambda info: info.compress_size, reverse=True)[:10]
    return {
        "archive": str(path),
        "archive_bytes": path.stat().st_size,
        "categories": dict(totals),
        "native_abi_uncompressed_bytes": dict(native_abis),
        "baseline_profiles": profiles,
        "largest_compressed_entries": [
            {"path": info.filename, "bytes": info.compress_size} for info in largest
        ],
    }


def markdown(reports):
    lines = ["## Android archive sizes", "", "Sizes describe each built archive; universal APKs include multiple ABIs.",
             "AAB archive size is not the per-device Play download size.", ""]
    for report in reports:
        lines += [f"### {Path(report['archive']).name}", "",
                  f"Archive: {report['archive_bytes']:,} bytes", "",
                  "| Content | Compressed bytes | Uncompressed bytes |", "| --- | ---: | ---: |"]
        for name, size in sorted(report["categories"].items()):
            lines.append(f"| {name} | {size['compressed_bytes']:,} | {size['uncompressed_bytes']:,} |")
        lines += ["", "| Native ABI | Uncompressed bytes |", "| --- | ---: |"]
        lines += [f"| {abi} | {size:,} |" for abi, size in sorted(report["native_abi_uncompressed_bytes"].items())]
        lines += ["", "| Largest compressed entries | Bytes |", "| --- | ---: |"]
        lines += [f"| `{entry['path']}` | {entry['bytes']:,} |" for entry in report["largest_compressed_entries"]]
        lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("app/build/outputs"))
    parser.add_argument("--json", type=Path, required=True)
    parser.add_argument("--markdown", type=Path, required=True)
    args = parser.parse_args()
    paths = sorted([*args.root.rglob("*.apk"), *args.root.rglob("*.aab")])
    if not paths:
        parser.error(f"No APK/AAB files found under {args.root}")
    reports = [analyze(path) for path in paths]
    args.json.write_text(json.dumps(reports, indent=2) + "\n", encoding="utf-8")
    args.markdown.write_text(markdown(reports) + "\n", encoding="utf-8")
    for report in reports:
        for profile in report["baseline_profiles"]:
            if profile["bytes"] >= PROFILE_LIMIT_BYTES:
                print(f"ART profile exceeds the 1.5 MB budget: {report['archive']} ({profile['bytes']:,} bytes)", file=sys.stderr)
                return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
