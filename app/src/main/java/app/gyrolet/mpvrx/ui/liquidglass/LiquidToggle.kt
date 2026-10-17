package app.gyrolet.mpvrx.ui.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import app.gyrolet.mpvrx.ui.theme.AppMotion
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    accentColor: Color = LiquidControlColors.accent,
    enabled: Boolean = true,
    isInteractive: Boolean = true,
) {
    val glassSettings = rememberLiquidGlassSettings()
    val trackColor = LiquidControlColors.inactiveTrack
    val thumbFilm = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        accentColor,
        0.18f,
    )
    val currentSelected = rememberUpdatedState(selected)
    val currentOnSelect = rememberUpdatedState(onSelect)
    val reduceMotion = AppMotion.shouldReduceMotion()

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    val dampedDragAnimation = remember(animationScope, enabled, isLtr, dragWidth, reduceMotion) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = if (reduceMotion) 1f else 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (!enabled) return@DampedDragAnimation
                if (didDrag) {
                    val target = if (fraction >= 0.5f) 1f else 0f
                    fraction = target
                    currentOnSelect.value(target == 1f)
                    animateToValue(target)
                    didDrag = false
                } else {
                    val target = if (currentSelected.value()) 0f else 1f
                    fraction = target
                    currentOnSelect.value(target == 1f)
                    animateToValue(target)
                }
            },
            onDrag = { _, dragAmount ->
                if (!enabled) return@DampedDragAnimation
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                val newFraction =
                    if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                    else (fraction - delta).fastCoerceIn(0f, 1f)
                fraction = newFraction
                snapToValue(newFraction)
            }
        )
    }
    LaunchedEffect(Unit) {
        snapshotFlow { currentSelected.value() }
            .collectLatest { isSelected ->
                val target = if (isSelected) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    dampedDragAnimation.animateToValue(target)
                }
            }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(
        modifier.alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    val frac = dampedDragAnimation.value
                    drawRect(glassSettings.surfaceColor(lerp(trackColor, accentColor, frac)))
                }
                .size(64f.dp, 28f.dp)
        )

        val toggleShadow = remember {
            Shadow(
                radius = 4f.dp,
                color = Color.Black.copy(alpha = 0.05f)
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    val frac = dampedDragAnimation.value
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, frac)
                        else lerp(-padding, -(padding + dragWidth), frac)
                }
                .semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(selected())
                }
                .then(if (isInteractive && enabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        liquidGlassEffects(
                            glassSettings,
                            8f.dp.toPx() * (1f - progress),
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        glassSettings.highlight(Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        ))
                    },
                    shadow = { toggleShadow },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        // Off = white; on = the existing themed film, with a smooth transition.
                        val film = lerp(
                            Color.White,
                            thumbFilm.copy(alpha = 0.72f - 0.28f * progress),
                            dampedDragAnimation.value.coerceIn(0f, 1f),
                        )
                        drawRect(glassSettings.surfaceColor(film))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
