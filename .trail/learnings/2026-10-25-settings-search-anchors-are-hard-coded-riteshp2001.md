---
title: Settings search anchors are hard-coded lazy-list indexes
author: Riteshp2001
date: 2026-10-25
branch: master
id: l-a8fb21b9
scope: repo
status: active
src: agent
touches:
  - app/src/main/java/app/gyrolet/mpvrx/di/PreferencesModule.kt
  - app/src/main/java/app/gyrolet/mpvrx/preferences/AppearancePreferences.kt
  - app/src/main/java/app/gyrolet/mpvrx/preferences/BrowserPreferences.kt
  - app/src/main/java/app/gyrolet/mpvrx/preferences/PlayerPreferences.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/preferences/AdvancedPreferencesScreen.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/preferences/SearchablePreference.kt
  - app/src/main/java/app/gyrolet/mpvrx/ui/preferences/SettingsSearchNavigation.kt
---

## Decided
- `settingsSearchListAnchors` in `ui/preferences/SettingsSearchNavigation.kt` and the `anchorItemIndex` values passed to `addSearchEntries` in `ui/preferences/SearchablePreference.kt` are top-level LazyColumn item indexes of each settings screen. Reordering sections (moving the Advanced language/notification group below Scripts in `AdvancedPreferencesScreen`) must update both, because `scrollToSettingsSearchTarget` uses the map first, then the entry anchor, as its first jump.
- A stale index still resolves eventually (the scroller walks every item), but search lands on the wrong card first and is visibly slow.
