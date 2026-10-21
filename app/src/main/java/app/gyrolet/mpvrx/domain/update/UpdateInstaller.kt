/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.domain.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import app.gyrolet.mpvrx.BuildConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Native self-update sessions with durable state and a system installer fallback. */
object UpdateInstaller {
  const val ACTION_RESULT = "app.gyrolet.mpvrx.UPDATE_INSTALL_RESULT"
  private val installMutex = Mutex()
  private var approvalIntent: Intent? = null
  @Volatile private var resumeAfterPermission = false

  fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
    context.packageManager.canRequestPackageInstalls()

  suspend fun install(context: Context, release: Release, automatic: Boolean = false) = withContext(Dispatchers.IO) {
    if (!BuildConfig.ENABLE_UPDATE_FEATURE) return@withContext
    installMutex.withLock {
      val previous = UpdateWork.state(context).value
      if (previous.stage == UpdateStage.INSTALLING || previous.stage == UpdateStage.APPROVAL) return@withLock
      if (automatic && (!previous.automatic || !UpdateWork.automaticEnabled(context) || !UpdateWork.safeToInstallAutomatically())) return@withLock
      val file = UpdateManager(context).getApkFile(release)
      if (file == null) {
        UpdateWork.update(context) { it.copy(stage = UpdateStage.FAILED, downloadError = true) }
        return@withLock
      }
      if (!hasPermission(context)) {
        UpdateWork.update(context) { it.copy(stage = UpdateStage.PERMISSION, automatic = automatic) }
        UpdateNotifications.status(context, UpdateStage.PERMISSION)
        return@withLock
      }
      val installer = context.packageManager.packageInstaller
      var sessionId = -1
      var committed = false
      try {
        validatePackage(context, file)
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
          setAppPackageName(context.packageName)
          setSize(file.length())
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
          }
        }
        sessionId = installer.createSession(params)
        val token = UUID.randomUUID().toString()
        UpdateWork.update(context) { it.copy(stage = UpdateStage.INSTALLING, automatic = automatic,
          downloadError = false, sessionId = sessionId, sessionToken = token, sessionStartedAt = System.currentTimeMillis()) }
        UpdateNotifications.status(context, UpdateStage.INSTALLING)
        installer.openSession(sessionId).use { session ->
          session.openWrite("base.apk", 0, file.length()).use { output ->
            file.inputStream().use { input ->
              val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
              while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
              }
            }
            session.fsync(output)
          }
          currentCoroutineContext().ensureActive()
          // Recheck after the potentially long APK copy: never interrupt newly started playback.
          if (automatic && (!UpdateWork.automaticEnabled(context) || !UpdateWork.safeToInstallAutomatically())) {
            UpdateWork.update(context) { it.copy(stage = UpdateStage.READY, sessionId = -1, sessionToken = "") }
            return@withLock
          }
          val callback = Intent(context, UpdateInstallReceiver::class.java)
            .setAction(ACTION_RESULT).setData(Uri.parse("mpvrx-update://session/$sessionId/$token"))
            .putExtra("token", token)
          // PackageInstaller fills status/confirmation extras; only this explicit receiver is mutable.
          val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
          val sender = PendingIntent.getBroadcast(context, sessionId, callback, flags)
          session.commit(sender.intentSender)
          committed = true
        }
      } catch (cancelled: CancellationException) {
        UpdateWork.update(context) { it.copy(stage = UpdateStage.READY, sessionId = -1, sessionToken = "") }
        throw cancelled
      } catch (_: Exception) {
        UpdateWork.update(context) { it.copy(stage = UpdateStage.FAILED, downloadError = false, sessionId = -1, sessionToken = "") }
        UpdateNotifications.status(context, UpdateStage.FAILED)
      } finally {
        if (!committed && sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
      }
    }
  }

  suspend fun requestUserInstall(context: Context, release: Release) {
    if (!hasPermission(context)) {
      resumeAfterPermission = true
      UpdateWork.update(context) { it.copy(stage = UpdateStage.PERMISSION, automatic = false) }
      withContext(Dispatchers.Main) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
      return
    }
    val current = UpdateWork.state(context).value
    when {
      current.stage == UpdateStage.APPROVAL && approvalIntent != null -> withContext(Dispatchers.Main) {
        context.startActivity(Intent(approvalIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
      current.stage == UpdateStage.APPROVAL || (current.stage == UpdateStage.FAILED && !current.downloadError) -> manualInstall(context, release)
      else -> install(context, release)
    }
  }

  /** Called only when a visible activity resumes after a user-requested settings visit. */
  suspend fun onForeground(context: Context) {
    reconcile(context)
    if (!resumeAfterPermission) return
    resumeAfterPermission = false
    if (hasPermission(context)) UpdateWork.state(context).value.release?.let { install(context, it) }
  }

  private suspend fun manualInstall(context: Context, release: Release) = withContext(Dispatchers.IO) {
    val file = UpdateManager(context).getApkFile(release) ?: error("Update APK is no longer available")
    validatePackage(context, file)
    val current = UpdateWork.state(context).value
    if (current.sessionId >= 0) runCatching { context.packageManager.packageInstaller.abandonSession(current.sessionId) }
    approvalIntent = null
    UpdateWork.update(context) { it.copy(stage = UpdateStage.READY, automatic = false, sessionId = -1, sessionToken = "") }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    withContext(Dispatchers.Main) {
      context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }

  @Suppress("DEPRECATION")
  private fun validatePackage(context: Context, file: File) {
    val manager = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val candidate = manager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Invalid update APK")
    val installed = manager.getPackageInfo(context.packageName, flags)
    require(candidate.packageName == context.packageName) { "Update package does not match this app" }
    require(PackageInfoCompat.getLongVersionCode(candidate) >= PackageInfoCompat.getLongVersionCode(installed)) { "Update would downgrade this app" }
    fun currentSigners(info: PackageInfo) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      info.signingInfo?.apkContentsSigners?.toSet().orEmpty()
    } else info.signatures?.toSet().orEmpty()
    val installedSigners = currentSigners(installed)
    val candidateSigners = currentSigners(candidate)
    val compatible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && candidate.signingInfo?.hasMultipleSigners() == false) {
      val history = candidate.signingInfo?.signingCertificateHistory?.toSet().orEmpty()
      installedSigners.size == 1 && history.containsAll(installedSigners)
    } else installedSigners == candidateSigners
    require(installedSigners.isNotEmpty() && candidateSigners.isNotEmpty() && compatible) { "Update signing certificate does not match" }
  }

  suspend fun onResult(context: Context, intent: Intent) = withContext(Dispatchers.IO) {
    val current = UpdateWork.state(context).value
    if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != current.sessionId ||
      current.sessionId < 0 || intent.getStringExtra("token") != current.sessionToken) return@withContext
    when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        @Suppress("DEPRECATION")
        val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        approvalIntent = confirmation
        UpdateWork.update(context) { it.copy(stage = UpdateStage.APPROVAL) }
        // Do not start an activity from a background receiver. Notification and sheet both work.
        UpdateNotifications.status(context, UpdateStage.APPROVAL, confirmation)
      }
      PackageInstaller.STATUS_SUCCESS -> completed(context)
      else -> {
        approvalIntent = null
        runCatching { context.packageManager.packageInstaller.abandonSession(current.sessionId) }
      UpdateWork.update(context) { it.copy(stage = UpdateStage.FAILED, downloadError = false, automatic = false, sessionId = -1, sessionToken = "") }
        UpdateNotifications.status(context, UpdateStage.FAILED)
      }
    }
  }

  suspend fun reconcile(context: Context) = withContext(Dispatchers.IO) {
    installMutex.withLock {
      val current = UpdateWork.state(context).value
      if (current.sessionId < 0) return@withLock
      val session = context.packageManager.packageInstaller.getSessionInfo(current.sessionId)
      if (session == null || !session.isSealed) {
        runCatching { context.packageManager.packageInstaller.abandonSession(current.sessionId) }
        UpdateWork.update(context) { it.copy(stage = UpdateStage.READY, sessionId = -1, sessionToken = "") }
      } else if (current.stage == UpdateStage.INSTALLING && current.sessionStartedAt > 0L &&
        System.currentTimeMillis() - current.sessionStartedAt > TimeUnit.MINUTES.toMillis(15)) {
        // Recover from an OEM installer that never delivered its final callback.
        runCatching { context.packageManager.packageInstaller.abandonSession(current.sessionId) }
        UpdateWork.update(context) { it.copy(stage = UpdateStage.FAILED, downloadError = false,
          automatic = false, sessionId = -1, sessionToken = "") }
        UpdateNotifications.status(context, UpdateStage.FAILED)
      }
    }
  }

  suspend fun completed(context: Context) = withContext(Dispatchers.IO) {
    val wasOurUpdate = UpdateWork.state(context).value.sessionId >= 0 || UpdateWork.state(context).value.stage == UpdateStage.READY
    approvalIntent = null
    UpdateWork.update(context) { UpdateTaskState(stage = UpdateStage.COMPLETE) }
    androidx.work.WorkManager.getInstance(context).cancelUniqueWork(UpdateWork.DOWNLOAD_WORK)
    UpdateManager(context).clearCache()
    UpdateNotifications.cancelProgress(context)
    if (wasOurUpdate) UpdateNotifications.status(context, UpdateStage.COMPLETE)
    UpdateWork.schedule(context)
  }
}

class UpdateInstallReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (!BuildConfig.ENABLE_UPDATE_FEATURE) return
    if (intent.action != UpdateInstaller.ACTION_RESULT && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateInstaller.completed(context)
        else UpdateInstaller.onResult(context, intent)
      } catch (error: Exception) {
        android.util.Log.w("AppUpdate", "Could not handle installer result", error)
      } finally {
        pending.finish()
      }
    }
  }
}

