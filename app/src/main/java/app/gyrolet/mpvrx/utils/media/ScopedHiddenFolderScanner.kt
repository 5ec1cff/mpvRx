/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.gyrolet.mpvrx.utils.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.domain.media.model.VideoFolder
import app.gyrolet.mpvrx.utils.storage.FileTypeUtils
import app.gyrolet.mpvrx.utils.storage.VideoScanUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

/**
 * Play Store-safe explicit folder grants. Never tries to translate a SAF URI into a raw file path:
 * every playable item retains its content:// URI. A single user-picked tree grant also covers
 * its nested directories. Each directory with playable media appears as its own folder, using a
 * document URI under the original grant; no raw paths or broad storage permissions are needed.
 * Metadata is read through the same grants, not filesystem paths or MediaStore permissions.
 * The player also uses this scan to rebuild sibling queues for history and other single-item launches.
 */
object ScopedHiddenFolderScanner {
  private const val TAG = "ScopedHiddenFolderScanner"

  fun isScopedFolder(bucketId: String): Boolean =
    bucketId.startsWith("content://") && Uri.parse(bucketId).path?.startsWith("/tree/") == true

  // Keep walks bounded even if a document provider exposes cyclic or excessively deep trees.
  private const val MAX_DIRECTORY_DEPTH = 64

  private data class DirectoryVisit(
    val document: DocumentFile,
    val bucketId: String,
    val displayName: String,
    val depth: Int,
    val parentBucketId: String? = null,
  )

  private data class IndexedDirectory(
    val document: DocumentFile,
    val grantedTreeUri: String,
    val displayName: String,
    val parentBucketId: String?,
  )

  // A folder list scan already visited every child. Reuse those DocumentFile handles for the
  // library's per-folder counts and video pages instead of traversing the tree once per folder.
  // listFiles() still queries current contents; the cache only holds directory locations.
  @Volatile private var indexedDirectories: Map<String, IndexedDirectory> = emptyMap()

  fun isIndexedDescendant(bucketId: String, ancestorBucketId: String): Boolean {
    var current = bucketId
    val visited = hashSetOf<String>()
    while (visited.add(current)) {
      if (current == ancestorBucketId) return true
      current = indexedDirectories[current]?.parentBucketId ?: return false
    }
    return false
  }

  /**
   * Child folder buckets are document URIs anchored to the *original* granted tree, not new
   * grants. URI matching here is only a fast filter; getVideos still finds the child by walking
   * an accessible persisted tree, so a fabricated document URI cannot bypass the grant.
   */
  fun isGrantedFolder(bucketId: String, grantedTreeUris: Set<String>): Boolean {
    if (!isScopedFolder(bucketId)) return false
    val requested = Uri.parse(bucketId)
    val parts = requested.pathSegments
    if (parts.size != 2 && !(parts.size == 4 && parts[2] == "document")) return false
    return grantedTreeUris.any { granted ->
      val root = Uri.parse(granted)
      val rootParts = root.pathSegments
      rootParts.size == 2 &&
        rootParts[0] == "tree" &&
        requested.scheme == root.scheme &&
        requested.authority == root.authority &&
        parts[1] == rootParts[1]
    }
  }

