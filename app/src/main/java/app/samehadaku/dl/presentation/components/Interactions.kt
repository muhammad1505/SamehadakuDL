package app.samehadaku.dl.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * A click target that matches the Orbit Neon look: no square ripple, just a
 * small "press" scale so buttons and cards feel tactile without leaving a
 * white box on the glass surfaces.
 *
 * Apply this before background/border/clip modifiers so the whole surface
 * scales, not only the label inside it.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role? = null,
    onClickLabel: String? = null,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "press-scale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

/** Same press feedback as [pressable], with a long-press action. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressableCombined(
    enabled: Boolean = true,
    role: Role? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "press-scale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}
