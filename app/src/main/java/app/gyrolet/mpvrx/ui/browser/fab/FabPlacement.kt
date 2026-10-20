/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.browser.fab

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.ui.browser.LocalNavigationBarHeight
import app.gyrolet.mpvrx.ui.theme.AppMotion

/**
 * Single source of truth for where browser FABs float.
 *
 * [LocalNavigationBarHeight] describes everything floating along the bottom edge of the current
 * region, excluding system insets: the navigation pill on the main tabs, selection actions, and the
 * mini player, whether it sits above the pill, beside it on wide screens, alone at the bottom of a
 * pushed screen such as a folder, or over only the right pane in dual-pane mode. FABs float one
 * spacing above that stack and glide with it as the mini player or navigation comes and goes.
 */
object FabPlacement {
  /** Material 3 Scaffold adds this spacing, plus the system bar inset, below its FAB slot. */
  val ScaffoldFabSpacing: Dp = 16.dp

  /** Bottom padding for a FAB hosted in a Scaffold `floatingActionButton` slot. */
  @Composable
  fun scaffoldBottomPadding(): Dp =
    animatedPadding((LocalNavigationBarHeight.current - ScaffoldFabSpacing).coerceAtLeast(0.dp))

  /**
   * Bottom padding for a FAB drawn as an overlay at the bottom of a full-height region rather than
   * in a Scaffold slot. Apply it inside [overlayInsets] so it lines up with Scaffold-hosted FABs.
   */
  @Composable
  fun overlayBottomPadding(): Dp =
    animatedPadding(maxOf(LocalNavigationBarHeight.current, ScaffoldFabSpacing))

  /** The system bars Scaffold keeps its FAB clear of. */
  val overlayInsets: WindowInsets
    @Composable get() = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

  @Composable
  private fun animatedPadding(target: Dp): Dp {
    val padding by animateDpAsState(
      targetValue = target,
      animationSpec = AppMotion.spatial<Dp>(AppMotion.noBounce(Spring.StiffnessMedium), snap()),
      label = "fab_bottom_padding",
    )
    return padding
  }
}
