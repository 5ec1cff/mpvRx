---
title: "Connected mini-player navigation means matched stacked panels, not a bridge"
author: Ritesh
date: 2026-10-27
branch: master
id: l-60b51f7e
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
- The connected navigation and mini-player design requested by the user means a matched-width stacked dock, not a narrow connector between two floating pills. app/src/main/java/app/gyrolet/mpvrx/MainActivity.kt should position the player from the measured navigation bounds with a small gap; NavigationBarState and MainScreen coordinate rounded outer corners and flattened joining corners.
- In app/src/main/java/app/gyrolet/mpvrx/ui/browser/NavigationGlassSurface.kt, use the same corner-based outline for Haze, tint, clipping, and highlights. Use actual parent/root coordinates for inset-aware and RTL alignment.
## Tried and ruled out
- NavigationPlayerConnection and NavigationPlayerBridgeShape were the wrong visual interpretation. Their custom concave path also caused the Kyant lens shape-compatibility crash. Remove the bridge rather than decorating or re-enabling it.
## Watch out
- Preserve standalone behavior for TV, legacy navigation, selection mode, and dual-pane layouts. Share the mini-player shape with its transition anchors so expanding and collapsing do not disagree with the resting dock.
