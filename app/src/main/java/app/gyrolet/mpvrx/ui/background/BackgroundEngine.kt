package app.gyrolet.mpvrx.ui.background

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/* ------------------------------------------------------------------------------------------
 * Shared plumbing for every backdrop.
 *
 * The backdrops here are *analytic*: each one is a pure function of time. A particle's position
 * is computed from its index and the clock rather than integrated frame by frame, so there is no
 * per-particle state to allocate, nothing to reset when the page is recreated, and a background
 * can be paused simply by not advancing the clock.
 * ------------------------------------------------------------------------------------------ */

/** Colours a backdrop may use, taken from the live theme so every backdrop follows it. */
internal class BackdropPalette(
  val primary: Color,
  val secondary: Color,
  val tertiary: Color,
  /** Readable tone against the page: near-white on dark themes, near-black on light ones. */
  val ink: Color,
  val isDark: Boolean,
) {
  fun accent(i: Int): Color =
    when (((i % 3) + 3) % 3) {
      0 -> primary
      1 -> secondary
      else -> tertiary
    }
}

@Composable
internal fun rememberBackdropPalette(): BackdropPalette {
  val cs = MaterialTheme.colorScheme
  val dark = cs.background.luminance() < 0.5f
  return remember(cs.primary, cs.secondary, cs.tertiary, cs.onBackground, dark) {
    BackdropPalette(cs.primary, cs.secondary, cs.tertiary, cs.onBackground, dark)
  }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Seconds since this backdrop appeared. Advances only while the host is resumed, at most once per
 * [MIN_STEP_MS], so a background that drifts slowly does not repaint the screen at every vsync of
 * a 120 Hz panel.
 */
@Composable
internal fun rememberBackdropClock(running: Boolean = true): State<Float> {
  val lifecycle = LocalLifecycleOwner.current
  val clock = remember { mutableFloatStateOf(0f) }
  LaunchedEffect(lifecycle, running) {
    if (!running) return@LaunchedEffect
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      var last = -1L
      while (true) {
        androidx.compose.runtime.withFrameNanos { now ->
          if (last < 0L) {
            last = now
          } else if ((now - last) >= MIN_STEP_MS * 1_000_000L) {
            // Clamp the step so a stall (dialog, doze) does not teleport particles
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
            clock.floatValue += dt
            last = now
          }
        }
      }
    }
  }
  return clock
}

private const val MIN_STEP_MS = 14L

/**
 * Device tilt as a value in -1..1 on both axes (x: right-down positive, y: top-toward-you
 * positive), eased so a shaky hand does not make the artwork shiver. Stays at zero when
 * [enabled] is false or the device has no gravity/accelerometer.
 */
@Composable
internal fun rememberTilt(enabled: Boolean): State<Offset> {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current
  val tilt = remember { mutableStateOf(Offset.Zero) }
  val live by rememberUpdatedState(enabled)

  DisposableEffect(enabled, lifecycle, context) {
    if (!live) {
      tilt.value = Offset.Zero
      return@DisposableEffect onDispose { }
    }
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    val sensor =
      manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var ex = 0f
    var ey = 0f
    val listener =
      object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
          // 9.81 m/s^2 along an axis means the device is fully on its side; ~5 is a hearty tilt
          val tx = (-event.values[0] / TILT_FULL_SCALE).coerceIn(-1f, 1f)
          val ty = ((event.values[1] - REST_PITCH) / TILT_FULL_SCALE).coerceIn(-1f, 1f)
          ex += (tx - ex) * EASE
          ey += (ty - ey) * EASE
          // Ignore changes below ~2% of the range: they would only cost repaints
          val cur = tilt.value
          if (abs(ex - cur.x) > 0.02f || abs(ey - cur.y) > 0.02f) tilt.value = Offset(ex, ey)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
      }

    val observer =
      androidx.lifecycle.LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_RESUME ->
            if (sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
          Lifecycle.Event.ON_PAUSE -> manager?.unregisterListener(listener)
          else -> Unit
        }
      }
    lifecycle.lifecycle.addObserver(observer)

    onDispose {
      lifecycle.lifecycle.removeObserver(observer)
      manager?.unregisterListener(listener)
    }
  }
  return tilt
}

private const val TILT_FULL_SCALE = 5f
private const val REST_PITCH = 6f // phone held upright reads ~6 m/s^2 on the y axis
private const val EASE = 0.12f

/**
 * Canvas that redraws from the clock and tilt without recomposing: both are read inside the draw
 * block, so a tick invalidates drawing only.
 */
@Composable
internal fun BackdropCanvas(
  parallax: Boolean,
  modifier: Modifier = Modifier,
  running: Boolean = true,
  onDraw: DrawScope.(t: Float, tilt: Offset, palette: BackdropPalette) -> Unit,
) {
  val clock = rememberBackdropClock(running)
  val tilt = rememberTilt(parallax)
  val palette = rememberBackdropPalette()
  Canvas(modifier = modifier.fillMaxSize()) {
    onDraw(clock.value, tilt.value, palette)
  }
}

/* ------------------------------------------------------------------ helpers ---------------- */

internal const val TAU = (2.0 * PI).toFloat()

/** Fraction part, always in 0..1 (also for negatives). */
internal fun fract(x: Float): Float = x - floor(x)

/** Cheap deterministic noise in 0..1 for (index, salt). Same inputs, same output, every launch. */
internal fun hash01(index: Int, salt: Int = 0): Float {
  var h = index * 374761393 + salt * 668265263 + 0x2545F491
  h = (h xor (h ushr 13)) * 1274126177
  h = h xor (h ushr 16)
  return (h and 0xFFFFFF) / 16777215f
}

/** Smooth 0..1..0 pulse of period 1. */
internal fun pulse(x: Float): Float = 0.5f - 0.5f * kotlin.math.cos(x * TAU)

/** Sine mapped to 0..1. */
internal fun wave01(x: Float): Float = 0.5f + 0.5f * sin(x)

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
  val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/** Draws [block] shifted by tilt: [depth] is the pixel travel at full tilt, deeper layers move less. */
internal inline fun DrawScope.parallaxLayer(
  tilt: Offset,
  depth: Float,
  block: DrawScope.() -> Unit,
) {
  translate(left = tilt.x * depth, top = tilt.y * depth) { block() }
}
