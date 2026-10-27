package app.gyrolet.mpvrx.ui.browser

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import app.gyrolet.mpvrx.ui.liquidglass.liquidGlassEffects
import app.gyrolet.mpvrx.ui.liquidglass.rememberLiquidGlassSettings
import app.gyrolet.mpvrx.ui.player.LocalInAppMusicSheet
import app.gyrolet.mpvrx.ui.player.PlayerSheetTransitions
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun NavigationPlayerConnection(
  hazeBackdrop: HazeState?,
  glassBackdrop: Backdrop?,
  liquidGlassEnabled: Boolean,
  surfaceColor: Color,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier,
) {
  var navigationBounds by remember { mutableStateOf(Rect.Zero) }
  var lastBridge by remember { mutableStateOf<NavigationPlayerBridge?>(null) }
  val density = LocalDensity.current
  val miniBounds = NavigationBarState.miniPlayerBounds
  val currentBridge = remember(navigationBounds, miniBounds, density) {
    with(density) { navigationPlayerBridge(navigationBounds, miniBounds, 64.dp.toPx(), 1.dp.toPx()) }
  }
  SideEffect { if (currentBridge != null) lastBridge = currentBridge }
  val musicSheet = LocalInAppMusicSheet.current
  val connected = currentBridge != null && NavigationBarState.miniPlayerConnectionEnabled &&
    NavigationBarState.isNavBarVisible && !NavigationBarState.isInSelectionMode &&
    musicSheet?.isVisible != true && PlayerSheetTransitions.motion == null &&
    PlayerSheetTransitions.drag == null && PlayerSheetTransitions.expandingMediaId == null
  val progress = animateFloatAsState(
    targetValue = if (connected) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else tween(240, easing = FastOutSlowInEasing),
    label = "navigation_player_connection",
  )
  val settings = rememberLiquidGlassSettings()
  val blurStyle = remember(surfaceColor) {
    HazeBlurStyle {
      blurRadius(24.dp)
      backgroundColor(surfaceColor)
      colorEffects(listOf(HazeColorEffect.tint(Color.Transparent)))
      noiseFactor(0f)
    }
  }
  Box(modifier.onGloballyPositioned { navigationBounds = it.boundsInRoot() }) {
    val bridge = currentBridge ?: lastBridge ?: return@Box
    if (progress.value <= 0f) return@Box
    val shape = remember(bridge.horizontal) { NavigationPlayerBridgeShape(bridge.horizontal) }
    val useGlass = liquidGlassEnabled && glassBackdrop != null
    val surface = if (useGlass) {
      Modifier.drawBackdrop(
        backdrop = checkNotNull(glassBackdrop),
        shape = { shape },
        effects = { liquidGlassEffects(settings, 8.dp.toPx(), 18.dp.toPx(), 22.dp.toPx(), refractionEnabled = !reducedMotion) },
        highlight = null,
        shadow = null,
        onDrawSurface = { drawRect(settings.surfaceColor(surfaceColor.copy(alpha = 0.32f))) },
      )
    } else {
      val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeBackdrop != null
      val navTint = surfaceColor.copy(alpha = if (canBlur) 0.55f else 1f)
      val miniTint = surfaceColor.copy(alpha = if (canBlur) 0.96f else 1f)
      val colors = if (bridge.miniAtStart) listOf(miniTint, navTint) else listOf(navTint, miniTint)
      val tint = if (bridge.horizontal) Brush.horizontalGradient(colors) else Brush.verticalGradient(colors)
      (if (canBlur) Modifier.hazeBlur(input = HazeInput.Sources(checkNotNull(hazeBackdrop)), style = blurStyle) else Modifier)
        .background(tint)
    }
    Box(
      Modifier
        .layout { measurable, constraints ->
          val bounds = bridge.bounds
          val placeable = measurable.measure(
            Constraints.fixed(bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1)),
          )
          layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(bounds.left.roundToInt(), bounds.top.roundToInt())
          }
        }
        .graphicsLayer {
          alpha = progress.value
          scaleX = if (bridge.horizontal) 1f else progress.value
          scaleY = if (bridge.horizontal) progress.value else 1f
          this.shape = shape
          clip = true
        }
        .then(surface),
    )
  }
}

private data class NavigationPlayerBridge(val bounds: Rect, val horizontal: Boolean, val miniAtStart: Boolean)

