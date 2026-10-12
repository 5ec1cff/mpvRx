/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.browser.medialibrary

import android.content.Context
import android.net.Uri
import android.util.Log
import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.utils.media.MediaUtils
import app.gyrolet.mpvrx.utils.storage.VideoScanUtils

/**
 * Persisted copy of the media library list, so a cold boot can show it immediately.
 *
 * MediaLibraryViewModel otherwise re-queries MediaStore once per folder on every launch. That is the
 * slowest of the three browser modes and, unlike the folder list, it had no cache at all. This stores
 * the resolved list alongside the MediaStore generation it was built from; the generation is what
 * [app.gyrolet.mpvrx.utils.storage.MediaStoreGenerationGuard] later compares to decide whether the
 * list is still trustworthy.
 *
 * Format is a delimiter-separated string rather than JSON, matching FolderListViewModel's cache. The
 * separator is stripped from stored values on write so a title or path containing it cannot corrupt
 * the record.
 */
internal object MediaLibraryCache {
  private const val TAG = "MediaLibraryCache"
  private const val PREFS = "media_library_cache"
  private const val KEY_VIDEOS = "videos_v1"

  // ASCII unit/record separators, escaped rather than embedded as literal control characters
  // so the source stays readable. Both are pathological in a filename.
  private const val FIELD_SEPARATOR = "\u001F"
  private const val RECORD_SEPARATOR = "\u001E"
  private const val FIELD_COUNT = 14

  /**
   * Fields written per video, in order. Mirrors the [Video] constructor; anything not listed here is
   * reconstructed on read.
   */
  private fun encode(video: Video): String =
    listOf(
      video.id.toString(),
      video.title,
      video.displayName,
      video.path,
      video.uri.toString(),
      video.duration.toString(),
      video.size.toString(),
      video.dateModified.toString(),
      video.dateAdded.toString(),
      video.mimeType,
      video.bucketId,
      video.bucketDisplayName,
      video.resolution,
      video.isAudio.toString(),
    ).joinToString(FIELD_SEPARATOR) { sanitize(it) }

  fun save(
    context: Context,
    videos: List<Video>,
  ) {
    runCatching {
      context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_VIDEOS, videos.joinToString(RECORD_SEPARATOR) { encode(it) })
        .apply()
    }.onFailure { error ->
      Log.w(TAG, "Could not persist media library cache", error)
    }
  }

  /** Returns the cached list, or null when nothing usable was stored. */
  fun load(context: Context): List<Video>? =
    runCatching {
      val raw =
        context.applicationContext
          .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
          .getString(KEY_VIDEOS, null)
          ?: return null
      if (raw.isBlank()) return null

      val videos = raw.split(RECORD_SEPARATOR).mapNotNull(::decode)
      // A single unreadable record means the format changed or the file was truncated; treating that
      // as an empty library would silently hide everything, so fall back to scanning instead.
      if (videos.isEmpty()) null else videos
    }.onFailure { error ->
      Log.w(TAG, "Could not read media library cache", error)
    }.getOrNull()

  fun clear(context: Context) {
    runCatching {
      context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_VIDEOS).apply()
    }.onFailure { error ->
      Log.w(TAG, "Could not clear media library cache", error)
    }
  }

  private fun decode(record: String): Video? {
    val parts = record.split(FIELD_SEPARATOR)
    if (parts.size != FIELD_COUNT) return null
    val id = parts[0].toLongOrNull() ?: return null
    val duration = parts[5].toLongOrNull() ?: return null
    val size = parts[6].toLongOrNull() ?: return null
    val dateModified = parts[7].toLongOrNull() ?: return null
    val dateAdded = parts[8].toLongOrNull() ?: return null
    val isAudio = parts[13].toBooleanStrictOrNull() ?: return null

    return Video(
      id = id,
      title = parts[1],
      displayName = parts[2],
      path = parts[3],
      uri = Uri.parse(parts[4]),
      duration = duration,
      durationFormatted = VideoScanUtils.formatDuration(duration),
      size = size,
      sizeFormatted = MediaUtils.formatFileSize(size),
      dateModified = dateModified,
      dateAdded = dateAdded,
      mimeType = parts[9],
      bucketId = parts[10],
      bucketDisplayName = parts[11],
      // Resolution, fps and dimensions are only used for optional chips. They are re-read by
      // MetadataRetrieval when a chip is enabled, so zero here is correct rather than stale.
      width = 0,
      height = 0,
      fps = 0f,
      resolution = parts[12],
      isAudio = isAudio,
    )
  }

  private fun sanitize(value: String): String =
    value
      .replace(FIELD_SEPARATOR, " ")
      .replace(RECORD_SEPARATOR, " ")
      .replace('\n', ' ')
      .replace('\r', ' ')
}
