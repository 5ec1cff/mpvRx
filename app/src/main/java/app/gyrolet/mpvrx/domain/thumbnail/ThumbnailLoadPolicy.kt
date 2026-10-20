/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.domain.thumbnail

internal fun thumbnailParallelism(
  lowRam: Boolean,
  memoryClassMb: Int,
  processorCount: Int,
  sdkInt: Int,
): Int =
  if (lowRam || memoryClassMb in 1..192 || sdkInt <= 29 || processorCount <= 2) 1 else 2

/** Highest value offered in settings: more parallel frame decodes than cores only add memory use. */
internal fun maxThumbnailParallelism(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

/** The user's setting, or [automatic] when it is Auto (0). */
internal fun effectiveThumbnailParallelism(
  setting: Int,
  automatic: Int,
): Int = if (setting <= 0) automatic else setting.coerceIn(1, maxThumbnailParallelism())