private fun navigationPlayerBridge(navigation: Rect, mini: Rect, maxGap: Float, inset: Float): NavigationPlayerBridge? {
  if (navigation.isEmpty || mini.isEmpty) return null
  val overlapLeft = maxOf(navigation.left, mini.left)
  val overlapRight = minOf(navigation.right, mini.right)
  val verticalGap = navigation.top - mini.bottom
  if (verticalGap in 0f..maxGap && overlapRight - overlapLeft >= maxGap) {
    val halfWidth = min((overlapRight - overlapLeft) * 0.25f, maxGap * 0.875f)
    val center = ((navigation.center.x + mini.center.x) / 2f).coerceIn(overlapLeft + halfWidth, overlapRight - halfWidth)
    return NavigationPlayerBridge(
      Rect(center - halfWidth, mini.bottom - inset, center + halfWidth, navigation.top + inset)
        .translate(-navigation.topLeft),
      horizontal = false,
      miniAtStart = true,
    )
  }
  val overlapTop = maxOf(navigation.top, mini.top)
  val overlapBottom = minOf(navigation.bottom, mini.bottom)
  if (overlapBottom <= overlapTop) return null
  val miniOnRight = mini.left >= navigation.right
  val gap = if (miniOnRight) mini.left - navigation.right else navigation.left - mini.right
  if (gap !in 0f..maxGap) return null
  val halfHeight = min((overlapBottom - overlapTop) * 0.3f, maxGap * 0.3125f)
  val center = (overlapTop + overlapBottom) / 2f
  val shoulderInset = inset * 8f
  return NavigationPlayerBridge(
    Rect(
      if (miniOnRight) navigation.right - shoulderInset else mini.right - shoulderInset,
      center - halfHeight,
      if (miniOnRight) mini.left + shoulderInset else navigation.left + shoulderInset,
      center + halfHeight,
    ).translate(-navigation.topLeft),
    horizontal = true,
    miniAtStart = !miniOnRight,
  )
}

private class NavigationPlayerBridgeShape(private val horizontal: Boolean) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    fun point(across: Float, along: Float): Offset =
      if (horizontal) Offset(along * size.width, across * size.height) else Offset(across * size.width, along * size.height)
    val path = Path()
    fun curve(first: Offset, second: Offset, end: Offset) {
      path.cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
    }
    path.moveTo(0f, 0f)
    val topEnd = point(1f, 0f)
    path.lineTo(topEnd.x, topEnd.y)
    curve(point(0.68f, 0f), point(0.68f, 0.15f), point(0.68f, 0.5f))
    curve(point(0.68f, 0.85f), point(0.68f, 1f), point(1f, 1f))
    val bottomStart = point(0f, 1f)
    path.lineTo(bottomStart.x, bottomStart.y)
    curve(point(0.32f, 1f), point(0.32f, 0.85f), point(0.32f, 0.5f))
    curve(point(0.32f, 0.15f), point(0.32f, 0f), Offset.Zero)
    path.close()
    return Outline.Generic(path)
  }
}

@Composable
internal fun NavigationGlassSurface(
  backdrop: HazeState?,
  surfaceColor: Color,
  glowStrength: Float,
  modifier: Modifier = Modifier,
) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
    Box(
      modifier
        .background(surfaceColor.copy(alpha = 1f))
        .navigationGlassRim(glowStrength),
    )
    return
  }

  val blurStyle = remember(surfaceColor) {
    HazeBlurStyle {
      blurRadius(24.dp)
      backgroundColor(surfaceColor)
      colorEffects(listOf(HazeColorEffect.tint(Color.Transparent)))
      noiseFactor(0f)
    }
  }
  val backing = if (backdrop != null) Modifier.hazeBlur(input = HazeInput.Sources(backdrop), style = blurStyle) else Modifier
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backdrop != null && glowStrength > 0f) {
    RefractedNavigationGlass(surfaceColor, glowStrength, modifier, backing)
  } else {
    Box(modifier.then(backing).background(surfaceColor.copy(alpha = if (backdrop != null) 0.55f else 0.82f))
      .navigationGlassRim(glowStrength))
  }
}

