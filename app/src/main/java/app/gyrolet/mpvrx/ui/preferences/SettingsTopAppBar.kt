/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.preferences

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalShowSettingsBackArrow

/** Standard settings-screen top bar; the back arrow follows [LocalShowSettingsBackArrow]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTopAppBar(
  title: String,
  onBack: () -> Unit,
  titleModifier: Modifier = Modifier,
) {
  TopAppBar(
    title = {
      Text(
        modifier = titleModifier,
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
      )
    },
    navigationIcon = {
      if (LocalShowSettingsBackArrow.current) {
        IconButton(onClick = onBack) {
          Icon(
            Icons.RoundedFilled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    },
  )
}
