/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.ui.theme.AppMotion
import app.gyrolet.mpvrx.ui.liquidglass.liquidGlassEffects
import app.gyrolet.mpvrx.ui.liquidglass.rememberLiquidGlassSettings
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

typealias LiquidGlassBackdrop = LayerBackdrop

internal val LocalLiquidGlassBackdrop = staticCompositionLocalOf<LiquidGlassBackdrop?> { null }

@Composable
fun rememberLiquidGlassBackdrop(): LiquidGlassBackdrop = rememberLayerBackdrop()

fun Modifier.captureLiquidGlassBackdrop(
  backdrop: LiquidGlassBackdrop?,
  enabled: Boolean = true,
): Modifier = if (enabled && backdrop != null) layerBackdrop(backdrop) else this

@Composable
fun ProvideLiquidGlassBackdrop(
  backdrop: LiquidGlassBackdrop,
  enabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalLiquidGlassBackdrop provides backdrop.takeIf { enabled },
    content = content,
  )
}

enum class LiquidGlassStyle {
  MiniPlayer,
  Navigation,
}

/** Kyant-backed liquid glass surface shared by mini players and floating action bars. */
@Composable
fun LiquidGlassSurface(
  shape: Shape,
  modifier: Modifier = Modifier,
  style: LiquidGlassStyle = LiquidGlassStyle.Navigation,
  glassColor: Color,
  fallbackColor: Color,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  backdrop: LiquidGlassBackdrop? = LocalLiquidGlassBackdrop.current,
  glowStrength: Float = 1f,
  cornerRadius: Dp? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val glassSettings = rememberLiquidGlassSettings()
  val preferences = koinInject<AppearancePreferences>()
  val glassEnabled by preferences.liquidGlassEnabled.collectAsState()
  val renderGlass = glassEnabled && backdrop != null
  val reducedMotion = AppMotion.shouldReduceMotion()
  val blurRadius = when (style) {
    LiquidGlassStyle.MiniPlayer -> 12.dp
    LiquidGlassStyle.Navigation -> 8.dp
  }
  val refractionHeight = when (style) {
    LiquidGlassStyle.MiniPlayer -> 18.dp
    LiquidGlassStyle.Navigation -> 14.dp
  }
  val refractionAmount = when (style) {
    LiquidGlassStyle.MiniPlayer -> 26.dp
    LiquidGlassStyle.Navigation -> 22.dp
  }
  val shadowElevation: Dp = if (style == LiquidGlassStyle.MiniPlayer) 10.dp else 8.dp

  val surfaceModifier =
    if (renderGlass) {
      modifier
        .drawBackdrop(
          backdrop = checkNotNull(backdrop),
          shape = {
            when {
              cornerRadius != null -> RoundedRectangle(cornerRadius)
              style == LiquidGlassStyle.MiniPlayer -> shape
              else -> Capsule()
            }
          },
          effects = {
            liquidGlassEffects(
              glassSettings, blurRadius.toPx(), refractionHeight.toPx(), refractionAmount.toPx(),
              vibrant = true,
              refractionEnabled = !reducedMotion,
            )
          },
          highlight = {
            glassSettings.highlight(Highlight.Ambient.copy(alpha = (if (reducedMotion) 0.28f else 0.52f) * glowStrength))
          },
          shadow = {
            glassSettings.shadow(Shadow(
              radius = shadowElevation,
              color = Color.Black.copy(alpha = 0.16f * glowStrength),
            ))
          },
          innerShadow = {
            glassSettings.innerShadow(InnerShadow(
              radius = 2.dp,
              color = Color.White.copy(alpha = 0.14f * glowStrength),
            ))
          },
          onDrawSurface = {
            drawRect(glassSettings.surfaceColor(glassColor))
          },
        )
    } else {
      modifier
        .shadow(shadowElevation, shape)
        .clip(shape)
        .background(if (renderGlass) glassSettings.surfaceColor(glassColor) else fallbackColor)
    }

  CompositionLocalProvider(LocalContentColor provides contentColor) {
    Box(modifier = surfaceModifier, content = content)
  }
}
