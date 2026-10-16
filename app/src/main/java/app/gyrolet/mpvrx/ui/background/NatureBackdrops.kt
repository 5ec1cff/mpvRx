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

/* Weather and nature family: Snow, Rain, Waves, Sakura, Fireflies, Bubbles. */

/** Three depth layers of falling snow; the nearest, biggest flakes are drawn as six-armed crystals. */
internal fun DrawScope.drawSnow(t: Float, tilt: Offset, p: BackdropPalette, intensity: Float = 1f) {
  val w = size.width
  val h = size.height
  val tint = lerp(p.ink, p.primary, 0.15f)
  val count = (72 * intensity).toInt()
  for (i in 0 until count) {
    val layer = i % 3
    val depth = (layer + 1) / 3f
    val u = fract(hash01(i, 1) + t * (0.028f + 0.05f * depth))
    val y = h * (u * 1.1f - 0.05f) + tilt.y * 14.dp.toPx() * depth
    val x = w * hash01(i, 2) + sin(t * 0.55f + i * 1.3f) * 16.dp.toPx() * depth + u * w * 0.06f + tilt.x * 26.dp.toPx() * depth
    val r = (0.8f + 2.3f * depth).dp.toPx()
    val a = (0.28f + 0.50f * depth) * smoothstep(0f, 0.05f, u) * smoothstep(1f, 0.95f, u)
    val c = Offset(x, y)
    if (layer == 2 && hash01(i, 3) > 0.45f) {
      // Crystal: three crossing strokes, spinning slowly
      val arm = r * 3.2f
      val spin = t * 0.45f * (if (i % 2 == 0) 1f else -1f) + i
      val stroke = (0.9f + 0.4f * depth).dp.toPx()
      for (k in 0 until 3) {
        val ang = spin + k * TAU / 6f
        val dx = cos(ang) * arm
        val dy = sin(ang) * arm
        drawLine(tint.copy(alpha = a), Offset(x - dx, y - dy), Offset(x + dx, y + dy), strokeWidth = stroke, cap = StrokeCap.Round)
      }
      drawCircle(tint.copy(alpha = a * 0.5f), radius = r * 0.55f, center = c)
    } else {
      drawCircle(tint.copy(alpha = a * 0.22f), radius = r * 2.6f, center = c)
      drawCircle(tint.copy(alpha = a), radius = r, center = c)
    }
  }
}

