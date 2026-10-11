package app.gyrolet.mpvrx.ui.browser.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.theme.AppMotion

/**
 * Selection morph is restricted to the container's outline: no layout scaling or thumbnail
 * resizing. Animate only the selected card (the stable media key/thumbnail stays untouched).
 * Logical start/end radii automatically mirror for RTL.
 */
@Composable
internal fun rememberMediaSelectionShape(selected: Boolean, grid: Boolean): Shape {
  val progress by animateFloatAsState(
    targetValue = if (selected) 1f else 0f,
    animationSpec = AppMotion.spatial(AppMotion.Spatial.ExpressiveFast, snap()),
    label = "mediaSelectionShape",
  )
  val outer = if (grid) 28f else 26f
  val inner = if (grid) 8f else 10f
  return RoundedCornerShape(
    topStart = (16f + (outer - 16f) * progress).dp,
    topEnd = (16f + (inner - 16f) * progress).dp,
    bottomEnd = (16f + (outer - 16f) * progress).dp,
    bottomStart = (16f + (inner - 16f) * progress).dp,
  )
}

@Composable
internal fun animatedSelectionBorderColor(selected: Boolean): Color {
  val colors = MaterialTheme.colorScheme
  val stroke by animateColorAsState(
    targetValue = if (selected) colors.tertiary else colors.tertiary.copy(alpha = 0f),
    animationSpec = AppMotion.spatial(AppMotion.Effect.Color, snap()),
    label = "mediaSelectionStroke",
  )
  return stroke
}

internal val mediaSelectionColor: Color
  @Composable get() = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)

@Composable
internal fun animatedSelectionColor(
  selected: Boolean,
  unselectedColor: Color = mediaSelectionColor.copy(alpha = 0f),
): Color {
  val selectedColor = mediaSelectionColor
  val color by animateColorAsState(
    targetValue = if (selected) selectedColor else unselectedColor,
    animationSpec = AppMotion.spatial(AppMotion.Effect.Color, snap()),
    label = "selectionTint",
  )
  return color
}

@Composable
internal fun SelectionIndicator(
  selected: Boolean,
  modifier: Modifier = Modifier,
) {
  val opacity by animateFloatAsState(
    targetValue = if (selected) 1f else 0f,
    animationSpec = AppMotion.spatial(AppMotion.Effect.Alpha, snap()),
    label = "selectionIndicatorAlpha",
  )
  val scale by animateFloatAsState(
    targetValue = if (selected) 1f else 0.88f,
    animationSpec = AppMotion.spatial(AppMotion.Spatial.ExpressiveFast, snap()),
    label = "selectionIndicatorScale",
  )
  Box(
    modifier =
      modifier.size(24.dp).clearAndSetSemantics { }
        .graphicsLayer {
          alpha = opacity
          scaleX = scale
          scaleY = scale
        }.background(MaterialTheme.colorScheme.tertiary, CircleShape)
        .padding(4.dp),
  ) {
    Icon(Icons.RoundedFilled.Check, null, tint = MaterialTheme.colorScheme.onTertiary)
  }
}