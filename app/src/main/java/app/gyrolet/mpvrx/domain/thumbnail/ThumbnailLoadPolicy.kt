/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.domain.thumbnail

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

internal fun thumbnailParallelism(
  lowRam: Boolean,
  memoryClassMb: Int,
  processorCount: Int,
  sdkInt: Int,
): Int =
  if (lowRam || memoryClassMb in 1..192 || sdkInt <= 29 || processorCount <= 2) 1 else 2

/** Both disk decoding and generation wait for a continuous idle interval, including at startup. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<Boolean>.deferThumbnailLoadingUntilIdle(): Flow<Boolean> =
  distinctUntilChanged().transformLatest { scrolling ->
    emit(true)
    if (!scrolling) {
      delay(200L)
      emit(false)
    }
  }
