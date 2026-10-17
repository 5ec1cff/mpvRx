package app.gyrolet.mpvrx.ui.background

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * The Aurora backdrop: the Celestial ribbons exactly as they are in the Celestial effects, so the
 * picker and the Celestial toggle show the same artwork.
 */
@Composable
internal fun AuroraBackdrop() {
  val cs = MaterialTheme.colorScheme
  val transition = rememberInfiniteTransition(label = "celestial_folder_bg")
  val drift by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec =
      infiniteRepeatable(
        animation = tween(durationMillis = 12000, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse,
      ),
    label = "celestial_folder_drift",
  )
  val glowPulse by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec =
      infiniteRepeatable(
        animation = tween(durationMillis = 6000, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse,
      ),
    label = "celestial_folder_glow",
  )
  val twinkle by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec =
      infiniteRepeatable(
        animation = tween(durationMillis = 2600),
        repeatMode = RepeatMode.Reverse,
      ),
    label = "celestial_folder_twinkle",
  )
  val primary = cs.primary
  val secondary = cs.secondary
  val tertiary = cs.tertiary
  val sparklePositions =
    remember {
      listOf(
        0.72f to 0.30f, 0.85f to 0.48f, 0.60f to 0.58f, 0.90f to 0.20f, 0.50f to 0.42f,
        0.20f to 0.22f, 0.32f to 0.62f, 0.12f to 0.45f, 0.78f to 0.65f, 0.42f to 0.15f,
      )
    }

  Canvas(modifier = Modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    val shift = h * 0.05f * (drift - 0.5f)

    // Soft ambient glow, like a distant light source easing the pure-black canvas
    // into a richer, more premium depth without ever reading as "flat blue".
    val glowRadius = w * (0.75f + 0.10f * glowPulse)
    drawRect(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              primary.copy(alpha = 0.10f),
              tertiary.copy(alpha = 0.05f),
              Color.Transparent,
            ),
          center = Offset(w * 0.82f, h * 0.06f),
          radius = glowRadius,
        ),
      size = size,
    )
    drawRect(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              secondary.copy(alpha = 0.06f),
              Color.Transparent,
            ),
          center = Offset(w * 0.10f, h * 0.85f),
          radius = w * 0.7f,
        ),
      size = size,
    )

    fun ribbon(
      color: Color,
      yStart: Float,
      yEnd: Float,
      alpha: Float,
      strokeWidth: Float,
      glow: Boolean = false,
    ) {
      val path =
        Path().apply {
          moveTo(-w * 0.1f, h * yStart + shift)
          cubicTo(
            w * 0.35f,
            h * (yStart - 0.10f) + shift,
            w * 0.65f,
            h * (yEnd + 0.10f) - shift,
            w * 1.1f,
            h * yEnd - shift,
          )
        }
      val gradientBrush =
        Brush.linearGradient(
          colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
          start = Offset(0f, h * yStart),
          end = Offset(w, h * yEnd),
        )
      // Wide, low-alpha underlay first for a soft glow halo, then a crisp core stroke.
      if (glow) {
        drawPath(
          path = path,
          brush = gradientBrush,
          style = Stroke(width = strokeWidth * 3.2f, cap = StrokeCap.Round),
          alpha = 0.35f,
        )
      }
      drawPath(
        path = path,
        brush = gradientBrush,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
      )
    }

    ribbon(primary, yStart = 0.30f, yEnd = 0.50f, alpha = 0.34f, strokeWidth = 5.dp.toPx(), glow = true)
    ribbon(tertiary, yStart = 0.38f, yEnd = 0.58f, alpha = 0.22f, strokeWidth = 9.dp.toPx(), glow = true)
    ribbon(secondary, yStart = 0.24f, yEnd = 0.40f, alpha = 0.16f, strokeWidth = 3.dp.toPx())
    ribbon(primary, yStart = 0.55f, yEnd = 0.72f, alpha = 0.10f, strokeWidth = 6.dp.toPx())

    sparklePositions.forEachIndexed { index, (fx, fy) ->
      val phase = (twinkle + index * 0.19f) % 1f
      val twinkleAlpha = 0.12f + 0.42f * sin(phase * Math.PI).toFloat()
      drawCircle(
        color = Color.White.copy(alpha = twinkleAlpha.coerceIn(0f, 1f)),
        radius = 1.4.dp.toPx(),
        center = Offset(w * fx, h * fy),
      )
    }
  }
}
