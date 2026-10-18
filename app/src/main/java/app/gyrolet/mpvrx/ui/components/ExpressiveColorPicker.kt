/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import app.gyrolet.mpvrx.ui.components.AppSlider as Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.theme.AppConnectedControlShapeTokens
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.theme.AppConnectedShapeTokens
import app.gyrolet.mpvrx.ui.theme.ConnectedLayout
import app.gyrolet.mpvrx.ui.theme.rememberConnectedShape
import kotlin.math.roundToInt

/** Inline editor shared by player panels and theme preferences. Colors are Android ARGB. */
@Composable
fun ExpressiveColorPicker(
  color: Int,
  onColorChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  showAlpha: Boolean = true,
  attachedToHeader: Boolean = false,
) {
  var sliders by rememberSaveable { mutableStateOf(false) }
  val hsv = remember { floatArrayOf(0f, 0f, 0f).also { AndroidColor.colorToHSV(color, it) } }
  var hue by remember { mutableFloatStateOf(hsv[0]) }
  var saturation by remember { mutableFloatStateOf(hsv[1]) }
  var brightness by remember { mutableFloatStateOf(hsv[2]) }
  var emittedColor by remember { mutableIntStateOf(color) }
  // Preserve the chosen hue at white/gray and both hue/saturation at black.
  LaunchedEffect(color) {
    if (color != emittedColor) {
      AndroidColor.colorToHSV(color, hsv)
      if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
      if (hsv[2] > 0f) saturation = hsv[1]
      brightness = hsv[2]
      emittedColor = color
    }
  }
  fun update(h: Float = hue, s: Float = saturation, v: Float = brightness) {
    hue = h
    saturation = s
    brightness = v
    emittedColor = AndroidColor.HSVToColor(if (showAlpha) AndroidColor.alpha(color) else 255, floatArrayOf(h, s, v))
    onColorChange(emittedColor)
  }
  val count = if (showAlpha) 4 else 3
  val offset = if (attachedToHeader) 1 else 0
  Column(modifier.alpha(if (enabled) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(AppConnectedShapeTokens.spacing)) {
    PickerTile(0 + offset, count + offset) {
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().selectableGroup(),
          horizontalArrangement = Arrangement.spacedBy(AppConnectedControlShapeTokens.spacing),
        ) {
          listOf(R.string.color_picker_field, R.string.color_picker_sliders).forEachIndexed { index, label ->
            val selected = sliders == (index == 1)
            val shape = rememberConnectedShape(index, 2, ConnectedLayout.Horizontal, tokens = AppConnectedControlShapeTokens)
            val colors = MaterialTheme.colorScheme
            Surface(
              modifier = Modifier.weight(1f).clip(shape).selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { sliders = index == 1 },
              ),
              shape = shape,
              color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
              contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
              border = BorderStroke(1.dp, if (selected) colors.secondaryContainer else colors.outlineVariant),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (selected) Icon(Icons.RoundedFilled.Check, null, modifier = Modifier.size(18.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                  modifier = Modifier.weight(1f, fill = false))
              }
            }
          }
        }
        if (sliders) {
          listOf(R.string.player_sheets_sub_color_red, R.string.player_sheets_sub_color_green, R.string.player_sheets_sub_color_blue).forEachIndexed { index, label ->
            val shift = 16 - index * 8
            ChannelSlider(stringResource(label), (color ushr shift) and 255, enabled) {
              onColorChange((color and (255 shl shift).inv()) or (it shl shift))
            }
          }
        } else {
          Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val fieldLabel = stringResource(R.string.color_picker_field_description)
            Canvas(
              Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                .semantics { contentDescription = fieldLabel }
                .colorGesture(enabled) { x, y -> update(s = x, v = 1f - y) },
            ) {
              drawRect(Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))))
              drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
              drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
              val radius = 9.dp.toPx()
              val point = Offset(
                (saturation * size.width).coerceIn(radius + 2.dp.toPx(), size.width - radius - 2.dp.toPx()),
                ((1f - brightness) * size.height).coerceIn(radius + 2.dp.toPx(), size.height - radius - 2.dp.toPx()),
              )
              drawCircle(Color.Black.copy(alpha = 0.5f), radius + 2.dp.toPx(), point, style = Stroke(3.dp.toPx()))
              drawCircle(Color.White, radius, point, style = Stroke(2.dp.toPx()))
            }
            val hueLabel = stringResource(R.string.color_picker_hue)
            Canvas(
              Modifier.width(48.dp).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                .semantics { contentDescription = hueLabel }
                .colorGesture(enabled) { _, y -> update(h = (y * 360f).coerceAtMost(359.99f)) },
            ) {
              drawRect(Brush.verticalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
              val y = (hue / 360f * size.height).coerceIn(12.dp.toPx(), size.height - 12.dp.toPx())
              drawCircle(Color.Black.copy(alpha = 0.5f), 11.dp.toPx(), Offset(center.x, y), style = Stroke(4.dp.toPx()))
              drawCircle(Color.White, 11.dp.toPx(), Offset(center.x, y), style = Stroke(2.dp.toPx()))
            }
          }
        }
      }
    }
    PickerTile(1 + offset, count + offset) {
      Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.size(44.dp).clip(CircleShape)) {
          drawCheckerboard()
          drawRect(Color(color))
        }
        Column {
          Text(stringResource(R.string.color_picker_selected), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(if (showAlpha) "#%08X".format(color) else "#%06X".format(color and 0xFFFFFF), style = MaterialTheme.typography.titleMedium)
        }
      }
    }
    if (showAlpha) {
      PickerTile(2 + offset, count + offset) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.player_sheets_sub_color_alpha), style = MaterialTheme.typography.labelLarge)
            Text("${(AndroidColor.alpha(color) / 255f * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
          }
          val alphaLabel = stringResource(R.string.player_sheets_sub_color_alpha)
          Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
              drawCheckerboard()
              drawRect(Brush.horizontalGradient(listOf(Color(color).copy(alpha = 0f), Color(color).copy(alpha = 1f))))
            }
            Slider(
              modifier = Modifier.semantics { contentDescription = alphaLabel },
              value = AndroidColor.alpha(color).toFloat(),
              onValueChange = { onColorChange((color and 0xFFFFFF) or (it.roundToInt().coerceIn(0, 255) shl 24)) },
              enabled = enabled,
              valueRange = 0f..255f,
              colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
            )
          }
        }
      }
    }
    PickerTile(count - 1 + offset, count + offset) {
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.color_picker_quick_colors), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          listOf(0xFFFFFFFF, 0xFF9E9E9E, 0xFF424242, 0xFF000000, 0xFFFF1744, 0xFFFF9100, 0xFFFFEA00, 0xFF00C853, 0xFF00E5FF, 0xFF2979FF, 0xFFD500F9).forEach { preset ->
            val rgb = preset.toInt()
            val isSelected = (color and 0xFFFFFF) == (rgb and 0xFFFFFF)
            Box(
              Modifier.size(48.dp).semantics {
                contentDescription = "#%06X".format(rgb and 0xFFFFFF)
                selected = isSelected
              }.clip(CircleShape).clickable(enabled = enabled) {
                onColorChange((rgb and 0xFFFFFF) or ((if (showAlpha) AndroidColor.alpha(color) else 255) shl 24))
              }.padding(6.dp).border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(if (isSelected) 4.dp else 1.dp).background(Color(rgb), CircleShape),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PickerTile(index: Int, count: Int, content: @Composable () -> Unit) {
  Surface(
    shape = rememberConnectedShape(index, count, ConnectedLayout.Vertical),
    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
    modifier = Modifier.fillMaxWidth(),
  ) { content() }
}

@Composable
private fun ChannelSlider(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
  Column {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, style = MaterialTheme.typography.labelMedium)
      Text(value.toString(), style = MaterialTheme.typography.labelMedium)
    }
    Slider(value = value.toFloat(), onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) }, valueRange = 0f..255f, enabled = enabled,
      modifier = Modifier.semantics { contentDescription = label })
  }
}

/** Capture the gesture immediately so picking cannot drag the parent player panel. */
@Composable
private fun Modifier.colorGesture(enabled: Boolean, onChange: (Float, Float) -> Unit): Modifier {
  val latestChange by rememberUpdatedState(onChange)
  return pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
      val down = awaitFirstDown()
      down.consume()
      fun update(position: Offset) {
        latestChange((position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f), (position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f))
      }
      update(down.position)
      do {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (change.pressed) update(change.position)
        change.consume()
      } while (change.pressed)
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerboard() {
  val cell = 6.dp.toPx()
  drawRect(Color.White)
  for (row in 0..(size.height / cell).toInt()) {
    for (column in 0..(size.width / cell).toInt()) {
      if ((row + column) % 2 == 0) drawRect(Color.LightGray, Offset(column * cell, row * cell), Size(cell, cell))
    }
  }
}
