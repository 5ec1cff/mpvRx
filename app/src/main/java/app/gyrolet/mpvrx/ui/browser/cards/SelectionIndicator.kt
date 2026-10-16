package app.gyrolet.mpvrx.ui.browser.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.theme.AppMotion

/** Shared click handling that preserves each card's layout, clipping and semantics modifiers. */
@Composable
internal fun Modifier.mediaCardClickable(
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
): Modifier {
  val interactionSource = remember { MutableInteractionSource() }
  return this.combinedClickable(
    interactionSource = interactionSource,
    indication = LocalIndication.current,
    onClick = onClick,
    onLongClick = onLongClick,
  )
}

/**
 * Stable, visible empty space between neighboring media surfaces. Kept in the layout (not
 * conditional on selection) so toggling selection never jumps rows or reloads thumbnails.
 */
internal val MediaSelectionGridGap = 4.dp
// Keep the list and grid breathing room identical; gaps never depend on selection.
internal val MediaSelectionListGap = MediaSelectionGridGap

/**
 * A uniform grid cell height is essential for connected selections. LazyVerticalGrid
 * measures its children separately, so a mere heightIn(min) leaves shorter cells
 * incomplete when metadata wraps. Set an exact proportional height based on the
 * available cell width and reserve enough space for the text/chips.
 *
 * This modifier is grid-only; list rows retain natural heights.
 */
internal fun Modifier.mediaGridTileHeight(aspect: Float, footer: Dp): Modifier = layout { measurable, constraints ->
  val artworkWidth = (constraints.maxWidth - 8.dp.roundToPx()).coerceAtLeast(0)
  val target = (artworkWidth / aspect).toInt() + footer.roundToPx()
  val height = target.coerceIn(constraints.minHeight, constraints.maxHeight)
  val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
  layout(placeable.width, height) { placeable.placeRelative(0, 0) }
}

/**
 * Neighbors are from the visible, filtered/sorted media order. Logical start and end work for
 * RTL as RoundedCornerShape resolves them using the current layout direction.
 */
data class MediaSelectionNeighbors(
  val top: Boolean = false,
  val bottom: Boolean = false,
  val start: Boolean = false,
  val end: Boolean = false,
)

/** Grid-aware adjacency, also usable for vertical lists by passing columns = 1. */
fun mediaSelectionNeighbors(
  index: Int,
  itemCount: Int,
  columns: Int,
  isSelectedAt: (Int) -> Boolean,
): MediaSelectionNeighbors {
  if (index !in 0 until itemCount) return MediaSelectionNeighbors()
  val width = columns.coerceAtLeast(1)
  val column = index % width
  return MediaSelectionNeighbors(
    top = index >= width && isSelectedAt(index - width),
    bottom = index + width < itemCount && isSelectedAt(index + width),
    start = column > 0 && isSelectedAt(index - 1),
    end = column < width - 1 && index + 1 < itemCount && isSelectedAt(index + 1),
  )
}

/**
 * Independent selected cards keep their original modest all-round shape.
 * Adjacent selected cards share tight inner corners; exposed corners stay rounded.
 * Geometry is animated, never layout size, thumbnail requests or the item key.
 */
@Composable
internal fun rememberMediaSelectionShape(
  selected: Boolean,
  grid: Boolean,
  neighbors: MediaSelectionNeighbors = MediaSelectionNeighbors(),
): Shape {
  val outer = if (grid) 18.dp else 18.dp
  val inner = 5.dp
  val regular = 16.dp
  val topStart by androidx.compose.animation.core.animateDpAsState(
    targetValue = if (!selected) regular else if (neighbors.top || neighbors.start) inner else outer,
    animationSpec = AppMotion.spatial(AppMotion.Spatial.SnappyDp, snap()),
    label = "mediaTopStart",
  )
  val topEnd by androidx.compose.animation.core.animateDpAsState(
    targetValue = if (!selected) regular else if (neighbors.top || neighbors.end) inner else outer,
    animationSpec = AppMotion.spatial(AppMotion.Spatial.SnappyDp, snap()),
    label = "mediaTopEnd",
  )
  val bottomStart by androidx.compose.animation.core.animateDpAsState(
    targetValue = if (!selected) regular else if (neighbors.bottom || neighbors.start) inner else outer,
    animationSpec = AppMotion.spatial(AppMotion.Spatial.SnappyDp, snap()),
    label = "mediaBottomStart",
  )
  val bottomEnd by androidx.compose.animation.core.animateDpAsState(
    targetValue = if (!selected) regular else if (neighbors.bottom || neighbors.end) inner else outer,
    animationSpec = AppMotion.spatial(AppMotion.Spatial.SnappyDp, snap()),
    label = "mediaBottomEnd",
  )
  return RoundedCornerShape(
    topStart = topStart, topEnd = topEnd,
    bottomStart = bottomStart, bottomEnd = bottomEnd,
  )
}

internal val mediaSelectionColor: Color
  @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

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
