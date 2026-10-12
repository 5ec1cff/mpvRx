/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.player.ytdlp

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.gyrolet.mpvrx.R

/**
 * Shown instead of silently installing/updating yt-dlp in the background before playback
 * (which used to leave the player buffering with no explanation on a link's first play).
 *
 * Mirrors the layout of [app.gyrolet.mpvrx.ui.securefolder.SecureConfirmDialog] /
 * `ConfirmDialog`, but with a third, visually separated action ([onConfigure]) pinned to the
 * start of the row so it doesn't get mistaken for a variant of confirm/cancel.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YtdlpInstallPromptDialog(
  isOpen: Boolean,
  onInstall: () -> Unit,
  onConfigure: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!isOpen) return

  app.gyrolet.mpvrx.presentation.components.AppAlertDialog(
    onDismissRequest = onDismiss,
    modifier = modifier,
    title = { Text(stringResource(R.string.ui_yt_dlp_not_installed)) },
    text = { Text(stringResource(R.string.ytdlp_install_prompt_subtitle)) },
    dismissButton = {
      app.gyrolet.mpvrx.presentation.components.AppDialogTextButton(onClick = onConfigure) {
        Text(stringResource(R.string.generic_configure))
      }
      app.gyrolet.mpvrx.presentation.components.AppDialogTextButton(onClick = onDismiss) {
        Text(stringResource(R.string.generic_cancel))
      }
    },
    confirmButton = {
      app.gyrolet.mpvrx.presentation.components.AppDialogTextButton(onClick = onInstall) {
        Text(stringResource(R.string.generic_install), fontWeight = FontWeight.Bold)
      }
    },
  )
}
