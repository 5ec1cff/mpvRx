/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.gyrolet.mpvrx.utils.media

import android.content.Context
import android.net.Uri
import android.util.Log
import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.domain.media.model.VideoFolder
import app.gyrolet.mpvrx.utils.storage.FileTypeUtils
import app.gyrolet.mpvrx.utils.storage.VideoScanUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Play Store-safe explicit folder grants. Never tries to translate a SAF URI into a raw file path:
 * every playable item retains its content:// URI. Only immediate children of user-picked folders
 * are scanned; the user can explicitly add another nested folder without recursive storage walks.
 */
object ScopedHiddenFolderScanner {
  private const val TAG = "ScopedHiddenFolderScanner"

  fun isScopedFolder(bucketId: String): Boolean =
    bucketId.startsWith("content://") && Uri.parse(bucketId).path?.startsWith("/tree/") == true

  suspend fun getFolders(
    context: Context,
    grantedTreeUris: Set<String>,
    includeAudio: Boolean,
  ): List<VideoFolder> = withContext(Dispatchers.IO) {
    grantedTreeUris.sorted().mapNotNull { treeUri ->
      currentCoroutineContext().ensureActive()
      val root = openPersistedTreeDocument(context, treeUri) ?: return@mapNotNull null
      val videos = readVideos(context, treeUri, includeAudio)
      if (videos.isEmpty()) return@mapNotNull null
      VideoFolder(
        bucketId = treeUri,
        name = root.name ?: Uri.parse(treeUri).lastPathSegment?.substringAfterLast(':') ?: "Selected folder",
        path = treeUri,
        videoCount = videos.size,
        totalSize = videos.sumOf { it.size },
        totalDuration = videos.sumOf { it.duration },
        lastModified = videos.maxOfOrNull { it.dateModified } ?: 0L,
      )
    }.sortedBy { it.name.lowercase(Locale.ROOT) }
  }

  suspend fun getVideos(
    context: Context,
    treeUri: String,
    includeAudio: Boolean,
  ): List<Video> = withContext(Dispatchers.IO) {
    readVideos(context, treeUri, includeAudio)
  }

  private fun readVideos(
    context: Context,
    treeUri: String,
    includeAudio: Boolean,
  ): List<Video> {
    val root = openPersistedTreeDocument(context, treeUri) ?: return emptyList()
    val folderName = root.name ?: "Selected folder"
    return listTreeFilesSafely(root).mapNotNull { document ->
      run {
        val name = document.name ?: return@run null
        try {
          if (!document.isFile) return@run null
          val fakeFile = File(name)
          val isAudio = FileTypeUtils.isAudioFile(fakeFile)
          if (!(FileTypeUtils.isVideoFile(fakeFile) || (includeAudio && isAudio))) return@run null
          val documentUri = document.uri
          val uriString = documentUri.toString()
          val length = document.length().coerceAtLeast(0L)
          val modified = (document.lastModified() / 1_000L).coerceAtLeast(0L)
          Video(
            id = uriString.hashCode().toLong(),
            title = name.substringBeforeLast('.', name),
            displayName = name,
            path = uriString,
            uri = documentUri,
            duration = 0L,
            durationFormatted = VideoScanUtils.formatDuration(0L),
            size = length,
            sizeFormatted = VideoScanUtils.formatFileSize(length),
            dateModified = modified,
            dateAdded = modified,
            mimeType = document.type ?: if (isAudio) "audio/*" else "video/*",
            bucketId = treeUri,
            bucketDisplayName = folderName,
            width = 0,
            height = 0,
            fps = 0f,
            resolution = "",
            isAudio = isAudio,
          )
        } catch (error: Exception) {
          Log.w(TAG, "Cannot read an item in a selected folder: $name", error)
          null
        }
      }
    }.sortedBy { it.displayName.lowercase(Locale.ROOT) }
  }
}
