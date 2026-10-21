/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gyrolet.mpvrx.BuildConfig
import app.gyrolet.mpvrx.domain.update.AppUpdateChannel
import app.gyrolet.mpvrx.domain.update.Asset
import app.gyrolet.mpvrx.domain.update.Release
import app.gyrolet.mpvrx.domain.update.UpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One state keeps the download sheet mounted while checking, downloading and installing. */
data class UpdateUiState(
  val visible: Boolean = false,
  val release: Release? = null,
  val asset: Asset? = null,
  val checking: Boolean = false,
  val checkError: Boolean = false,
  val noUpdate: Boolean = false,
  val selecting: Boolean = false,
  val isDownloading: Boolean = false,
  val progress: Float = 0f,
  val readyToInstall: Boolean = false,
  val downloadError: Boolean = false,
  val installError: Boolean = false,
  val installPermissionRequired: Boolean = false,
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
  private val updateManager = UpdateManager(application)
  private val prefs = application.getSharedPreferences("mpvrx_prefs", Context.MODE_PRIVATE)
  private val _isAutoUpdateEnabled = MutableStateFlow(
    BuildConfig.ENABLE_UPDATE_FEATURE && prefs.getBoolean("auto_update", false),
  )
  val isAutoUpdateEnabled = _isAutoUpdateEnabled.asStateFlow()

  private val _updateChannel = MutableStateFlow(
    prefs.getString(UPDATE_CHANNEL_KEY, null)?.let(AppUpdateChannel::fromStoredValue)
      ?: when {
        BuildConfig.IS_EXPERIMENTAL_BUILD -> AppUpdateChannel.EXPERIMENTAL
        BuildConfig.IS_PREVIEW_BUILD -> AppUpdateChannel.PREVIEW
        else -> AppUpdateChannel.STABLE
      },
  )
  val updateChannel = _updateChannel.asStateFlow()
  private val _uiState = MutableStateFlow(UpdateUiState())
  val uiState = _uiState.asStateFlow()
  private var checkJob: Job? = null
  private var selectionJob: Job? = null

  init {
    if (BuildConfig.ENABLE_UPDATE_FEATURE && _isAutoUpdateEnabled.value) {
      viewModelScope.launch {
        delay(UPDATE_CHECK_STARTUP_DELAY_MS)
        if (_isAutoUpdateEnabled.value) checkForUpdate()
      }
    }
  }

  fun setUpdateChannel(channel: AppUpdateChannel) {
    if (!BuildConfig.ENABLE_UPDATE_FEATURE || _uiState.value.isDownloading || channel == _updateChannel.value) return
    stopChecking()
    prefs.edit().putString(UPDATE_CHANNEL_KEY, channel.name).apply()
    _updateChannel.value = channel
    _uiState.value = UpdateUiState(visible = _uiState.value.visible)
    checkForUpdate(manual = true)
  }

  fun toggleAutoUpdate(enabled: Boolean) {
    if (!BuildConfig.ENABLE_UPDATE_FEATURE) return
    prefs.edit().putBoolean("auto_update", enabled).apply()
    _isAutoUpdateEnabled.value = enabled
    if (enabled) checkForUpdate() else checkJob?.cancel()
  }

  fun retryCheck() = checkForUpdate(manual = true)

  fun checkForUpdate(manual: Boolean = false) {
    if (!BuildConfig.ENABLE_UPDATE_FEATURE || _uiState.value.isDownloading || _uiState.value.selecting) return
    if (!manual && _uiState.value.visible) return
    checkJob?.cancel()
    val channel = _updateChannel.value
    if (manual) _uiState.update { it.copy(visible = true, checking = true, checkError = false, noUpdate = false) }
    checkJob = viewModelScope.launch {
      try {
        val release = updateManager.checkForUpdate(channel, forceShow = manual)
        ensureActive()
        if (channel != _updateChannel.value || _uiState.value.isDownloading) return@launch
        val asset = release?.let(updateManager::selectUpdateAsset)
        if (release != null && asset != null) {
          _uiState.value = UpdateUiState(visible = true, release = release, asset = asset, selecting = true)
          val file = updateManager.getApkFile(release)
          ensureActive()
          _uiState.update { it.copy(readyToInstall = file != null, selecting = false) }
        } else if (manual) {
          _uiState.update { it.copy(checking = false, noUpdate = true) }
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Exception) {
        if (manual) _uiState.update { it.copy(checking = false, selecting = false, checkError = true) }
        else _uiState.update { it.copy(selecting = false) }
      }
    }
  }

  fun downloadUpdate() {
    val state = _uiState.value
    if (!BuildConfig.ENABLE_UPDATE_FEATURE || state.isDownloading || state.selecting || state.checking) return
    val release = state.release ?: return
    if (state.asset == null) return
    checkJob?.cancel()
    // Guard synchronously, before launching: repeated taps must never start parallel writers.
    _uiState.update { it.copy(isDownloading = true, checking = false, progress = 0f, downloadError = false) }
    viewModelScope.launch {
      try {
        val resumedProgress = updateManager.getResumableProgress(release) ?: 0f
        _uiState.update { it.copy(progress = resumedProgress) }
        updateManager.downloadUpdate(release).collect { progress ->
          _uiState.update { it.copy(progress = progress) }
        }
        _uiState.update { it.copy(isDownloading = false, readyToInstall = true, progress = 100f) }
      } catch (cancellation: CancellationException) {
        _uiState.update { it.copy(isDownloading = false) }
        throw cancellation
      } catch (_: Exception) {
        _uiState.update { it.copy(isDownloading = false, readyToInstall = false, downloadError = true) }
      }
    }
  }

  fun installUpdate() {
    val state = _uiState.value
    if (!BuildConfig.ENABLE_UPDATE_FEATURE || state.isDownloading || state.selecting || !state.readyToInstall) return
    val release = state.release ?: return
    _uiState.update { it.copy(selecting = true, installError = false, installPermissionRequired = false) }
    selectionJob = viewModelScope.launch {
      try {
        val file = updateManager.getApkFile(release)
        ensureActive()
        if (file == null) {
          _uiState.update { it.copy(readyToInstall = false, downloadError = true) }
          return@launch
        }
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
          _uiState.update { it.copy(installPermissionRequired = true) }
          context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
          )
          return@launch
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        context.startActivity(
          Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
          },
        )
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Exception) {
        _uiState.update { it.copy(installError = true) }
      } finally {
        if (currentCoroutineContext().isActive) _uiState.update { it.copy(selecting = false) }
      }
    }
  }

  fun dismiss() {
    if (_uiState.value.isDownloading) return
    stopChecking()
    // Retain verified APKs and partials when closing; the next download prunes other versions.
    _uiState.update { it.copy(visible = false, checking = false, selecting = false) }
  }

  fun ignoreVersion() {
    if (_uiState.value.isDownloading) return
    _uiState.value.release?.let { updateManager.ignoreVersion(it.updateKey, _updateChannel.value) }
    dismiss()
  }

  private fun stopChecking() {
    checkJob?.cancel()
    selectionJob?.cancel()
  }

  private companion object {
    const val UPDATE_CHECK_STARTUP_DELAY_MS = 1500L
    const val UPDATE_CHANNEL_KEY = "app_update_channel"
  }
}

