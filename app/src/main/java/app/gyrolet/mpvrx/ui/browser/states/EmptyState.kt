/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.browser.states

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.ui.icons.AppIcon
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.theme.AppMotion
import app.gyrolet.mpvrx.ui.theme.spacing

@Composable
fun EmptyState(
  icon: AppIcon,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
) {
  BrowserStateContent(icon = icon, title = title, message = message, modifier = modifier)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BrowserStateContent(
  icon: AppIcon,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  loading: Boolean = false,
) {
  val reduceMotion = AppMotion.shouldReduceMotion()
  val scrollState = rememberScrollState()
  BoxWithConstraints(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    val scrollModifier = if (constraints.hasBoundedHeight) Modifier.verticalScroll(scrollState) else Modifier
    Column(
      modifier =
        Modifier
          .widthIn(max = 480.dp)
          .fillMaxWidth()
          .then(scrollModifier)
          .padding(MaterialTheme.spacing.large)
          .semantics { if (loading) liveRegion = LiveRegionMode.Polite },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
      if (loading && !reduceMotion) {
        LoadingIndicator(modifier = Modifier.size(80.dp))
      } else {
        Surface(
          modifier = Modifier.size(80.dp),
          shape = MaterialTheme.shapes.extraLarge,
          color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }

      Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLargeEmphasized,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
      )

      if (message.isNotBlank()) {
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
