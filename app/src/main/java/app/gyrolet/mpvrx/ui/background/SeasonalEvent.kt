package app.gyrolet.mpvrx.ui.background

import androidx.annotation.StringRes
import app.gyrolet.mpvrx.R
import java.time.LocalDate
import java.time.MonthDay

/**
 * Festive periods that may take over the backdrop (and add a short greeting to the top bar).
 *
 * Fixed-date events are a [MonthDay] range. Diwali and Holi follow the lunar calendar, so their
 * main day comes from [FestivalDates], one entry per year.
 *
 * Order matters: the first event that is running wins, so an overlap (Diwali can land on the
 * Halloween window, New Year sits inside the winter one) resolves towards the earlier entry.
 */
enum class SeasonalEvent(
  val background: BackgroundType,
  @StringRes val greetingRes: Int,
  val icon: String,
) {
  DIWALI(BackgroundType.DIWALI, R.string.bg_greeting_diwali, "\uD83E\uDE94"),
  HOLI(BackgroundType.HOLI, R.string.bg_greeting_holi, "\uD83C\uDFA8"),
  HALLOWEEN(BackgroundType.HALLOWEEN, R.string.bg_greeting_halloween, "\uD83C\uDF83"),
  NEW_YEAR(BackgroundType.FIREWORKS, R.string.bg_greeting_new_year, "\uD83C\uDF86"),
  WINTER(BackgroundType.WINTER, R.string.bg_greeting_winter, "\u2744\uFE0F"),
  ;

  /** First and last day (inclusive) of this event in [year], or null when we have no data for it. */
  private fun window(year: Int): ClosedRange<LocalDate>? =
    when (this) {
      DIWALI ->
        FestivalDates.diwali[year]?.let { main ->
          // Dhanteras two days ahead, up to Bhai Dooj three days after Lakshmi Puja
          main.minusDays(2)..main.plusDays(3)
        }
      HOLI ->
        FestivalDates.holi[year]?.let { main ->
          // Holika Dahan the evening before, then the day of colours
          main.minusDays(1)..main
        }
      HALLOWEEN -> MonthDay.of(10, 24).atYear(year)..MonthDay.of(11, 1).atYear(year)
      NEW_YEAR -> MonthDay.of(12, 31).atYear(year)..MonthDay.of(1, 1).atYear(year + 1)
      WINTER -> MonthDay.of(12, 20).atYear(year)..MonthDay.of(1, 7).atYear(year + 1)
    }

  fun isRunningOn(date: LocalDate): Boolean {
    // A range can start in the previous calendar year (New Year, Winter), so test both
    return window(date.year)?.contains(date) == true || window(date.year - 1)?.contains(date) == true
  }

  companion object {
    /** The event running on [date], or null on an ordinary day. */
    fun on(date: LocalDate = LocalDate.now()): SeasonalEvent? = entries.firstOrNull { it.isRunningOn(date) }
  }
}

/**
 * Main day of the two moving festivals, per year.
 *
 * 2026 comes from a published calendar (Lakshmi Puja 8 Nov, Holi 4 Mar). The other years are from
 * the standard panchang tables; extend the maps once a year, an event whose year is missing
 * simply does not run.
 */
object FestivalDates {
  val diwali: Map<Int, LocalDate> =
    mapOf(
      2025 to LocalDate.of(2025, 10, 21),
      2026 to LocalDate.of(2026, 11, 8),
      2027 to LocalDate.of(2027, 10, 29),
      2028 to LocalDate.of(2028, 10, 17),
      2029 to LocalDate.of(2029, 11, 5),
      2030 to LocalDate.of(2030, 10, 26),
      2031 to LocalDate.of(2031, 11, 14),
    )

  val holi: Map<Int, LocalDate> =
    mapOf(
      2025 to LocalDate.of(2025, 3, 14),
      2026 to LocalDate.of(2026, 3, 4),
      2027 to LocalDate.of(2027, 3, 22),
      2028 to LocalDate.of(2028, 3, 11),
      2029 to LocalDate.of(2029, 3, 1),
      2030 to LocalDate.of(2030, 3, 20),
      2031 to LocalDate.of(2031, 3, 9),
    )
}

/** The backdrop to draw: the running event's own, unless the user turned those off or picked NONE. */
fun SeasonalEvent?.backdropFor(
  picked: BackgroundType,
  seasonalThemes: Boolean,
): BackgroundType = this?.takeIf { seasonalThemes && picked != BackgroundType.NONE }?.background ?: picked
