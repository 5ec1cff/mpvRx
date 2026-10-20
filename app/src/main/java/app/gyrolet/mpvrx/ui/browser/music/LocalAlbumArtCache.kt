/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.browser.music

import android.os.SystemClock
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.sync.Semaphore

/**
 * Decoded local cover art for song, album and artist rows. Rows used to decode their cover again
 * whenever they scrolled back into view, each on its own IO thread, so a fling started dozens of
 * parallel decodes that competed with the UI thread on low-end devices.
 */
internal object LocalAlbumArtCache {
  private const val MISSING_ART_RETRY_MS = 5 * 60_000L

  private val images =
    object : LruCache<String, ImageBitmap>(
      ((Runtime.getRuntime().maxMemory() / 1024L) / 24L).toInt().coerceAtLeast(4 * 1024),
    ) {
      override fun sizeOf(key: String, value: ImageBitmap): Int =
        (value.width.toLong() * value.height * 4L / 1024L).toInt().coerceAtLeast(1)
    }

  // MediaStore throws for albums without indexed art; remember it instead of asking on every bind.
  private val missingArt = LruCache<String, Long>(1024)

  /** Bounds concurrent decodes; rows that scroll away are cancelled while they wait. */
  val decodePermits = Semaphore(2)

  fun get(key: String): ImageBitmap? = images.get(key)

  fun put(key: String, image: ImageBitmap) {
    images.put(key, image)
  }

  fun isArtMissing(uri: String): Boolean {
    val markedAt = missingArt.get(uri) ?: return false
    if (SystemClock.elapsedRealtime() - markedAt < MISSING_ART_RETRY_MS) return true
    missingArt.remove(uri)
    return false
  }

  fun markArtMissing(uri: String) {
    missingArt.put(uri, SystemClock.elapsedRealtime())
  }
}
