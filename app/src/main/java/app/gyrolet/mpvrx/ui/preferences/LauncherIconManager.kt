/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences

import android.app.Activity
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import app.gyrolet.mpvrx.R

/** Add future icon designs to this catalogue and declare corresponding launcher aliases. */
internal enum class LauncherIcon(
  val phoneAlias: String,
  val tvAlias: String,
  @StringRes val label: Int,
  @StringRes val description: Int,
  @DrawableRes val preview: Int,
) {
  Purple("app.gyrolet.mpvrx.PurpleLauncher", "app.gyrolet.mpvrx.TvPurpleLauncher",
    R.string.app_icon_purple, R.string.app_icon_purple_description, R.mipmap.ic_launcher_purple),
  SolidPurple("app.gyrolet.mpvrx.SolidPurpleLauncher", "app.gyrolet.mpvrx.TvSolidPurpleLauncher",
    R.string.app_icon_solid_purple, R.string.app_icon_solid_purple_description, R.mipmap.ic_launcher_solid_purple),
  Classic("app.gyrolet.mpvrx.ClassicLauncher", "app.gyrolet.mpvrx.TvClassicLauncher",
    R.string.app_icon_classic, R.string.app_icon_classic_description, R.mipmap.ic_launcher),
}

internal object LauncherIconManager {
  private fun component(context: Context, name: String) = ComponentName(context.packageName, name)

  fun current(context: Context): LauncherIcon {
    val pm = context.packageManager
    // The gradient Purple alias is the manifest default; all other choices start disabled.
    // Detect explicitly selected aliases so existing user selections survive an upgrade.
    return LauncherIcon.entries.firstOrNull { icon ->
      pm.getComponentEnabledSetting(component(context, icon.phoneAlias)) ==
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } ?: LauncherIcon.Purple
  }


  /**
   * Synchronizes Android's Recents task artwork with the currently selected launcher alias.
   * Kept outside application startup and any video-first-frame critical path.
   */
  @Suppress("DEPRECATION")
  fun refreshTaskIcon(activity: Activity) {
    val iconRes = current(activity).preview
    val drawable: Drawable = ContextCompat.getDrawable(activity, iconRes) ?: return
    val size = 128
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(Canvas(bitmap))
    activity.setTaskDescription(ActivityManager.TaskDescription(
      activity.getString(R.string.app_name), bitmap,
    ))
  }

  fun select(context: Context, icon: LauncherIcon) {
    val pm = context.packageManager
    val flags = PackageManager.DONT_KILL_APP
    val changes = LauncherIcon.entries.flatMap { choice ->
      val state = if (choice == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
      listOf(component(context, choice.phoneAlias) to state, component(context, choice.tvAlias) to state)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // Apply phone and TV aliases atomically on Android 13+.
      pm.setComponentEnabledSettings(changes.map { (name, state) ->
        PackageManager.ComponentEnabledSetting(name, state, flags)
      })
    } else {
      // Ensure a launcher remains enabled throughout a switch on Android 8–12.
      changes.filter { it.second == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
        .forEach { (name, state) -> pm.setComponentEnabledSetting(name, state, flags) }
      changes.filter { it.second == PackageManager.COMPONENT_ENABLED_STATE_DISABLED }
        .forEach { (name, state) -> pm.setComponentEnabledSetting(name, state, flags) }
    }
    (context as? Activity)?.let { refreshTaskIcon(it) }
  }
}
