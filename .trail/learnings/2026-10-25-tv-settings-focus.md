# TV settings, focus and remote controls

- `TvScreenFocusHost` must check the active navigation entry, not its parent's `hasFocus`.
  Parent focus includes the left settings pane, which used to block focus entering detail pages.
  The detail pane opts in after category activation; its request key also handles selecting the
  same category again. Back from a detail category restores the selected category on the left.
- Register settings content with `tvContentFocus()`. `settingsSearchTarget()` also remembers
  descendant focus, so Back and search deep links land on actual settings rather than toolbars.
- `TvSettingsPolicy` filters both settings rows and search. It never changes stored preferences.
  Runtime gates separately ignore mobile brightness, video mini-player, projection, PiP,
  biometric and haptic preferences on televisions. Phone/tablet rendering follows its old path.
- TV remote transport actions bypass phone gesture mappings. Scrubbing uses the same accelerating
  steps as the TV seekbar. Volume/transport key-up events must not leak through to mpv bindings.
- TV seekbar cache state is read during drawing while controls are composed, and its clock ticks
  at minute boundaries. The persistent HUD skips battery observation and unnecessary TV updates.
- TV sheets inset controls and retain rounded bottom corners. Restored phone grid column counts
  cannot make TV cards smaller than the existing TV minimum sizes.

Validation: parsed every modified Kotlin file, checked resource references/imports, and reviewed
the device gates. No Android build, emulator or physical-TV run was performed. Trail CLI/cache
was unavailable in this environment.

Device QA: remote-only entry into each settings category and subpage, Back to the prior row,
search-to-setting focus, scrolling long lists, Left/Right slider changes (including RTL), folder
return focus, sheet dismissal, held seek keys, transport keys, volume HUD and controls auto-hide.
Repeat phone/tablet navigation with the same saved preferences.
