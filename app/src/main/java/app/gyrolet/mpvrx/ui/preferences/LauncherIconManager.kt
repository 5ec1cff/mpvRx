/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences

import android.app.Activity
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
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
  SolidPurple("app.gyrolet.mpvrx.SolidPurpleLauncher", "app.gyrolet.mpvrx.TvSolidPurpleLauncher",
    R.string.app_icon_solid_purple, R.string.app_icon_solid_purple_description, R.mipmap.ic_launcher_solid_purple),
  Purple("app.gyrolet.mpvrx.PurpleLauncher", "app.gyrolet.mpvrx.TvPurpleLauncher",
    R.string.app_icon_purple, R.string.app_icon_purple_description, R.mipmap.ic_launcher_purple),
  Classic("app.gyrolet.mpvrx.ClassicLauncher", "app.gyrolet.mpvrx.TvClassicLauncher",
    R.string.app_icon_classic, R.string.app_icon_classic_description, R.mipmap.ic_launcher),
}

internal object LauncherIconManager {
  private val defaultIcon = LauncherIcon.SolidPurple
  private var cachedArtwork: Pair<LauncherIcon, Bitmap>? = null

  private fun component(context: Context, name: String) = ComponentName(context.packageName, name)

  private fun aliases(context: Context, icon: LauncherIcon) =
    listOf(component(context, icon.phoneAlias), component(context, icon.tvAlias))

  private fun manifestEnabled(name: ComponentName): Boolean =
    name.className == defaultIcon.phoneAlias || name.className == defaultIcon.tvAlias

  private fun enabled(name: ComponentName, state: Int): Boolean = when (state) {
    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestEnabled(name)
    else -> false
  }

  private fun states(context: Context): Map<ComponentName, Int> =
    LauncherIcon.entries.flatMap { aliases(context, it) }.associateWith {
      context.packageManager.getComponentEnabledSetting(it)
    }

  private fun selected(context: Context, states: Map<ComponentName, Int>): LauncherIcon =
    // Honor explicit selections from older versions before considering manifest defaults.
    LauncherIcon.entries.firstOrNull { icon ->
      aliases(context, icon).all { states[it] == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
    } ?: LauncherIcon.entries.firstOrNull { icon ->
      aliases(context, icon).any { states[it] == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
    } ?: LauncherIcon.entries.firstOrNull { icon ->
      aliases(context, icon).all { enabled(it, states.getValue(it)) }
    } ?: defaultIcon

  fun current(context: Context): LauncherIcon = selected(context, states(context))

  /** Repairs duplicate, missing, or mismatched phone/TV aliases without changing a valid choice. */
  @Synchronized
  fun reconcile(context: Context) {
    val currentStates = states(context)
    val choice = selected(context, currentStates)
    val wanted = aliases(context, choice).toSet()
    if (currentStates.all { (name, state) -> enabled(name, state) == (name in wanted) }) return
    select(context, choice)
  }

  /** Recents artwork failures must not turn a successful launcher change into an error. */
  @Suppress("DEPRECATION")
  fun refreshTaskIcon(activity: Activity) {
    runCatching {
      val icon = current(activity)
      val bitmap = cachedArtwork?.takeIf { it.first == icon }?.second ?: run {
        val drawable: Drawable = ContextCompat.getDrawable(activity, icon.preview) ?: return@runCatching
        Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also {
          drawable.setBounds(0, 0, it.width, it.height)
          drawable.draw(Canvas(it))
          cachedArtwork = icon to it
        }
      }
      activity.setTaskDescription(ActivityManager.TaskDescription(activity.getString(R.string.app_name), bitmap))
    }
  }

  private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
      if (current is Activity) return current
      val base = current.baseContext
      if (base === current) break
      current = base
    }
    return current as? Activity
  }

  private fun applyStates(context: Context, changes: Map<ComponentName, Int>) {
    val pm = context.packageManager
    val flags = PackageManager.DONT_KILL_APP
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.setComponentEnabledSettings(changes.map { (name, state) ->
        PackageManager.ComponentEnabledSetting(name, state, flags)
      })
    } else {
      // Enable the destination first, including manifest-default aliases during rollback.
      changes.filter { (name, state) -> enabled(name, state) }.forEach { (name, state) ->
        pm.setComponentEnabledSetting(name, state, flags)
      }
      changes.filter { (name, state) -> !enabled(name, state) }.forEach { (name, state) ->
        pm.setComponentEnabledSetting(name, state, flags)
      }
    }
  }

  @Synchronized
  @Suppress("DEPRECATION")
  fun select(context: Context, icon: LauncherIcon) {
    val previous = states(context)
    // Fail before mutating anything if an APK is missing a declared alias.
    previous.keys.forEach {
      context.packageManager.getActivityInfo(it, PackageManager.MATCH_DISABLED_COMPONENTS)
    }
    val wanted = aliases(context, icon).toSet()
    val changes = previous.mapValues { (name, _) ->
      if (name in wanted) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
    try {
      applyStates(context, changes)
      check(states(context) == changes) { "Launcher alias changes were not applied" }
    } catch (failure: Exception) {
      // Roll back to a coherent previous selection, even if the initial state needed repair.
      val oldChoice = selected(context, previous)
      val oldAliases = aliases(context, oldChoice).toSet()
      val coherentPrevious = previous.mapValues { (name, state) ->
        if (enabled(name, state) == (name in oldAliases)) state
        else if (name in oldAliases) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
      }
      runCatching { applyStates(context, coherentPrevious) }.exceptionOrNull()?.let(failure::addSuppressed)
      throw failure
    }
    findActivity(context)?.let(::refreshTaskIcon)
  }
}
