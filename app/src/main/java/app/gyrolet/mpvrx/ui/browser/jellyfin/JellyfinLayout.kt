/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.browser.jellyfin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One spacing system for every Jellyfin screen, so section titles, rows, carousels and grids all
 * start on the same edge and every nested shape stays concentric with its container.
 */
internal object JellyfinSpacing {
  /** Between home sections. */
  val SectionGap = 28.dp

  /** Between a section title and its content. */
  val HeaderGap = 12.dp

  /** Between cards in a horizontal row. */
  val RowItemGap = 12.dp

  /** Between carousel items; Material 3 carousels use a tight 8 dp. */
  val CarouselItemGap = 8.dp

  /** Inner padding of tonal cards; images inside them use [inner] radii. */
  val CardPadding = 8.dp

  /** Between rows of a vertical list. */
  val ListItemGap = 8.dp

  /** Corner radius of an image inside a tonal card, concentric with the card's own corners. */
  fun inner(
    outer: Dp,
    padding: Dp = CardPadding,
  ): Dp = (outer - padding).coerceAtLeast(4.dp)
}

/** The screen edge inset, wider on tablets and large screens. */
@Composable
internal fun jellyfinGutter(): Dp {
  val width = LocalConfiguration.current.screenWidthDp
  return when {
    width >= 840 -> 32.dp
    width >= 600 -> 24.dp
    else -> 16.dp
  }
}

/**
 * Lets a horizontally scrolling row inside a padded column reach the screen edges; give the row
 * the same [padding] as content padding so its first and last items still line up with the column.
 */
internal fun Modifier.bleedHorizontally(padding: Dp): Modifier =
  layout { measurable, constraints ->
    val extra = (padding * 2).roundToPx()
    val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
    val placeable =
      measurable.measure(
        constraints.copy(
          minWidth = (constraints.minWidth + extra).coerceAtMost(maxWidth),
          maxWidth = maxWidth,
        ),
      )
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.place(-extra / 2, 0) }
  }
