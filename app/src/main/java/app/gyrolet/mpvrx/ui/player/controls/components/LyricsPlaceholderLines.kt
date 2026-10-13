/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player.controls.components

import androidx.annotation.ArrayRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringArrayResource
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * One line out of a string-array, for the moments the lyrics panel has nothing
 * real to say: fetching, an instrumental intro, or no lyrics anywhere.
 *
 * The starting line is random so the panel doesn't open on the same sentence
 * every time, and it survives rotation. With [intervalMs] the line advances on
 * its own while on screen, which is what a fetch that takes a few seconds wants;
 * without it the line stays put, which is what an intro or a miss wants.
 */
@Composable
fun rememberPlaceholderLine(
  @ArrayRes arrayId: Int,
  intervalMs: Long? = null,
): String {
  val lines = stringArrayResource(arrayId)
  var index by rememberSaveable(arrayId) {
    mutableIntStateOf(if (lines.isEmpty()) 0 else Random.nextInt(lines.size))
  }
  if (intervalMs != null && lines.size > 1) {
    LaunchedEffect(arrayId, lines.size, intervalMs) {
      while (true) {
        delay(intervalMs)
        index = (index + 1) % lines.size
      }
    }
  }
  return lines.getOrElse(index % lines.size.coerceAtLeast(1)) { "" }
}

/** How long each fetching line stays up before the next one replaces it. */
const val PLACEHOLDER_ROTATE_MS = 2_400L
