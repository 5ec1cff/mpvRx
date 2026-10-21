/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.mediainfo

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.domain.thumbnail.ThumbnailInspection
import app.gyrolet.mpvrx.domain.thumbnail.ThumbnailRepository
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import app.gyrolet.mpvrx.preferences.ThumbnailGenerator
import app.gyrolet.mpvrx.preferences.ThumbnailMode
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.components.ConnectedInfoSurface
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.theme.AppConnectedControlShapeTokens
import app.gyrolet.mpvrx.ui.theme.AppConnectedShapeTokens
import app.gyrolet.mpvrx.ui.theme.ConnectedLayout
import app.gyrolet.mpvrx.ui.theme.rememberConnectedShape
import app.gyrolet.mpvrx.ui.theme.spacing
import app.gyrolet.mpvrx.utils.media.MediaInfoOps
import app.gyrolet.mpvrx.utils.storage.VideoScanUtils
import app.gyrolet.mpvrx.utils.storage.FileTypeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

@Composable
internal fun MediaThumbnailTab(video: Video, onValue: (String, String) -> Unit) {
  val repository = koinInject<ThumbnailRepository>()
  val preferences = koinInject<BrowserPreferences>()
  val generator by preferences.thumbnailGenerator.collectAsState()
  val revision by repository.cacheRevision.collectAsState()
  val quality by preferences.thumbnailQuality.collectAsState()
  val mode by preferences.thumbnailMode.collectAsState()
  val position by preferences.thumbnailFramePosition.collectAsState()
  var inspection by remember(video) { mutableStateOf<ThumbnailInspection?>(null) }
  var busy by remember(video) { mutableStateOf(false) }
  var failed by remember(video) { mutableStateOf(false) }
  var unavailable by remember(video) { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  suspend fun refresh(generate: Boolean) {
    busy = true
    failed = false
    unavailable = false
    try {
      // Reading this page never triggers extraction. Generation is an explicit user action only.
      if (generate) repository.getThumbnail(inspection?.cacheVideo ?: video, 0, 0)
      val snapshot = repository.inspectThumbnail(video)
      inspection = snapshot
      unavailable = generate && snapshot.preview == null
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      failed = true
    } finally {
      busy = false
    }
  }

  LaunchedEffect(video, generator, quality, mode, position, revision) { refresh(generate = false) }

  val info = inspection
  val status = stringResource(
    when {
      info?.preview != null && info.filePath != null -> R.string.thumbnail_status_disk
      info?.preview != null -> R.string.thumbnail_status_memory
      info?.filePath != null -> R.string.thumbnail_status_unreadable
      else -> R.string.thumbnail_status_missing
    },
  )
  val unknown = stringResource(R.string.generic_unknown)
  val rows = buildList<Pair<String, String>> {
    if (info != null) {
      add(stringResource(R.string.thumbnail_info_status) to status)
      add(
        stringResource(R.string.thumbnail_info_generator) to stringResource(
          when (info.generator) {
            ThumbnailGenerator.Auto -> R.string.pref_thumbnail_generator_auto
            ThumbnailGenerator.Native -> R.string.pref_thumbnail_generator_native
            ThumbnailGenerator.MediaStore -> R.string.pref_thumbnail_generator_mediastore
          },
        ),
      )
      add(
        stringResource(R.string.thumbnail_info_strategy) to when {
          info.generator == ThumbnailGenerator.Auto -> stringResource(R.string.thumbnail_strategy_auto)
          info.generator == ThumbnailGenerator.MediaStore -> stringResource(R.string.thumbnail_strategy_android)
          video.isAudio -> stringResource(R.string.thumbnail_strategy_artwork)
          else -> info.mode.displayName
        },
      )
      add(stringResource(R.string.thumbnail_info_quality) to info.quality.displayName)
      add(stringResource(R.string.thumbnail_info_maximum) to stringResource(R.string.thumbnail_maximum_value, info.quality.maxSizePx))
      add(
        stringResource(R.string.thumbnail_info_dimensions) to
          if (info.width != null && info.height != null) {
            stringResource(R.string.thumbnail_dimensions_value, info.width, info.height)
          } else unknown,
      )
      add(stringResource(R.string.thumbnail_info_format) to (info.mimeType ?: unknown))
      add(stringResource(R.string.thumbnail_info_size) to (info.fileSizeBytes?.let { VideoScanUtils.formatFileSize(it) } ?: unknown))
      add(stringResource(R.string.thumbnail_info_memory) to stringResource(if (info.cachedInMemory) R.string.thumbnail_yes else R.string.thumbnail_no))
      if (info.preview != null) {
        add(stringResource(R.string.thumbnail_info_preview_memory) to VideoScanUtils.formatFileSize(info.preview.byteCount.toLong()))
      }
      if (info.generator != ThumbnailGenerator.MediaStore && !video.isAudio && info.mode == ThumbnailMode.FrameAtPosition) {
        add(stringResource(R.string.thumbnail_info_frame) to stringResource(R.string.thumbnail_percent_value, info.framePositionPercent))
      }
      if (info.filePath != null) {
        add(stringResource(R.string.thumbnail_info_jpeg_setting) to stringResource(R.string.thumbnail_jpeg_value, info.jpegEncoderQuality))
        info.modifiedAtMillis?.let { time ->
          add(stringResource(R.string.thumbnail_info_modified) to DateFormat.getDateTimeInstance().format(Date(time)))
        }
        add(stringResource(R.string.thumbnail_info_cache_path) to info.filePath)
      }
      add(stringResource(R.string.thumbnail_info_source) to video.uri.toString())
    }
  }

  LazyColumn(
    contentPadding = PaddingValues(vertical = MaterialTheme.spacing.medium),
    verticalArrangement = Arrangement.spacedBy(AppConnectedShapeTokens.spacing),
  ) {
    item(key = "preview") {
      Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(
          Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp).padding(MaterialTheme.spacing.medium),
          contentAlignment = Alignment.Center,
        ) {
          val preview = info?.preview
          if (preview != null && !preview.isRecycled) {
            Image(
              bitmap = preview.asImageBitmap(),
              contentDescription = stringResource(R.string.thumbnail_preview_description, video.displayName),
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxWidth().heightIn(max = 328.dp),
            )
          } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
              Icon(Icons.RoundedFilled.Image, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
              Text(status, style = MaterialTheme.typography.titleMedium)
            }
          }
        }
      }
    }
    item(key = "actions") {
      val canGenerate = info != null && info.preview == null
      Row(
        Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.smaller),
        horizontalArrangement = Arrangement.spacedBy(AppConnectedControlShapeTokens.spacing),
      ) {
        FilledTonalButton(
          onClick = { scope.launch { refresh(generate = false) } },
          enabled = !busy,
          shape = rememberConnectedShape(0, if (canGenerate) 2 else 1, ConnectedLayout.Horizontal, tokens = AppConnectedControlShapeTokens),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.thumbnail_refresh)) }
        if (canGenerate) {
          FilledTonalButton(
            onClick = { scope.launch { refresh(generate = true) } },
            enabled = !busy,
            shape = rememberConnectedShape(1, 2, ConnectedLayout.Horizontal, tokens = AppConnectedControlShapeTokens),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
          ) { Text(stringResource(R.string.thumbnail_generate)) }
        }
      }
    }
    if (busy) item(key = "progress") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.smaller)) }
    item(key = "note") {
      Text(
        stringResource(when {
          failed -> R.string.thumbnail_inspection_failed
          unavailable -> R.string.thumbnail_generation_unavailable
          else -> R.string.thumbnail_quality_note
        }),
        style = MaterialTheme.typography.bodySmall,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = MaterialTheme.spacing.small),
      )
    }
    itemsIndexed(rows, key = { _, row -> row.first }) { index, (label, value) ->
      ConnectedInfoSurface(index, rows.size, onClick = { onValue(label, value) }) {
        Column(Modifier.padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
          Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
      }
    }
  }
}

/** Reuses the report's duration and the repository's normal indexed/stat cache identity lookup. */
internal fun mediaInfoThumbnailVideo(info: MediaInfoOps.MediaInfoData, uri: Uri, name: String, path: String): Video {
  val parts = info.general.duration.split(':').mapNotNull(String::toDoubleOrNull)
  val duration = if (parts.size == 3 && parts.all { it.isFinite() && it >= 0 }) {
    (parts.fold(0.0) { total, part -> total * 60 + part } * 1000).roundToLong()
  } else 0L
  val stream = info.videoStreams.firstOrNull()
  val audio = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in FileTypeUtils.AUDIO_EXTENSIONS ||
    (stream == null && info.audioStreams.isNotEmpty())
  return Video(
    id = 0L, title = name, displayName = name, path = path, uri = uri,
    duration = duration, durationFormatted = info.general.duration,
    size = 0L, sizeFormatted = "", dateModified = 0L, dateAdded = 0L,
    mimeType = if (audio) "audio/*" else "video/*", bucketId = "", bucketDisplayName = "",
    width = stream?.width?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
    height = stream?.height?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
    fps = 0f, resolution = "", isAudio = audio,
  )
}