internal fun Modifier.navigationGlassRim(strength: Float = 1f): Modifier = drawWithCache {
  val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.27f), Color.White.copy(alpha = 0.02f)))
  val strokeWidth = 0.75.dp.toPx()
  onDrawWithContent {
    drawContent()
    drawRoundRect(
      brush = edge,
      topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
      size = Size((size.width - strokeWidth).coerceAtLeast(0f), (size.height - strokeWidth).coerceAtLeast(0f)),
      cornerRadius = CornerRadius((size.height - strokeWidth).coerceAtLeast(0f) / 2),
      style = Stroke(strokeWidth),
      alpha = strength,
    )
  }
}

internal fun Modifier.navigationAccentMask(brush: Brush): Modifier =
  graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
    onDrawWithContent {
      drawContent()
      drawRect(brush = brush, blendMode = BlendMode.SrcIn)
    }
  }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun RefractedNavigationGlass(surfaceColor: Color, glowStrength: Float, modifier: Modifier, backing: Modifier) {
  val shader = remember { RuntimeShader(NavigationGlassShader) }
  Box(modifier
    .layout { measurable, constraints ->
      val outset = 24.dp.roundToPx()
      val placeable = measurable.measure(constraints.offset(outset * 2, outset * 2))
      layout(placeable.width - outset * 2, placeable.height - outset * 2) {
        placeable.place(-outset, -outset)
      }
    }
    .graphicsLayer {
      shader.setFloatUniform("resolution", size.width, size.height)
      shader.setFloatUniform("density", density)
      shader.setFloatUniform("outset", 24.dp.roundToPx().toFloat())
      shader.setFloatUniform("glowStrength", glowStrength)
      shader.setFloatUniform("surfaceTint", surfaceColor.red, surfaceColor.green, surfaceColor.blue)
      renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop").asComposeRenderEffect()
    }
    .then(backing))
}

private const val NavigationGlassShader = """
uniform shader backdrop;
uniform float2 resolution;
uniform float density;
uniform float outset;
uniform float glowStrength;
uniform float3 surfaceTint;

half3 sampleLight(float2 position, float2 tangent) {
    float2 spread = tangent * density * 10.0;
    return backdrop.eval(position).rgb * 0.5
        + backdrop.eval(position - spread).rgb * 0.25
        + backdrop.eval(position + spread).rgb * 0.25;
}

half4 main(float2 position) {
    float2 halfSize = resolution * 0.5 - outset;
    float radius = halfSize.y;
    float2 local = position - resolution * 0.5;
    float2 capsule = float2(max(abs(local.x) - halfSize.x + radius, 0.0), local.y);
    float distanceToCenter = length(capsule);
    float distanceToEdge = distanceToCenter - radius;
    float coverage = 1.0 - smoothstep(-0.5, 0.5, distanceToEdge);
    if (coverage <= 0.0) return half4(0.0);

    float2 normal = float2(capsule.x * sign(local.x), capsule.y) / max(distanceToCenter, 0.001);
    float2 tangent = float2(-normal.y, normal.x);
    float depth = max(-distanceToEdge, 0.0) / density;
    half3 surface = mix(backdrop.eval(position).rgb, surfaceTint, 0.55);
    if (depth >= 16.0 || glowStrength <= 0.0) return half4(surface * coverage, coverage);

    float rim = exp(-0.0565 * depth - 0.0322 * depth * depth);
    float upperLight = 0.18 + 0.82 * pow(max(-normal.y, 0.0), 0.65);
    float bend = pow(rim, 0.18);
    half3 redLight = sampleLight(position - normal * density * 16.0 * bend, tangent);
    half3 greenLight = sampleLight(position - normal * density * 62.0 * bend, tangent);
    half3 blueLight = sampleLight(position - normal * density * 57.0 * bend, tangent);
    half3 refracted = half3(redLight.r, greenLight.g, blueLight.b);
    half luminance = dot(refracted, half3(0.2126, 0.7152, 0.0722));
    refracted = clamp(mix(half3(luminance), refracted, 1.25), 0.0, 1.0);

    half3 sheen = half3(0.1735, 0.0529, 0.0184) + refracted * half3(0.0953, 0.3152, 0.3822);
    half3 color = surface + sheen * rim * upperLight * glowStrength;
    float highlight = exp(-pow((depth - 0.35) / 0.42, 2.0));
    float highlightLight = 0.12 + 0.88 * sqrt(max((1.0 - normal.y) * 0.5, 0.0));
    color += half3(0.25) * highlight * highlightLight * glowStrength;
    return half4(clamp(color, 0.0, 1.0) * coverage, coverage);
}
"""