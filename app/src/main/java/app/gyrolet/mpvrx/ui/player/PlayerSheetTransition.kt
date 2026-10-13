/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player

import android.graphics.Bitmap
import android.graphics.Picture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.gyrolet.mpvrx.ui.theme.AppMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

internal enum class PlayerSheetDestination { FULL, MINI }

internal class PlayerSheetCapture(
  private val context: GraphicsContext,
  val view: View,
  val density: Density,
  val layoutDirection: LayoutDirection,
) {
  val layer = context.createGraphicsLayer()
  var isRecorded by mutableStateOf(false)
  private var isReleased = false

  // Live layers belong to their own window; a stopping Activity may discard them, so anything
  // drawn by the other Activity is copied into a hardware bitmap first.
  fun snapshot(): ImageBitmap? {
    if (isReleased || !isRecorded || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    if (layer.size.width <= 0 || layer.size.height <= 0) return null
    // The overlay fades this layer while dragging; the frozen copy must be fully opaque.
    layer.alpha = 1f
    return runCatching {
      Bitmap.createBitmap(LayerPicture(layer, density, layoutDirection)).asImageBitmap()
    }.onFailure { Log.w("PlayerSheetTransitions", "Unable to snapshot player sheet", it) }.getOrNull()
  }

  fun release() {
    if (isReleased) return
    isReleased = true
    context.releaseGraphicsLayer(layer)
  }
}

private class LayerPicture(
  private val layer: GraphicsLayer,
  private val density: Density,
  private val layoutDirection: LayoutDirection,
) : Picture() {
  override fun getWidth(): Int = layer.size.width

  override fun getHeight(): Int = layer.size.height

  override fun requiresHardwareAcceleration(): Boolean = true

  override fun draw(canvas: android.graphics.Canvas) {
    CanvasDrawScope().draw(
      density,
      layoutDirection,
      androidx.compose.ui.graphics.Canvas(canvas),
      layer.size.toSize(),
    ) { drawLayer(layer) }
  }
}

internal data class PlayerSheetAnchor(
  val owner: Any,
  val mediaId: String?,
  val bounds: Rect,
  val cornerRadius: Float,
  val backgroundColor: Color,
  val capture: PlayerSheetCapture? = null,
  val snapshot: ImageBitmap? = null,
) {
  val hasContent: Boolean
    get() = snapshot != null || capture?.isRecorded == true

  /** Detaches the anchor from its window so another Activity can draw it safely. */
  fun frozen(): PlayerSheetAnchor =
    if (snapshot != null || capture == null) this else copy(snapshot = capture.snapshot(), capture = null)
}

internal data class PlayerSheetEndpoints(
  val full: PlayerSheetAnchor,
  val mini: PlayerSheetAnchor,
  val backdrop: PlayerSheetAnchor?,
) {
  val travel: Float
    get() = (mini.bounds.top - full.bounds.top).coerceAtLeast(1f)

  fun uses(capture: PlayerSheetCapture): Boolean =
    full.capture === capture || mini.capture === capture || backdrop?.capture === capture
}

internal class PlayerSheetMotion(
  val id: Long,
  val destination: PlayerSheetDestination,
  endpoints: PlayerSheetEndpoints,
  initialExpansion: Float,
  val initialVelocity: Float,
) {
  var endpoints by mutableStateOf(endpoints)
  val expansion: Animatable<Float, AnimationVector1D> = Animatable(initialExpansion)
  var hasStarted = false
  var renderDestination by mutableStateOf(
    if (destination == PlayerSheetDestination.FULL) PlayerSheetDestination.MINI else PlayerSheetDestination.FULL,
  )
}

internal class PlayerSheetDrag(
  val source: PlayerSheetDestination,
  val mediaId: String,
  val endpoints: PlayerSheetEndpoints,
  initialExpansion: Float,
) {
  var expansion by mutableFloatStateOf(initialExpansion)
  var velocity = 0f
}

internal object PlayerSheetTransitions {
  private const val TAG = "PlayerSheetTransitions"
  private var fullAnchor by mutableStateOf<PlayerSheetAnchor?>(null)
  private var miniAnchor by mutableStateOf<PlayerSheetAnchor?>(null)
  private var miniPlacement by mutableStateOf<PlayerSheetAnchor?>(null)
  private var backdrop by mutableStateOf<PlayerSheetAnchor?>(null)
  // Browser content frozen while MainActivity was still visible, for gestures inside the player.
  private var browserBackdrop: PlayerSheetAnchor? = null
  private var browserMini: PlayerSheetAnchor? = null
  private val pendingReleases = mutableSetOf<PlayerSheetCapture>()
  private val handler = Handler(Looper.getMainLooper())
  private var timeout: Runnable? = null
  private var expansionTimeout: Runnable? = null
  private var nextId = 0L
  var motion by mutableStateOf<PlayerSheetMotion?>(null)
    private set
  var drag by mutableStateOf<PlayerSheetDrag?>(null)
    private set
  /** Keeps the collapsed mini player shown until the playback service reports foreground. */
  var collapsedMediaId by mutableStateOf<String?>(null)
    private set
  /** Keep the source laid out until its window is covered, including native video transitions. */
  var expandingMediaId by mutableStateOf<String?>(null)
    private set
  var reducedMotion = false

  fun holdExpansion(mediaId: String?): Boolean {
    if (mediaId == null || expandingMediaId != null) return false
    expandingMediaId = mediaId
    expansionTimeout = Runnable { releaseExpansionHold() }.also { handler.postDelayed(it, 3_000L) }
    return true
  }

  fun releaseExpansionHold() {
    expandingMediaId = null
    expansionTimeout?.let(handler::removeCallbacks)
    expansionTimeout = null
  }

  fun releaseCollapsedHold(mediaId: String) {
    if (collapsedMediaId == mediaId) collapsedMediaId = null
  }

  fun anchor(destination: PlayerSheetDestination): PlayerSheetAnchor? =
    if (destination == PlayerSheetDestination.FULL) fullAnchor else miniAnchor

  fun update(destination: PlayerSheetDestination?, anchor: PlayerSheetAnchor) {
    when (destination) {
      PlayerSheetDestination.FULL -> fullAnchor = anchor
      PlayerSheetDestination.MINI -> miniAnchor = anchor
      null -> backdrop = anchor
    }
    val active = motion ?: return
    if (destination != active.destination || anchor.mediaId != active.endpoints.full.mediaId) return
    active.endpoints = if (destination == PlayerSheetDestination.FULL) {
      active.endpoints.copy(full = if (active.hasStarted) anchor.copy(bounds = active.endpoints.full.bounds) else anchor)
    } else {
      // Resolve the real target after the browser's insets/nav bar settle, then lock its geometry.
      active.endpoints.copy(mini = if (active.hasStarted) anchor.copy(bounds = active.endpoints.mini.bounds) else anchor)
    }
    releaseUnusedCaptures()
  }

  fun remove(destination: PlayerSheetDestination?, owner: Any, capture: PlayerSheetCapture) {
    when (destination) {
      PlayerSheetDestination.FULL -> if (fullAnchor?.owner === owner) fullAnchor = null
      PlayerSheetDestination.MINI -> if (miniAnchor?.owner === owner) miniAnchor = null
      null -> if (backdrop?.owner === owner) backdrop = null
    }
    if (drag?.endpoints?.uses(capture) == true && drag?.source == destination) drag = null
    // The source Activity can disappear before the destination draws its first frame.
    pendingReleases.add(capture)
    releaseUnusedCaptures()
  }

  fun updateMiniPlacement(anchor: PlayerSheetAnchor) {
    miniPlacement = anchor
  }

  fun removeMiniPlacement(owner: Any) {
    if (miniPlacement?.owner === owner) miniPlacement = null
  }

  /** Freezes the browser while MainActivity is still visible, before the player covers it. */
  fun captureBrowser() {
    if (motion != null || drag != null) return
    // Video uses the native window transition; no full-browser GPU snapshot is needed there.
    if (PlaybackSession.state.value.currentItem?.declaredMediaKind() == DeclaredPlaybackMediaKind.VIDEO) return
    browserBackdrop = backdrop?.frozen()?.takeIf { it.snapshot != null }
    browserMini = miniAnchor?.takeIf { it.capture?.isRecorded == true }?.frozen()?.takeIf { it.snapshot != null }
  }

  fun clearBrowserCache() {
    browserBackdrop = null
    browserMini = null
  }

  private fun endpoints(source: PlayerSheetDestination, mediaId: String): PlayerSheetEndpoints? {
    val placement = miniPlacement ?: return null
    val sourceAnchor = anchor(source)?.takeIf { it.mediaId == mediaId && it.hasContent } ?: return null
    val pair = if (source == PlayerSheetDestination.MINI) {
      PlayerSheetEndpoints(
        // Opaque stand-in until the player's own sheet registers, so the browser never ghosts through.
        full = backdrop?.copy(
          mediaId = mediaId,
          cornerRadius = 0f,
          backgroundColor = sourceAnchor.backgroundColor.copy(alpha = 1f),
          capture = null,
          snapshot = null,
        ) ?: return null,
        mini = sourceAnchor.copy(bounds = placement.bounds),
        backdrop = backdrop,
      )
    } else {
      PlayerSheetEndpoints(
        full = sourceAnchor,
        mini = browserMini?.takeIf { it.mediaId == mediaId }?.copy(bounds = placement.bounds)
          ?: placement.copy(mediaId = mediaId),
        backdrop = browserBackdrop,
      )
    }
    if (pair.full.bounds.isEmpty || pair.mini.bounds.isEmpty) return null
    return pair
  }

  fun begin(destination: PlayerSheetDestination, mediaId: String?): Boolean {
    // The mini player must be present even when this media uses the native Activity animation.
    if (destination == PlayerSheetDestination.MINI) collapsedMediaId = mediaId
    if (mediaId == null || reducedMotion || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val source = if (destination == PlayerSheetDestination.FULL) PlayerSheetDestination.MINI else PlayerSheetDestination.FULL
    val activeDrag = drag?.takeIf { it.mediaId == mediaId && it.source == source }
    val previous = motion?.takeIf { it.endpoints.full.mediaId == mediaId }
    val sourcePair = activeDrag?.endpoints ?: previous?.endpoints ?: endpoints(source, mediaId)
    if (sourcePair == null) {
      Log.d(TAG, "No recorded player sheet for $destination; keeping the Activity transition")
      return false
    }
    // The destination Activity draws this motion, so detach the source window's live layers.
    val pair = if (destination == PlayerSheetDestination.FULL) {
      sourcePair.copy(mini = sourcePair.mini.frozen(), backdrop = sourcePair.backdrop?.frozen())
    } else {
      sourcePair.copy(full = sourcePair.full.frozen())
    }
    val frozenSource = if (destination == PlayerSheetDestination.FULL) pair.backdrop else pair.full
    if (frozenSource?.snapshot == null || (destination == PlayerSheetDestination.FULL && pair.mini.snapshot == null)) {
      Log.d(TAG, "Unable to freeze the source sheet for $destination; keeping the Activity transition")
      return false
    }
    if (destination == PlayerSheetDestination.FULL) {
      browserBackdrop = pair.backdrop
      browserMini = pair.mini.takeIf { it.snapshot != null }
      collapsedMediaId = null
    } else {
      collapsedMediaId = mediaId
    }
    val expansion = activeDrag?.expansion ?: previous?.expansion?.value
      ?: if (source == PlayerSheetDestination.FULL) 1f else 0f
    val id = ++nextId
    motion = PlayerSheetMotion(
      id, destination, pair, expansion,
      initialVelocity = (-(activeDrag?.velocity ?: 0f) / pair.travel).coerceIn(-6f, 6f),
    )
    drag = null
    timeout?.let(handler::removeCallbacks)
    timeout = Runnable {
      Log.w(TAG, "Player sheet transition $id timed out")
      finish(id)
    }.also { handler.postDelayed(it, 3_000L) }
    releaseUnusedCaptures()
    return true
  }

  fun finish(id: Long) {
    if (motion?.id != id) return
    motion = null
    timeout?.let(handler::removeCallbacks)
    timeout = null
    releaseUnusedCaptures()
  }

  fun startDrag(source: PlayerSheetDestination, mediaId: String?): PlayerSheetDrag? {
    if (mediaId == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val previous = motion?.takeIf { it.endpoints.full.mediaId == mediaId }
    val pair = previous?.endpoints ?: endpoints(source, mediaId) ?: return null
    // Without a frozen browser, dragging inside the player would expose its black video surface.
    if (source == PlayerSheetDestination.FULL && pair.backdrop?.snapshot == null) return null
    val expansion = previous?.expansion?.value
      ?: if (source == PlayerSheetDestination.FULL) 1f else 0f
    val active = PlayerSheetDrag(source, mediaId, pair, expansion)
    drag = active
    motion = null
    timeout?.let(handler::removeCallbacks)
    timeout = null
    releaseUnusedCaptures()
    return active
  }

  fun cancelDrag(active: PlayerSheetDrag) {
    if (drag !== active) return
    drag = null
    releaseUnusedCaptures()
  }

  suspend fun restoreDrag(active: PlayerSheetDrag, reduceMotion: Boolean) {
    try {
      if (!reduceMotion) {
        val target = if (active.source == PlayerSheetDestination.FULL) 1f else 0f
        Animatable(active.expansion).animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)) {
          active.expansion = value
        }
      }
    } finally {
      cancelDrag(active)
    }
  }

  fun isHidden(destination: PlayerSheetDestination, mediaId: String?): Boolean {
    val pair = motion?.endpoints ?: drag?.endpoints ?: return false
    val participatingAnchor = if (destination == PlayerSheetDestination.FULL) pair.full else pair.mini
    // Frozen source anchors intentionally have no live capture. Their original content must still
    // be hidden, otherwise it is drawn twice underneath the moving snapshot.
    return participatingAnchor.mediaId == mediaId
  }

  private fun releaseUnusedCaptures() {
    val unused = pendingReleases.filter {
      motion?.endpoints?.uses(it) != true && drag?.endpoints?.uses(it) != true
    }
    unused.forEach { it.release() }
    pendingReleases.removeAll(unused.toSet())
  }
}

private fun LayoutCoordinates.sheetScreenBounds(view: View): Rect {
  val location = IntArray(2)
  view.rootView.getLocationOnScreen(location)
  return Rect(localToWindow(Offset.Zero), localToWindow(Offset(size.width.toFloat(), size.height.toFloat())))
    .translate(Offset(location[0].toFloat(), location[1].toFloat()))
}

@Composable
private fun Modifier.recordPlayerSheet(
  destination: PlayerSheetDestination?,
  mediaId: String?,
  shape: Shape,
  backgroundColor: Color,
): Modifier {
  val context = LocalGraphicsContext.current
  val view = LocalView.current
  val density = LocalDensity.current
  val direction = LocalLayoutDirection.current
  val capture = remember(context, view, destination, mediaId, density, direction) {
    PlayerSheetCapture(context, view, density, direction)
  }
  val owner = remember(capture) { Any() }
  var coordinates by remember(capture) { mutableStateOf<LayoutCoordinates?>(null) }

  fun updateAnchor(layout: LayoutCoordinates) {
    if (!layout.isAttached) return
    val bounds = layout.sheetScreenBounds(view)
    val outline = shape.createOutline(Size(bounds.width, bounds.height), direction, density)
    val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
    PlayerSheetTransitions.update(
      destination,
      PlayerSheetAnchor(owner, mediaId, bounds, radius, backgroundColor, capture),
    )
  }

  SideEffect { coordinates?.let(::updateAnchor) }
  DisposableEffect(capture) {
    onDispose { PlayerSheetTransitions.remove(destination, owner, capture) }
  }
  return this
    .onGloballyPositioned {
      coordinates = it
      updateAnchor(it)
    }
    .drawWithContent {
      val hidden = destination != null && PlayerSheetTransitions.isHidden(destination, mediaId)
      val holdRecording = hidden && capture.isRecorded &&
        PlayerSheetTransitions.motion?.renderDestination == destination &&
        capture.layer.size.toSize() == size
      // The morph changes layer alpha every frame; do not re-record the hidden controls for it.
      if (!holdRecording) {
        capture.layer.record { this@drawWithContent.drawContent() }
        capture.isRecorded = true
      }
      if (!hidden) {
        capture.layer.alpha = 1f
        drawLayer(capture.layer)
      }
    }
}

@Composable
internal fun Modifier.playerSheetAnchor(
  destination: PlayerSheetDestination,
  mediaId: String?,
  shape: Shape = RectangleShape,
  backgroundColor: Color,
): Modifier = recordPlayerSheet(destination, mediaId, shape, backgroundColor)

@Composable
internal fun Modifier.playerSheetBackdrop(): Modifier =
  recordPlayerSheet(null, null, RectangleShape, Color.Transparent)

@Composable
internal fun Modifier.playerSheetMiniPlacement(shape: Shape, backgroundColor: Color): Modifier {
  val view = LocalView.current
  val density = LocalDensity.current
  val direction = LocalLayoutDirection.current
  val owner = remember { Any() }
  DisposableEffect(owner) {
    onDispose { PlayerSheetTransitions.removeMiniPlacement(owner) }
  }
  return onGloballyPositioned {
    val bounds = it.sheetScreenBounds(view)
    val outline = shape.createOutline(Size(bounds.width, bounds.height), direction, density)
    PlayerSheetTransitions.updateMiniPlacement(
      PlayerSheetAnchor(
        owner, null, bounds,
        (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f,
        backgroundColor,
      ),
    )
  }
}

private fun DrawScope.drawSheetContent(
  anchor: PlayerSheetAnchor,
  hostView: View,
  left: Float,
  top: Float,
  alpha: Float,
) {
  if (alpha <= 0f) return
  val snapshot = anchor.snapshot
  if (snapshot != null) {
    drawImage(snapshot, topLeft = Offset(left, top), alpha = alpha)
    return
  }
  val capture = anchor.capture?.takeIf { it.isRecorded && it.view.rootView === hostView.rootView } ?: return
  capture.layer.alpha = alpha
  translate(left, top) { drawLayer(capture.layer) }
}

private fun DrawScope.drawPlayerSheet(
  endpoints: PlayerSheetEndpoints,
  expansion: Float,
  hostView: View,
  origin: Offset,
  drawBackdrop: Boolean,
) {
  if (drawBackdrop) {
    endpoints.backdrop?.let {
      drawSheetContent(it, hostView, it.bounds.left - origin.x, it.bounds.top - origin.y, 1f)
    }
  }
  val fraction = expansion.coerceIn(0f, 1f)
  val full = endpoints.full
  val mini = endpoints.mini
  val bounds = lerp(mini.bounds, full.bounds, fraction).translate(-origin)
  val overshoot = ((fraction - expansion) * endpoints.travel)
    .coerceIn(-mini.bounds.height * 0.2f, mini.bounds.height * 0.2f)
  val sheetBounds = Rect(bounds.left, bounds.top + overshoot, bounds.right, bounds.bottom)
  if (sheetBounds.isEmpty) return
  val radius = mini.cornerRadius + (full.cornerRadius - mini.cornerRadius) * fraction
  val clip = Path().apply { addRoundRect(RoundRect(sheetBounds, CornerRadius(radius))) }
  val fullAlpha = ((fraction - 0.25f) / 0.75f).coerceIn(0f, 1f)
  val miniAlpha = (1f - fraction * 2f).coerceIn(0f, 1f)
  clipPath(clip) {
    drawRect(
      color = lerp(mini.backgroundColor, full.backgroundColor, fraction),
      topLeft = sheetBounds.topLeft,
      size = sheetBounds.size,
    )
    drawSheetContent(
      full,
      hostView,
      sheetBounds.center.x - full.bounds.width / 2f,
      sheetBounds.top + 24.dp.toPx() * (1f - fullAlpha),
      fullAlpha,
    )
    drawSheetContent(mini, hostView, sheetBounds.center.x - mini.bounds.width / 2f, sheetBounds.top, miniAlpha)
  }
}

@Composable
internal fun PlayerSheetTransitionOverlay(
  destination: PlayerSheetDestination,
  modifier: Modifier = Modifier,
  contentReady: Boolean = true,
) {
  val motion = PlayerSheetTransitions.motion
  val drag = PlayerSheetTransitions.drag
  val session by PlaybackSession.state.collectAsState()
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val reducedMotion = AppMotion.shouldReduceMotion()
  val animationSpec = remember {
    spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f, visibilityThreshold = 0.001f)
  }
  val view = LocalView.current
  // Written during layout, which runs before draw in the same frame, so the first frame is placed.
  val origin = remember { arrayOf(Offset.Zero) }

  SideEffect { PlayerSheetTransitions.reducedMotion = reducedMotion }
  LaunchedEffect(session.currentItem?.stableId) {
    motion?.takeIf { it.endpoints.full.mediaId != session.currentItem?.stableId }?.let {
      PlayerSheetTransitions.finish(it.id)
    }
    drag?.takeIf { it.mediaId != session.currentItem?.stableId }?.let(PlayerSheetTransitions::cancelDrag)
  }
  LaunchedEffect(motion?.id, lifecycle, contentReady) {
    val active = motion ?: return@LaunchedEffect
    if (active.destination != destination || !contentReady) return@LaunchedEffect
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      try {
        // Both targets must have drawn once. Otherwise collapse lands on an empty/stale mini
        // placement and then jumps when its real layout and navigation clearance arrive.
        val target = withTimeoutOrNull(750L) {
          snapshotFlow {
            PlayerSheetTransitions.anchor(destination)?.takeIf {
              it.mediaId == active.endpoints.full.mediaId && it.capture?.isRecorded == true
            }
          }.filterNotNull().first()
        }
        if (target == null) {
          Log.w("PlayerSheetTransitions", "Destination $destination did not record a player sheet")
          return@repeatOnLifecycle
        }
        withFrameNanos { }
        active.hasStarted = true
        active.renderDestination = destination
        val targetExpansion = if (destination == PlayerSheetDestination.FULL) 1f else 0f
        if (reducedMotion) {
          active.expansion.snapTo(targetExpansion)
        } else {
          active.expansion.animateTo(targetExpansion, animationSpec, active.initialVelocity)
        }
      } finally {
        PlayerSheetTransitions.finish(active.id)
      }
    }
  }

  val showMotion = motion != null &&
    (motion.renderDestination == destination || motion.destination == destination)
  val showDrag = drag?.source == destination && !reducedMotion
  if (!showMotion && !showDrag) return
  Canvas(
    modifier = modifier.fillMaxSize().onGloballyPositioned { origin[0] = it.sheetScreenBounds(view).topLeft },
  ) {
    val drawBackdrop = destination == PlayerSheetDestination.FULL
    if (showMotion && motion != null) {
      drawPlayerSheet(motion.endpoints, motion.expansion.value, view, origin[0], drawBackdrop)
    } else if (showDrag && drag != null) {
      drawPlayerSheet(drag.endpoints, drag.expansion, view, origin[0], drawBackdrop)
    }
  }
}

