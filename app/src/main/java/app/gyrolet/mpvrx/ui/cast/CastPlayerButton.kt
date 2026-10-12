/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.cast

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import app.gyrolet.mpvrx.preferences.PlayerButton
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.liquidglass.LiquidIconButton
import app.gyrolet.mpvrx.ui.theme.controlColor
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.compose.ui.graphics.Color as ComposeColor

/** Uses the SDK button for Cast behavior while keeping the app's rounded symbol visible. */
@Composable
fun CastPlayerButton(
  hideBackground: Boolean,
  buttonSize: Dp,
  onInvoked: () -> Unit = {},
  contentColor: ComposeColor? = null,
  useGlass: Boolean = false,
) {
  val castContentDescription =
    androidx.compose.ui.res
      .stringResource(app.gyrolet.mpvrx.R.string.ui_cast)
  var routeButton by remember { mutableStateOf<MediaRouteButton?>(null) }
  val tint = contentColor ?: if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
  Box(modifier = Modifier.size(buttonSize), contentAlignment = Alignment.Center) {
    if (useGlass) {
      LiquidIconButton(
        icon = PlayerButton.CAST.icon,
        onClick = { routeButton?.performClick() },
        title = castContentDescription,
        tint = tint,
        size = buttonSize,
      )
    } else {
      Surface(
        shape = CircleShape,
        color =
          if (hideBackground) {
            ComposeColor.Transparent
          } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
          },
        contentColor = tint,
        border =
          if (hideBackground) {
            null
          } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
          },
        modifier = Modifier.fillMaxSize(),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = PlayerButton.CAST.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
          )
        }
      }
    }
    AndroidView(
      factory = { context ->
        MediaRouteButton(context).apply {
          setBackgroundColor(Color.TRANSPARENT)
          CastButtonFactory.setUpMediaRouteButton(context.applicationContext, this)
          setRemoteIndicatorDrawable(ColorDrawable(Color.TRANSPARENT))
          routeButton = this
        }
      },
      update = { button ->
        button.contentDescription = castContentDescription
        button.importantForAccessibility =
          if (useGlass) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        button.isFocusable = !useGlass
        button.setOnClickListener { onInvoked() }
      },
      modifier = if (useGlass) Modifier.size(0.dp) else Modifier.fillMaxSize(),
    )
  }
}
