---
title: Phone remote delivers keys to the focused window root and pairs with committed nonces
author: Riteshp2001
date: 2026-10-25
branch: master
id: l-5aec66b0
scope: repo
status: active
src: agent
touches:
  - app/src/main/java/app/gyrolet/mpvrx/App.kt
  - app/src/main/java/app/gyrolet/mpvrx/MainActivity.kt
  - app/src/main/java/app/gyrolet/mpvrx/domain/thumbnail/ThumbnailRepository.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/player/PlayerActivity.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/player/controls/PlayerControls.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/player/controls/TvPlayerControls.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/preferences/PreferencesScreen.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/preferences/WallpaperEditorScreen.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/theme/AppWallpaper.kt
  - app/src/main/res/values/strings.xml
---

## Decided
- `TvInputDispatcher` delivers phone-remote keys by calling `dispatchKeyEvent` on the focused window root from `WindowInspector.getGlobalWindowViews()` (`WindowManagerGlobal.mViews` below API 29), so Compose dialogs and popups receive them like a hardware remote. `Instrumentation.sendKeySync` injection is not used because normal apps cannot rely on it on modern Android.
- Text is typed as `KeyCharacterMap` events; only text with characters the virtual keyboard cannot produce falls back to clipboard plus Ctrl+V, which keeps typed passwords off the clipboard.
- Pairing in `RemoteProtocol.kt` / `TvRemoteServer` uses ephemeral ECDH plus a committed TV nonce before the phone reveals its nonce, so a man in the middle cannot steer the six-digit code shown on both screens.
## Tried and ruled out
- Forgetting the stored pairing on a plaintext `unpaired` reply in `RemoteControlViewModel`: a spoofed TV could erase pairings. Users clear one with Forget instead.
