/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.domain.thumbnail

internal fun thumbnailParallelism(
  lowRam: Boolean,
  memoryClassMb: Int,
  processorCount: Int,
  sdkInt: Int,
): Int =
  if (lowRam || memoryClassMb in 1..192 || sdkInt <= 29 || processorCount <= 2) 1 else 2
