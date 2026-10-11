/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.ui.theme.AppMotion
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** Shared material for player sheets and movable panels; one optical layer per container. */
@Composable
internal fun Modifier.playerGlassSurface(
  backdrop: Backdrop?,
  shape: Shape,
  containerColor: Color,
): Modifier {
  if (backdrop == null) return this
  val settings = rememberLiquidGlassSettings()
  val reducedMotion = AppMotion.playerReducedMotion()
  val tint = remember(containerColor) {
    // Preserve the player's original 80% frosted backing. Rows inside it provide lighter
    // 35% / 65% resting / selected surfaces, rather than opaque cards hiding the video.
    Brush.verticalGradient(
      0f to containerColor.copy(alpha = 0.86f),
      0.25f to containerColor.copy(alpha = 0.80f),
      1f to containerColor.copy(alpha = 0.80f),
    )
  }
  return drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
      liquidGlassEffects(
        settings,
        blurRadius = 12.dp.toPx(),
        refractionHeight = 12.dp.toPx(),
        refractionAmount = 20.dp.toPx(),
        vibrant = true,
        refractionEnabled = !reducedMotion,
      )
    },
    highlight = { settings.highlight(Highlight.Ambient.copy(alpha = if (reducedMotion) 0.15f else 0.30f)) },
    shadow = { settings.shadow(Shadow(radius = 16.dp, color = Color.Black.copy(alpha = 0.25f))) },
    innerShadow = { settings.innerShadow(InnerShadow(radius = 2.dp, color = Color.White.copy(alpha = 0.12f))) },
    onDrawSurface = { drawRect(tint) },
  )
}
