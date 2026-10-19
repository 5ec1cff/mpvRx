/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.browser.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import app.gyrolet.mpvrx.domain.thumbnail.deferThumbnailLoadingUntilIdle

/** Memory hits remain visible; work for a newly visible row starts only after scrolling settles. */
@Composable
internal fun rememberThumbnailLoadingDeferred(isScrolling: Boolean): Boolean {
  val scrolling = rememberUpdatedState(isScrolling)
  val deferred by produceState(initialValue = true) {
    snapshotFlow { scrolling.value }
      .deferThumbnailLoadingUntilIdle()
      .collect { value = it }
  }
  // Stop new requests in this composition, before the producer observes the scroll transition.
  return isScrolling || deferred
}
