package app.gyrolet.mpvrx.ui.preferences.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.components.AppSlider
import app.gyrolet.mpvrx.ui.preferences.ExpressivePreferenceItem
import me.zhanghai.compose.preference.LocalPreferenceTheme
import me.zhanghai.compose.preference.Preference
import org.koin.compose.koinInject

@Composable
fun AppSliderPreference(
  value: Float,
  onValueChange: (Float) -> Unit,
  sliderValue: Float,
  onSliderValueChange: (Float) -> Unit,
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  valueSteps: Int = 0,
  enabled: Boolean = true,
  icon: (@Composable () -> Unit)? = null,
  summary: (@Composable () -> Unit)? = null,
  valueText: (@Composable () -> Unit)? = null,
) {
  val preferences = koinInject<AppearancePreferences>()
  val glassEnabled by preferences.liquidGlassEnabled.collectAsState()
  if (!glassEnabled) {
    ExpressivePreferenceItem(modifier = modifier) {
      me.zhanghai.compose.preference.SliderPreference(
      value = value,
      onValueChange = onValueChange,
      sliderValue = sliderValue,
      onSliderValueChange = onSliderValueChange,
      title = title,
      valueRange = valueRange,
      valueSteps = valueSteps,
      enabled = enabled,
      icon = icon,
      summary = summary,
      valueText = valueText,
      )
    }
    return
  }

  var observedValue by remember { mutableFloatStateOf(value) }
  SideEffect {
    if (observedValue != value) {
      onSliderValueChange(value)
      observedValue = value
    }
  }
  ExpressivePreferenceItem(modifier = modifier) {
  Preference(
    title = title,
    enabled = enabled,
    icon = icon,
    summary = {
      Column {
        summary?.invoke()
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Completion may arrive after recomposition; retain the last pointer value.
          var latestSliderValue by remember { mutableFloatStateOf(sliderValue) }
          var isUserInteracting by remember { mutableStateOf(false) }
          SideEffect { if (!isUserInteracting) latestSliderValue = sliderValue }
          AppSlider(
            value = sliderValue,
            onValueChange = { changedValue ->
              latestSliderValue = changedValue
              onSliderValueChange(changedValue)
            },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            valueRange = valueRange,
            steps = valueSteps,
            onValueChangeFinished = { onValueChange(latestSliderValue) },
            onInteractionChange = { isUserInteracting = it },
          )
          if (valueText != null) {
            Box(Modifier.padding(start = LocalPreferenceTheme.current.horizontalSpacing)) {
              valueText()
            }
          }
        }
      }
    },
  )
  }
}