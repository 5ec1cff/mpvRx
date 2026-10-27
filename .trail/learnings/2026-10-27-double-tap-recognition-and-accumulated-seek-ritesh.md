---
title: Double-tap recognition and accumulated seek totals must be independent
author: Ritesh
date: 2026-10-27
branch: master
id: l-c5f743c7
scope: repo
status: active
src: agent
touches:
  - app/src/main/java/app/gyrolet/mpvrx/MainActivity.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/browser/MainScreen.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/browser/NavigationBarState.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/browser/NavigationGlassSurface.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/browser/components/MiniPlayer.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/player/controls/GestureHandler.kt
---

## Decided
- In app/src/main/java/app/gyrolet/mpvrx/ui/player/controls/GestureHandler.kt, recognize every seek with a fresh double-tap pair, but retain DoubleTapSeekFeedback separately so repeated same-side pairs accumulate their displayed total.
- Continue feedback only for the same playback generation, side, and feedback window. Measure continuation from the next pair first DOWN, not its second tap, so a valid pair can complete across the feedback timeout.
## Tried and ruled out
- Unconditionally resetting updateSeekAmount(0) on each completed double tap preserved tap safety but regressed the display to +10 every time. Allowing single-tap continuation instead violates the user requirement that each seek needs two taps.
## Watch out
- Clear pairing and totals on media, lock-state, seek-region, cancellation, and timeout changes. Keep playback-boundary handling in PlayerViewModel.leftSeek/rightSeek unchanged.
