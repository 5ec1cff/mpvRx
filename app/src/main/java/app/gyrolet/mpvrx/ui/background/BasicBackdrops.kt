package app.gyrolet.mpvrx.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/* Geometric, calm backdrops: Circles, Rings, Shapes, Grid, Lava, Bokeh. */

/**
 * Translucent discs that each circle a private anchor point, the near ones large and quick to
 * react to tilt, the far ones small and nearly still. Every disc has a faint fill and a thin rim.
 */
internal fun DrawScope.drawCircles(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val base = min(w, h)
  val travel = 22.dp.toPx()
  for (i in 0 until 10) {
    val depth = 0.35f + 0.65f * hash01(i, 1)
    val r = base * (0.09f + 0.20f * depth)
    val ax = w * (0.08f + 0.84f * hash01(i, 2))
    val ay = h * (0.06f + 0.88f * hash01(i, 3))
    val dir = if (i % 2 == 0) 1f else -1f
    val ang = hash01(i, 5) * TAU + t * dir * (0.05f + 0.07f * hash01(i, 4)) * TAU * 0.25f
    val orbit = base * (0.04f + 0.09f * hash01(i, 6))
    val c =
      Offset(
        ax + cos(ang) * orbit + tilt.x * travel * depth,
        ay + sin(ang) * orbit * 1.35f + tilt.y * travel * depth,
      )
    val color = p.accent(i)
    drawCircle(color.copy(alpha = 0.05f + 0.07f * depth), radius = r, center = c)
    drawCircle(color.copy(alpha = 0.16f + 0.16f * depth), radius = r, center = c, style = Stroke(width = 1.4.dp.toPx()))
  }
}

/** Ripples spreading out of three drifting sources, each emitting a train of fading rings. */
internal fun DrawScope.drawRings(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val reach = hypot(w, h) * 0.45f
  val travel = 16.dp.toPx()
  for (s in 0 until 3) {
    val cx = w * (0.18f + 0.64f * hash01(s, 10)) + sin(t * 0.07f * TAU * 0.5f + s * 2f) * 24.dp.toPx() + tilt.x * travel * (s + 1) * 0.5f
    val cy = h * (0.14f + 0.72f * hash01(s, 11)) + cos(t * 0.05f * TAU * 0.5f + s) * 24.dp.toPx() + tilt.y * travel * (s + 1) * 0.5f
    val color = p.accent(s)
    for (k in 0 until 5) {
      val ph = fract(t / 11f + k / 5f + hash01(s, 12))
      val fade = (1f - ph)
      val alpha = fade * fade * 0.38f * smoothstep(0f, 0.08f, ph)
      drawCircle(
        color = color.copy(alpha = alpha),
        radius = ph * reach,
        center = Offset(cx, cy),
        style = Stroke(width = (1f + 2.2f * fade).dp.toPx()),
      )
    }
    drawCircle(color.copy(alpha = 0.35f), radius = 3.dp.toPx(), center = Offset(cx, cy))
  }
}

