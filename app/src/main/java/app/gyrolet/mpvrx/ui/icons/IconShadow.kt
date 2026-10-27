/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.icons

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

@Suppress("CompositionLocalAllowlist")
internal val LocalIconShadow = staticCompositionLocalOf { false }

/** A small glyph-shaped shadow, including on Android versions without render-effect blur. */
internal fun Modifier.iconShadow(enabled: Boolean): Modifier =
  if (!enabled) {
    this
  } else {
    drawWithCache {
      val shadowPaint = Paint().apply { colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.8f)) }
      val shadowBounds = Rect(Offset.Zero, size).inflate(2.dp.toPx())
      val shadowX = 0.75.dp.toPx()
      val shadowY = 1.5.dp.toPx()
      onDrawWithContent {
        translate(left = shadowX, top = shadowY) {
          val canvas = drawContext.canvas
          canvas.saveLayer(shadowBounds, shadowPaint)
          try {
            this@onDrawWithContent.drawContent()
          } finally {
            canvas.restore()
          }
        }
        drawContent()
      }
    }
  }
