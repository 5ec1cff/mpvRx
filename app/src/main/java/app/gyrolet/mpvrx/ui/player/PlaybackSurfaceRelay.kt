/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player

import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.media.ImageWriter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.util.concurrent.FutureTask

/**
 * A session-owned native window. mpv keeps rendering into [inputSurface] while Android replaces
 * the fullscreen, mini-player or external-display window. Updating mpv's vo/wid instead would
 * issue a whole-file seek, flush AudioTrack and interrupt background audio.
 *
 * PRIVATE images are transferred to the current window by ImageWriter without reading pixels
 * or applying another color transform. Android carries their crop, transform, timestamp and
 * dataspace with them. Without a window, keep consuming images so the producer never blocks.
 */
internal class PlaybackSurfaceRelay(width: Int, height: Int) : AutoCloseable {
  private val thread = HandlerThread("PlaybackSurfaceRelay").apply { start() }
  private val handler = Handler(thread.looper)
  private val reader =
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ImageReader.newInstance(width, height, ImageFormat.PRIVATE, 4, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
      } else {
        ImageReader.newInstance(width, height, ImageFormat.PRIVATE, 4)
      }
    } catch (error: Throwable) {
      thread.quitSafely()
      throw error
    }

  val inputSurface: Surface = reader.surface

  // All output state and ImageWriter calls belong to the consumer thread. In particular, a
  // stale window's release callback must never return capacity to its replacement writer.
  private var writer: ImageWriter? = null
  private var outputWidth = 0
  private var outputHeight = 0
  private var queuedImages = 0
  private var onOutputFailed: (() -> Unit)? = null
  private var closed = false

  init {
    reader.setOnImageAvailableListener({ source -> consumeFrame(source) }, handler)
  }

  fun attachOutput(surface: Surface, width: Int, height: Int, onFailed: () -> Unit) = onConsumerThread {
    check(!closed)
    closeOutput()
    check(surface.isValid) { "Playback window was destroyed before attachment" }
    val next =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ImageWriter.newInstance(surface, 3, ImageFormat.PRIVATE)
      } else {
        // The explicit-format ImageWriter factory was added in API 29. On 26–28 set
        // the same native-window format before using the original public factory.
        PlaybackSurfaceNative.useOpaqueBuffers(surface)
        ImageWriter.newInstance(surface, 3)
      }
    writer = next
    outputWidth = width
    outputHeight = height
    onOutputFailed = onFailed
    next.setOnImageReleasedListener({ released ->
      if (writer === released && queuedImages > 0) queuedImages--
    }, handler)
  }

  fun resizeOutput(width: Int, height: Int) = onConsumerThread {
    outputWidth = width
    outputHeight = height
  }

  /** Returns only after the consumer has stopped using Android's outgoing Surface. */
  fun detachOutput() = onConsumerThread { closeOutput() }

  private fun consumeFrame(source: ImageReader) {
    if (closed) return
    val image = source.acquireLatestImage() ?: return
    try {
      val target = writer ?: return
      // Never wait for a hidden/stalled window: leave a spare writer slot and drop video
      // instead of allowing presentation backpressure to reach mpv's audio timeline.
      if (queuedImages >= 2) return
      // SurfaceView's BLAST queue rejects buffers from the old window size after rotation.
      // mpv receives android-surface-size separately and will produce the new size next.
      if (image.width != outputWidth || image.height != outputHeight) return
      queuedImages++
      try {
        target.queueInputImage(image)
      } catch (error: RuntimeException) {
        val failed = onOutputFailed
        closeOutput()
        Log.w(TAG, "Playback window rejected a video buffer", error)
        failed?.invoke()
      }
    } finally {
      // queueInputImage transfers ownership and closes the image on success; closing an
      // already transferred Image is harmless. Every dropped/failed buffer is returned too.
      image.close()
    }
  }

  private fun closeOutput() {
    val previous = writer
    writer = null
    queuedImages = 0
    onOutputFailed = null
    previous?.close()
  }

  private fun <T> onConsumerThread(action: () -> T): T {
    if (Looper.myLooper() === handler.looper) return action()
    val task = FutureTask(action)
    check(handler.post(task)) { "Playback surface consumer has stopped" }
    return task.get()
  }

  override fun close() {
    onConsumerThread {
      if (!closed) {
        closed = true
        closeOutput()
        reader.setOnImageAvailableListener(null, null)
        reader.close()
      }
    }
    thread.quitSafely()
  }

  private companion object {
    const val TAG = "PlaybackSurfaceRelay"
  }
}

internal object PlaybackSurfaceNative {
  init {
    System.loadLibrary("playback_surface")
  }

  external fun useOpaqueBuffers(surface: Surface)
}
