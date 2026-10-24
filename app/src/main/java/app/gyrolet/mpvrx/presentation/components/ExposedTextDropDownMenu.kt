/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList

/** String-list form of [AppComboBox], kept for existing callers. */
@Composable
fun ExposedTextDropDownMenu(
  selectedValue: String,
  options: ImmutableList<String>,
  label: String,
  onValueChangedEvent: (String) -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
) {
  AppComboBox(
    options = options,
    selected = selectedValue,
    onSelect = onValueChangedEvent,
    optionLabel = { it },
    modifier = modifier,
    label = label,
    valueText = selectedValue,
    enabled = enabled,
    leadingIcon = leadingIcon,
  )
}
