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
import app.gyrolet.mpvrx.ui.player.BufferingReason
import app.gyrolet.mpvrx.ui.player.PlaybackSession
import app.gyrolet.mpvrx.ui.player.PlaybackSessionState
import kotlinx.coroutines.delay

/** A wait must last this long before the spinner appears, so quick seeks and fast opens never flash it. */
private const val BUFFERING_SHOW_DELAY_MS = 250L

/** And it stays up this long after the wait ends, so back-to-back waits read as one instead of flickering. */
private const val BUFFERING_HIDE_DELAY_MS = 250L

/** What the indicator is waiting for, which picks its label. */
internal enum class BufferingPhase {
  /** A network stream is being opened (including link resolution before mpv starts). */
  OPENING,

  /** A seek is waiting for data at the new position. */
  SEEKING,

  /** mpv paused playback to refill its cache. */
  CACHE,
}

/**
 * Resolved buffering state for the player overlay.
 *
 * @param visible whether the spinner should be on screen right now.
 * @param isCacheStall mpv paused network playback specifically to wait for cached media.
 * @param phase what the spinner is waiting for, kept while it fades out so its label never jumps.
 * @param cachePercent mpv's fill progress toward the unpause threshold, when it reports one.
 * @param cacheSeconds how many seconds of media the demuxer currently holds.
 */
internal data class BufferingState(
  val visible: Boolean = false,
  val isCacheStall: Boolean = false,
  val phase: BufferingPhase? = null,
  val cachePercent: Int? = null,
  val cacheSeconds: Double? = null,
)

/**
 * Network waits only: opening a stream, seeking in one, and cache stalls. Local files, pauses and
 * decoder restarts never show it. Appearance and dismissal are both debounced (as in REX Player), so
 * a stall that ends and immediately resumes keeps one steady spinner instead of flashing.
 *
 * @param openingNetworkStream the player is still opening a network item and has no video yet.
 */
@Composable
internal fun rememberBufferingState(
  enabled: Boolean,
  sessionState: PlaybackSessionState,
  openingNetworkStream: Boolean = false,
): BufferingState {
  val phase =
    when {
      !enabled -> null
      sessionState.bufferingReason == BufferingReason.CACHE -> BufferingPhase.CACHE
      sessionState.bufferingReason == BufferingReason.SEEKING -> BufferingPhase.SEEKING
      openingNetworkStream -> BufferingPhase.OPENING
      else -> null
    }
  val waiting = phase != null

  var visible by remember { mutableStateOf(false) }
  // The label keeps the last reason while the spinner fades out, instead of jumping to a default.
  val lastPhase = remember { arrayOfNulls<BufferingPhase>(1) }
  if (phase != null) lastPhase[0] = phase
  LaunchedEffect(waiting, enabled) {
    when {
      waiting && !visible -> {
        delay(BUFFERING_SHOW_DELAY_MS)
        visible = true
      }
      !waiting && visible -> {
        // Turning the indicator off, or scrubbing, hides it at once.
        if (enabled) delay(BUFFERING_HIDE_DELAY_MS)
        visible = false
      }
    }
  }

  // mpv republishes the cache fill properties several times per second during normal playback.
  // They only label the spinner, so observe them only during a cache stall; reading them
  // unconditionally recomposed the caller (the whole player controls tree) on every update.
  val isCacheStall = phase == BufferingPhase.CACHE
  val cacheBufferingState =
    if (isCacheStall) PlaybackSession.propInt["cache-buffering-state"].collectAsState().value else null
  val demuxerCacheDuration =
    if (isCacheStall) PlaybackSession.propDouble["demuxer-cache-duration"].collectAsState().value else null

  return BufferingState(
    visible = visible,
    isCacheStall = isCacheStall,
    phase = phase ?: lastPhase[0],
    cachePercent = cacheBufferingState?.takeIf { it in 0..100 },
    cacheSeconds = demuxerCacheDuration?.takeIf { it.isFinite() && it > 0.0 },
  )
}
