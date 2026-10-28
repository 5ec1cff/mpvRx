/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.gyrolet.mpvrx.ui.preferences.playerSettingSummary
import app.gyrolet.mpvrx.ui.preferences.playerSettingAvailability

/** Shared preference row, including the availability of an action in the running player. */
@Composable
fun AppPreference(
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: (@Composable () -> Unit)? = null,
  summary: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
) {
  val availability = playerSettingAvailability(modifier)
  if (!availability.visible) return
  me.zhanghai.compose.preference.Preference(
    title = title,
    modifier = modifier,
    enabled = enabled && availability.enabled,
    icon = icon,
    summary = playerSettingSummary(availability, enabled, summary),
    onClick = onClick?.let { action -> { if (enabled && availability.enabled) action() } },
  )
}
