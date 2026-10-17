package app.gyrolet.mpvrx.ui.background

import androidx.annotation.StringRes
import app.gyrolet.mpvrx.R

/**
 * Every animated backdrop the app can draw behind its screens.
 *
 * The first block is what the user can pick by hand. The seasonal block is only ever worn while
 * its [SeasonalEvent] runs, so it never shows in the picker and never comes out of the shuffle.
 */
enum class BackgroundType(
  @StringRes val labelRes: Int,
) {
  CIRCLES(R.string.bg_circles),
  RINGS(R.string.bg_rings),
  SPACE(R.string.bg_space),
  SHAPES(R.string.bg_shapes),
  SNOW(R.string.bg_snow),
  GRID(R.string.bg_grid),
  PARTICLES(R.string.bg_particles),
  LAVA(R.string.bg_lava),
  AURORA(R.string.bg_aurora),
  FIREFLIES(R.string.bg_fireflies),
  RAIN(R.string.bg_rain),
  BOKEH(R.string.bg_bokeh),
  SAKURA(R.string.bg_sakura),
  CONSTELLATION(R.string.bg_constellation),
  BUBBLES(R.string.bg_bubbles),
  NONE(R.string.bg_none),
  RANDOM(R.string.bg_random),

  // Seasonal only
  HALLOWEEN(R.string.bg_halloween),
  FIREWORKS(R.string.bg_fireworks),
  WINTER(R.string.bg_winter),
  DIWALI(R.string.bg_diwali),
  HOLI(R.string.bg_holi),
  ;

  val isSeasonal: Boolean get() = this in SEASONAL

  companion object {
    val SEASONAL: Set<BackgroundType> = setOf(HALLOWEEN, FIREWORKS, WINTER, DIWALI, HOLI)

    /** Tiles of the picker, in display order. */
    val PICKABLE: List<BackgroundType> = entries.filter { it !in SEASONAL }

    /** What RANDOM may land on: anything that actually draws something. */
    val SHUFFLEABLE: List<BackgroundType> = PICKABLE.filter { it != NONE && it != RANDOM }
  }
}

/** How often RANDOM re-rolls. */
enum class RandomInterval(
  @StringRes val labelRes: Int,
  val days: Int,
) {
  EACH_LAUNCH(R.string.bg_interval_launch, 0),
  EVERY_DAY(R.string.bg_interval_daily, 1),
  EVERY_3_DAYS(R.string.bg_interval_3_days, 3),
}
