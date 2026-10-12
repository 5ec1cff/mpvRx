/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.player.controls.components.rememberTvInitialFocusRequester
import app.gyrolet.mpvrx.ui.player.controls.components.tvFocusHighlight
import app.gyrolet.mpvrx.ui.player.controls.components.tvInitialFocus
import app.gyrolet.mpvrx.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfirmDialog(
  title: String,
  subtitle: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  customContent: (@Composable () -> Unit)? = null,
) {
  val initialFocusRequester = rememberTvInitialFocusRequester(requestKey = title)
  AppAlertDialog(
    onDismissRequest = onCancel,
    modifier = modifier,
    title = { Text(title) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      ) {
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        customContent?.invoke()
      }
    },
    dismissButton = {
      AppDialogTextButton(
        onClick = onCancel,
        modifier = Modifier.tvInitialFocus(initialFocusRequester)
          .tvFocusHighlight(MaterialTheme.shapes.extraLarge, focusedScale = 1.04f),
      ) { Text(stringResource(R.string.generic_cancel), fontWeight = FontWeight.Medium) }
    },
    confirmButton = {
      AppDialogTextButton(
        onClick = onConfirm,
        modifier = Modifier.tvFocusHighlight(MaterialTheme.shapes.extraLarge, focusedScale = 1.04f),
      ) { Text(stringResource(R.string.generic_confirm), fontWeight = FontWeight.Bold) }
    },
  )
}
