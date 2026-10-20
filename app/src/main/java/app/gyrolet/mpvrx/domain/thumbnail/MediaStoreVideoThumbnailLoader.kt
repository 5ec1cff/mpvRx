/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.domain.thumbnail

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore
import android.util.Size
import app.gyrolet.mpvrx.domain.media.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** Android owns frame selection and rotation; null lets the caller use native extraction. */
@Suppress("DEPRECATION")
internal object MediaStoreVideoThumbnailLoader {
  private val requestSequence = AtomicLong()

  suspend fun load(
    context: Context,
    video: Video,
    localPath: String,
    sizePx: Int,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      suspendCancellableCoroutine { continuation ->
        val resolver = context.contentResolver
        val signal = CancellationSignal()
        val legacyRequest = AtomicReference<Pair<Long, Long>?>(null)
        continuation.invokeOnCancellation {
          signal.cancel()
          legacyRequest.get()?.let { (videoId, groupId) ->
            runCatching { MediaStore.Video.Thumbnails.cancelThumbnailRequest(resolver, videoId, groupId) }
          }
        }
        try {
          signal.throwIfCanceled()
          val uri = resolveVideoUri(context, video, localPath, signal)
          signal.throwIfCanceled()
          val bitmap =
            when {
              uri == null -> null
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                resolver.loadThumbnail(uri, Size(sizePx.coerceAtLeast(1), sizePx.coerceAtLeast(1)), signal)
              // The legacy helper addresses the external collection only. Never interpret an
              // internal-volume row ID as an unrelated external video.
              uri.pathSegments.firstOrNull() == "external" -> {
                val videoId = ContentUris.parseId(uri)
                val groupId = requestSequence.incrementAndGet()
                legacyRequest.set(videoId to groupId)
                signal.throwIfCanceled()
                MediaStore.Video.Thumbnails.getThumbnail(
                  resolver,
                  videoId,
                  groupId,
                  MediaStore.Video.Thumbnails.MINI_KIND,
                  null,
                )
              }
              else -> null
            }
          if (continuation.isActive) {
            continuation.resume(bitmap)
          } else {
            bitmap?.recycle()
          }
        } catch (cancellation: CancellationException) {
          continuation.cancel(cancellation)
        } catch (cancellation: OperationCanceledException) {
          continuation.cancel(cancellation)
        } catch (_: Exception) {
          // Missing index entries, revoked permissions and unsupported codecs use native fallback.
          continuation.resume(null)
        }
      }
    }

  private fun resolveVideoUri(
    context: Context,
    video: Video,
    localPath: String,
    signal: CancellationSignal,
  ): Uri? {
    val originalUri = video.uri
    if (originalUri.scheme == "content") {
      if (originalUri.authority == MediaStore.AUTHORITY) return originalUri
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val indexedUri =
          try {
            MediaStore.getMediaUri(context, originalUri)
          } catch (cancellation: OperationCanceledException) {
            throw cancellation
          } catch (_: Exception) {
            null
          }
        if (indexedUri != null) return indexedUri
      }
    }
    // Filesystem browsing supplies file:// URIs and filesystem IDs, not MediaStore row IDs.
    // Resolve the actual indexed video by its path rather than trusting Video.id.
    if (!localPath.startsWith("/")) return null
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.VOLUME_NAME)
      } else {
        arrayOf(MediaStore.Video.Media._ID)
      }
    return context.contentResolver.query(
      collection,
      projection,
      "${MediaStore.Video.Media.DATA}=?",
      arrayOf(localPath),
      null,
      signal,
    )?.use { cursor ->
      if (!cursor.moveToFirst()) return@use null
      val sourceCollection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          // Keep the physical volume: removable storage must not resolve to another volume's row.
          MediaStore.Video.Media.getContentUri(cursor.getString(1))
        } else {
          collection
        }
      ContentUris.withAppendedId(sourceCollection, cursor.getLong(0))
    }
  }
}
