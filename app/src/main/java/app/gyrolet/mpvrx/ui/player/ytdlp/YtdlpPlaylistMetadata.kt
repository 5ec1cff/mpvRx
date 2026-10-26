/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player.ytdlp

data class YtdlpPlaylistEntry(
  val id: String?,
  val url: String,
  val title: String,
  val artist: String?,
  val thumbnailUrl: String?,
  val durationSeconds: Int,
)

data class YtdlpPlaylistMetadata(
  val sourceUrl: String,
  val title: String,
  val entries: List<YtdlpPlaylistEntry>,
)
