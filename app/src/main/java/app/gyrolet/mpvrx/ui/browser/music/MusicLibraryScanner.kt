/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.browser.music

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

object MusicLibraryScanner {

  private const val TAG = "MusicLibraryScanner"
  private val ALBUM_ART_BASE_URI = Uri.parse("content://media/external/audio/albumart")
  private val albumTagCache = LruCache<String, String>(4096)

  private data class PendingAlbumTag(
    val songIndex: Int,
    val indexedAlbum: String?,
    val mediaStoreAlbumId: Long,
  )

  suspend fun scanSongs(
    context: Context,
    onSongsIndexed: (suspend (List<MusicSong>) -> Unit)? = null,
  ): List<MusicSong> = withContext(Dispatchers.IO) {
    val songs = mutableListOf<MusicSong>()
    val pendingAlbumTags = mutableListOf<PendingAlbumTag>()
    val projection = arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.TITLE,
      MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.ALBUM,
      MediaStore.Audio.Media.ALBUM_ID,
      MediaStore.Audio.Media.DURATION,
      MediaStore.Audio.Media.DATA,
      MediaStore.Audio.Media.DATE_ADDED,
      MediaStore.Audio.Media.DATE_MODIFIED,
      MediaStore.Audio.Media.TRACK,
      MediaStore.Audio.Media.YEAR,
      MediaStore.Audio.Media.SIZE
    ).let { columns ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) columns + MediaStore.MediaColumns.RELATIVE_PATH else columns
    }

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 1000"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    try {
      context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        sortOrder
      )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
        val relativePathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        } else -1
        val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

        while (cursor.moveToNext()) {
          currentCoroutineContext().ensureActive()
          val id = cursor.getLong(idCol)
          val path = cursor.getString(dataCol)
          val size = cursor.getLong(sizeCol)
          val duration = cursor.getLong(durationCol)
          val dateModified = cursor.getLong(dateModifiedCol)

          // On Android 10+ the DATA column may be null or stale; use content URI as fallback.
          val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
          val effectivePath = path ?: contentUri.toString()
          val file = path?.let { File(it) }
          if (size <= 0L && duration <= 0L) {
            val fileExists = try { file?.exists() == true } catch (_: Exception) { false }
            if (!fileExists) continue
          }
          if (app.gyrolet.mpvrx.domain.audiobook.AudiobookMarkerUtils.isAudiobookPath(path ?: effectivePath)) continue

          val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: (file?.nameWithoutExtension ?: id.toString())
          val artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Artist"
          val indexedAlbum = cursor.getString(albumCol)?.trim()?.takeIf { it.isNotBlank() && it != "<unknown>" }
          val folderName = file?.parentFile?.name ?: relativePathCol.takeIf { it >= 0 }?.let { column ->
            cursor.getString(column)?.let { File(it).name }
          }
          val needsAlbumVerification =
            indexedAlbum == null || folderName == null || indexedAlbum.equals(folderName, ignoreCase = true)
          val cachedAlbumTag = if (needsAlbumVerification) {
            albumTagCache.get(albumTagCacheKey(contentUri, indexedAlbum, dateModified, size))
          } else null
          val album = if (needsAlbumVerification) cachedAlbumTag?.takeIf(String::isNotBlank) else indexedAlbum
          val mediaStoreAlbumId = cursor.getLong(albumIdCol)
          val albumId = if (album?.equals(indexedAlbum, ignoreCase = true) == true) mediaStoreAlbumId else 0L
          val dateAdded = cursor.getLong(dateAddedCol)
          val track = cursor.getInt(trackCol)
          val year = cursor.getInt(yearCol)

          if (needsAlbumVerification && cachedAlbumTag == null) {
            pendingAlbumTags.add(PendingAlbumTag(songs.size, indexedAlbum, mediaStoreAlbumId))
          }

          // Keep MediaStore's artwork identity even when we intentionally avoid its album ID for
          // grouping because the embedded album tag disagrees with the index. Dropping the raw ID
          // here made otherwise valid cover art disappear for those songs.
          val albumArtUri =
            if (mediaStoreAlbumId > 0) ContentUris.withAppendedId(ALBUM_ART_BASE_URI, mediaStoreAlbumId) else null

          songs.add(
            MusicSong(
              id = id,
              title = title,
              artist = artist,
              album = album ?: "Unknown Album",
              albumId = albumId,
              durationMs = duration,
              path = effectivePath,
              uri = contentUri,
              dateAdded = dateAdded,
              trackNumber = track,
              year = year,
              albumArtUri = albumArtUri,
              size = size,
              hasAlbumTag = album != null,
              dateModified = dateModified,
            )
          )
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error scanning songs from MediaStore", e)
    }

    if (pendingAlbumTags.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      onSongsIndexed?.invoke(songs.toList())
      for (pending in pendingAlbumTags) {
        currentCoroutineContext().ensureActive()
        val song = songs[pending.songIndex]
        val album = resolveAlbumTag(context, song.uri, pending.indexedAlbum, song.dateModified, song.size)
        songs[pending.songIndex] = song.copy(
          album = album ?: "Unknown Album",
          albumId = if (album?.equals(pending.indexedAlbum, ignoreCase = true) == true) pending.mediaStoreAlbumId else 0L,
          hasAlbumTag = album != null,
        )
      }
    }

    songs
  }

  private fun albumTagCacheKey(uri: Uri, indexedAlbum: String?, dateModified: Long, size: Long): String =
    "$uri:$dateModified:$size:${indexedAlbum.orEmpty()}"

  private fun resolveAlbumTag(
    context: Context,
    uri: Uri,
    indexedAlbum: String?,
    dateModified: Long,
    size: Long,
  ): String? {
    val cacheKey = albumTagCacheKey(uri, indexedAlbum, dateModified, size)
    albumTagCache.get(cacheKey)?.let { return it.takeIf(String::isNotBlank) }
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(context, uri)
      val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        ?.trim()?.takeIf { it.isNotBlank() && it != "<unknown>" }
      albumTagCache.put(cacheKey, album.orEmpty())
      album
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      null
    } finally {
      retriever.release()
    }
  }

}
