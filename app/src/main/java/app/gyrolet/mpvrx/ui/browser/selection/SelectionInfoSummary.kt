/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.browser.selection

import app.gyrolet.mpvrx.domain.media.model.Video

internal data class SelectionInfoSummary(
  val videos: List<Video>,
  val totalSize: Long,
  val totalDuration: Long,
  val knownSizeCount: Int,
  val knownDurationCount: Int,
  val videoCount: Int,
  val audioCount: Int,
  val longest: Video?,
  val largest: Video?,
)

internal fun summarizeSelection(videos: List<Video>): SelectionInfoSummary {
  // A file can be selected directly and also appear inside a selected folder (or playlist twice).
  val uniqueVideos = videos.distinctBy { it.path.ifBlank { it.uri.toString() } }
  return SelectionInfoSummary(
    videos = uniqueVideos,
    totalSize = uniqueVideos.sumOf { it.size.coerceAtLeast(0L) },
    totalDuration = uniqueVideos.sumOf { it.duration.coerceAtLeast(0L) },
    knownSizeCount = uniqueVideos.count { it.size > 0L },
    knownDurationCount = uniqueVideos.count { it.duration > 0L },
    videoCount = uniqueVideos.count { !it.isAudio },
    audioCount = uniqueVideos.count { it.isAudio },
    longest = uniqueVideos.filter { it.duration > 0 }.maxByOrNull { it.duration },
    largest = uniqueVideos.filter { it.size > 0 }.maxByOrNull { it.size },
  )
}