  suspend fun getFolders(
    context: Context,
    grantedTreeUris: Set<String>,
    includeAudio: Boolean,
    minimumAudioDurationSeconds: Int = 0,
    audioOnly: Boolean = false,
  ): List<VideoFolder> = withContext(Dispatchers.IO) {
    val folders = mutableListOf<VideoFolder>()
    val discovered = mutableMapOf<String, IndexedDirectory>()
    for (treeUri in grantedTreeUris.sorted()) {
      currentCoroutineContext().ensureActive()
      val root = openPersistedTreeDocument(context, treeUri) ?: continue
      val rootName = root.name ?: Uri.parse(treeUri).lastPathSegment?.substringAfterLast(':') ?: "Selected folder"
      val pending = ArrayDeque<DirectoryVisit>()
      val visited = hashSetOf<String>()
      pending.addLast(DirectoryVisit(root, treeUri, rootName, 0))

      while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val folder = pending.removeFirst()
        if (!visited.add(folder.document.uri.toString())) continue
        discovered[folder.bucketId] =
          IndexedDirectory(folder.document, treeUri, folder.displayName, folder.parentBucketId)
        val children = listTreeFilesSafely(folder.document)
        val videos = readVideosFromFiles(
          context, children, folder.bucketId, folder.displayName, includeAudio,
          minimumAudioDurationSeconds, audioOnly,
        )
        if (videos.isNotEmpty()) {
          folders += VideoFolder(
            bucketId = folder.bucketId,
            name = folder.displayName,
            path = folder.bucketId,
            videoCount = videos.size,
            totalSize = videos.sumOf { it.size },
            totalDuration = videos.sumOf { it.duration },
            lastModified = videos.maxOfOrNull { it.dateModified } ?: 0L,
          )
        }

        if (folder.depth < MAX_DIRECTORY_DEPTH) {
          for (child in children) {
            currentCoroutineContext().ensureActive()
            if (!child.isDirectory) continue
            val childName = child.name ?: continue
            pending.addLast(
              DirectoryVisit(
                child,
                child.uri.toString(),
                "${folder.displayName} / $childName",
                folder.depth + 1,
                folder.bucketId,
              ),
            )
          }
        }
      }
    }
    indexedDirectories = discovered.toMap()
    folders.distinctBy { it.bucketId }.sortedBy { it.name.lowercase(Locale.ROOT) }
  }

  suspend fun getVideos(
    context: Context,
    bucketId: String,
    includeAudio: Boolean,
    grantedTreeUris: Set<String>,
    minimumAudioDurationSeconds: Int = 0,
  ): List<Video> = withContext(Dispatchers.IO) {
    if (!isGrantedFolder(bucketId, grantedTreeUris)) return@withContext emptyList()

    val cached = indexedDirectories[bucketId]
    if (cached != null && cached.grantedTreeUri in grantedTreeUris &&
      openPersistedTreeDocument(context, cached.grantedTreeUri) != null
    ) {
      return@withContext readVideosFromFiles(
        context, listTreeFilesSafely(cached.document), bucketId, cached.displayName, includeAudio,
        minimumAudioDurationSeconds,
      )
    }

    // Resolve the requested directory from the actual persisted grant rather than treating its
    // URI as a separate tree permission. Only directories discovered beneath the grant are used.
    for (treeUri in grantedTreeUris.sorted()) {
      if (!isGrantedFolder(bucketId, setOf(treeUri))) continue
      val root = openPersistedTreeDocument(context, treeUri) ?: continue
      val rootName = root.name ?: "Selected folder"
      val pending = ArrayDeque<DirectoryVisit>()
      val visited = hashSetOf<String>()
      pending.addLast(DirectoryVisit(root, treeUri, rootName, 0))

      while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val folder = pending.removeFirst()
        if (!visited.add(folder.document.uri.toString())) continue
        val children = listTreeFilesSafely(folder.document)
        if (folder.bucketId == bucketId) {
          return@withContext readVideosFromFiles(
            context, children, folder.bucketId, folder.displayName, includeAudio,
            minimumAudioDurationSeconds,
          )
        }
        if (folder.depth < MAX_DIRECTORY_DEPTH) {
          for (child in children) {
            currentCoroutineContext().ensureActive()
            if (child.isDirectory) {
              val childName = child.name ?: continue
              pending.addLast(
                DirectoryVisit(
                  child,
                  child.uri.toString(),
                  "${folder.displayName} / $childName",
                  folder.depth + 1,
                ),
              )
            }
          }
        }
      }
    }
    emptyList()
  }

  private suspend fun readVideosFromFiles(
    context: Context,
    children: Array<DocumentFile>,
    bucketId: String,
    folderName: String,
    includeAudio: Boolean,
    minimumAudioDurationSeconds: Int = 0,
    audioOnly: Boolean = false,
  ): List<Video> =
    children.mapNotNull { document ->
      currentCoroutineContext().ensureActive()
      run {
        val name = document.name ?: return@run null
        try {
          if (!document.isFile) return@run null
          val fakeFile = File(name)
          val isAudio = FileTypeUtils.isAudioFile(fakeFile)
          if (audioOnly && !isAudio) return@run null
          if (!(FileTypeUtils.isVideoFile(fakeFile) || (includeAudio && isAudio))) return@run null
          val documentUri = document.uri
          val uriString = documentUri.toString()
          val length = document.length().coerceAtLeast(0L)
          val modified = (document.lastModified() / 1_000L).coerceAtLeast(0L)
          val metadata =
            MediaInfoOps.extractBasicMetadata(context, documentUri, name)
              .onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Cannot read metadata in a selected folder: $name", error)
              }.getOrNull()
          val duration = metadata?.durationMs ?: 0L
          if (isAudio && duration < minimumAudioDurationSeconds.coerceAtLeast(0).toLong() * 1_000L) return@run null
          if (metadata != null && duration <= 0L) {
            Log.w(TAG, "No duration available for an item in a selected folder: $name")
          }
          Video(
            id = uriString.hashCode().toLong(),
            title = name.substringBeforeLast('.', name),
            displayName = name,
            path = uriString,
            uri = documentUri,
            duration = duration,
            durationFormatted = VideoScanUtils.formatDuration(duration),
            size = length,
            sizeFormatted = VideoScanUtils.formatFileSize(length),
            dateModified = modified,
            dateAdded = modified,
            mimeType = document.type ?: if (isAudio) "audio/*" else "video/*",
            bucketId = bucketId,
            bucketDisplayName = folderName,
            width = metadata?.width ?: 0,
            height = metadata?.height ?: 0,
            fps = metadata?.fps ?: 0f,
            resolution =
              metadata?.let { VideoScanUtils.formatResolutionWithFps(it.width, it.height, it.fps) }.orEmpty(),
            hasEmbeddedSubtitles = metadata?.hasEmbeddedSubtitles ?: false,
            subtitleCodec = metadata?.subtitleCodec.orEmpty(),
            videoCodec = metadata?.videoCodec.orEmpty(),
            videoCodecMimeType = metadata?.videoCodecMimeType.orEmpty(),
            isAudio = isAudio,
          )
        } catch (error: CancellationException) {
          throw error
        } catch (error: Exception) {
          Log.w(TAG, "Cannot read an item in a selected folder: $name", error)
          null
        }
      }
    }.sortedBy { it.displayName.lowercase(Locale.ROOT) }
}
