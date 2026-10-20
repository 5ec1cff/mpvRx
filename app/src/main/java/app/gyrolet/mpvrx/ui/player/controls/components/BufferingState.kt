/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.player.controls.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.gyrolet.mpvrx.ui.player.PlaybackSession
import app.gyrolet.mpvrx.ui.player.PlaybackSessionState
import kotlinx.coroutines.delay

/** A stall must persist this long before the spinner appears, so ordinary seeks never flash it. */
private const val BUFFERING_SHOW_DELAY_MS = 350L

/**
 * Resolved buffering state for the player overlay.
 *
 * @param visible whether the spinner should be on screen right now.
 * @param isCacheStall mpv paused network playback specifically to wait for cached media.
 * @param cachePercent mpv's fill progress toward the unpause threshold, when it reports one.
 * @param cacheSeconds how many seconds of media the demuxer currently holds.
 */
internal data class BufferingState(
  val visible: Boolean = false,
  val isCacheStall: Boolean = false,
  val cachePercent: Int? = null,
  val cacheSeconds: Double? = null,
)

/**
 * Debounces only the appearance of the session's confirmed network cache stall. Loading, torrent
 * connection setup and decoder/renderer restarts are not buffering. Dismissal is immediate, and a
 * new media generation must wait through its own show delay instead of inheriting a visible spinner.
 */
@Composable
internal fun rememberBufferingState(
  enabled: Boolean,
  sessionState: PlaybackSessionState,
): BufferingState {
  val stalled = enabled && sessionState.isBuffering
  var visible by remember(sessionState.generation, stalled) { mutableStateOf(false) }
  LaunchedEffect(sessionState.generation, stalled) {
    if (stalled) {
      delay(BUFFERING_SHOW_DELAY_MS)
      visible = true
    }
  }

  // mpv republishes the cache fill properties several times per second during normal playback.
  // They only label the spinner, so observe them only while a stall is pending or shown; reading
  // them unconditionally recomposed the caller (the whole player controls tree) on every update.
  val cacheDetailsNeeded = stalled
  val cacheBufferingState =
    if (cacheDetailsNeeded) PlaybackSession.propInt["cache-buffering-state"].collectAsState().value else null
  val demuxerCacheDuration =
    if (cacheDetailsNeeded) PlaybackSession.propDouble["demuxer-cache-duration"].collectAsState().value else null

  return BufferingState(
    visible = stalled && visible,
    isCacheStall = stalled,
    cachePercent = cacheBufferingState?.takeIf { it in 0..100 },
    cacheSeconds = demuxerCacheDuration?.takeIf { it.isFinite() && it > 0.0 },
  )
}
