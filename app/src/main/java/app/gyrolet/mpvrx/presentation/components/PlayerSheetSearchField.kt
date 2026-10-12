/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Derived from BitChord's SearchField, GPL-3.0-or-later.
 */

package app.gyrolet.mpvrx.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

/**
 * Shared tonal search input for sheet lists. Filtering remains controlled by the caller;
 * optional search submission and clearing retain their existing focus and IME behavior.
 */
@Composable
fun PlayerSheetSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = stringResource(R.string.generic_search),
  onSubmit: (() -> Unit)? = null,
) {
  val focusManager = LocalFocusManager.current
  val submit = {
    onSubmit?.invoke()
    focusManager.clearFocus()
  }
  TextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier.fillMaxWidth(),
    singleLine = true,
    textStyle = MaterialTheme.typography.bodyLarge,
    shape = MaterialTheme.shapes.extraLarge,
    placeholder = { Text(placeholder) },
    leadingIcon = {
      if (onSubmit != null) {
        IconButton(
          onClick = submit,
          enabled = query.isNotBlank(),
          shapes = IconButtonDefaults.shapes(),
          modifier = Modifier.size(48.dp),
        ) {
          Icon(
            imageVector = Icons.RoundedFilled.Search,
            contentDescription = stringResource(R.string.generic_search),
            modifier = Modifier.size(24.dp),
          )
        }
      } else {
        Icon(
          imageVector = Icons.RoundedFilled.Search,
          contentDescription = null,
          modifier = Modifier.size(24.dp),
        )
      }
    },
    trailingIcon =
      if (query.isNotEmpty()) {
        {
          IconButton(
            onClick = {
              onQueryChange("")
              focusManager.clearFocus()
            },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              imageVector = Icons.RoundedFilled.Close,
              contentDescription = stringResource(R.string.generic_clear),
              modifier = Modifier.size(24.dp),
            )
          }
        }
      } else {
        null
      },
    colors =
      TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { submit() }),
  )
}
