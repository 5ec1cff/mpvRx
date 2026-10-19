"""Publish signed CI APKs without replacing stable releases or the nightly Pages feed."""

import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path


def run(*args):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE, text=True).stdout.strip()


repo = os.environ["GH_REPO"]
source_sha = os.environ["GITHUB_SHA"]
build_number = int(run("git", "rev-list", "--count", "HEAD"))
tag = "experimental"
title = f"mpvRx Experimental r{build_number}"

existing_result = subprocess.run(
    ["gh", "release", "view", tag, "--repo", repo, "--json", "name,assets"],
    capture_output=True,
    text=True,
)
existing = json.loads(existing_result.stdout) if existing_result.returncode == 0 else None
if existing:
    latest_number = re.search(r"\br(\d+)\b", existing["name"], re.IGNORECASE)
    if latest_number and int(latest_number[1]) > build_number:
        print("A newer experimental build is already published; leaving it in place.")
        raise SystemExit(0)

staged_dir = Path("experimental-apks")
staged_dir.mkdir(exist_ok=True)
sources = {
    **{abi: f"app-standard-{abi}-release.apk" for abi in (
        "universal", "arm64-v8a", "armeabi-v7a", "x86", "x86_64"
    )},
    "fongmi": "app-fongmi-universal-release.apk",
    "no-vulkan": "app-noVulkan-universal-release.apk",
}
staged = []
checksums = []
for variant, filename in sources.items():
    candidates = list(Path("ci-apks").rglob(filename))
    if len(candidates) != 1:
        raise RuntimeError(f"Expected one signed {variant} APK, found {len(candidates)}")
    destination = staged_dir / f"mpvRx-experimental-r{build_number}-{variant}.apk"
    shutil.copyfile(candidates[0], destination)
    staged.append(destination)
    with destination.open("rb") as apk:
        digest = hashlib.file_digest(apk, "sha256").hexdigest()
    checksums.append(f"{digest}  {destination.name}")

notes = Path("experimental-notes.md")
run_url = f"https://github.com/{repo}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
changes = run("git", "log", "-6", "--format=- %s")
notes.write_text(
    f"Experimental CI build r{build_number}. These builds may be unstable.\n\n"
    f"Source: [{source_sha[:7]}](https://github.com/{repo}/commit/{source_sha})\n\n"
    f"[CI build]({run_url})\n\n{changes}\n\n"
    "### SHA-256\n\n```text\n" + "\n".join(checksums) + "\n```\n",
    encoding="utf-8",
)

if existing is None:
    # A first publication stays private until every ABI/flavor asset has uploaded.
    run("gh", "release", "create", tag, "--repo", repo, "--target", source_sha,
        "--draft", "--prerelease", "--latest=false", "--title", title,
        "--notes-file", str(notes))

# Unique build names keep the previous release downloadable while the new assets upload.
run("gh", "release", "upload", tag, *map(str, staged), "--repo", repo, "--clobber")
ref = f"repos/{repo}/git/refs/tags/{tag}"
tag_result = subprocess.run(
    ["gh", "api", f"repos/{repo}/git/ref/tags/{tag}"], capture_output=True, text=True
)
if tag_result.returncode == 0:
    run("gh", "api", "--method", "PATCH", ref, "-f", f"sha={source_sha}", "-F", "force=true")
else:
    run("gh", "api", "--method", "POST", f"repos/{repo}/git/refs",
        "-f", f"ref=refs/tags/{tag}", "-f", f"sha={source_sha}")

# The app takes the build number from this title and selects only that build's assets.
# Update metadata last, after all downloads are available. Stable remains GitHub's latest.
run("gh", "release", "edit", tag, "--repo", repo, "--target", source_sha,
    "--draft=false", "--prerelease", "--latest=false", "--title", title,
    "--notes-file", str(notes))

if existing:
    asset_pattern = re.compile(r"mpvRx-experimental-r(\d+)-.+\.apk", re.IGNORECASE)
    numbers = {build_number}
    for asset in existing["assets"]:
        match = asset_pattern.fullmatch(asset["name"])
        if match:
            numbers.add(int(match[1]))
    keep = set(sorted(numbers, reverse=True)[:3])
    for asset in existing["assets"]:
        match = asset_pattern.fullmatch(asset["name"])
        if match and int(match[1]) not in keep:
            subprocess.run(
                ["gh", "release", "delete-asset", tag, asset["name"], "--repo", repo, "--yes"],
                check=False,
            )

print(f"Published {title}: https://github.com/{repo}/releases/tag/{tag}")
