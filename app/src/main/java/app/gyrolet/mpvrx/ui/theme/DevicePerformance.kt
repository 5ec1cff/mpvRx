/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.theme

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android's low-RAM flag is the authoritative signal; a small app heap is an
 * additional fallback on older budget phones that do not advertise it.
 */
@Composable
fun rememberLowEndDevice(): Boolean {
  val context = LocalContext.current.applicationContext
  return remember(context) {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    manager?.isLowRamDevice == true || (manager != null && manager.memoryClass <= 192)
  }
}
