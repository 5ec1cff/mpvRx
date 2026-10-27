---
title: Connected glass docks need synchronized geometry and edge-aware optics
author: Ritesh
date: 2026-10-27
branch: master
id: l-a19d2ac7
scope: repo
status: active
replaces: l-60b51f7e
src: agent
touches:
  - app/src/main/java/app/gyrolet/mpvrx/MainActivity.kt
  - app/src/main/java/app/gyrolet/mpvrx/preferences/AppearancePreferences.kt
  - app/src/main/java/app/gyrolet/mpvrx/presentation/components/LiquidGlassSurface.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/browser/MainScreen.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/browser/NavigationBarState.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/browser/NavigationGlassSurface.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/browser/components/MiniPlayer.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/liquidglass/LiquidGlassUtils.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/player/controls/DoubleTapSeekOverlay.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/preferences/AppearancePreferencesScreen.kt
---

## Decided
- For the Liquid Glass connected dock, app/src/main/java/app/gyrolet/mpvrx/MainActivity.kt owns one dock animation progress published through NavigationBarState. Both panel shapes, placement, and glass appearance must consume that same progress so their facing edges cannot lag independently.
- Connected mini-player and navigation surfaces use a flush zero-gap, zero-inner-corner join. They sample the same raw scene backdrop and converge to the same tint and blur; per-panel refraction, inner shadows, and whole-panel highlights fade away so they do not outline the internal join. LiquidGlassUtils.connectedGlassRim draws only the outer edges.
- Keep the saved connected_mini_player_navigation preference in Appearance / Navigation Actions. Turning it off restores independent panels and their normal glass effects. Add its search entry and anchor without inserting a new top-level settings item.
## Tried and ruled out
- A 2dp gap and separate corner animations still looked disconnected in glass. Matched geometry alone was insufficient because independently sampled backdrops and lens/shadow bands made the seam visible.
## Watch out
- The connected shape remains CornerBasedShape-compatible; do not restore the rejected custom-path bridge. Preserve RTL, consumed insets, reduced motion, TV/legacy/dual-pane fallbacks, and shared player transition anchors.
