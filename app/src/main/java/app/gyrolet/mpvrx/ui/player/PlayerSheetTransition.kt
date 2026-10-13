/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player

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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.compose.ui.unit.dp
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

internal class PlayerSheetCapture(private val context: GraphicsContext) {
  val layer = context.createGraphicsLayer()
  var isRecorded by mutableStateOf(false)
  private var isReleased = false

  fun release() {
    if (isReleased) return
    isReleased = true
    context.releaseGraphicsLayer(layer)
  }
}

internal data class PlayerSheetAnchor(
  val owner: Any,
  val mediaId: String?,
  val bounds: Rect,
  val cornerRadius: Float,
  val backgroundColor: Color,
  val capture: PlayerSheetCapture? = null,
)

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
  val fromGesture: Boolean,
) {
  var endpoints by mutableStateOf(endpoints)
  val expansion: Animatable<Float, AnimationVector1D> = Animatable(initialExpansion)
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
  private val pendingReleases = mutableSetOf<PlayerSheetCapture>()
  private val handler = Handler(Looper.getMainLooper())
  private var timeout: Runnable? = null
  private var nextId = 0L
  var motion by mutableStateOf<PlayerSheetMotion?>(null)
    private set
  var drag by mutableStateOf<PlayerSheetDrag?>(null)
    private set
  var reducedMotion = false

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
      active.endpoints.copy(full = anchor)
    } else {
      active.endpoints.copy(mini = anchor)
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

  private fun endpoints(source: PlayerSheetDestination, mediaId: String): PlayerSheetEndpoints? {
    val sourceAnchor = anchor(source)?.takeIf {
      it.mediaId == mediaId && it.capture?.isRecorded == true
    } ?: return null
    val mini = miniAnchor?.takeIf { it.mediaId == mediaId }
      ?: miniPlacement?.copy(mediaId = mediaId)
      ?: return null
    val full = fullAnchor?.takeIf { it.mediaId == mediaId }
      ?: backdrop?.copy(mediaId = mediaId, cornerRadius = 0f, capture = null)
      ?: return null
    if (sourceAnchor.bounds.isEmpty || mini.bounds.isEmpty || full.bounds.isEmpty) return null
    return PlayerSheetEndpoints(full, mini, backdrop)
  }

  fun begin(destination: PlayerSheetDestination, mediaId: String?): Boolean {
    if (mediaId == null) return false
    val source = if (destination == PlayerSheetDestination.FULL) PlayerSheetDestination.MINI else PlayerSheetDestination.FULL
    val activeDrag = drag?.takeIf { it.mediaId == mediaId && it.source == source }
    val previous = motion?.takeIf { it.endpoints.full.mediaId == mediaId }
    val pair = activeDrag?.endpoints ?: previous?.endpoints ?: endpoints(source, mediaId)
    if (pair == null) {
      Log.d(TAG, "No recorded player sheet for $destination; keeping the Activity transition")
      return false
    }
    val expansion = activeDrag?.expansion ?: previous?.expansion?.value
      ?: if (source == PlayerSheetDestination.FULL) 1f else 0f
    val id = ++nextId
    motion = PlayerSheetMotion(
      id, destination, pair, expansion,
      initialVelocity = -(activeDrag?.velocity ?: 0f) / pair.travel,
      fromGesture = activeDrag != null,
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
    if (mediaId == null) return null
    val previous = motion?.takeIf { it.endpoints.full.mediaId == mediaId }
    val pair = previous?.endpoints ?: endpoints(source, mediaId) ?: return null
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
        Animatable(active.expansion).animateTo(target, spring(stiffness = 500f)) {
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
    return participatingAnchor.mediaId == mediaId && participatingAnchor.capture != null
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
  val capture = remember(context, destination, mediaId) { PlayerSheetCapture(context) }
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
      val freezeBackdrop = destination == null &&
        (PlayerSheetTransitions.motion != null || PlayerSheetTransitions.drag != null)
      // Reuse the display list during the morph instead of recording the whole player each frame.
      if (!capture.isRecorded || (!hidden && !freezeBackdrop)) {
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

private fun DrawScope.drawPlayerSheet(
  endpoints: PlayerSheetEndpoints,
  expansion: Float,
  origin: Offset,
  drawBackdrop: Boolean,
) {
  if (drawBackdrop) {
    val backdrop = endpoints.backdrop
    val capture = backdrop?.capture?.takeIf { it.isRecorded }
    if (backdrop != null && capture != null) {
      translate(backdrop.bounds.left - origin.x, backdrop.bounds.top - origin.y) {
        drawLayer(capture.layer)
      }
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
    full.capture?.takeIf { it.isRecorded }?.let {
      it.layer.alpha = fullAlpha
      translate(
        sheetBounds.center.x - full.bounds.width / 2f,
        sheetBounds.top + 24.dp.toPx() * (1f - fullAlpha),
      ) {
        drawLayer(it.layer)
      }
    }
    mini.capture?.takeIf { it.isRecorded }?.let {
      it.layer.alpha = miniAlpha
      translate(sheetBounds.center.x - mini.bounds.width / 2f, sheetBounds.top) {
        drawLayer(it.layer)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerSheetTransitionOverlay(
  destination: PlayerSheetDestination,
  modifier: Modifier = Modifier,
) {
  val motion = PlayerSheetTransitions.motion
  val drag = PlayerSheetTransitions.drag
  val session by PlaybackSession.state.collectAsState()
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val reducedMotion = AppMotion.shouldReduceMotion()
  val animationSpec = remember { MotionScheme.expressive().defaultSpatialSpec<Float>() }
  val view = LocalView.current
  var origin by remember { mutableStateOf<Offset?>(null) }

  SideEffect { PlayerSheetTransitions.reducedMotion = reducedMotion }
  LaunchedEffect(session.currentItem?.stableId) {
    motion?.takeIf { it.endpoints.full.mediaId != session.currentItem?.stableId }?.let {
      PlayerSheetTransitions.finish(it.id)
    }
    drag?.takeIf { it.mediaId != session.currentItem?.stableId }?.let(PlayerSheetTransitions::cancelDrag)
  }
  LaunchedEffect(motion?.id, lifecycle, origin != null) {
    val active = motion ?: return@LaunchedEffect
    if (active.destination != destination || origin == null) return@LaunchedEffect
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      try {
        val target = withTimeoutOrNull(1_000L) {
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
        active.renderDestination = destination
        val targetExpansion = if (destination == PlayerSheetDestination.FULL) 1f else 0f
        if (reducedMotion) {
          active.expansion.snapTo(targetExpansion)
        } else {
          val spec = if (active.fromGesture && destination == PlayerSheetDestination.MINI) {
            spring<Float>(
              dampingRatio = 1f - 0.5f * active.expansion.value.coerceIn(0f, 1f),
              stiffness = Spring.StiffnessLow,
            )
          } else {
            animationSpec
          }
          active.expansion.animateTo(targetExpansion, spec, active.initialVelocity)
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
    modifier = modifier.fillMaxSize().onGloballyPositioned { origin = it.sheetScreenBounds(view).topLeft },
  ) {
    val positionedOrigin = origin ?: return@Canvas
    if (showMotion && motion != null) {
      drawPlayerSheet(motion.endpoints, motion.expansion.value, positionedOrigin, destination == PlayerSheetDestination.FULL)
    } else if (showDrag && drag != null) {
      drawPlayerSheet(drag.endpoints, drag.expansion, positionedOrigin, destination == PlayerSheetDestination.FULL)
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
