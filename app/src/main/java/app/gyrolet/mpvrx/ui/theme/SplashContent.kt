/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.widget.ImageView
import androidx.compose.ui.viewinterop.AndroidView
import app.gyrolet.mpvrx.ui.preferences.LauncherIconManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import kotlinx.coroutines.delay

/**
 * App-drawn launch screen, shown for as long as the system splash is held.
 *
 * The system splash exists only between the launcher tap and the first frame, and its background can
 * only be a static theme colour — Android's SplashScreen API has no setter for it, so a palette the
 * user picks at runtime (custom themes, AMOLED black) could never reach it. This overlay is the part
 * that actually follows the theme, because it reads the live [MaterialTheme.colorScheme] that
 * [MpvrxTheme] has already resolved.
 *
 * It is layered above the app content rather than replacing it, so the first real frame is already
 * laid out underneath and the handover is a cross-fade instead of a blank flash.
 *
 * @param visible false starts the fade-out. The composable removes itself once that finishes, so the
 *   caller can leave it in the tree for the whole session.
 */
@Composable
fun SplashContent(
  visible: Boolean,
  modifier: Modifier = Modifier,
) {
  // Kept mounted through the fade: unmounting on `visible = false` would drop it instantly and the
  // cross-fade would never be seen. Only the finished state stops rendering.
  var mounted by remember { mutableStateOf(true) }
  LaunchedEffect(visible) {
    if (visible) {
      mounted = true
    } else {
      delay(SPLASH_FADE_OUT_DURATION_MS.toLong())
      mounted = false
    }
  }
  if (!mounted) return

  // Fade only the branded content. The fullscreen background stays fully opaque until this
  // composable unmounts, otherwise the real UI shows through as a dim/ghost overlay during the
  // final splash frames.
  val contentAlpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(durationMillis = SPLASH_FADE_OUT_DURATION_MS, easing = FastOutSlowInEasing),
    label = "splashContentAlpha",
  )

  // A short grow-in gives the icon some life; it settles well before the fade starts.
  var entered by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { entered = true }
  val iconScale by animateFloatAsState(
    targetValue = if (entered) 1f else SPLASH_ICON_ENTER_FROM_SCALE,
    animationSpec = tween(durationMillis = SPLASH_ICON_ENTER_DURATION_MS, easing = FastOutSlowInEasing),
    label = "splashIconScale",
  )

  Box(
    modifier =
      modifier
        .fillMaxSize()
        // Keep this background opaque for the entire handoff. Only the logo/text fade below;
        // fading the background itself exposes the already-rendered UI underneath and creates a
        // visible translucent overlay at the end of the splash animation.
        .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.alpha(contentAlpha),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(SPLASH_LABEL_SPACING),
    ) {
      AndroidView(
        modifier = Modifier.size(SPLASH_ICON_SIZE).scale(iconScale),
        factory = { context ->
          ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(LauncherIconManager.current(context).preview)
          }
        },
        update = { it.setImageResource(LauncherIconManager.current(it.context).preview) },
      )
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = SPLASH_LABEL_PADDING),
      )
    }
  }
}

internal const val SPLASH_FADE_OUT_DURATION_MS = 320
internal const val SPLASH_ICON_ENTER_DURATION_MS = 420

private const val SPLASH_ICON_ENTER_FROM_SCALE = 0.86f
private val SPLASH_ICON_SIZE = 96.dp
private val SPLASH_LABEL_SPACING = 20.dp
private val SPLASH_LABEL_PADDING = 24.dp