@Composable
private fun Modifier.swipePlayerSheet(
  source: PlayerSheetDestination,
  mediaId: String?,
  enabled: Boolean,
  animateSheet: Boolean,
  onToggle: () -> Boolean,
): Modifier {
  val scope = rememberCoroutineScope()
  val toggle by rememberUpdatedState(onToggle)
  val reducedMotion = AppMotion.shouldReduceMotion()
  val density = LocalDensity.current
  val directionThreshold = with(density) { 5.dp.toPx() }
  val fallbackThreshold = with(density) { (if (source == PlayerSheetDestination.FULL) 80.dp else 48.dp).toPx() }
  var restoreJob by remember { mutableStateOf<Job?>(null) }
  var activeDrag by remember { mutableStateOf<PlayerSheetDrag?>(null) }

  fun restore() {
    restoreJob?.cancel()
    val active = activeDrag ?: return
    restoreJob = scope.launch {
      try {
        PlayerSheetTransitions.restoreDrag(active, reducedMotion)
      } finally {
        PlayerSheetTransitions.cancelDrag(active)
        if (activeDrag === active) activeDrag = null
      }
    }
  }

  DisposableEffect(source, mediaId, enabled) {
    onDispose {
      restoreJob?.cancel()
      activeDrag?.let(PlayerSheetTransitions::cancelDrag)
      activeDrag = null
    }
  }
  return pointerInput(source, mediaId, enabled, animateSheet, directionThreshold, fallbackThreshold) {
    if (!enabled) return@pointerInput
    val tracker = VelocityTracker()
    var accumulated = 0f
    detectVerticalDragGestures(
      onDragStart = {
        restoreJob?.cancel()
        accumulated = 0f
        tracker.resetTracking()
        activeDrag = if (animateSheet && !reducedMotion) PlayerSheetTransitions.startDrag(source, mediaId) else null
      },
      onDragCancel = { restore() },
      onDragEnd = {
        val active = activeDrag
        val velocity = tracker.calculateVelocity().y
        val towardTarget = if (source == PlayerSheetDestination.FULL) accumulated else -accumulated
        val targetVelocity = if (source == PlayerSheetDestination.FULL) velocity else -velocity
        val shouldToggle = if (active != null) {
          when {
            source == PlayerSheetDestination.FULL && accumulated <= 0f -> false
            abs(accumulated) > directionThreshold -> towardTarget > 0f
            abs(velocity) > 150f -> targetVelocity > 0f
            else -> if (source == PlayerSheetDestination.FULL) active.expansion < 0.5f else active.expansion > 0.5f
          }
        } else {
          towardTarget > fallbackThreshold
        }
        if (active != null) active.velocity = velocity
        if (!shouldToggle || !toggle()) {
          restore()
        } else if (active != null && PlayerSheetTransitions.drag === active) {
          restoreJob = scope.launch {
            kotlinx.coroutines.delay(1_000L)
            if (PlayerSheetTransitions.drag === active) restore()
          }
        } else {
          activeDrag = null
        }
      },
      onVerticalDrag = { change, amount ->
        change.consume()
        accumulated += amount
        if (activeDrag == null) {
          accumulated = if (source == PlayerSheetDestination.FULL) {
            accumulated.coerceAtLeast(0f)
          } else {
            accumulated.coerceAtMost(0f)
          }
        }
        tracker.addPosition(change.uptimeMillis, change.position)
        activeDrag?.let {
          val limit = it.endpoints.mini.bounds.height * 0.2f / it.endpoints.travel
          it.expansion = (it.expansion - amount / it.endpoints.travel).coerceIn(-limit, 1f + limit)
        }
      },
    )
  }
}

@Composable
internal fun Modifier.swipeDownToMiniPlayer(
  mediaId: String?,
  enabled: Boolean,
  onMinimize: () -> Boolean,
): Modifier = swipePlayerSheet(PlayerSheetDestination.FULL, mediaId, enabled, true, onMinimize)

@Composable
internal fun Modifier.swipeUpToFullPlayer(
  mediaId: String?,
  animateSheet: Boolean,
  onExpand: () -> Boolean,
): Modifier = swipePlayerSheet(PlayerSheetDestination.MINI, mediaId, true, animateSheet, onExpand)
