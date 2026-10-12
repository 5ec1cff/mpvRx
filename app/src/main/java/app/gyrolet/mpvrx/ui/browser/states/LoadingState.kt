/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.browser.states

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.icons.AppIcon
import app.gyrolet.mpvrx.ui.icons.Icons

@Composable
fun LoadingState(
  icon: AppIcon = Icons.RoundedFilled.FolderOpen,
  title: String = stringResource(R.string.ui_scanning_for_videos),
  message: String = "",
  modifier: Modifier = Modifier,
) {
  BrowserStateContent(icon = icon, title = title, message = message, modifier = modifier, loading = true)
}
