/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.gyrolet.mpvrx.preferences.preference.defaultSharedPreferences
import app.gyrolet.mpvrx.presentation.crash.CrashMonitor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Makes restoring a settings backup crash-safe.
 *
 * A backup can hold values that load fine but break a screen later (a hand-edited or damaged file,
 * or one written by a much older or newer version). Before an import is committed, the previous
 * value of every key it changes is saved. If the app then crashes before the restored settings have
 * run for a while, those keys are put back as they were, so the next launch starts cleanly instead
 * of crashing again. Keys changed after the import (for example the folder picked during
 * onboarding) are left alone.
 */
internal object SettingsRestoreGuard {
  private const val TAG = "SettingsRestoreGuard"
  private const val STATE = "settings_restore_guard"
  private const val KEY_ARMED_AT = "armed_at"
  private const val KEY_SOURCE = "source"
  private const val KEY_UNVERIFIED_LAUNCHES = "unverified_launches"
  private const val KEY_REVERTED_SOURCE = "reverted_source"
  private const val FILE_NAME = "settings_restore_rollback.json"
  private const val VERIFY_AFTER_MS = 30_000L
  private const val MAX_UNVERIFIED_LAUNCHES = 2
  private const val MAX_GUARD_AGE_MS = 24L * 60 * 60 * 1000

  private val handler by lazy { Handler(Looper.getMainLooper()) }
  private var pendingVerification: Runnable? = null

  internal data class Change(
    val key: String,
    val previous: Any?,
    val imported: Any,
  )

  private fun state(context: Context): SharedPreferences =
    context.getSharedPreferences(STATE, Context.MODE_PRIVATE)

  private fun rollbackFile(context: Context): File = File(context.noBackupFilesDir, FILE_NAME)

  fun isArmed(context: Context): Boolean = state(context).getLong(KEY_ARMED_AT, 0L) > 0L

  /** Saves how to undo [changes]; must complete before the imported values are committed. */
  fun arm(
    context: Context,
    source: String,
    changes: List<Change>,
  ) {
    if (changes.isEmpty()) return
    val entries = JSONArray()
    changes.forEach { change ->
      entries.put(
        JSONObject()
          .put("key", change.key)
          .put("previous", change.previous?.let(::encode) ?: JSONObject.NULL)
          .put("imported", encode(change.imported)),
      )
    }
    val file = rollbackFile(context)
    val temporary = File(file.parentFile, "$FILE_NAME.tmp")
    temporary.writeText(entries.toString(), Charsets.UTF_8)
    check(temporary.renameTo(file) || (file.delete() && temporary.renameTo(file))) { "Could not save restore rollback" }
    state(context).edit()
      .putLong(KEY_ARMED_AT, System.currentTimeMillis())
      .putString(KEY_SOURCE, source)
      .putInt(KEY_UNVERIFIED_LAUNCHES, 0)
      .commit()
  }

  /**
   * Called once per process start, before anything reads the restored settings. Launches that
   * keep dying before verification (including crashes too early for the crash handler) revert.
   */
  fun onProcessStart(context: Context) {
    val state = state(context)
    val armedAt = state.getLong(KEY_ARMED_AT, 0L)
    if (armedAt <= 0L) return
    if (System.currentTimeMillis() - armedAt > MAX_GUARD_AGE_MS) {
      disarm(context)
      return
    }
    // Native crashes and ANRs never reach the exception handler; the exit record still shows them.
    if (CrashMonitor.diedAbnormallySince(context, armedAt)) {
      Log.w(TAG, "The app died with freshly restored settings; reverting them")
      revert(context)
      return
    }
    val launches = state.getInt(KEY_UNVERIFIED_LAUNCHES, 0) + 1
    if (launches > MAX_UNVERIFIED_LAUNCHES) {
      Log.w(TAG, "Restored settings never ran long enough to verify; reverting them")
      revert(context)
    } else {
      state.edit().putInt(KEY_UNVERIFIED_LAUNCHES, launches).commit()
    }
  }

  /** The restored settings survived a while on screen; the rollback is no longer needed. */
  fun onScreenShown(context: Context) {
    if (!isArmed(context)) return
    val appContext = context.applicationContext
    pendingVerification?.let(handler::removeCallbacks)
    pendingVerification = Runnable { disarm(appContext) }.also { handler.postDelayed(it, VERIFY_AFTER_MS) }
  }

