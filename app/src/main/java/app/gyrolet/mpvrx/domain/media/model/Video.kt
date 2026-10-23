/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.domain.media.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Video(
  val id: Long,
  val title: String,
  val displayName: String,
  val path: String,
  val uri: Uri,
  val duration: Long,
  val durationFormatted: String,
  val size: Long,
  val sizeFormatted: String,
  val dateModified: Long,
  val dateAdded: Long,
  val mimeType: String,
  val bucketId: String,
  val bucketDisplayName: String,
  val width: Int,
  val height: Int,
  /**
   * Container rotation in degrees, not yet applied to [width]/[height]. Media library rows already
   * carry this from MediaStore, so the player can pick the correct launch orientation before mpv has
   * reported any geometry.
   */
  val rotation: Int = 0,
  val fps: Float,
  val resolution: String,
  val hasEmbeddedSubtitles: Boolean = false,
  val subtitleCodec: String = "",
  val videoCodec: String = "",
  val videoCodecMimeType: String = "",
  val isAudio: Boolean = false,
  val artworkUrl: String? = null,
)
