/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player.controls.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Horizontal scrolling for a row of player buttons that may not fit its slot.
 *
 * An edge fades only while buttons are scrolled past it, and the fade deepens with the hidden
 * distance, so a row that fits is drawn unchanged. [shadowRoom] keeps button shadows inside the
 * offscreen layer the fade needs; without it they would be cut flat at the row's top and bottom.
 */
internal fun Modifier.fadingHorizontalScroll(
  state: ScrollState,
  reverseScrolling: Boolean = false,
  fadeLength: Dp = 24.dp,
  shadowRoom: Dp = 8.dp,
): Modifier =
  this
    .graphicsLayer {
      compositingStrategy = if (state.maxValue > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
      drawContent()
      val fadePx = fadeLength.toPx().coerceAtMost(size.width / 2f)
      if (fadePx <= 0f || state.maxValue <= 0) return@drawWithContent
      // ScrollState counts from the row's first item, which sits on the right for RTL or a
      // reversed scroll.
      val firstItemOnRight = (layoutDirection == LayoutDirection.Rtl) != reverseScrolling
      val hiddenBefore = state.value.toFloat()
      val hiddenAfter = (state.maxValue - state.value).toFloat()
      val hiddenLeft = if (firstItemOnRight) hiddenAfter else hiddenBefore
      val hiddenRight = if (firstItemOnRight) hiddenBefore else hiddenAfter
      val leftEdgeAlpha = 1f - (hiddenLeft / fadePx).coerceIn(0f, 1f)
      val rightEdgeAlpha = 1f - (hiddenRight / fadePx).coerceIn(0f, 1f)
      if (leftEdgeAlpha < 1f) {
        drawRect(
          brush =
            Brush.horizontalGradient(
              0f to Color.Black.copy(alpha = leftEdgeAlpha),
              1f to Color.Black,
              startX = 0f,
              endX = fadePx,
            ),
          size = Size(fadePx, size.height),
          blendMode = BlendMode.DstIn,
        )
      }
      if (rightEdgeAlpha < 1f) {
        drawRect(
          brush =
            Brush.horizontalGradient(
              0f to Color.Black,
              1f to Color.Black.copy(alpha = rightEdgeAlpha),
              startX = size.width - fadePx,
              endX = size.width,
            ),
          topLeft = Offset(size.width - fadePx, 0f),
          size = Size(fadePx, size.height),
          blendMode = BlendMode.DstIn,
        )
      }
    }.padding(vertical = shadowRoom)
    .horizontalScroll(state, reverseScrolling = reverseScrolling)
