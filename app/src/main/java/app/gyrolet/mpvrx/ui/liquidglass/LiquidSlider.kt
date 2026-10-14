// Adapted from Square's Backdrop catalog LiquidSlider, Apache-2.0.
// https://github.com/Lelonio/Square
// https://github.com/Kyant0/AndroidLiquidGlass
// Upstream catalog commit: b18eb0ff12c616546a68c72e7d0097f1ab286c87.
// App changes: shared optics, enabled state, completion callbacks and reduced motion.

package app.gyrolet.mpvrx.ui.liquidglass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.DragInteraction
import kotlin.math.abs
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import app.gyrolet.mpvrx.ui.theme.AppMotion
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidSlider(
  value: () -> Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
  visibilityThreshold: Float,
  backdrop: Backdrop,
  modifier: Modifier = Modifier,
  accentColor: Color,
  trackColor: Color,
  steps: Int = 0,
  enabled: Boolean = true,
  onValueChangeFinished: (() -> Unit)? = null,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  onInteractionChange: (Boolean) -> Unit = {},
) {
  val trackBackdrop = rememberLayerBackdrop()
  val settings = rememberLiquidGlassSettings()
  val reduceMotion = AppMotion.shouldReduceMotion()
  val currentValue = rememberUpdatedState(value)
  val currentOnValueChange = rememberUpdatedState(onValueChange)
  val currentOnFinished = rememberUpdatedState(onValueChangeFinished)
  val currentOnInteractionChange = rememberUpdatedState(onInteractionChange)
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val animationScope = rememberCoroutineScope()
  // Like the player seekbar, draw the complete track and clamp only the thumb center.
  val thumbWidth = 40.dp
  val density = LocalDensity.current

  BoxWithConstraints(
    modifier.fillMaxWidth().height(48.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    val sliderWidthPx = constraints.maxWidth.toFloat()
    val thumbWidthPx = with(density) { thumbWidth.toPx() }.coerceAtMost(sliderWidthPx)
    val span = valueRange.endInclusive - valueRange.start
    fun progress(): Float = ((currentValue.value() - valueRange.start) / span).coerceIn(0f, 1f)
    val animation = remember(animationScope, valueRange, visibilityThreshold, reduceMotion) {
      DampedDragAnimation(
        animationScope = animationScope,
        initialValue = currentValue.value(),
        valueRange = valueRange,
        visibilityThreshold = visibilityThreshold,
        initialScale = 1f,
        pressedScale = if (reduceMotion) 1f else 1.5f,
        onDragStarted = {},
        onDragStopped = {},
        onDrag = { _, _ -> },
      )
    }
    LaunchedEffect(animation, reduceMotion) {
      snapshotFlow { currentValue.value() }.collectLatest { changedValue ->
        if (animation.targetValue != changedValue) {
          // The spring animates glass deformation, never the position under the finger.
          animation.snapToValue(changedValue)
        }
      }
    }

    if (enabled) {
      Box(
        Modifier.matchParentSize()
          .pointerInput(animation, sliderWidthPx, thumbWidthPx, isLtr, enabled, interactionSource) {
            fun seekTo(horizontalPosition: Float) {
              if (sliderWidthPx <= 0f) return
              val fraction = (horizontalPosition / sliderWidthPx).coerceIn(0f, 1f)
              val delta = (valueRange.endInclusive - valueRange.start) * fraction
              val target = (if (isLtr) valueRange.start + delta else valueRange.endInclusive - delta)
                .coerceIn(valueRange)
              currentOnValueChange.value(target)
            }
            if (sliderWidthPx <= 0f) return@pointerInput
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = true)
              val logicalValueX = sliderWidthPx * progress()
              val halfThumb = (thumbWidthPx / 2f).coerceAtMost(sliderWidthPx / 2f)
              val logicalCenterX = logicalValueX.coerceIn(halfThumb, sliderWidthPx - halfThumb)
              val centerX = if (isLtr) logicalCenterX else sliderWidthPx - logicalCenterX
              val valueX = if (isLtr) logicalValueX else sliderWidthPx - logicalValueX
              // Preserve the grab offset when picking up the thumb, including at either endpoint.
              // Tapping elsewhere on the track still seeks directly to the tapped value.
              val grabOffset = if (abs(down.position.x - centerX) <= thumbWidthPx / 2f) {
                down.position.x - valueX
              } else 0f
              val press = PressInteraction.Press(down.position)
              var drag: DragInteraction.Start? = null
              var completed = false
              currentOnInteractionChange.value(true)
              interactionSource.tryEmit(press)
              if (!reduceMotion) animation.press()
              down.consume()
              try {
                seekTo(down.position.x - grabOffset)
                while (true) {
                  // Observe movement before the parent panel's scroll handler consumes it.
                  // This keeps dragging continuous even inside vertically scrollable sheets;
                  // the slider consumes the pointer motion itself below.
                  val event = awaitPointerEvent(PointerEventPass.Initial)
                  val change = event.changes.firstOrNull { it.id == down.id } ?: break
                  if (change.isConsumed || event.changes.count { it.pressed } > 1) break
                  if (!change.pressed) {
                    seekTo(change.position.x - grabOffset)
                    completed = true
                    break
                  }
                  if (change.positionChanged()) {
                    if (drag == null) {
                      drag = DragInteraction.Start().also { interactionSource.tryEmit(it) }
                    }
                    change.consume()
                    seekTo(change.position.x - grabOffset)
                  }
                }
              } finally {
                try {
                  if (completed) currentOnFinished.value?.invoke()
                } finally {
                  currentOnInteractionChange.value(false)
                  interactionSource.tryEmit(if (completed) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                  drag?.let {
                    interactionSource.tryEmit(if (completed) DragInteraction.Stop(it) else DragInteraction.Cancel(it))
                  }
                  if (!reduceMotion) animation.release()
                }
              }
            }
          }.zIndex(1f),
      )
    }

    Box(Modifier.fillMaxWidth().layerBackdrop(trackBackdrop)) {
      Box(
        Modifier.clip(Capsule())
          .background(trackColor)
          .height(6.dp)
          .fillMaxWidth(),
      )
      Box(
        Modifier.clip(Capsule())
          .background(accentColor)
          .height(6.dp)
          .layout { measurable, trackConstraints ->
            val placeable = measurable.measure(trackConstraints)
            val width = (trackConstraints.maxWidth * progress()).fastRoundToInt()
            layout(width, placeable.height) { placeable.placeRelative(0, 0) }
          },
      )
      if (steps > 0) {
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
          val intervalCount = steps.toLong() + 1L
          val tickSpacing = size.width / intervalCount
          if (tickSpacing >= 4.dp.toPx()) {
            for (tickIndex in 0..intervalCount.toInt()) {
              val fraction = tickIndex.toFloat() / intervalCount
              val horizontalPosition = if (isLtr) size.width * fraction else size.width * (1f - fraction)
              drawCircle(
                color = if (fraction <= progress()) Color.White.copy(alpha = 0.7f) else accentColor.copy(alpha = 0.7f),
                radius = 1.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(horizontalPosition, size.height / 2f),
              )
            }
          }
        }
      }
    }

    val combinedBackdrop = rememberCombinedBackdrop(
      backdrop,
      rememberBackdrop(trackBackdrop) { drawTrack ->
        val progress = animation.pressProgress
        scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) { drawTrack() }
      },
    )
    // A translucent themed film keeps both the accent and the refracted content visible.
    val film = androidx.compose.ui.graphics.lerp(
      MaterialTheme.colorScheme.surfaceContainerHigh,
      accentColor,
      0.18f,
    ).copy(alpha = if (enabled) 0.72f - 0.28f * animation.pressProgress else 0.32f)
    // The idle thumb stays white; pressing it reveals the existing themed glass film.
    val thumbFilm = androidx.compose.ui.graphics.lerp(
      Color.White,
      film,
      animation.pressProgress.coerceIn(0f, 1f),
    )
    val thumbModifier = Modifier.drawBackdrop(
          backdrop = combinedBackdrop,
          shape = { Capsule() },
          effects = {
            val progress = animation.pressProgress
            liquidGlassEffects(
              settings,
              8.dp.toPx() * (1f - progress),
              10.dp.toPx() * progress,
              14.dp.toPx() * progress,
              refractionEnabled = enabled && !reduceMotion,
            )
          },
          highlight = {
            val progress = animation.pressProgress
            settings.highlight(Highlight.Ambient.copy(
              width = Highlight.Ambient.width / 1.5f,
              blurRadius = Highlight.Ambient.blurRadius / 1.5f,
              alpha = progress,
            ))
          },
          shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
          innerShadow = {
            val progress = animation.pressProgress
            InnerShadow(radius = 4.dp * progress, alpha = progress)
          },
          layerBlock = {
            scaleX = animation.scaleX
            scaleY = animation.scaleY
            val velocity = animation.velocity / 10f
            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
          },
          onDrawSurface = { drawRect(settings.surfaceColor(thumbFilm)) },
        )
    Box(
      Modifier.graphicsLayer {
        val halfThumb = (thumbWidthPx / 2f).coerceAtMost(sliderWidthPx / 2f)
        val safeCenterPx = (sliderWidthPx * progress())
          .coerceIn(halfThumb, sliderWidthPx - halfThumb)
        translationX = (safeCenterPx - thumbWidthPx / 2f) * if (isLtr) 1f else -1f
      }.then(thumbModifier).size(40.dp, 24.dp),
    )
  }
}