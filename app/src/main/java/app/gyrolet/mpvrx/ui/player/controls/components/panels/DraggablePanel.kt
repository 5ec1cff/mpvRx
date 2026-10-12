/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

@file:Suppress("ktlint:standard:no-wildcard-imports")

package app.gyrolet.mpvrx.ui.player.controls.components.panels

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.presentation.components.PlayerSheetDragHandle
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.liquidglass.LocalKyantPlayerBackdrop
import app.gyrolet.mpvrx.ui.liquidglass.playerGlassSurface
import app.gyrolet.mpvrx.ui.liquidglass.LocalPlayerGlassSurfaceActive
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import org.koin.compose.koinInject
import app.gyrolet.mpvrx.ui.player.controls.panelCardsColors
import kotlin.math.roundToInt

/**
 * A draggable panel with an optional fixed header and scrollable content.
 *
 * @param modifier Modifier for the panel
 * @param header Optional composable for the fixed header that stays constant during scroll
 * @param content The scrollable content of the panel
 */
@Composable
fun DraggablePanel(
  modifier: Modifier = Modifier,
  header: (@Composable () -> Unit)? = null,
  shape: Shape? = null,
  containerColor: Color? = null,
  tonalElevation: Dp = 0.dp,
  shadowElevation: Dp = 0.dp,
  border: BorderStroke? = null,
  content: @Composable () -> Unit,
) {
  var offsetX by remember { mutableFloatStateOf(0f) }
  var panelWidth by remember { mutableIntStateOf(0) }
  val preferences = koinInject<AppearancePreferences>()
  val liquidGlassEnabled by preferences.liquidGlassEnabled.collectAsState()

  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
  val panelAlignment = AbsoluteAlignment.CenterRight
  val layoutDirection = LocalLayoutDirection.current

  BoxWithConstraints(
    modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
    contentAlignment = panelAlignment,
  ) {
    val freeSpace = (constraints.maxWidth - panelWidth).coerceAtLeast(0)
    val initialLeft =
      panelAlignment.align(
        size = IntSize(panelWidth, 0),
        space = IntSize(constraints.maxWidth, 0),
        layoutDirection = layoutDirection,
      ).x
    val minOffset = -initialLeft.toFloat()
    val maxOffset = (freeSpace - initialLeft).toFloat()

    val panelHeight = if (isPortrait) maxHeight * 0.62f else maxHeight

    val colors = panelCardsColors()
    val backdrop = LocalKyantPlayerBackdrop.current
    val panelShape = shape ?: MaterialTheme.shapes.extraLargeIncreased
    val panelColor = containerColor ?: colors.containerColor
    val expressivePanelColor = panelColor.copy(alpha = 1f)
    Surface(
      modifier =
        Modifier
          .absoluteOffset { IntOffset(offsetX.coerceIn(minOffset, maxOffset).roundToInt(), 0) }
          .onSizeChanged { panelWidth = it.width }
          .widthIn(max = 420.dp)
          .height(panelHeight)
          .playerGlassSurface(backdrop, panelShape, panelColor, enabled = liquidGlassEnabled),
      shape = panelShape,
      color = if (liquidGlassEnabled) Color.Transparent else expressivePanelColor,
      contentColor = colors.contentColor,
      tonalElevation = if (liquidGlassEnabled) 0.dp else tonalElevation,
      shadowElevation = if (liquidGlassEnabled) 0.dp else shadowElevation,
      border =
        if (liquidGlassEnabled) {
          null
        } else {
          border ?: BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
          )
        },
    ) {
      Column(Modifier.fillMaxWidth()) {
        // Drag Handle & Indicator
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .pointerInput(maxOffset, minOffset) {
                detectDragGestures { change, dragAmount ->
                  change.consume()
                  val newOffset = offsetX.coerceIn(minOffset, maxOffset) + dragAmount.x
                  offsetX = newOffset.coerceIn(minOffset, maxOffset)
                }
              },
          contentAlignment = Alignment.Center,
        ) {
          PlayerSheetDragHandle()
        }

        // Fixed header (if provided) - stays constant
        if (header != null) {
          Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            header()
          }
        }

        // Scrollable content
        Column(
          modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
        ) {
          CompositionLocalProvider(LocalPlayerGlassSurfaceActive provides liquidGlassEnabled) {
            content()
          }
        }
      }
    }
  }
}

@Composable
fun DraggablePanelHeader(
  title: String,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  leadingContent: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    leadingContent?.invoke()
    Text(
      text = title,
      style = MaterialTheme.typography.titleLargeEmphasized,
      modifier = Modifier.weight(1f),
      maxLines = 2,
    )
    FilledTonalIconButton(onClick = onDismissRequest) {
      Icon(Icons.RoundedFilled.Close, contentDescription = stringResource(R.string.generic_cancel))
    }
  }
}