  fun onScreenHidden() {
    pendingVerification?.let(handler::removeCallbacks)
    pendingVerification = null
  }

  /** From the crash handler: undo an unverified restore so the next launch starts cleanly. */
  fun revertAfterCrash(context: Context): Boolean {
    if (!isArmed(context)) return false
    Log.w(TAG, "App crashed with freshly restored settings; reverting them")
    return revert(context)
  }

  /** The backup that had to be reverted, reported once. */
  fun consumeRevertedSource(context: Context): String? {
    val state = state(context)
    val source = state.getString(KEY_REVERTED_SOURCE, null) ?: return null
    state.edit().remove(KEY_REVERTED_SOURCE).apply()
    return source
  }

  private fun disarm(context: Context) {
    pendingVerification?.let(handler::removeCallbacks)
    pendingVerification = null
    rollbackFile(context).delete()
    state(context).edit()
      .remove(KEY_ARMED_AT)
      .remove(KEY_SOURCE)
      .remove(KEY_UNVERIFIED_LAUNCHES)
      .commit()
  }

  private fun revert(context: Context): Boolean {
    val state = state(context)
    val source = state.getString(KEY_SOURCE, null).orEmpty()
    val reverted =
      runCatching {
        val file = rollbackFile(context)
        if (!file.isFile) return@runCatching false
        val entries = JSONArray(file.readText(Charsets.UTF_8))
        val preferences = context.defaultSharedPreferences()
        val current = preferences.all
        val editor = preferences.edit()
        for (index in 0 until entries.length()) {
          val entry = entries.optJSONObject(index) ?: continue
          val key = entry.optString("key").takeIf(String::isNotEmpty) ?: continue
          val imported = entry.optJSONObject("imported")?.let(::decode)
          // Something set this key after the import; that newer choice stays.
          if (!sameValue(current[key], imported)) continue
          val previous = entry.optJSONObject("previous")?.let(::decode)
          if (previous == null) editor.remove(key) else put(editor, key, previous)
        }
        editor.commit()
      }.onFailure { Log.e(TAG, "Could not revert restored settings", it) }
        .getOrDefault(false)
    disarm(context)
    if (reverted) state.edit().putString(KEY_REVERTED_SOURCE, source.ifBlank { "backup" }).commit()
    return reverted
  }

  private fun sameValue(
    current: Any?,
    imported: Any?,
  ): Boolean =
    when {
      current == null || imported == null -> current == imported
      current is Set<*> && imported is Set<*> -> current.toSet() == imported.toSet()
      else -> current == imported
    }

  private fun encode(value: Any): JSONObject =
    when (value) {
      is String -> JSONObject().put("type", "string").put("value", value)
      is Int -> JSONObject().put("type", "int").put("value", value)
      is Long -> JSONObject().put("type", "long").put("value", value)
      is Float -> JSONObject().put("type", "float").put("value", value.toString())
      is Boolean -> JSONObject().put("type", "boolean").put("value", value)
      is Set<*> -> JSONObject().put("type", "stringSet").put("value", JSONArray(value.map { it.toString() }))
      else -> JSONObject().put("type", "string").put("value", value.toString())
    }

  private fun decode(json: JSONObject): Any? =
    when (json.optString("type")) {
      "string" -> json.optString("value")
      "int" -> json.optInt("value")
      "long" -> json.optLong("value")
      "float" -> json.optString("value").toFloatOrNull()
      "boolean" -> json.optBoolean("value")
      "stringSet" ->
        json.optJSONArray("value")?.let { array -> (0 until array.length()).map(array::optString).toSet() }
      else -> null
    }

  @Suppress("UNCHECKED_CAST")
  private fun put(
    editor: SharedPreferences.Editor,
    key: String,
    value: Any,
  ) {
    when (value) {
      is String -> editor.putString(key, value)
      is Int -> editor.putInt(key, value)
      is Long -> editor.putLong(key, value)
      is Float -> editor.putFloat(key, value)
      is Boolean -> editor.putBoolean(key, value)
      is Set<*> -> editor.putStringSet(key, value as Set<String>)
    }
  }
}
