/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.theme.AppConnectedShapeTokens
import app.gyrolet.mpvrx.ui.theme.ConnectedLayout
import app.gyrolet.mpvrx.ui.theme.connectedShape
import app.gyrolet.mpvrx.ui.theme.loadWallpaperBitmap
import app.gyrolet.mpvrx.ui.theme.saveWallpaperCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** Photo is decoration for this preview, never a launcher alias or the application's wallpaper. */
@Composable
internal fun IconPreviewCard(content: @Composable ColumnScope.() -> Unit) {
  val context = LocalContext.current
  val preferences = koinInject<AppearancePreferences>()
  val source by preferences.appIconPreviewWallpaper.collectAsState()
  val scope = rememberCoroutineScope()
  var saving by remember { mutableStateOf(false) }
  val loaded by produceState<Pair<String, Bitmap?>?>(initialValue = null, source) {
    val bitmap = if (source.isBlank()) null else withContext(Dispatchers.IO) { loadWallpaperBitmap(context, source) }
    value = source to bitmap
  }
  // Never display the previous photo while a replacement is loading, or after Remove.
  val photo = loaded?.takeIf { it.first == source }?.second
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null && !saving) {
      saving = true
      scope.launch {
        try {
          // Copy while the picker grant is live. Reuse the bounded, sampled wallpaper decoder;
          // the saved image survives provider removal and needs no broad media permission.
          val copy = saveWallpaperCopy(context, uri.toString())
          preferences.appIconPreviewWallpaper.set(copy)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Exception) {
          Toast.makeText(context, R.string.app_icon_photo_failed, Toast.LENGTH_LONG).show()
        } finally {
          saving = false
        }
      }
    }
  }
  val colors = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppConnectedShapeTokens.spacing)) {
    Box(
      Modifier
        .fillMaxWidth()
        .clip(connectedShape(0, 2, ConnectedLayout.Vertical))
        .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer))),
    ) {
      if (photo != null) {
        Image(
          bitmap = photo.asImageBitmap(),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize(),
        )
        // A fixed tint works on all supported Android versions without a live blur pass.
        Box(Modifier.matchParentSize().background(colors.surface.copy(alpha = 0.32f)))
      }
      Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          stringResource(R.string.app_icon_preview),
          style = MaterialTheme.typography.labelLarge,
          color = colors.onSurfaceVariant,
          modifier = Modifier
            .background(colors.surface.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        content()
      }
    }
    Surface(shape = connectedShape(1, 2, ConnectedLayout.Vertical), color = colors.surfaceContainer) {
      Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.app_icon_preview_photo), style = MaterialTheme.typography.titleSmall)
        Text(
          stringResource(R.string.app_icon_preview_photo_summary),
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
        TextButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !saving) {
          Text(stringResource(when {
            saving -> R.string.app_icon_loading_photo
            source.isBlank() -> R.string.app_icon_choose_photo
            else -> R.string.app_icon_replace_photo
          }))
        }
        if (source.isNotBlank()) {
          TextButton(onClick = { preferences.appIconPreviewWallpaper.set("") }, enabled = !saving) {
            Text(stringResource(R.string.app_icon_remove_photo))
          }
        }
      }
    }
  }
}
