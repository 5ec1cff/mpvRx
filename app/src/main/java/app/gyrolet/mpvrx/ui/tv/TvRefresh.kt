/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.controls.components.tvFocusHighlight

/**
 * TV replacement for pull-to-refresh, which a remote cannot perform and which fights vertical
 * D-pad scrolling: a Refresh button above the content and the remote's Refresh key. The button
 * sits at the end of the row so the first list item, not the button, is where focus starts.
 */
@Composable
internal fun TvRefreshContainer(
  isRefreshing: Boolean,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val currentOnRefresh by rememberUpdatedState(onRefresh)
  val refreshing by rememberUpdatedState(isRefreshing)
  val contentFocus = remember { FocusRequester() }
  RegisterTvContentFocus(contentFocus)

  Column(
    modifier =
      modifier.onKeyEvent { event ->
        if (!enabled || event.key != Key.Refresh) return@onKeyEvent false
        if (event.type == KeyEventType.KeyUp && !refreshing) currentOnRefresh()
        true
      },
  ) {
    if (enabled) {
      TvRefreshButton(
        isRefreshing = isRefreshing,
        // Kept enabled while refreshing: disabling the focused button would drop focus.
        onClick = { if (!refreshing) currentOnRefresh() },
        modifier =
          Modifier
            .align(Alignment.End)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
      )
    }
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .weight(1f)
          .focusRequester(contentFocus)
          .focusGroup(),
      content = content,
    )
  }
}

@Composable
private fun TvRefreshButton(
  isRefreshing: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FilledTonalButton(
    onClick = onClick,
    modifier = modifier.tvFocusHighlight(CircleShape, focusedScale = 1.06f),
    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
  ) {
    Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
      if (isRefreshing) {
        CircularProgressIndicator(
          modifier = Modifier.size(16.dp),
          color = LocalContentColor.current,
          strokeWidth = 2.dp,
        )
      } else {
        Icon(Icons.RoundedFilled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
      }
    }
    Spacer(Modifier.width(8.dp))
    Text(stringResource(R.string.ui_refresh))
  }
}
