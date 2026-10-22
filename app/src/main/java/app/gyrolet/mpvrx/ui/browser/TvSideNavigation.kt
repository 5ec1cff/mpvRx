/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Side-panel navigation for Android TV. Layout, sizing, colours and focus behaviour are adapted
 * from NuvioTV's sidebar (https://github.com/NuvioMedia/NuvioTV, GPL-3.0).
 */

package app.gyrolet.mpvrx.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

internal object TvSideNavigationDefaults {
  val CollapsedWidth: Dp = 88.dp
  val ExpandedWidth: Dp = 264.dp
  val ItemHeight: Dp = 52.dp
  val IconSize: Dp = 22.dp
  val LeadingVisualSize: Dp = 34.dp
  val PanelCornerRadius: Dp = 30.dp

  const val PanelInMillis = 345
  const val PanelOutMillis = 385
  const val ColorMillis = 180
  const val FocusedScale = 1.06f
  val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * A collapsible left rail. It stays icon-only while the content owns focus and expands over the
 * content (without relaying it out) once D-pad focus enters the rail.
 */
@Composable
internal fun TvSideNavigation(
  tabs: List<MainScreen.MainTab>,
  selectedTab: MainScreen.MainTab,
  onTabSelected: (MainScreen.MainTab) -> Unit,
  onOpenSettings: () -> Unit,
  selectedItemFocusRequester: FocusRequester,
  onRailFocusChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val progress by animateFloatAsState(
    targetValue = if (expanded) 1f else 0f,
    animationSpec =
      if (expanded) {
        tween(TvSideNavigationDefaults.PanelInMillis, easing = FastOutSlowInEasing)
      } else {
        tween(TvSideNavigationDefaults.PanelOutMillis, easing = LinearOutSlowInEasing)
      },
    label = "tv_rail_progress",
  )
  val width by animateDpAsState(
    targetValue = if (expanded) TvSideNavigationDefaults.ExpandedWidth else TvSideNavigationDefaults.CollapsedWidth,
    animationSpec =
      tween(
        durationMillis = if (expanded) TvSideNavigationDefaults.PanelInMillis else TvSideNavigationDefaults.PanelOutMillis,
        easing = TvSideNavigationDefaults.EmphasizedEasing,
      ),
    label = "tv_rail_width",
  )

  val surface = MaterialTheme.colorScheme.surfaceContainer
  val panelShape =
    RoundedCornerShape(
      topEnd = TvSideNavigationDefaults.PanelCornerRadius * progress,
      bottomEnd = TvSideNavigationDefaults.PanelCornerRadius * progress,
    )
  val lastIndex = tabs.lastIndex

  Box(
    modifier =
      modifier
        .fillMaxHeight()
        .width(width)
        .clip(panelShape)
        .background(
          Brush.horizontalGradient(
            listOf(
              surface.copy(alpha = 0.55f + 0.42f * progress),
              surface.copy(alpha = 0.35f + 0.6f * progress),
            ),
          ),
        ),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxHeight()
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
          .padding(horizontal = 12.dp, vertical = 24.dp)
          .onFocusChanged { state ->
            if (expanded != state.hasFocus) {
              expanded = state.hasFocus
              onRailFocusChanged(state.hasFocus)
            }
          }
          .focusRestorer(selectedItemFocusRequester)
          .focusGroup(),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      TvRailHeader(expanded = expanded)
      Spacer(Modifier.weight(1f))
      tabs.forEachIndexed { index, tab ->
        val selected = tab == selectedTab
        TvSideNavigationItem(
          label = mainNavigationLabel(tab),
          selected = selected,
          expanded = expanded,
          onClick = { onTabSelected(tab) },
          modifier =
            Modifier
              .then(if (selected) Modifier.focusRequester(selectedItemFocusRequester) else Modifier)
              .focusProperties {
                left = FocusRequester.Cancel
                if (index == 0) up = FocusRequester.Cancel
              },
        ) { tint ->
          MainTabIcon(
            tab = tab,
            tint = tint,
            contentDescription = null,
            iconSize = TvSideNavigationDefaults.IconSize,
          )
        }
      }
      Spacer(Modifier.weight(1f))
      TvSideNavigationItem(
        label = stringResource(R.string.tv_nav_settings),
        selected = false,
        expanded = expanded,
        onClick = onOpenSettings,
        modifier =
          Modifier.focusProperties {
            left = FocusRequester.Cancel
            down = FocusRequester.Cancel
            if (lastIndex < 0) up = FocusRequester.Cancel
          },
      ) { tint ->
        Icon(
          Icons.RoundedFilled.Settings,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(TvSideNavigationDefaults.IconSize),
        )
      }
    }
  }
}

@Composable
private fun TvRailHeader(expanded: Boolean) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(TvSideNavigationDefaults.ItemHeight)
        .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier
          .size(TvSideNavigationDefaults.LeadingVisualSize)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(R.string.app_name).take(1),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    }
    AnimatedVisibility(
      visible = expanded,
      enter = fadeIn(tween(280)) + expandHorizontally(tween(TvSideNavigationDefaults.PanelInMillis)),
      exit = fadeOut(tween(200)) + shrinkHorizontally(tween(TvSideNavigationDefaults.PanelOutMillis)),
    ) {
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 14.dp),
      )
    }
  }
}

@Composable
private fun TvSideNavigationItem(
  label: String,
  selected: Boolean,
  expanded: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: @Composable (tint: Color) -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val focused by interactionSource.collectIsFocusedAsState()
  val accent = MaterialTheme.colorScheme.primary
  val onSurface = MaterialTheme.colorScheme.onSurface
  val colorSpec = tween<Color>(TvSideNavigationDefaults.ColorMillis)

  val background by animateColorAsState(
    targetValue =
      when {
        focused && selected -> accent.copy(alpha = 0.28f)
        focused -> onSurface.copy(alpha = 0.12f)
        selected -> accent.copy(alpha = 0.15f)
        else -> Color.Transparent
      },
    animationSpec = colorSpec,
    label = "tv_rail_item_bg",
  )
  val contentColor by animateColorAsState(
    targetValue =
      when {
        selected -> accent
        focused -> onSurface
        else -> onSurface.copy(alpha = 0.72f)
      },
    animationSpec = colorSpec,
    label = "tv_rail_item_content",
  )
  val scale by animateFloatAsState(
    targetValue = if (focused) TvSideNavigationDefaults.FocusedScale else 1f,
    animationSpec = tween(TvSideNavigationDefaults.ColorMillis, easing = FastOutSlowInEasing),
    label = "tv_rail_item_scale",
  )

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(TvSideNavigationDefaults.ItemHeight)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }.clip(CircleShape)
        .background(background)
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          role = Role.Tab,
          onClick = onClick,
        ).padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier.size(TvSideNavigationDefaults.LeadingVisualSize),
      contentAlignment = Alignment.Center,
    ) {
      icon(contentColor)
    }
    AnimatedVisibility(
      visible = expanded,
      enter = fadeIn(tween(280)) + expandHorizontally(tween(TvSideNavigationDefaults.PanelInMillis)),
      exit = fadeOut(tween(200)) + shrinkHorizontally(tween(TvSideNavigationDefaults.PanelOutMillis)),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 14.dp),
      )
    }
  }
}
