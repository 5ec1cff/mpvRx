package app.gyrolet.mpvrx.ui.background

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import org.koin.compose.koinInject
import java.time.LocalDate

/**
 * Draws [type] full-screen and cross-fades when it changes. NONE and RANDOM draw nothing here:
 * RANDOM must be resolved to a concrete type first (see [rememberActiveBackground]).
 *
 * @param paused Stop advancing the clock, for when something opaque covers the page.
 */
@Composable
fun AnimatedBackground(
  type: BackgroundType,
  parallax: Boolean,
  modifier: Modifier = Modifier,
  paused: Boolean = false,
) {
  Crossfade(
    targetState = type,
    animationSpec = tween(durationMillis = 600),
    modifier = modifier.fillMaxSize().clipToBounds(),
    label = "background_type",
  ) { shown ->
    when (shown) {
      BackgroundType.NONE, BackgroundType.RANDOM -> Box(Modifier.fillMaxSize())
      else ->
        BackdropCanvas(parallax = parallax, running = !paused) { t, tilt, p ->
          when (shown) {
            BackgroundType.CIRCLES -> drawCircles(t, tilt, p)
            BackgroundType.RINGS -> drawRings(t, tilt, p)
            BackgroundType.SPACE -> drawSpace(t, tilt, p)
            BackgroundType.SHAPES -> drawShapes(t, tilt, p)
            BackgroundType.SNOW -> drawSnow(t, tilt, p)
            BackgroundType.GRID -> drawGrid(t, tilt, p)
            BackgroundType.PARTICLES -> drawParticles(t, tilt, p)
            BackgroundType.LAVA -> drawLava(t, tilt, p)
            BackgroundType.AURORA -> drawAurora(t, tilt, p)
            BackgroundType.FIREFLIES -> drawFireflies(t, tilt, p)
            BackgroundType.RAIN -> drawRain(t, tilt, p)
            BackgroundType.WAVES -> drawWaves(t, tilt, p)
            BackgroundType.BOKEH -> drawBokeh(t, tilt, p)
            BackgroundType.SAKURA -> drawSakura(t, tilt, p)
            BackgroundType.CONSTELLATION -> drawConstellation(t, tilt, p)
            BackgroundType.BUBBLES -> drawBubbles(t, tilt, p)
            BackgroundType.HALLOWEEN -> drawHalloween(t, tilt, p)
            BackgroundType.FIREWORKS -> drawFireworks(t, tilt, p)
            BackgroundType.WINTER -> drawWinter(t, tilt, p)
            BackgroundType.DIWALI -> drawDiwali(t, tilt, p)
            BackgroundType.HOLI -> drawHoli(t, tilt, p)
            BackgroundType.NONE, BackgroundType.RANDOM -> Unit
          }
        }
    }
  }
}

/** What the app should be drawing behind its screens right now. */
data class ActiveBackground(
  /** Concrete backdrop to draw (never RANDOM). NONE means nothing. */
  val type: BackgroundType,
  val parallax: Boolean,
  val overWallpaper: Boolean,
  val event: SeasonalEvent?,
)

/**
 * The user's choices from [AppearancePreferences] folded into one answer: RANDOM turned into a
 * concrete type, and a running seasonal event laid over the pick when seasonal themes are on.
 */
@Composable
fun rememberActiveBackground(): ActiveBackground {
  val prefs = koinInject<AppearancePreferences>()
  val picked by prefs.backgroundType.collectAsState()
  val interval by prefs.randomBackgroundInterval.collectAsState()
  val seasonal by prefs.seasonalThemes.collectAsState()
  val parallax by prefs.backgroundParallax.collectAsState()
  val overWallpaper by prefs.backgroundOverWallpaper.collectAsState()
  val event = remember { SeasonalEvent.on() }

  val resolved = remember(picked, interval) { if (picked == BackgroundType.RANDOM) RandomBackground.current(prefs, interval) else picked }
  val shown = event.backdropFor(picked, seasonal).let { if (it == BackgroundType.RANDOM) resolved else it }
  return ActiveBackground(shown, parallax, overWallpaper, event?.takeIf { seasonal })
}

/**
 * String resource of today's festive greeting, or null on an ordinary day or when seasonal themes
 * are off. A pick of NONE does not switch greetings off: only the seasonal switch does.
 */
@Composable
fun rememberSeasonalGreeting(): Int? {
  val prefs = koinInject<AppearancePreferences>()
  val seasonal by prefs.seasonalThemes.collectAsState()
  val event = remember { SeasonalEvent.on() }
  return event?.takeIf { seasonal }?.greetingRes
}

/** Resolves RANDOM once per process (or per interval) so every caller agrees on the same pick. */
object RandomBackground {
  private var processPick: BackgroundType? = null

  /** Forget the current pick so the next lookup rolls again. */
  fun reroll(prefs: AppearancePreferences) {
    processPick = null
    prefs.lastRandomBackgroundDay.set(0L)
  }

  fun current(
    prefs: AppearancePreferences,
    interval: RandomInterval,
  ): BackgroundType {
    processPick?.let { if (interval == RandomInterval.EACH_LAUNCH) return it }
    val today = LocalDate.now().toEpochDay()
    val last =
      BackgroundType.SHUFFLEABLE.firstOrNull { it.name == prefs.lastRandomBackground.get() }
    val lastDay = prefs.lastRandomBackgroundDay.get()
    if (interval != RandomInterval.EACH_LAUNCH && last != null && lastDay > 0L && today - lastDay < interval.days) {
      processPick = last
      return last
    }
    // Never hand out the one the previous launch showed
    val next = BackgroundType.SHUFFLEABLE.filter { it != last }.random()
    prefs.lastRandomBackground.set(next.name)
    prefs.lastRandomBackgroundDay.set(today)
    processPick = next
    return next
  }
}

/** Counts opaque layers (full-screen dialogs) sitting over the app so the page behind can stop animating. */
object BackdropGate {
  var covers by mutableIntStateOf(0)
}
