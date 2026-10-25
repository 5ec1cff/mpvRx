/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.tv

import android.os.SystemClock
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.ui.utils.LocalCurrentScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CONTENT_FOCUS_WAIT_MS = 1_500L
private const val CONTENT_FOCUS_RETRY_MS = 100L
private val TvDpadScrollStep = 96.dp

/** Requests focus without throwing when the requester is not attached; true if focus moved. */
internal fun FocusRequester.tryRequestFocus(): Boolean =
  runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)

/** Focus bookkeeping for one screen (navigation entry) on TV. */
@Stable
internal class TvScreenFocusState {
  /** Whether anything inside the screen currently holds D-pad focus. */
  var hasFocus by mutableStateOf(false)
    internal set

  private val contentTargets = ArrayList<FocusRequester>(2)

  internal val hasContentTargets: Boolean
    get() = contentTargets.isNotEmpty()

  internal fun register(requester: FocusRequester) {
    if (requester !in contentTargets) contentTargets += requester
  }

  internal fun unregister(requester: FocusRequester) {
    contentTargets -= requester
  }

  /** Focuses the screen's main content; the first registered target that takes focus wins. */
  fun requestContentFocus(): Boolean = contentTargets.any { it.tryRequestFocus() }
}

internal val LocalTvScreenFocus = staticCompositionLocalOf<TvScreenFocusState?> { null }
internal val LocalTvFocusMemoryScope = staticCompositionLocalOf<Any?> { null }

/**
 * Marks [requester] (attached to a screen's list or grid) as where D-pad focus starts when the
 * screen opens, instead of the toolbar button in the top-left corner. The outermost registration
 * wins, so a host such as the main screen keeps routing focus to its current tab.
 */
@Composable
internal fun RegisterTvContentFocus(requester: FocusRequester) {
  val screen = LocalTvScreenFocus.current ?: return
  DisposableEffect(screen, requester) {
    screen.register(requester)
    onDispose { screen.unregister(requester) }
  }
}

/**
 * Hosts one navigation entry on TV so every screen opens with a visible, predictable focus: the
 * item that had it before (when coming back), else the screen's content, else its first control.
 * Applies the overscan-safe inset when [safeArea] is set. Off TV it only draws [content].
 */
@Composable
internal fun TvScreenFocusHost(
  scope: Any,
  safeArea: Boolean,
  active: Boolean = true,
  autoFocus: Boolean = true,
  focusRequestKey: Any? = Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  if (!isTelevision()) {
    content()
    return
  }
  val state = remember { TvScreenFocusState() }
  val screenRequester = remember { FocusRequester() }
  LaunchedEffect(scope, active, autoFocus, focusRequestKey) {
    if (!active || !autoFocus) return@LaunchedEffect
    // Screens that place their own initial focus do so after one frame; let them go first.
    withFrameNanos { }
    withFrameNanos { }
    val deadline = SystemClock.uptimeMillis() + CONTENT_FOCUS_WAIT_MS
    // Only the active entry may claim focus. Nested panes opt in when the user opens them;
    // checking the parent's hasFocus here also includes the category pane and blocks handoff.
    while (true) {
      if (TvFocusMemory.restore(scope)) break
      if (state.requestContentFocus()) break
      val worthWaiting = state.hasContentTargets || TvFocusMemory.hasRemembered(scope)
      if (!worthWaiting || SystemClock.uptimeMillis() >= deadline) {
        if (!state.hasFocus) screenRequester.tryRequestFocus()
        break
      }
      // Lists that are still loading get a moment before focus falls back to the first control.
      delay(CONTENT_FOCUS_RETRY_MS)
    }
  }

  CompositionLocalProvider(LocalTvScreenFocus provides state, LocalTvFocusMemoryScope provides scope) {
    Box(
      modifier =
        modifier
          .fillMaxSize()
          .then(if (safeArea) Modifier.padding(TvSafeArea.Padding) else Modifier)
          .onFocusChanged { state.hasFocus = it.hasFocus }
          .focusRequester(screenRequester)
          .focusGroup(),
    ) {
      content()
    }
  }
}

/** Registers the list/pane itself, so initial focus skips toolbar Back buttons on TV. */
internal fun Modifier.tvContentFocus(): Modifier =
  composed {
    if (!isTelevision()) return@composed this
    val requester = remember { FocusRequester() }
    RegisterTvContentFocus(requester)
    this.focusRequester(requester).focusGroup()
  }

/**
 * Remembers which keyed item last had focus on each screen, so coming back (Back from a folder, or
 * Right from the side rail) lands on the exact card that was left instead of the first one.
 */
internal object TvFocusMemory {
  private const val MAX_SCREENS = 32

