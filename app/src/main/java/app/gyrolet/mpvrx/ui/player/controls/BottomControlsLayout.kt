/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.player.controls

import app.gyrolet.mpvrx.preferences.PlaybackControlsPosition
import app.gyrolet.mpvrx.preferences.SeekbarPlacement

internal enum class BottomSlot { Buttons, Playback, Seekbar }

internal data class BottomControlsLayout(
  val bottomToTop: List<BottomSlot>,
  val buttonsBesidePlayback: Boolean,
)

/**
 * Resolve both preferences together without letting either override the other.
 * Landscape can share a row only when both button groups belong on the same side of the seekbar.
 * Portrait keeps its scrolling layout buttons separate, with playback closest to the seekbar.
 */
internal fun resolveBottomControlsLayout(
  isPortrait: Boolean,
  seekbarPlacement: SeekbarPlacement,
  playbackPosition: PlaybackControlsPosition,
): BottomControlsLayout {
  val seekbarAboveButtons = seekbarPlacement == SeekbarPlacement.AboveButtons
  val playbackBelowSeekbar = playbackPosition == PlaybackControlsPosition.BelowSeekbar
  val playbackAboveSeekbar = playbackPosition == PlaybackControlsPosition.AboveSeekbar
  val buttonsBesidePlayback =
    !isPortrait &&
      ((seekbarAboveButtons && playbackBelowSeekbar) || (!seekbarAboveButtons && playbackAboveSeekbar))

  val bottomToTop =
    buildList {
      when {
        buttonsBesidePlayback -> {
          if (playbackBelowSeekbar) add(BottomSlot.Buttons)
          add(BottomSlot.Seekbar)
          if (playbackAboveSeekbar) add(BottomSlot.Buttons)
        }
        seekbarAboveButtons -> {
          add(BottomSlot.Buttons)
          if (playbackBelowSeekbar) add(BottomSlot.Playback)
          add(BottomSlot.Seekbar)
          if (playbackAboveSeekbar) add(BottomSlot.Playback)
        }
        else -> {
          if (playbackBelowSeekbar) add(BottomSlot.Playback)
          add(BottomSlot.Seekbar)
          if (playbackAboveSeekbar) add(BottomSlot.Playback)
          add(BottomSlot.Buttons)
        }
      }
    }

  return BottomControlsLayout(bottomToTop, buttonsBesidePlayback)
}
