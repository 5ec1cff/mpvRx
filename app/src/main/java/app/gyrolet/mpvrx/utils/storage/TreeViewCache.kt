/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.utils.storage

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Blob store for a string snapshot, keyed by an opaque cache key.
 *
 * TreeViewScanner's own cache is process-local, so every cold boot rebuilt the whole folder tree
 * from MediaStore plus a filesystem walk. This lets that tree be persisted and restored instead.
 * [MediaStoreGenerationGuard] separately proves a restored snapshot still matches the device, so
 * this only handles storage: it cannot decide on its own that data is fresh.
 *
 * Lives in `cacheDir` because it is a pure derivative of the device's media — the OS may evict it
 * at any time, and rebuilding is always safe.
 */
internal object TreeViewCache {
  private const val TAG = "TreeViewCache"
  private const val DIR = "tree_view_cache"

  fun save(
    context: Context,
    cacheKey: String,
    payload: String,
  ) {
    runCatching {
      val file = fileFor(context, cacheKey)
      val temp = File(file.parentFile, file.name + ".tmp")
      // Written to a temp file and renamed, so a kill mid-write cannot leave a truncated snapshot
      // that later decodes into a plausible-looking but wrong tree.
      temp.writeText(payload)
      if (!temp.renameTo(file)) {
        temp.delete()
        throw IllegalStateException("Could not commit tree cache")
      }
    }.onFailure { error ->
      Log.w(TAG, "Could not persist tree cache", error)
    }
  }

  fun load(
    context: Context,
    cacheKey: String,
  ): String? =
    runCatching {
      val file = fileFor(context, cacheKey)
      if (!file.isFile) return null
      file.readText().takeIf { it.isNotBlank() }
    }.onFailure { error ->
      Log.w(TAG, "Could not read tree cache", error)
    }.getOrNull()

  fun clear(context: Context) {
    runCatching {
      context.cacheDir.resolve(DIR).takeIf { it.isDirectory }?.listFiles()?.forEach { it.delete() }
    }.onFailure { error ->
      Log.w(TAG, "Could not clear tree cache", error)
    }
  }

  private fun fileFor(
    context: Context,
    cacheKey: String,
  ): File {
    val dir = context.cacheDir.resolve(DIR)
    if (!dir.isDirectory) dir.mkdirs()
    // cacheKey embeds user-facing preference values, so hash it rather than using it as a filename.
    return dir.resolve("${cacheKey.hashCode().toUInt().toString(16)}.tree")
  }
}
