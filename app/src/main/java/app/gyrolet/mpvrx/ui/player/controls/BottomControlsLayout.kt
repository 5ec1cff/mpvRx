/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.player.controls

import androidx.annotation.StringRes
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.PlaybackControlsPosition
import app.gyrolet.mpvrx.preferences.SeekbarPlacement

internal enum class BottomSlot { Buttons, Playback, Seekbar }

internal enum class VideoControlsLayoutPreset(
  @StringRes val titleRes: Int,
  val seekbarPlacement: SeekbarPlacement,
  val playbackPosition: PlaybackControlsPosition,
) {
  CenterSeekbarAboveActions(
    R.string.pref_layout_center_seekbar_above,
    SeekbarPlacement.AboveButtons,
    PlaybackControlsPosition.Center,
  ),
  CenterSeekbarBelowActions(
    R.string.pref_layout_center_seekbar_below,
    SeekbarPlacement.BelowButtons,
    PlaybackControlsPosition.Center,
  ),
  PlaybackAboveSeekbar(
    R.string.pref_layout_playback_above,
    SeekbarPlacement.BelowButtons,
    PlaybackControlsPosition.AboveSeekbar,
  ),
  PlaybackBelowSeekbar(
    R.string.pref_layout_playback_below,
    SeekbarPlacement.AboveButtons,
    PlaybackControlsPosition.BelowSeekbar,
  ),
  ;

  companion object {
    // Keep existing saved keys; older three-row combinations collapse toward their playback position.
    fun fromPreferences(
      seekbarPlacement: SeekbarPlacement,
      playbackPosition: PlaybackControlsPosition,
    ): VideoControlsLayoutPreset = when (playbackPosition) {
      PlaybackControlsPosition.AboveSeekbar -> PlaybackAboveSeekbar
      PlaybackControlsPosition.BelowSeekbar -> PlaybackBelowSeekbar
      PlaybackControlsPosition.Center ->
        if (seekbarPlacement == SeekbarPlacement.AboveButtons) CenterSeekbarAboveActions else CenterSeekbarBelowActions
    }
  }
}

internal data class BottomControlsLayout(
  val bottomToTop: List<BottomSlot>,
  val buttonsBesidePlayback: Boolean,
)

/**
 * Android video controls have at most two bottom rows. An exceptionally narrow window keeps
 * playback in the centre rather than clipping action touch targets beside it.
 */
internal fun resolveBottomControlsLayout(
  seekbarPlacement: SeekbarPlacement,
  playbackPosition: PlaybackControlsPosition,
  hasButtons: Boolean = true,
  allowSharedRow: Boolean = true,
): BottomControlsLayout {
  val preset = VideoControlsLayoutPreset.fromPreferences(seekbarPlacement, playbackPosition)
  val buttonsBesidePlayback = allowSharedRow && preset.playbackPosition != PlaybackControlsPosition.Center
  val includeButtonsRow = hasButtons || buttonsBesidePlayback
  val bottomToTop = buildList {
    if (includeButtonsRow && preset.seekbarPlacement == SeekbarPlacement.AboveButtons) add(BottomSlot.Buttons)
    add(BottomSlot.Seekbar)
    if (includeButtonsRow && preset.seekbarPlacement == SeekbarPlacement.BelowButtons) add(BottomSlot.Buttons)
  }
  return BottomControlsLayout(bottomToTop, buttonsBesidePlayback)
}
