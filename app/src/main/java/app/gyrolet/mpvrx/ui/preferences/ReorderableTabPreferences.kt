/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.gyrolet.mpvrx.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gyrolet.mpvrx.ui.components.IconSwitch
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import kotlin.math.roundToInt

/** Used by both main-navigation and music-library settings. */
internal data class ReorderableTabItem(
  val id: String,
  val title: String,
  val summary: String,
  val enabled: Boolean,
  val required: Boolean = false,
)

/**
 * Drag a handle up/down to reorder, or tap the handle to move to the next position.
 * A pressed switch only toggles visibility and cannot accidentally initiate a drag.
 * Persist changes at drag end to avoid canceling the active pointer input on recomposition.
 */
@Composable
internal fun ReorderableTabPreferences(
  tabs: List<ReorderableTabItem>,
  onEnabledChange: (String, Boolean) -> Unit,
  onMove: (String, Int) -> Unit,
) {
  val rowSizePx = with(LocalDensity.current) { 78.dp.toPx() }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    tabs.forEachIndexed { index, tab ->
      var offset by remember(tab.id) { mutableFloatStateOf(0f) }
      ExpressivePreferenceTile(index = index, itemCount = tabs.size) {
      Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp).padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .pointerInput(tab.id, index, tabs.size) {
              detectDragGesturesAfterLongPress(
                onDragStart = { offset = 0f },
                onDragCancel = { offset = 0f },
                onDragEnd = {
                  val steps = (offset / rowSizePx).roundToInt()
                  offset = 0f
                  if (steps != 0 && !tab.required) {
                    onMove(tab.id, (index + steps).coerceIn(0, tabs.lastIndex))
                  }
                },
                onDrag = { change, drag ->
                  change.consume()
                  if (!tab.required) offset += drag.y
                },
              )
            }
            .clickable(enabled = !tab.required && tabs.size > 1) {
              onMove(tab.id, if (index == tabs.lastIndex) 0 else index + 1)
            },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.RoundedFilled.DragHandle,
            contentDescription = if (tab.required) "Fixed first tab" else "Drag to reorder ${tab.title}; tap to move down",
            modifier = Modifier.size(25.dp).graphicsLayer { translationY = offset.coerceIn(-rowSizePx, rowSizePx) },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(tab.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
          if (tab.summary.isNotBlank()) {
            Text(tab.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        IconSwitch(
          checked = tab.enabled,
          onCheckedChange = if (tab.required) null else { checked -> onEnabledChange(tab.id, checked) },
        )
      }
      }
    }
  }
}
