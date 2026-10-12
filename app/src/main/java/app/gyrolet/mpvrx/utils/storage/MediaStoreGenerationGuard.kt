/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.utils.storage

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log

/**
 * Cheap "has MediaStore changed at all?" signal, so cold boots can skip a rescan.
 *
 * All three browser modes rescan on every launch even when nothing changed, because the caches in
 * [FolderViewScanner] and [TreeViewScanner] are process-local and die with the app, and
 * `getAllVideos` issues one MediaStore query per folder. [MediaStore.getGeneration] answers the
 * same question with one monotonic integer per volume: it only advances when MediaProvider actually
 * mutates a row, so an unchanged value proves the previous scan is still accurate.
 *
 * Scope — this only speaks for MediaStore-indexed media. Files inside `.nomedia` trees are
 * deliberately absent from MediaStore, so this guard must never be used to skip
 * [FolderViewScanner.scanNoMediaFoldersIncrementally], which has its own fingerprint index.
 */
object MediaStoreGenerationGuard {
  private const val TAG = "MediaStoreGenerationGuard"

  private const val PREFS = "media_store_generation"
  private const val KEY_TOKEN = "token"

  /** Marker separating the recorded generation from caller payload in a combined blob. */
  internal const val PAYLOAD_PREFIX = "gen:"

  /**
   * Current generation across every external volume, or null when it cannot be determined.
   *
   * Null means "unknown" and callers must then scan. It is returned below API 30, where
   * [MediaStore.getGeneration] does not exist, so those devices keep today's behaviour rather than
   * trusting a homegrown fingerprint that could miss a same-second edit.
   */
  fun currentToken(context: Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return runCatching {
      val volumes = MediaStore.getExternalVolumeNames(context)
      // No external volume is a legitimate state (an emulator image with no mounted media), not a
      // failure, so this still yields a stable token.
      val perVolume = volumes.sorted().map { volume -> "$volume=${MediaStore.getGeneration(context, volume)}" }
      // getVersion changes when MediaProvider is upgraded or its schema resets, which can renumber
      // generations; folding it in makes that a guaranteed mismatch instead of a false match.
      val providerVersion = runCatching { MediaStore.getVersion(context) }.getOrDefault("")
      "v1|$providerVersion|${perVolume.joinToString(",")}"
    }.onFailure { error ->
      Log.w(TAG, "Could not read MediaStore generation", error)
    }.getOrNull()
  }

  /**
   * Records the MediaStore state that a just-completed scan observed. A null token is ignored so the
   * stored value can never be downgraded to something a later comparison would accept.
   */
  fun remember(context: Context, token: String?) {
    if (token == null) return
    runCatching {
      prefs(context).edit().putString(KEY_TOKEN, token).apply()
    }.onFailure { error ->
      Log.w(TAG, "Could not persist MediaStore generation", error)
    }
  }

  /**
   * True when MediaStore is unchanged since the last [remember], meaning an existing snapshot still
   * describes the library and the scan can be skipped.
   */
  fun isUnchangedSinceLastScan(context: Context): Boolean {
    val previous = lastRemembered(context) ?: return false
    val current = currentToken(context) ?: return false
    return previous == current
  }

  /**
   * True when a blob that embeds its own generation was produced by a scan that observed the current
   * MediaStore state.
   *
   * This is the form the tree snapshot uses. Embedding the generation in the payload keeps the two
   * atomic: there is no window where a payload and a separately stored token disagree, which is
   * exactly how a stale snapshot would end up being trusted.
   */
  fun isSnapshotCurrent(
    context: Context,
    payload: String,
  ): Boolean {
    // startsWith, not substringAfter: a corrupt or hand-edited blob that merely *contains* the
    // marker mid-string must not be able to pass as valid.
    if (!payload.startsWith(PAYLOAD_PREFIX)) return false
    val embedded = payload.removePrefix(PAYLOAD_PREFIX).substringBefore('\n')
    if (embedded.isBlank()) return false
    val current = currentToken(context) ?: return false
    return embedded == current
  }

  /**
   * Prefixes a payload with the generation it was built from. Pair with [isSnapshotCurrent] rather
   * than [remember], so the freshness proof travels with the data instead of beside it.
   */
  fun withGeneration(
    payload: String,
    token: String?,
  ): String? {
    if (token == null) return null
    return "$PAYLOAD_PREFIX$token\n$payload"
  }

  /**
   * Drops the recorded token so the next scan always recomputes.
   *
   * Called whenever something outside a MediaStore query may have changed the library: a
   * user-driven refresh, a media event, or an in-app file mutation.
   */
  fun invalidate(context: Context) {
    runCatching { prefs(context).edit().remove(KEY_TOKEN).apply() }
      .onFailure { error ->
        Log.w(TAG, "Could not clear stored MediaStore generation", error)
      }
    // Snapshots embed their own generation, so they have to be dropped too or a stale tree would
    // still be restorable through the isSnapshotCurrent path.
    TreeViewCache.clear(context)
  }

  private fun lastRemembered(context: Context): String? =
    runCatching { prefs(context).getString(KEY_TOKEN, null) }
      .onFailure { error ->
        Log.w(TAG, "Could not read stored MediaStore generation", error)
      }.getOrNull()

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
