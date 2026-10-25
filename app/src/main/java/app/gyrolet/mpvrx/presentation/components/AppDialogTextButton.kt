/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.gyrolet.mpvrx.ui.player.controls.components.tvFocusHighlight

/** Filled, morphing actions inside app dialogs; ordinary text buttons elsewhere. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDialogTextButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = ButtonDefaults.textShape,
  colors: ButtonColors? = null,
  elevation: ButtonElevation? = null,
  border: BorderStroke? = null,
  contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
  interactionSource: MutableInteractionSource? = null,
  shapes: ButtonShapes? = null,
  content: @Composable RowScope.() -> Unit,
) {
  val focusModifier = modifier.tvFocusHighlight(shape, clipToShape = false)
  val emphasis = LocalDialogActionEmphasis.current
  val scheme = MaterialTheme.colorScheme
  val resolvedColors = colors ?: when (emphasis) {
    DialogActionEmphasis.Primary -> ButtonDefaults.textButtonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)
    DialogActionEmphasis.Secondary -> ButtonDefaults.textButtonColors(containerColor = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer)
    DialogActionEmphasis.None -> ButtonDefaults.textButtonColors()
  }
  val resolvedShapes = shapes ?: if (emphasis != DialogActionEmphasis.None) ButtonDefaults.shapes() else null
  if (resolvedShapes != null) {
    androidx.compose.material3.TextButton(
      onClick = onClick, modifier = focusModifier, enabled = enabled, shapes = resolvedShapes,
      colors = resolvedColors, elevation = elevation, border = border,
      contentPadding = contentPadding, interactionSource = interactionSource, content = content,
    )
  } else {
    androidx.compose.material3.TextButton(
      onClick = onClick, modifier = focusModifier, enabled = enabled, shape = shape,
      colors = resolvedColors, elevation = elevation, border = border,
      contentPadding = contentPadding, interactionSource = interactionSource, content = content,
    )
  }
}
