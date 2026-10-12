/* SPDX-License-Identifier: AGPL-3.0-or-later */

package app.gyrolet.mpvrx.ui.player

import kotlin.math.pow

fun videoZoomMultiplier(zoom: Float): Float = 2f.pow(zoom.coerceIn(-1f, 3f))

/** mpv pan values are fractions of the scaled video, while touch gestures use pixels. */
fun videoPanFractions(
  width: Float,
  height: Float,
  aspect: Float,
  zoom: Float,
  panX: Float,
  panY: Float,
): Pair<Float, Float> {
  if (width <= 0f || height <= 0f || aspect <= 0f) return 0f to 0f
  val fittedWidth = minOf(width, height * aspect)
  val fittedHeight = minOf(height, width / aspect)
  val scale = videoZoomMultiplier(zoom)
  return panX / (fittedWidth * scale) to panY / (fittedHeight * scale)
}
