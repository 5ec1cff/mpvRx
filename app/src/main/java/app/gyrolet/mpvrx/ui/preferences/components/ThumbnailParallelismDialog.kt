/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.thumbnail.effectiveThumbnailParallelism
import app.gyrolet.mpvrx.presentation.components.ConfirmDialog
import app.gyrolet.mpvrx.ui.components.AppSlider
import kotlin.math.roundToInt

/** "Auto (2 of 8)" or "4 of 8": the setting's value, shown in its summary and its dialog. */
@Composable
internal fun thumbnailParallelismLabel(
  setting: Int,
  automatic: Int,
  maximum: Int,
): String =
  if (setting <= 0) {
    stringResource(R.string.pref_appearance_thumbnail_parallelism_auto, automatic, maximum)
  } else {
    stringResource(R.string.pref_appearance_thumbnail_parallelism_value, setting.coerceIn(1, maximum), maximum)
  }

/** Picks how many thumbnails are generated at once. The slider's first step is Auto. */
@Composable
internal fun ThumbnailParallelismDialog(
  initialValue: Int,
  automatic: Int,
  maximum: Int,
  onConfirm: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  var selected by rememberSaveable { mutableIntStateOf(initialValue.coerceIn(0, maximum)) }
  ConfirmDialog(
    title = stringResource(R.string.pref_appearance_thumbnail_parallelism_title),
    subtitle = stringResource(R.string.pref_appearance_thumbnail_parallelism_dialog_summary),
    onConfirm = { onConfirm(selected) },
    onCancel = onDismiss,
    customContent = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = thumbnailParallelismLabel(selected, automatic, maximum),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
        AppSlider(
          value = selected.toFloat(),
          onValueChange = { selected = it.roundToInt().coerceIn(0, maximum) },
          valueRange = 0f..maximum.toFloat(),
          steps = (maximum - 1).coerceAtLeast(0),
        )
        if (effectiveThumbnailParallelism(selected, automatic) > automatic) {
          Text(
            text = stringResource(R.string.pref_appearance_thumbnail_parallelism_warning, automatic),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
  )
}
