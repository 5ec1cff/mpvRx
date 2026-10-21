/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.mediainfo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs

/**
 * Keeps connected detail tiles visible inside surfaceContainerLow cards.
 *
 * AMOLED intentionally gives Low and High the same tone for flat settings groups. Nested
 * metadata rows need a separate fill so their rounded ends and 4dp gaps remain visible.
 * Keep the theme's High tone when it already separates the two surfaces; otherwise apply
 * a small onSurface tint locally, without changing the app-wide AMOLED palette.
 */
@Composable
internal fun mediaInfoDetailContainerColor(): Color {
  val colors = MaterialTheme.colorScheme
  val card = colors.surfaceContainerLow
  val preferred = colors.surfaceContainerHigh
  val content = colors.onSurface
  return remember(card, preferred, content) {
    val separated = content.copy(alpha = 0.08f).compositeOver(card)
    val cardLuminance = card.luminance()
    if (abs(preferred.luminance() - cardLuminance) >= abs(separated.luminance() - cardLuminance)) {
      preferred
    } else {
      separated
    }
  }
}
