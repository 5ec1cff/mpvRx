package app.gyrolet.mpvrx.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* Weather and nature family: Snow, Rain, Sakura, Fireflies, Bubbles. */

/**
 * Unit snowflakes (radius 1, centred on the origin) built once. Three levels of detail, so near
 * flakes read as proper crystals and far ones as simple stars:
 * 0 = six plain arms, 1 = arms with one pair of side twigs, 2 = arms with two pairs of twigs, tipped
 * and with a small hexagon at the heart.
 */
private val SnowflakePaths: List<Path> by lazy {
  fun flake(level: Int): Path {
    val path = Path()
    for (arm in 0 until 6) {
      val a = arm * TAU / 6f
      val dx = cos(a)
      val dy = sin(a)
      path.moveTo(0f, 0f)
      path.lineTo(dx, dy)
      val twigs =
        when (level) {
          0 -> emptyList()
          1 -> listOf(0.55f to 0.30f)
          else -> listOf(0.40f to 0.34f, 0.68f to 0.24f)
        }
      for ((at, len) in twigs) {
        val bx = dx * at
        val by = dy * at
        for (side in intArrayOf(-1, 1)) {
          val ta = a + side * TAU / 6f
          path.moveTo(bx, by)
          path.lineTo(bx + cos(ta) * len, by + sin(ta) * len)
        }
      }
      if (level == 2) {
        // Little cross-bar near the tip
        val tipX = dx * 0.88f
        val tipY = dy * 0.88f
        path.moveTo(tipX - dy * 0.09f, tipY + dx * 0.09f)
        path.lineTo(tipX + dy * 0.09f, tipY - dx * 0.09f)
      }
    }
    if (level == 2) {
      for (v in 0..6) {
        val a = v * TAU / 6f + TAU / 12f
        val x = cos(a) * 0.16f
        val y = sin(a) * 0.16f
        if (v == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
    }
    return path
  }
  listOf(flake(0), flake(1), flake(2))
}

/**
 * Falling snow of four kinds, drawn far to near: soft out-of-focus dots, thin six-armed stars,
 * mid-size flakes with twigs, and a few large detailed crystals up front. Each drifts on its own
 * sway and turns slowly. [intensity] scales how many there are.
 */
internal fun DrawScope.drawSnow(t: Float, tilt: Offset, p: BackdropPalette, intensity: Float = 1f) {
  val w = size.width
  val h = size.height
  val ice = if (p.isDark) lerp(Color(0xFFF2F8FF), p.primary, 0.08f) else lerp(p.primary, p.ink, 0.35f)

  // kind: 0 dot, 1 star, 2 twig flake, 3 crystal
  val wind = sin(t * 0.13f) * 10.dp.toPx() + sin(t * 0.07f + 1.5f) * 6.dp.toPx()
  val counts = intArrayOf((20 * intensity).toInt(), (16 * intensity).toInt(), (13 * intensity).toInt(), (8 * intensity).toInt())
  var seed = 0
  for (kind in 0..3) {
    for (n in 0 until counts[kind]) {
      val i = seed++
      val near = kind / 3f
      val u = fract(hash01(i, 1) + t * (0.020f + 0.020f * near + 0.012f * hash01(i, 2)))
      val y = h * (u * 1.16f - 0.08f) + tilt.y * 12.dp.toPx() * (0.4f + near)
      val x =
        w * (hash01(i, 3) * 1.1f - 0.05f) +
          sin(t * (0.35f + 0.3f * hash01(i, 4)) + i * 1.7f) * (10f + 18f * near).dp.toPx() +
          u * w * 0.05f + wind * (0.4f + near) +
          tilt.x * 26.dp.toPx() * (0.3f + near)
      val fade = smoothstep(0f, 0.05f, u) * smoothstep(1f, 0.93f, u)
      val c = Offset(x, y)
      when (kind) {
        0 -> {
          val r = (4f + 8f * hash01(i, 5)).dp.toPx()
          drawCircle(
            brush = Brush.radialGradient(listOf(ice.copy(alpha = 0.45f * fade), ice.copy(alpha = 0.18f * fade), Color.Transparent), center = c, radius = r),
            radius = r,
            center = c,
          )
        }
        else -> {
          val r = when (kind) {
            1 -> (4f + 3f * hash01(i, 5)).dp.toPx()
            2 -> (7f + 5f * hash01(i, 5)).dp.toPx()
            else -> (12f + 8f * hash01(i, 5)).dp.toPx()
          }
          val level = if (kind == 1) 0 else if (kind == 2) 1 else 2
          val alpha = (if (kind == 1) 0.55f else if (kind == 2) 0.72f else 0.88f) * fade
          val spin = (if (i % 2 == 0) 1f else -1f) * t * (6f + 14f * hash01(i, 6)) + hash01(i, 7) * 360f
          val line = (if (kind == 3) 1.5f else if (kind == 2) 1.2f else 1f).dp.toPx()
          translate(left = x, top = y) {
            rotate(spin, pivot = Offset.Zero) {
              scale(r, r, pivot = Offset.Zero) {
                drawPath(SnowflakePaths[level], ice.copy(alpha = alpha), style = Stroke(width = line / r, cap = StrokeCap.Round))
              }
            }
          }
          if (kind == 3) {
            // A faint halo so the big crystals glow slightly
            drawCircle(ice.copy(alpha = 0.07f * fade), radius = r * 1.5f, center = c)
          }
        }
      }
    }
  }
}

/** Slanted rain in three layers with small elliptical ripples where it lands near the bottom. */
internal fun DrawScope.drawRain(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val tint = lerp(p.ink, p.secondary, 0.35f)
  val slant = 0.16f + 0.04f * sin(t * 0.16f) + tilt.x * 0.06f
  for (i in 0 until 72) {
    val depth = (i % 3 + 1) / 3f
    val len = (16f + 26f * depth).dp.toPx()
    val u = fract(hash01(i, 1) + t * (0.30f + 0.42f * depth))
    val y = h * (u * 1.2f - 0.1f)
    // Wrap across a padded horizontal span: diagonal motion must not empty the left side.
    val span = w + h * slant + len * 2f
    val x = fract(hash01(i, 2) + y * slant / span) * span - h * slant - len + tilt.x * 10.dp.toPx()
    val head = Offset(x, y)
    val tail = Offset(x - len * slant, y - len)
    val a = (0.08f + 0.20f * depth) * smoothstep(0f, 0.05f, u) * smoothstep(1f, 0.95f, u)
    drawLine(
      Brush.linearGradient(listOf(Color.Transparent, tint.copy(alpha = a)), start = tail, end = head),
      start = tail,
      end = head,
      strokeWidth = (0.7f + 0.8f * depth).dp.toPx(),
      cap = StrokeCap.Round,
    )
  }
  // Ripples: each slot has a fixed spot, re-rolled after every cycle
  for (i in 0 until 12) {
    val cyc = t * 0.45f + hash01(i, 7) * 3f
    val ph = fract(cyc)
    val seed = i + kotlin.math.floor(cyc).toInt() * 31
    val cx = w * hash01(seed, 8)
    val cy = h * (0.84f + 0.13f * hash01(seed, 9))
    val r = (4f + 24f * (1f - (1f - ph) * (1f - ph))).dp.toPx()
    drawOval(
      color = tint.copy(alpha = 0.26f * (1f - ph) * smoothstep(0f, 0.1f, ph)),
      topLeft = Offset(cx - r, cy - r * 0.32f),
      size = Size(r * 2f, r * 0.64f),
      style = Stroke(width = 1.dp.toPx()),
    )
  }
}

private fun Path.addPetal(s: Float) {
  moveTo(0f, -s)
  cubicTo(s * 0.95f, -s * 0.55f, s * 0.75f, s * 0.75f, 0f, s)
  cubicTo(-s * 0.75f, s * 0.75f, -s * 0.95f, -s * 0.55f, 0f, -s * 0.72f)
  close()
}

private val SakuraPetal: Path by lazy { Path().apply { addPetal(1f) } }

/** Cherry-blossom petals that flutter down, flipping edge-on as they turn. */
internal fun DrawScope.drawSakura(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val pink = lerp(Color(0xFFF6A5C0), p.tertiary, 0.2f)
  val deep = lerp(Color(0xFFE0668F), p.primary, 0.2f)
  for (i in 0 until 28) {
    val depth = 0.45f + 0.55f * hash01(i, 6)
    val u = fract(hash01(i, 1) + t * (0.017f + 0.022f * hash01(i, 2)))
    val x = w * (hash01(i, 3) * 0.9f - 0.1f) + u * w * 0.22f + (sin(u * TAU * 1.6f + i) * 26.dp.toPx() + sin(t * 0.16f) * 12.dp.toPx()) * depth + tilt.x * 24.dp.toPx() * depth
    val y = h * (u * 1.14f - 0.07f) + tilt.y * 14.dp.toPx() * depth
    val spin = (t * (0.5f + 0.7f * hash01(i, 4)) * (if (i % 2 == 0) 1f else -1f) + hash01(i, 5) * TAU) * 57.29578f
    val flip = cos(t * (0.8f + 0.5f * hash01(i, 7)) + i)
    val s = (5f + 6f * depth).dp.toPx()
    val a = 0.80f * smoothstep(0f, 0.06f, u) * smoothstep(1f, 0.94f, u)
    translate(left = x, top = y) {
      rotate(spin, pivot = Offset.Zero) {
        scale(scaleX = flip * s, scaleY = s, pivot = Offset.Zero) {
          drawPath(SakuraPetal, (if (flip >= 0f) pink else deep).copy(alpha = a))
          drawLine(deep.copy(alpha = a * 0.4f), Offset(0f, -0.5f), Offset(0f, 0.8f), strokeWidth = 0.7.dp.toPx() / s)
        }
      }
    }
  }
}

/** Fireflies wandering on looping paths, each blinking on its own beat with a soft green-gold glow. */
internal fun DrawScope.drawFireflies(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val glow = lerp(Color(0xFFD8F36A), p.secondary, 0.15f)
  for (i in 0 until 30) {
    val depth = 0.4f + 0.6f * hash01(i, 8)
    val x = w * (0.06f + 0.88f * hash01(i, 1)) + sin(t * (0.17f + 0.2f * hash01(i, 2)) + i) * 44.dp.toPx() + sin(t * (0.41f + 0.2f * hash01(i, 3)) + i * 2f) * 20.dp.toPx() + tilt.x * 24.dp.toPx() * depth
    val y = h * (0.10f + 0.80f * hash01(i, 4)) + cos(t * (0.14f + 0.2f * hash01(i, 5)) + i * 1.4f) * 40.dp.toPx() + sin(t * 0.37f + i) * 16.dp.toPx() + tilt.y * 24.dp.toPx() * depth
    val b = wave01(t * (0.55f + 0.65f * hash01(i, 6)) + i * 2.1f)
    val blink = b * b
    val warmth = lerp(glow, p.tertiary, 0.18f * hash01(i, 9))
    val c = Offset(x, y)
    val r = (10f + 12f * depth).dp.toPx()
    drawCircle(
      brush = Brush.radialGradient(listOf(warmth.copy(alpha = 0.38f * blink), warmth.copy(alpha = 0.08f * blink), Color.Transparent), center = c, radius = r),
      radius = r,
      center = c,
    )
    drawCircle(warmth.copy(alpha = 0.12f + 0.72f * blink), radius = (1.2f + 0.8f * depth).dp.toPx(), center = c)
  }
}

/** Soap bubbles: hollow, rim-lit, swelling as they rise and fading out near the top. */
internal fun DrawScope.drawBubbles(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val base = min(w, h)
  for (i in 0 until 20) {
    val depth = 0.35f + 0.65f * hash01(i, 6)
    val u = fract(hash01(i, 1) + t * (0.018f + 0.03f * hash01(i, 2)))
    val x = w * (0.04f + 0.92f * hash01(i, 3)) + sin(u * TAU * 3f + i) * 18.dp.toPx() * depth + tilt.x * 26.dp.toPx() * depth
    val y = h * (1.08f - 1.16f * u) + tilt.y * 14.dp.toPx() * depth
    val r = base * (0.022f + 0.055f * depth) * (0.85f + 0.3f * u) * (1f + 0.035f * sin(t * 0.7f + i))
    val a = smoothstep(0f, 0.08f, u) * smoothstep(1f, 0.90f, u)
    val color = p.accent(i)
    val c = Offset(x, y)
    drawCircle(
      brush =
        Brush.radialGradient(
          colorStops = arrayOf(0f to Color.Transparent, 0.7f to color.copy(alpha = 0.05f * a), 1f to color.copy(alpha = 0.22f * a)),
          center = c,
          radius = r,
        ),
      radius = r,
      center = c,
    )
    drawCircle(color.copy(alpha = 0.38f * a), radius = r, center = c, style = Stroke(width = 1.2.dp.toPx()))
    // A second, tinted reflection makes the rim read as a translucent bubble.
    drawArc(
      color = p.accent(i + 1).copy(alpha = 0.28f * a),
      startAngle = 30f + 10f * sin(t * 0.35f + i),
      sweepAngle = 76f,
      useCenter = false,
      topLeft = c - Offset(r, r),
      size = Size(r * 2f, r * 2f),
      style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
    )
    // Highlight arc, upper left
    val hr = r * 0.72f
    drawArc(
      color = p.ink.copy(alpha = 0.45f * a),
      startAngle = 200f,
      sweepAngle = 48f,
      useCenter = false,
      topLeft = Offset(x - hr, y - hr),
      size = Size(hr * 2f, hr * 2f),
      style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
    )
  }
}