/** Outlined polygons (3 to 6 sides) that spin as they rise, fading in at the bottom and out at the top. */
internal fun DrawScope.drawShapes(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val base = min(w, h)
  for (i in 0 until 16) {
    val depth = 0.3f + 0.7f * hash01(i, 1)
    val sides = 3 + (i % 4)
    val radius = base * (0.035f + 0.06f * depth)
    val life = fract(hash01(i, 2) + t * (0.012f + 0.02f * depth))
    val cx = w * hash01(i, 3) + sin(t * 0.25f + i * 1.7f) * 18.dp.toPx() + tilt.x * 28.dp.toPx() * depth
    val cy = h * (1.1f - 1.2f * life) + tilt.y * 28.dp.toPx() * depth
    val spin = (if (i % 2 == 0) 1f else -1f) * t * (0.12f + 0.25f * hash01(i, 4)) + hash01(i, 5) * TAU
    val edge = smoothstep(0f, 0.12f, life) * smoothstep(1f, 0.88f, life)
    val path = Path()
    for (v in 0 until sides) {
      val a = spin + v * TAU / sides
      val x = cx + cos(a) * radius
      val y = cy + sin(a) * radius
      if (v == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    val color = p.accent(i)
    drawPath(path, color.copy(alpha = 0.07f * edge))
    drawPath(path, color.copy(alpha = (0.18f + 0.22f * depth) * edge), style = Stroke(width = 1.3.dp.toPx()))
  }
}

/** A lattice of dots lit by two slow pulse waves, so the pattern swells and relaxes in rings. */
internal fun DrawScope.drawGrid(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val gap = 26.dp.toPx()
  val cols = (w / gap).toInt() + 2
  val rows = (h / gap).toInt() + 2
  val ox = tilt.x * 10.dp.toPx() - gap * 0.5f
  val oy = tilt.y * 10.dp.toPx() - gap * 0.5f
  val c1 = Offset(w * (0.5f + 0.35f * sin(t * 0.11f)), h * (0.35f + 0.2f * cos(t * 0.09f)))
  val c2 = Offset(w * (0.5f + 0.4f * cos(t * 0.07f + 2f)), h * (0.7f + 0.15f * sin(t * 0.13f)))
  val wavelength = gap * 9f
  for (r in 0 until rows) {
    for (c in 0 until cols) {
      val x = c * gap + ox
      val y = r * gap + oy
      val d1 = hypot(x - c1.x, y - c1.y)
      val d2 = hypot(x - c2.x, y - c2.y)
      val lit1 = wave01((d1 / wavelength) * TAU - t * 0.9f)
      val lit2 = wave01((d2 / wavelength) * TAU - t * 0.7f + 1.3f)
      val k = (lit1 * lit2)
      val k3 = k * k * k
      drawCircle(
        color = (if (lit1 > lit2) p.primary else p.tertiary).copy(alpha = 0.08f + 0.5f * k3),
        radius = 1.1.dp.toPx() + 1.9.dp.toPx() * k3,
        center = Offset(x, y),
      )
    }
  }
}

/** Soft wax blobs that rise, sink and stretch; where two overlap their glow adds up. */
internal fun DrawScope.drawLava(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val base = min(w, h)
  for (i in 0 until 7) {
    val depth = 0.5f + 0.5f * hash01(i, 1)
    val cx = w * (0.18f + 0.64f * hash01(i, 2) + 0.10f * sin(t * (0.10f + 0.08f * hash01(i, 3)) + i)) + tilt.x * 18.dp.toPx() * depth
    val cy = h * (0.5f + 0.42f * sin(t * (0.06f + 0.05f * hash01(i, 4)) + hash01(i, 5) * TAU)) + tilt.y * 18.dp.toPx() * depth
    val r = base * (0.15f + 0.14f * hash01(i, 6)) * (1f + 0.10f * sin(t * 0.55f + i * 1.3f))
    val stretch = 1f + 0.22f * cos(t * 0.31f + i * 0.9f)
    val color = p.accent(i)
    val center = Offset(cx, cy)
    scale(scaleX = 1f / stretch, scaleY = stretch, pivot = center) {
      drawCircle(
        brush =
          Brush.radialGradient(
            colorStops =
              arrayOf(
                0f to color.copy(alpha = 0.50f),
                0.55f to color.copy(alpha = 0.26f),
                1f to Color.Transparent,
              ),
            center = center,
            radius = r,
          ),
        radius = r,
        center = center,
      )
    }
  }
}

/** Large out-of-focus lights drifting sideways: dim centre, brighter rim, like a lens full of bulbs. */
internal fun DrawScope.drawBokeh(t: Float, tilt: Offset, p: BackdropPalette) {
  val w = size.width
  val h = size.height
  val base = min(w, h)
  for (i in 0 until 16) {
    val depth = hash01(i, 1)
    val r = base * (0.05f + 0.12f * depth) * (0.94f + 0.06f * sin(t * 0.6f + i))
    val f = fract(hash01(i, 2) + t * (0.004f + 0.006f * depth))
    val x = w * (f * 1.3f - 0.15f) + tilt.x * 30.dp.toPx() * (0.3f + depth)
    val y = h * (0.08f + 0.84f * hash01(i, 3)) + sin(t * (0.18f + 0.1f * hash01(i, 4)) + i) * 22.dp.toPx() + tilt.y * 30.dp.toPx() * (0.3f + depth)
    val edge = smoothstep(0f, 0.10f, f) * smoothstep(1f, 0.90f, f)
    val color = p.accent(i + 1)
    val c = Offset(x, y)
    drawCircle(
      brush =
        Brush.radialGradient(
          colorStops =
            arrayOf(
              0f to color.copy(alpha = 0.02f * edge),
              0.72f to color.copy(alpha = 0.07f * edge),
              1f to color.copy(alpha = (0.16f + 0.12f * depth) * edge),
            ),
          center = c,
          radius = r,
        ),
      radius = r,
      center = c,
    )
    drawCircle(color.copy(alpha = 0.22f * edge), radius = r, center = c, style = Stroke(width = (1f + 1.2f * depth).dp.toPx()))
  }
}
