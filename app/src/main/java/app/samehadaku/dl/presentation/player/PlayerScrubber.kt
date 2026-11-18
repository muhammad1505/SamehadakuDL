package app.samehadaku.dl.presentation.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Thin seek bar drawn for the video player.
 *
 * Material's Slider leaves a thick track and a thumb that touches the ends, which
 * looks heavy in a translucent player bar. This draws its own: a slim rounded
 * track, the buffered part slightly brighter, the watched part in the accent
 * colour and a small thumb that grows while dragging, with the whole control kept
 * tall enough to touch.
 *
 * All values are milliseconds. [onScrub] fires continuously while dragging and
 * [onScrubFinished] once when the finger is lifted or the track is tapped.
 */
@Composable
internal fun PlayerScrubber(
    value: Float,
    duration: Float,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier,
    buffered: Float = 0f,
    enabled: Boolean = true,
    trackColor: Color = Color.White.copy(alpha = 0.26f),
    bufferedColor: Color = Color.White.copy(alpha = 0.42f),
    activeColor: Color = Color.White,
    thumbColor: Color = Color.White,
    trackHeight: Dp = 3.dp,
    thumbRadius: Dp = 5.dp,
    touchHeight: Dp = 34.dp,
    contentDescription: String = "Posisi pemutaran",
    stateDescription: String? = null,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { thumbRadius.toPx() }
    val gapPx = with(density) { 2.dp.toPx() }
    val fraction = if (duration > 0f) (value / duration).coerceIn(0f, 1f) else 0f
    var dragging by remember { mutableStateOf(false) }
    val thumb by animateDpAsState(
        targetValue = if (dragging) thumbRadius * 1.9f else thumbRadius,
        label = "scrubberThumb",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(touchHeight)
            // The width is read again on every event: rotating or resizing keeps
            // the same pointerInput key, so a span captured at start would map
            // touches to the old geometry.
            .pointerInput(enabled, duration, radiusPx) {
                if (!enabled || duration <= 0f) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onScrub(positionAt(offset.x, size.width.toFloat(), radiusPx, duration))
                    },
                    onDragEnd = {
                        dragging = false
                        onScrubFinished()
                    },
                    onDragCancel = {
                        dragging = false
                        onScrubFinished()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onScrub(positionAt(change.position.x, size.width.toFloat(), radiusPx, duration))
                    },
                )
            }
            .pointerInput(enabled, duration, radiusPx) {
                if (!enabled || duration <= 0f) return@pointerInput
                detectTapGestures { offset ->
                    onScrub(positionAt(offset.x, size.width.toFloat(), radiusPx, duration))
                    onScrubFinished()
                }
            }
            .semantics {
                this.contentDescription = contentDescription
                if (duration > 0f) {
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { target ->
                        onScrub(target.coerceIn(0f, 1f) * duration)
                        onScrubFinished()
                        true
                    }
                    stateDescription?.let { this.stateDescription = it }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackH = trackHeight.toPx()
            val knobRadius = thumb.toPx()
            val startX = knobRadius
            val endX = size.width - knobRadius
            val span = (endX - startX).coerceAtLeast(1f)
            val knobX = startX + span * fraction
            val leftEdge = (knobX - knobRadius - gapPx).coerceAtLeast(startX)
            val rightEdge = (knobX + knobRadius + gapPx).coerceAtMost(endX)

            fun segment(from: Float, to: Float, color: Color) {
                if (to - from <= 0f) return
                drawRoundRect(
                    color = color,
                    topLeft = Offset(from, centerY - trackH / 2f),
                    size = Size(to - from, trackH),
                    cornerRadius = CornerRadius(trackH / 2f, trackH / 2f),
                )
            }

            segment(rightEdge, endX, trackColor)
            if (buffered > 0f && duration > 0f) {
                val bufferedX = startX + span * (buffered / duration).coerceIn(0f, 1f)
                if (bufferedX > rightEdge) segment(rightEdge, bufferedX, bufferedColor)
            }
            segment(startX, leftEdge, activeColor)
            drawCircle(color = thumbColor, radius = knobRadius, center = Offset(knobX, centerY))
        }
    }
}

private fun positionAt(x: Float, width: Float, radiusPx: Float, duration: Float): Float {
    if (duration <= 0f) return 0f
    val span = (width - 2f * radiusPx).coerceAtLeast(1f)
    return ((x - radiusPx) / span).coerceIn(0f, 1f) * duration
}
