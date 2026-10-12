/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

@Composable
fun AppDropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  offset: DpOffset = DpOffset(0.dp, 0.dp),
  scrollState: ScrollState = rememberScrollState(),
  properties: PopupProperties = PopupProperties(focusable = true),
  shape: Shape = MaterialTheme.shapes.extraLargeIncreased,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  tonalElevation: Dp = MenuDefaults.TonalElevation,
  shadowElevation: Dp = MenuDefaults.ShadowElevation,
  border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
  content: @Composable ColumnScope.() -> Unit,
) {
  androidx.compose.material3.DropdownMenu(
    expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier,
    offset = offset, scrollState = scrollState, properties = properties, shape = shape,
    containerColor = containerColor, tonalElevation = tonalElevation, shadowElevation = shadowElevation,
    border = border, content = content,
  )
}