/** Slanted rain in three layers with small elliptical ripples where it lands near the bottom. */
internal fun DrawScope.drawRain(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val tint = lerp(p.ink, p.secondary, 0.35f)
  val slant = 0.22f + tilt.x * 0.08f
  for (i in 0 until 90) {
    val depth = (i % 3 + 1) / 3f
    val len = (16f + 26f * depth).dp.toPx()
    val u = fract(hash01(i, 1) + t * (0.55f + 0.75f * depth))
    val y = h * (u * 1.2f - 0.1f)
    val x = w * (hash01(i, 2) * 1.3f - 0.1f) + (y * slant)
    val a = 0.10f + 0.22f * depth
    drawLine(
      tint.copy(alpha = a),
      start = Offset(x - len * slant, y - len),
      end = Offset(x, y),
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
    val r = (4f + 20f * ph).dp.toPx()
    drawOval(
      color = tint.copy(alpha = 0.26f * (1f - ph) * smoothstep(0f, 0.1f, ph)),
      topLeft = Offset(cx - r, cy - r * 0.32f),
      size = Size(r * 2f, r * 0.64f),
      style = Stroke(width = 1.dp.toPx()),
    )
  }
}

/** Four layers of rolling water along the bottom, each its own pitch and speed, topped by a bright crest. */
internal fun DrawScope.drawWaves(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val step = 12.dp.toPx()
  val n = (w / step).toInt() + 2
  for (layer in 0 until 4) {
    val k = layer / 3f
    val baseY = h * (0.60f + 0.10f * layer) + tilt.y * 10.dp.toPx() * (1f - k * 0.5f)
    val amp = h * (0.020f + 0.006f * layer)
    val f1 = TAU / (w * (0.9f - 0.12f * layer))
    val f2 = TAU / (w * (0.45f - 0.05f * layer))
    val dir = if (layer % 2 == 0) 1f else -1f
    val ph1 = t * (0.35f + 0.10f * layer) * dir + tilt.x * 1.5f + layer * 1.7f
    val ph2 = t * (0.55f + 0.08f * layer) * -dir + layer * 0.9f
    val top = Path()
    val fill = Path()
    fill.moveTo(0f, h)
    for (s in 0..n) {
      val x = s * step
      val y = baseY + amp * sin(x * f1 + ph1) + amp * 0.45f * sin(x * f2 + ph2)
      if (s == 0) top.moveTo(x, y) else top.lineTo(x, y)
      fill.lineTo(x, y)
    }
    fill.lineTo(n * step, h)
    fill.close()
    val color = p.accent(layer)
    drawPath(
      fill,
      Brush.verticalGradient(
        colors = listOf(color.copy(alpha = 0.16f + 0.02f * layer), color.copy(alpha = 0.02f)),
        startY = baseY - amp * 2f,
        endY = h,
      ),
    )
    drawPath(top, color.copy(alpha = 0.34f - 0.04f * layer), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
  }
}

private fun Path.addPetal(s: Float) {
  moveTo(0f, -s)
  cubicTo(s * 0.95f, -s * 0.55f, s * 0.75f, s * 0.75f, 0f, s)
  cubicTo(-s * 0.75f, s * 0.75f, -s * 0.95f, -s * 0.55f, 0f, -s * 0.72f)
  close()
}

/** Cherry-blossom petals that flutter down, flipping edge-on as they turn. */
internal fun DrawScope.drawSakura(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val pink = lerp(Color(0xFFF6A5C0), p.tertiary, 0.2f)
  val deep = lerp(Color(0xFFE0668F), p.primary, 0.2f)
  for (i in 0 until 28) {
    val depth = 0.45f + 0.55f * hash01(i, 6)
    val u = fract(hash01(i, 1) + t * (0.017f + 0.022f * hash01(i, 2)))
    val x = w * (hash01(i, 3) * 0.9f - 0.1f) + u * w * 0.22f + sin(u * TAU * 2.2f + i) * 32.dp.toPx() * depth + tilt.x * 24.dp.toPx() * depth
    val y = h * (u * 1.14f - 0.07f) + tilt.y * 14.dp.toPx() * depth
    val spin = (t * (0.5f + 0.7f * hash01(i, 4)) * (if (i % 2 == 0) 1f else -1f) + hash01(i, 5) * TAU) * 57.29578f
    val flip = cos(t * (0.8f + 0.5f * hash01(i, 7)) + i)
    val s = (5f + 6f * depth).dp.toPx()
    val a = 0.80f * smoothstep(0f, 0.06f, u) * smoothstep(1f, 0.94f, u)
    val petal = Path().apply { addPetal(s) }
    translate(left = x, top = y) {
      rotate(spin, pivot = Offset.Zero) {
        scale(scaleX = flip, scaleY = 1f, pivot = Offset.Zero) {
          drawPath(petal, pink.copy(alpha = a))
          drawLine(deep.copy(alpha = a * 0.5f), Offset(0f, -s * 0.5f), Offset(0f, s * 0.8f), strokeWidth = 0.8.dp.toPx())
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
    val b = wave01(t * (0.9f + 0.9f * hash01(i, 6)) + i * 2.1f)
    val blink = b * b * b
    val c = Offset(x, y)
    val r = (10f + 12f * depth).dp.toPx()
    drawCircle(
      brush = Brush.radialGradient(listOf(glow.copy(alpha = 0.55f * blink), Color.Transparent), center = c, radius = r),
      radius = r,
      center = c,
    )
    drawCircle(glow.copy(alpha = 0.25f + 0.7f * blink), radius = (1.2f + 0.8f * depth).dp.toPx(), center = c)
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
    val r = base * (0.018f + 0.05f * depth) * (0.85f + 0.3f * u)
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