  private val lastFocused =
    object : LinkedHashMap<Any, Any>(16, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Any>?): Boolean = size > MAX_SCREENS
    }
  private val attached = HashMap<Any, HashMap<Any, FocusRequester>>()

  fun hasRemembered(scope: Any): Boolean = lastFocused.containsKey(scope)

  /** Focuses the item that last had focus on [scope], if it is composed right now. */
  fun restore(scope: Any): Boolean {
    val key = lastFocused[scope] ?: return false
    return attached[scope]?.get(key)?.tryRequestFocus() ?: false
  }

  internal fun onFocused(
    scope: Any,
    key: Any,
  ) {
    lastFocused[scope] = key
  }

  internal fun attach(
    scope: Any,
    key: Any,
    requester: FocusRequester,
  ) {
    attached.getOrPut(scope) { HashMap() }[key] = requester
  }

  internal fun detach(
    scope: Any,
    key: Any,
    requester: FocusRequester,
  ) {
    val keys = attached[scope] ?: return
    if (keys[key] === requester) keys.remove(key)
    if (keys.isEmpty()) attached.remove(scope)
  }
}

/**
 * Registers a list or grid item with [TvFocusMemory] under a stable [key] (its path or id). TV only.
 * Place it before the item's clickable so it observes and targets the item's own focus.
 */
internal fun Modifier.tvFocusMemory(key: Any): Modifier =
  composed {
    val scope = LocalTvFocusMemoryScope.current ?: LocalCurrentScreen.current
    if (scope == null || !isTelevision()) return@composed this
    val requester = remember { FocusRequester() }
    DisposableEffect(scope, key, requester) {
      TvFocusMemory.attach(scope, key, requester)
      onDispose { TvFocusMemory.detach(scope, key, requester) }
    }
    this
      .onFocusChanged { if (it.hasFocus) TvFocusMemory.onFocused(scope, key) }
      .focusRequester(requester)
  }

/**
 * A control that animates out but stays composed (a FAB scaled to zero) must not keep taking
 * D-pad focus, or focus disappears onto something invisible. Place before the control's clickable.
 */
internal fun Modifier.focusableWhen(visible: Boolean): Modifier = focusProperties { if (!visible) canFocus = false }

/**
 * TV: Up/Down can still read a scrolling block that has nothing focusable inside (release notes, a
 * long message). Place on a container holding both the block and the buttons around it: the key
 * moves focus as usual, and only when there is nowhere to move does it scroll [state] instead.
 */
internal fun Modifier.tvScrollWhenFocusCannotMove(state: ScrollState): Modifier =
  composed {
    if (!isTelevision()) return@composed this
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { TvDpadScrollStep.toPx() }
    this.onKeyEvent { event ->
      if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
      val forward =
        when (event.key) {
          Key.DirectionDown -> true
          Key.DirectionUp -> false
          else -> return@onKeyEvent false
        }
      if (focusManager.moveFocus(if (forward) FocusDirection.Down else FocusDirection.Up)) return@onKeyEvent true
      val canScroll = if (forward) state.canScrollForward else state.canScrollBackward
      if (canScroll) scope.launch { state.animateScrollBy(if (forward) stepPx else -stepPx) }
      canScroll
    }
  }

/**
 * TV: makes this element a focus stop on which D-pad Left/Right turn the pages of a swipe-only
 * pager (see [TvPagerFocusArea]). At the first and last page the key falls through to normal focus
 * search.
 */
internal fun Modifier.tvPagerKeys(state: PagerState): Modifier =
  composed {
    if (!isTelevision()) return@composed this
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    this
      .onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val forward =
          when (event.key) {
            Key.DirectionRight -> !rtl
            Key.DirectionLeft -> rtl
            else -> return@onKeyEvent false
          }
        val base = if (state.isScrollInProgress) state.targetPage else state.currentPage
        val target = base + if (forward) 1 else -1
        if (target !in 0 until state.pageCount) return@onKeyEvent false
        scope.launch { state.animateScrollToPage(target) }
        true
      }.focusable()
  }

/**
 * TV: an invisible focus area over the middle of a swipe-only image viewer. Left/Right turn its
 * pages; the viewer's top and bottom controls sit outside the area, so Up/Down still reach them
 * (a full-screen focus target would have no neighbours above or below it). Takes focus when the
 * viewer opens. Draws nothing, intercepts no touches, and is absent off TV.
 */
@Composable
internal fun BoxScope.TvPagerFocusArea(state: PagerState) {
  if (!isTelevision()) return
  val requester = remember { FocusRequester() }
  LaunchedEffect(requester) {
    withFrameNanos { }
    requester.tryRequestFocus()
  }
  Box(
    modifier =
      Modifier
        .align(Alignment.Center)
        .fillMaxWidth()
        .fillMaxHeight(0.5f)
        .focusRequester(requester)
        .tvPagerKeys(state),
  )
}
