---
title: "Audio artwork disk cache is size-tiered, not first-request sized"
author: Riteshp2001
date: 2026-10-25
branch: master
id: l-5d3b67b5
scope: repo
status: active
src: agent
touches:
  - CHANGELOG.md
  - app/src/main/java/app/gyrolet/mpvrx/App.kt
  - app/src/main/java/app/gyrolet/mpvrx/MainActivity.kt
  - app/src/main/java/app/gyrolet/mpvrx/domain/thumbnail/ThumbnailRepository.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/browser/MainScreen.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/player/PlayerActivity.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/player/controls/PlayerControls.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/player/controls/TvPlayerControls.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/preferences/PreferencesScreen.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/preferences/WallpaperEditorScreen.kt
---

## Decided
- Audio artwork disk entries in `ThumbnailRepository` are size-tiered: `diskCacheKey(video, generator, artworkTier)` uses an `audio-art-v3|<tier>` prefix, `audioArtworkTier` picks 256 px, 512 px or the thumbnail-quality limit, `generateLocalThumbnail` produces audio art at that tier, and `getThumbnail` writes the tier copy before scaling to the request. Artwork disk reads decode ARGB_8888 (`readBitmapFromDisk(fullColor = true)`).
- Real cause of blurry album and song covers: the disk key carried no size, so the first request (a 48 dp song row, about 144 px) became the only stored cover, and album grids and the player upscaled it; RGB_565 decoding added gradient banding.
## Tried and ruled out
- One full-quality disk copy per song: thousands of songs at roughly 200 KB each is too much storage for list rows that need 256 px.
