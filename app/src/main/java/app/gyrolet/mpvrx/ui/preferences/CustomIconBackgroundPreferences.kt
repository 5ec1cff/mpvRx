/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences

import android.content.Context

/** Retains the last chosen free-form background and logo between icon picker sessions. */
internal object CustomIconBackgroundPreferences {
  // Keep the old storage name so colors saved by the former shortcut flow migrate automatically.
  private const val PREFERENCES = "custom_icon_shortcut"

  fun savedColor(context: Context): Int =
    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getInt("color", 0xFFC4F5E2.toInt())

  fun savedMark(context: Context, fallback: IconMark): IconMark {
    val name = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString("mark", null)
    return IconMark.entries.firstOrNull { it.name == name } ?: fallback
  }

  fun save(context: Context, color: Int, mark: IconMark) {
    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
      .putInt("color", color or 0xFF000000.toInt())
      .putString("mark", mark.name)
      .apply()
  }
}
