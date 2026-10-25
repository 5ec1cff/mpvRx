---
title: Cast and Clip layout migrations must only touch stored layouts
author: Riteshp2001
date: 2026-10-25
branch: master
id: l-eb4bb4d7
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
- The CAST and CLIP migrations in the `AppearancePreferences` init block add those buttons when the effective layout lacks them. On a fresh install the effective layout is the default, so trimmed defaults (`topRightControls`, `bottomRightControls`, `bottomLeftControls`, `portraitBottomControls`) would get CAST and CLIP re-added on first launch. Both migrations now run only for stored layouts (`isSet()`, landscape and portrait checked separately), because only stored layouts can predate those buttons.
