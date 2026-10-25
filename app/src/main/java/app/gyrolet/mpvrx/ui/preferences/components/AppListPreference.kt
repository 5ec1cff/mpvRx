/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import app.gyrolet.mpvrx.presentation.components.AppSingleChoiceDialog
import me.zhanghai.compose.preference.Preference

/** Keeps preference row/storage behavior while all choice popups use the shared app dialog. */
@Composable
fun <T> AppListPreference(
  value: T,
  onValueChange: (T) -> Unit,
  values: List<T>,
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: (@Composable () -> Unit)? = null,
  summary: (@Composable () -> Unit)? = null,
  valueToText: @Composable (T) -> AnnotatedString = { AnnotatedString(it.toString()) },
) {
  var openSelector by rememberSaveable { mutableStateOf(false) }
  if (openSelector && enabled) {
    AppSingleChoiceDialog(
      value = value,
      values = values,
      onValueChange = onValueChange,
      onDismissRequest = { openSelector = false },
      title = title,
      valueToText = valueToText,
    )
  }
  Preference(
    title = title,
    modifier = modifier,
    enabled = enabled,
    icon = icon,
    summary = summary,
    onClick = { openSelector = true },
  )
}
