/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player

/**
 * Geometry of the current video as it will appear on screen, i.e. with rotation metadata applied.
 *
 * mpv reports [width], [height] and [sourceAspect] before rotation. Orientation, PiP and every other
 * consumer must apply [rotation] in exactly one place, here, so they cannot disagree about whether a
 * phone recording is portrait.
 */
internal data class VideoDisplayGeometry(
  val width: Int,
  val height: Int,
  /** Display aspect before rotation, including pixel aspect ratio and aspect overrides. */
  val sourceAspect: Double,
  /** Clockwise rotation in degrees that mpv applies on output, normalized to 0, 90, 180 or 270. */
  val rotation: Int,
  val source: Source,
) {
  enum class Source { DECODER, DEMUXER }

  val isQuarterTurned: Boolean get() = rotation == 90 || rotation == 270

  val displayAspect: Double get() = if (isQuarterTurned) 1.0 / sourceAspect else sourceAspect

  companion object {
    /**
     * Reads geometry for [generation]'s loaded file, or null if it is unknown or superseded.
     *
     * Order of trust:
     * 1. Decoder output, unless it reports rotation 0 while the demuxer reports a real rotation
     *    and their unrotated frame axes agree. Some MediaCodec paths lose the container angle.
     * 2. The selected video track's demuxer fields while the decoder is unavailable (such as
     *    before the first frame, during background playback, or on PiP exit).
     *
     * Top-level `width`/`height` are deliberately never used: they carry no rotation, so pairing
     * them with an assumed rotation of 0 is what opened portrait phone videos in landscape.
     */
    fun read(generation: Long = PlaybackSession.loadedMediaGeneration()): VideoDisplayGeometry? =
      PlaybackSession.readLoadedMedia(generation) { reader ->
        val decoder = fromDecoder(reader)
        if (decoder == null) {
          fromDemuxer(reader)
        } else if (decoder.rotation != 0) {
          decoder
        } else {
          // Hardware decoders can report 0 even when the container requests a quarter-turn.
          // Do not substitute container rotation if the decoder has already swapped the frame
          // dimensions: in that case the rotation has been physically applied.
          val containerRotation = reader.int("current-tracks/video/demux-rotation") ?: 0
          val expectedRotation = applyUserRotation(containerRotation, reader.string("video-rotate"))
          if (expectedRotation == null || expectedRotation == 0) {
            decoder
          } else {
            val demuxer = fromDemuxer(reader)
            val axesAgree = demuxer != null &&
              ((decoder.width > decoder.height && demuxer.width > demuxer.height) ||
                (decoder.width < decoder.height && demuxer.width < demuxer.height))
            if (axesAgree) demuxer else decoder
          }
        }
      }

    private fun fromDecoder(reader: PlaybackSession.PropertyReader): VideoDisplayGeometry? {
      val width = reader.int("video-params/w")?.takeIf { it > 0 } ?: return null
      val height = reader.int("video-params/h")?.takeIf { it > 0 } ?: return null
      // mpv publishes rotate together with w/h. If it is missing the params are mid-reconfigure, so
      // fall through to the demuxer instead of guessing 0.
      val rotation = normalizeQuarterTurn(reader.int("video-params/rotate") ?: return null) ?: return null
      val aspect =
        reader.double("video-params/aspect")?.takeIf(::isUsableAspect)
          ?: (width.toDouble() / height)
      return VideoDisplayGeometry(width, height, aspect, rotation, Source.DECODER)
    }

    private fun fromDemuxer(reader: PlaybackSession.PropertyReader): VideoDisplayGeometry? {
      val width = reader.int("current-tracks/video/demux-w")?.takeIf { it > 0 } ?: return null
      val height = reader.int("current-tracks/video/demux-h")?.takeIf { it > 0 } ?: return null
      val par = reader.double("current-tracks/video/demux-par")?.takeIf(::isUsableAspect) ?: 1.0
      // mpv omits demux-rotation when the container has none, so absence with known w/h means 0.
      val containerRotation = reader.int("current-tracks/video/demux-rotation") ?: 0
      val rotation = applyUserRotation(containerRotation, reader.string("video-rotate")) ?: return null
      val aspect =
        reader.double("video-aspect-override")?.takeIf(::isUsableAspect)
          ?: (width.toDouble() * par / height)
      return VideoDisplayGeometry(width, height, aspect, rotation, Source.DEMUXER)
    }

    /**
     * Mirrors mpv's decoder wrapper: `--video-rotate=no` discards container rotation, otherwise the
     * user angle is added to it. Returns null for a non-quarter-turn result, which has no portrait or
     * landscape answer.
     */
    internal fun applyUserRotation(
      containerRotation: Int,
      videoRotate: String?,
    ): Int? {
      val option = videoRotate?.trim()
      val total =
        when {
          option.isNullOrEmpty() -> containerRotation
          option.equals("no", ignoreCase = true) -> 0
          else -> containerRotation + (option.toIntOrNull() ?: 0)
        }
      return normalizeQuarterTurn(total)
    }

    internal fun normalizeQuarterTurn(degrees: Int): Int? {
      val normalized = ((degrees % 360) + 360) % 360
      return normalized.takeIf { it % 90 == 0 }
    }

    private fun isUsableAspect(value: Double): Boolean = value.isFinite() && value > 0.001
  }
}
