/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import app.gyrolet.mpvrx.MainActivity
import app.gyrolet.mpvrx.R

/** An arbitrary background belongs to a pinned shortcut, never a manifest launcher alias. */
internal object CustomIconShortcut {
  private const val ID = "mpvrx.custom-background"
  private const val PREFERENCES = "custom_icon_shortcut"

  enum class Result { Requested, Updated, Unsupported, Failed }

  fun savedColor(context: Context): Int =
    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getInt("color", 0xFFC4F5E2.toInt())

  fun savedMark(context: Context, fallback: IconMark): IconMark {
    val name = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString("mark", null)
    return IconMark.entries.firstOrNull { it.name == name } ?: fallback
  }

  fun apply(context: Context, color: Int, mark: IconMark): Result {
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return Result.Unsupported
    val pinned = manager.pinnedShortcuts.firstOrNull { it.id == ID }
    if (pinned == null && !manager.isRequestPinShortcutSupported) return Result.Unsupported
    if (pinned?.isImmutable == true) return Result.Failed

    // Keep the full adaptive layer (108dp); the launcher supplies its own mask and inset.
    val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(color or 0xFF000000.toInt())
    val foreground = ContextCompat.getDrawable(context, mark.layer)?.mutate() ?: return Result.Failed
    foreground.setBounds(0, 0, bitmap.width, bitmap.height)
    foreground.draw(canvas)
    val shortcut = ShortcutInfo.Builder(context, ID)
      .setShortLabel(context.getString(R.string.app_name))
      .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
      // Target the permanent activity, not an alias that changes when another preset is selected.
      .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
      .build()
    val result = if (pinned != null) {
      if (!manager.updateShortcuts(listOf(shortcut))) return Result.Failed
      if (!pinned.isEnabled) manager.enableShortcuts(listOf(ID))
      Result.Updated
    } else {
      if (!manager.isRequestPinShortcutSupported) return Result.Unsupported
      if (!manager.requestPinShortcut(shortcut, null)) return Result.Failed
      // A successful request is not confirmation: the user can still cancel the launcher dialog.
      Result.Requested
    }
    // Retain the last submitted design, without claiming that a pin request was accepted.
    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
      .putInt("color", color or 0xFF000000.toInt()).putString("mark", mark.name).apply()
    return result
  }
}
