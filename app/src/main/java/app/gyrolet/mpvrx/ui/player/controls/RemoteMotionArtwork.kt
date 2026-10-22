/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player.controls

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import app.gyrolet.mpvrx.data.artwork.AppleMusicArtworkRepository
import app.gyrolet.mpvrx.data.artwork.MotionArtworkQuery
import app.gyrolet.mpvrx.data.artwork.RemoteMotionArtwork
import java.io.IOException
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
internal fun RemoteMotionArtwork(
  query: MotionArtworkQuery,
  isPlaying: Boolean,
  modifier: Modifier = Modifier,
) {
  val repository = koinInject<AppleMusicArtworkRepository>()
  val lifecycleOwner = LocalLifecycleOwner.current
  var artwork by remember(query) { mutableStateOf<RemoteMotionArtwork?>(null) }
  LaunchedEffect(query, lifecycleOwner, repository) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      // Metadata can arrive in several updates; skip transient combinations during track changes.
      delay(600)
      artwork = repository.find(query)
    }
  }
  val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
  if (lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
    artwork?.let { result ->
      key(query, result) {
        MotionArtworkVideo(artwork = result, isPlaying = isPlaying, modifier = modifier)
      }
    }
  }
}

@Composable
private fun MotionArtworkVideo(
  artwork: RemoteMotionArtwork,
  isPlaying: Boolean,
  modifier: Modifier,
) {
  var sourceIndex by remember { mutableIntStateOf(0) }
  var failed by remember { mutableStateOf(false) }
  if (failed) return

  key(sourceIndex) {
    var firstFrame by remember { mutableStateOf(false) }
    val attempt = sourceIndex
    val retryOrFallBack = {
      if (sourceIndex == attempt) {
        if (sourceIndex < artwork.urls.lastIndex) sourceIndex++ else failed = true
      }
    }
    LaunchedEffect(firstFrame, isPlaying) {
      if (!firstFrame && isPlaying) {
        delay(20_000)
        Log.w("MotionArtworkVideo", "Artwork first frame timed out; trying another rendition or retaining the cover")
        retryOrFallBack()
      }
    }
    AndroidView(
      modifier = modifier.graphicsLayer { alpha = if (firstFrame) 1f else 0f },
      factory = { context ->
        MotionArtworkTextureView(
          context = context,
          url = artwork.urls[sourceIndex],
          onFrameReady = { firstFrame = it },
          onFailure = retryOrFallBack,
        )
      },
      update = { view -> view.setPlaying(isPlaying) },
      onRelease = { view -> view.releasePlayer() },
    )
  }
}

/** A separate, muted platform decoder; it never requests audio focus or touches mpv playback. */
private class MotionArtworkTextureView(
  context: Context,
  private val url: String,
  private val onFrameReady: (Boolean) -> Unit,
  private val onFailure: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
  private var player: MediaPlayer? = null
  private var outputSurface: Surface? = null
  private var prepared = false
  private var playing = false
  private var failed = false
  private var videoAspect = 1f

  init {
    isOpaque = false
    isClickable = false
    isFocusable = false
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    surfaceTextureListener = this
  }

  fun setPlaying(value: Boolean) {
    playing = value
    val current = player ?: return
    if (!prepared) return
    try {
      if (value && !current.isPlaying) current.start()
      else if (!value && current.isPlaying) current.pause()
    } catch (e: IllegalStateException) {
      fail("Artwork decoder could not change playback state", e)
    }
  }

  override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
    if (failed) return
    val current = MediaPlayer()
    player = current
    outputSurface = Surface(texture)
    try {
      current.setVolume(0f, 0f)
      current.setSurface(outputSurface)
      current.setOnPreparedListener {
        if (player === it) {
          prepared = true
          it.isLooping = true
          setPlaying(playing)
        }
      }
      current.setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
        if (videoWidth > 0 && videoHeight > 0) {
          videoAspect = videoWidth.toFloat() / videoHeight
          updateCrop()
        }
      }
      current.setOnInfoListener { _, what, _ ->
        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onFrameReady(true)
        false
      }
      current.setOnErrorListener { _, what, extra ->
        fail("Artwork decoder error ($what/$extra)")
        true
      }
      current.setDataSource(context, Uri.parse(url), mapOf("User-Agent" to "mpvRx animated artwork"))
      current.prepareAsync()
    } catch (e: IOException) {
      fail("Could not open artwork stream", e)
    } catch (e: IllegalArgumentException) {
      fail("Unsupported artwork stream", e)
    } catch (e: IllegalStateException) {
      fail("Artwork decoder could not prepare", e)
    } catch (e: SecurityException) {
      fail("Artwork stream access denied", e)
    }
  }

  override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = updateCrop()

  override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
    releasePlayer()
    onFrameReady(false)
    return true
  }

  override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

  private fun updateCrop() {
    if (width <= 0 || height <= 0) return
    val viewAspect = width.toFloat() / height
    val scaleX = if (videoAspect > viewAspect) videoAspect / viewAspect else 1f
    val scaleY = if (videoAspect < viewAspect) viewAspect / videoAspect else 1f
    setTransform(Matrix().apply { setScale(scaleX, scaleY, width / 2f, height / 2f) })
  }

  private fun fail(message: String, cause: Exception? = null) {
    if (failed) return
    failed = true
    Log.w("MotionArtworkVideo", "$message; trying another rendition or retaining the cover", cause)
    releasePlayer()
    onFrameReady(false)
    onFailure()
  }

  fun releasePlayer() {
    val current = player
    player = null
    prepared = false
    current?.setOnPreparedListener(null)
    current?.setOnInfoListener(null)
    current?.setOnErrorListener(null)
    current?.setOnVideoSizeChangedListener(null)
    current?.release()
    outputSurface?.release()
    outputSurface = null
  }
}
