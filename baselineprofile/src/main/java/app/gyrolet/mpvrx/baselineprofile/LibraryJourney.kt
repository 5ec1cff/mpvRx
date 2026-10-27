/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.baselineprofile

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue

internal const val TARGET_PACKAGE = "app.gyrolet.mpvrx"
private const val FIXTURE_FOLDER = "mpvRxBenchmark"
private const val FIXTURE_PATH = "Movies/$FIXTURE_FOLDER/"
private const val FIXTURE_COUNT = 32

/** A deterministic local library on the disposable benchmark device; no server or user media needed. */
internal fun prepareLibrary() {
  val instrumentation = InstrumentationRegistry.getInstrumentation()
  val device = UiDevice.getInstance(instrumentation)
  // Standard uses all-files access; media runtime grants alone leave its onboarding gate closed.
  device.executeShellCommand("appops set --uid $TARGET_PACKAGE MANAGE_EXTERNAL_STORAGE allow")
  listOf("READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO", "POST_NOTIFICATIONS").forEach { permission ->
    device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.$permission")
  }
  val context = instrumentation.context
  val resolver = context.contentResolver
  val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
  val selection = "${MediaStore.Video.Media.RELATIVE_PATH} = ? AND ${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
  val args = arrayOf(FIXTURE_PATH, "mpvrx-benchmark-%")
  val existing = resolver.query(collection, arrayOf(MediaStore.Video.Media._ID), selection, args, null)
    ?.use { it.count } ?: 0
  if (existing == FIXTURE_COUNT) return
  // Only our test-owned files in this dedicated fixture folder are replaced.
  resolver.delete(collection, selection, args)
  repeat(FIXTURE_COUNT) { index ->
    val name = "mpvrx-benchmark-${(index + 1).toString().padStart(2, '0')}.mp4"
    val uri = checkNotNull(resolver.insert(collection, ContentValues().apply {
      put(MediaStore.Video.Media.DISPLAY_NAME, name)
      put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
      put(MediaStore.Video.Media.RELATIVE_PATH, FIXTURE_PATH)
      put(MediaStore.Video.Media.IS_PENDING, 1)
    }))
    try {
      context.assets.open("benchmark.mp4").use { input ->
        checkNotNull(resolver.openOutputStream(uri)).use { output -> input.copyTo(output) }
      }
      resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
    } catch (error: Exception) {
      resolver.delete(uri, null, null)
      throw error
    }
  }
}

internal fun UiDevice.waitForLibrary() {
  assertTrue("The seeded library must be visible before measuring it", wait(Until.hasObject(By.text(FIXTURE_FOLDER)), 15_000) == true)
  waitForIdle()
}

internal fun UiDevice.browseLibrary() {
  waitForLibrary()
  checkNotNull(findObject(By.text(FIXTURE_FOLDER))).click()
  assertTrue("The video folder must open", wait(Until.hasObject(By.textContains("mpvrx-benchmark-")), 15_000) == true)
  val x = displayWidth / 2
  // Same gesture and fixture count on every run; no network or random UI selection.
  repeat(3) { swipe(x, displayHeight * 3 / 4, x, displayHeight / 4, 20) }
  repeat(3) { swipe(x, displayHeight / 4, x, displayHeight * 3 / 4, 20) }
  waitForIdle()
}
