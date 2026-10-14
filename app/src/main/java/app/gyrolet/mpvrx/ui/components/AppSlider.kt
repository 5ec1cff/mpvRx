package app.gyrolet.mpvrx.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.RangeSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.liquidglass.LocalKyantPlayerBackdrop
import app.gyrolet.mpvrx.ui.liquidglass.LiquidSlider
import app.gyrolet.mpvrx.ui.liquidglass.liquidGlassEffects
import app.gyrolet.mpvrx.ui.liquidglass.rememberLiquidGlassSettings
import app.gyrolet.mpvrx.ui.theme.AppMotion
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  steps: Int = 0,
  onValueChangeFinished: (() -> Unit)? = null,
  colors: SliderColors = SliderDefaults.colors(),
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  onInteractionChange: (Boolean) -> Unit = {},
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val glassEnabled by appearancePreferences.liquidGlassEnabled.collectAsState()
  if (!glassEnabled || !valueRange.start.isFinite() || !valueRange.endInclusive.isFinite() || valueRange.endInclusive <= valueRange.start) {
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    LaunchedEffect(pressed, dragged) { onInteractionChange(pressed || dragged) }
    val materialState = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    materialState.value = value
    androidx.compose.material3.Slider(
      state = materialState,
      onValueChange = onValueChange,
      modifier = modifier,
      enabled = enabled,
      onValueChangeFinished = onValueChangeFinished,
      colors = colors,
      interactionSource = interactionSource,
    )
    return
  }

  val sliderState = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
  var isUserInteracting by remember(sliderState) { mutableStateOf(false) }
  // Incoming preference/MPV updates must not overwrite a value owned by the active drag.
  // Sync only when the external value changes, rather than on every recomposition.
  LaunchedEffect(value, sliderState) {
    if (!isUserInteracting) sliderState.value = value
  }
  val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val canvasBackdrop = rememberCanvasBackdrop { drawRect(surfaceColor) }
  val backdrop = LocalKyantPlayerBackdrop.current ?: canvasBackdrop
  val span = valueRange.endInclusive - valueRange.start
  val updateValue: (Float) -> Unit = { changedValue ->
    sliderState.value = changedValue
    val snappedValue = sliderState.value
    if (snappedValue != value) onValueChange(snappedValue)
  }
  LiquidSlider(
    value = { sliderState.value },
    onValueChange = updateValue,
    valueRange = valueRange,
    steps = steps,
    visibilityThreshold = if (steps > 0) span / (steps + 1) / 100f else span / 1000f,
    backdrop = backdrop,
    modifier = modifier
      .progressSemantics(sliderState.value, valueRange, steps)
      .semantics {
        if (!enabled) disabled()
        setProgress { requestedValue ->
          if (!enabled) {
            false
          } else {
            val previousValue = sliderState.value
            updateValue(requestedValue)
            val changed = previousValue != sliderState.value
            if (changed) onValueChangeFinished?.invoke()
            changed
          }
        }
      },
    enabled = enabled,
    onValueChangeFinished = onValueChangeFinished,
    interactionSource = interactionSource,
    onInteractionChange = {
      isUserInteracting = it
      onInteractionChange(it)
    },
    accentColor = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor,
    trackColor = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor,
  )
}

@Composable
private fun GlassSliderThumb(
  interactionSource: MutableInteractionSource,
  enabled: Boolean,
  colors: SliderColors,
  trackBackdrop: Backdrop,
) {
  val settings = rememberLiquidGlassSettings()
  val pressed by interactionSource.collectIsPressedAsState()
  val dragged by interactionSource.collectIsDraggedAsState()
  val interacting = enabled && (pressed || dragged)
  val reduceMotion = AppMotion.shouldReduceMotion()
  val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val canvasBackdrop = rememberCanvasBackdrop { drawRect(surfaceColor) }
  val backgroundBackdrop = LocalKyantPlayerBackdrop.current ?: canvasBackdrop
  val backdrop = rememberCombinedBackdrop(backgroundBackdrop, trackBackdrop)
  // Only the idle range-slider thumbs are white; interacting retains its themed film.
  val tint = if (interacting) {
    androidx.compose.ui.graphics.lerp(
      MaterialTheme.colorScheme.surfaceContainerHigh,
      colors.activeTrackColor,
      0.18f,
    ).copy(alpha = 0.4f)
  } else {
    androidx.compose.ui.graphics.Color.White
  }
  val materialModifier = Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { Capsule() },
        effects = {
          liquidGlassEffects(
            settings,
            blurRadius = if (interacting) 2.dp.toPx() else 8.dp.toPx(),
            refractionHeight = if (interacting) 10.dp.toPx() else 2.dp.toPx(),
            refractionAmount = if (interacting) 14.dp.toPx() else 2.dp.toPx(),
            refractionEnabled = enabled && !reduceMotion,
          )
        },
        highlight = { settings.highlight(Highlight.Ambient) },
        shadow = { Shadow(radius = 4.dp) },
        innerShadow = { InnerShadow(radius = 4.dp) },
        onDrawSurface = { drawRect(settings.surfaceColor(tint)) },
      )
  Box(Modifier.size(40.dp, 24.dp).then(materialModifier))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRangeSlider(
  value: ClosedFloatingPointRange<Float>,
  onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  steps: Int = 0,
  onValueChangeFinished: (() -> Unit)? = null,
  colors: SliderColors = SliderDefaults.colors(),
) {
  val preferences = koinInject<AppearancePreferences>()
  val glassEnabled by preferences.liquidGlassEnabled.collectAsState()
  val state = remember(steps, valueRange) { RangeSliderState(value.start, value.endInclusive, steps, valueRange) }
  if (!glassEnabled) {
    state.startValue = value.start
    state.endValue = value.endInclusive
    androidx.compose.material3.RangeSlider(
      state = state,
      onValueChange = onValueChange,
      modifier = modifier,
      enabled = enabled,
      onValueChangeFinished = onValueChangeFinished,
      colors = colors,
    )
    return
  }
  SideEffect {
    state.startValue = value.start
    state.endValue = value.endInclusive
  }
  val startInteraction = remember { MutableInteractionSource() }
  val endInteraction = remember { MutableInteractionSource() }
  val trackBackdrop = rememberLayerBackdrop()
  androidx.compose.material3.RangeSlider(
    state = state,
    onValueChange = onValueChange,
    modifier = modifier,
    enabled = enabled,
    onValueChangeFinished = onValueChangeFinished,
    colors = colors,
    startThumbInteractionSource = startInteraction,
    endThumbInteractionSource = endInteraction,
    startThumb = { GlassSliderThumb(startInteraction, enabled, colors, trackBackdrop) },
    endThumb = { GlassSliderThumb(endInteraction, enabled, colors, trackBackdrop) },
    track = { rangeState ->
      SliderDefaults.Track(
        rangeSliderState = rangeState,
        modifier = Modifier.height(6.dp).layerBackdrop(trackBackdrop),
        enabled = enabled,
        colors = colors,
      )
    },
  )
}