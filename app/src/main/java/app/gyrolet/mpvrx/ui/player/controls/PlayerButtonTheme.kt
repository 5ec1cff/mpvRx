/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.player.controls

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.gyrolet.mpvrx.ui.player.controls.components.LocalForceDarkPlayerButtonsBackground
import app.gyrolet.mpvrx.ui.player.controls.components.LocalHidePlayerButtonsBackground
import app.gyrolet.mpvrx.ui.theme.LocalDarkAppColorScheme

internal object PlayerButtonAlpha {
  const val CONTAINER = 0.55f
  const val BORDER = 0.4f
  const val DISABLED_CONTENT = 0.38f
  const val GLASS_CONTAINER = 0.32f
  const val SELECTED_GLASS_CONTAINER = 0.22f
}

/**
 * Carries the background visibility choice through every player-button implementation.
 *
 * Color overrides are deliberately not applied to this complete subtree: top-player groups also
 * contain status text and the audio player contains artwork, lyrics and sheets. Re-theming that
 * whole hierarchy just to style its buttons caused unrelated UI to switch palette.
 */
@Composable
internal fun PlayerButtonTheme(
  hideBackground: Boolean,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalHidePlayerButtonsBackground provides hideBackground,
    content = content,
  )
}

/** Applies the selected dark app palette to the complete player-control layer. */
@Composable
internal fun PlayerControlsContentTheme(content: @Composable () -> Unit) {
  val useDarkPalette =
    LocalForceDarkPlayerButtonsBackground.current && !LocalHidePlayerButtonsBackground.current
  val darkColors = LocalDarkAppColorScheme.current
  if (!useDarkPalette || darkColors == null) {
    content()
    return
  }

  MaterialTheme(
    colorScheme = darkColors,
    typography = MaterialTheme.typography,
    shapes = MaterialTheme.shapes,
    content = content,
  )
}

@Composable
internal fun PlayerButtonContentTheme(content: @Composable () -> Unit) {
  PlayerControlsContentTheme(content)
}
