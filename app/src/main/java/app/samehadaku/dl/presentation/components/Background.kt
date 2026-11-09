package app.samehadaku.dl.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.samehadaku.dl.R
import app.samehadaku.dl.presentation.theme.Abyss
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPink
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Ink
import app.samehadaku.dl.presentation.theme.Midnight
import app.samehadaku.dl.presentation.theme.TextPrimary

private data class StarSpec(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val radius: Float,
)

private val BackgroundStars = listOf(
    StarSpec(0.07f, 0.09f, 0.55f, 1.1f),
    StarSpec(0.17f, 0.18f, 0.32f, 0.8f),
    StarSpec(0.28f, 0.07f, 0.60f, 1.3f),
    StarSpec(0.39f, 0.14f, 0.28f, 0.7f),
    StarSpec(0.48f, 0.05f, 0.48f, 1.0f),
    StarSpec(0.58f, 0.19f, 0.30f, 0.8f),
    StarSpec(0.69f, 0.10f, 0.52f, 1.1f),
    StarSpec(0.78f, 0.21f, 0.25f, 0.7f),
    StarSpec(0.88f, 0.06f, 0.58f, 1.2f),
    StarSpec(0.95f, 0.16f, 0.30f, 0.8f),
    StarSpec(0.12f, 0.38f, 0.18f, 0.6f),
    StarSpec(0.32f, 0.33f, 0.22f, 0.7f),
    StarSpec(0.53f, 0.30f, 0.16f, 0.6f),
    StarSpec(0.74f, 0.36f, 0.20f, 0.7f),
    StarSpec(0.91f, 0.31f, 0.18f, 0.6f),
)

private val FloatingPetals = listOf(
    0.10f to 0.16f,
    0.24f to 0.27f,
    0.37f to 0.11f,
    0.54f to 0.22f,
    0.67f to 0.12f,
    0.81f to 0.24f,
    0.92f to 0.15f,
)

private data class ScenePalette(
    val skyTop: Color,
    val skyBottom: Color,
    val glow: Color,
    val ridgeFar: Color,
    val ridgeNear: Color,
    val accent: Color,
)

private val ScenePalettes = listOf(
    ScenePalette(
        skyTop = Color(0xFF06152E),
        skyBottom = Color(0xFF3B2165),
        glow = BrandPrimary,
        ridgeFar = Color(0xFF142F52),
        ridgeNear = Color(0xFF081322),
        accent = Color(0xFF8B5CFF),
    ),
    ScenePalette(
        skyTop = Color(0xFF0A132B),
        skyBottom = Color(0xFF5A2148),
        glow = Color(0xFF7CE7FF),
        ridgeFar = Color(0xFF263354),
        ridgeNear = Color(0xFF0B111F),
        accent = BrandPink,
    ),
    ScenePalette(
        skyTop = Color(0xFF041C27),
        skyBottom = Color(0xFF213A66),
        glow = Color(0xFF65FFD8),
        ridgeFar = Color(0xFF184052),
        ridgeNear = Color(0xFF07141C),
        accent = BrandMint,
    ),
    ScenePalette(
        skyTop = Color(0xFF120B2D),
        skyBottom = Color(0xFF5F2B32),
        glow = Color(0xFFFFB26B),
        ridgeFar = Color(0xFF3D3252),
        ridgeNear = Color(0xFF120E1E),
        accent = Color(0xFFFF8E53),
    ),
    ScenePalette(
        skyTop = Color(0xFF071629),
        skyBottom = Color(0xFF153C5C),
        glow = Color(0xFF6ED5FF),
        ridgeFar = Color(0xFF1A3A58),
        ridgeNear = Color(0xFF07121E),
        accent = Color(0xFF4DA3FF),
    ),
)

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "app-background")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora-drift",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Abyss),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_wallpaper),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(horizontalBias = 0.5f, verticalBias = 0.42f),
            alpha = 1f,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Abyss.copy(alpha = 0.18f),
                            Midnight.copy(alpha = 0.26f),
                            Abyss.copy(alpha = 0.42f),
                            Abyss.copy(alpha = 0.60f),
                        ),
                    ),
                ),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawAmbientBackdrop(drift)
        }
        content()
    }
}

private fun DrawScope.drawAmbientBackdrop(drift: Float) {
    val width = size.width
    val height = size.height

    val cyanCenter = Offset(width * 0.12f, height * 0.06f)
    val cyanRadius = width * 0.82f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(BrandPrimary.copy(alpha = 0.17f), Color.Transparent),
            center = cyanCenter,
            radius = cyanRadius,
        ),
        radius = cyanRadius,
        center = cyanCenter,
    )

    val purpleCenter = Offset(width * (0.98f - drift * 0.04f), height * (0.54f + drift * 0.03f))
    val purpleRadius = width * 0.8f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(BrandAccent.copy(alpha = 0.16f), Color.Transparent),
            center = purpleCenter,
            radius = purpleRadius,
        ),
        radius = purpleRadius,
        center = purpleCenter,
    )

    val pinkCenter = Offset(width * 0.1f, height * 0.94f)
    val pinkRadius = width * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(BrandPink.copy(alpha = 0.09f), Color.Transparent),
            center = pinkCenter,
            radius = pinkRadius,
        ),
        radius = pinkRadius,
        center = pinkCenter,
    )

    // Faint technical grid.
    val gridStep = 54.dp.toPx()
    var diagonal = -height
    while (diagonal < width + height) {
        drawLine(
            color = Color.White.copy(alpha = 0.018f),
            start = Offset(diagonal, 0f),
            end = Offset(diagonal + height * 0.58f, height),
            strokeWidth = 1f,
        )
        diagonal += gridStep
    }
    var horizontal = 0f
    while (horizontal < height) {
        drawLine(
            color = Color.White.copy(alpha = 0.012f),
            start = Offset(0f, horizontal),
            end = Offset(width, horizontal),
            strokeWidth = 1f,
        )
        horizontal += gridStep * 1.7f
    }

    BackgroundStars.forEach { star ->
        drawCircle(
            color = Color.White.copy(alpha = star.alpha),
            radius = star.radius.dp.toPx(),
            center = Offset(width * star.x, height * star.y),
        )
    }

    // Glowing aurora ribbons near the bottom.
    val aurora = Path().apply {
        moveTo(0f, height * 0.82f)
        cubicTo(
            width * 0.22f,
            height * 0.72f,
            width * 0.38f,
            height * 0.95f,
            width * 0.60f,
            height * 0.84f,
        )
        cubicTo(
            width * 0.80f,
            height * 0.75f,
            width * 0.94f,
            height * 0.86f,
            width,
            height * 0.78f,
        )
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(
        path = aurora,
        brush = Brush.verticalGradient(
            colors = listOf(
                BrandPrimary.copy(alpha = 0.09f),
                BrandAccent.copy(alpha = 0.06f),
                Color.Transparent,
            ),
            startY = height * 0.72f,
            endY = height,
        ),
    )

    val ribbon = Path().apply {
        moveTo(-width * 0.05f, height * 0.92f)
        cubicTo(
            width * 0.25f,
            height * 0.78f,
            width * 0.55f,
            height * 0.98f,
            width * 1.05f,
            height * 0.8f,
        )
    }
    drawPath(
        path = ribbon,
        brush = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                BrandPrimary.copy(alpha = 0.28f),
                BrandAccent.copy(alpha = 0.24f),
                Color.Transparent,
            ),
        ),
        style = Stroke(width = 1.3.dp.toPx()),
    )

    val lowerRibbon = Path().apply {
        moveTo(-width * 0.02f, height * 0.98f)
        cubicTo(
            width * 0.3f,
            height * 0.87f,
            width * 0.62f,
            height * 1.02f,
            width * 1.04f,
            height * 0.88f,
        )
    }
    drawPath(
        path = lowerRibbon,
        brush = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                BrandPink.copy(alpha = 0.16f),
                BrandPrimary.copy(alpha = 0.12f),
                Color.Transparent,
            ),
        ),
        style = Stroke(width = 1.dp.toPx()),
    )

    // Neon horizon glow behind the floating bottom navigation.
    val horizonCenter = Offset(width * 0.5f, height * 1.02f)
    val horizonRadius = width * 0.85f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(BrandPrimary.copy(alpha = 0.20f), Color.Transparent),
            center = horizonCenter,
            radius = horizonRadius,
        ),
        radius = horizonRadius,
        center = horizonCenter,
    )
    drawCircle(
        color = BrandPrimary.copy(alpha = 0.22f),
        radius = horizonRadius,
        center = horizonCenter,
        style = Stroke(width = 1.3.dp.toPx()),
    )

    // Static sakura petals add depth without distracting motion.
    FloatingPetals.forEachIndexed { index, (x, y) ->
        val petalWidth = (5.5f + (index % 3) * 1.4f).dp.toPx()
        val petalHeight = petalWidth * 0.55f
        rotate(degrees = 25f + index * 17f, pivot = Offset(width * x, height * y)) {
            drawOval(
                color = BrandPink.copy(alpha = 0.14f),
                topLeft = Offset(width * x - petalWidth / 2f, height * y - petalHeight / 2f),
                size = Size(petalWidth, petalHeight),
            )
        }
    }
}

@Composable
fun SceneryArt(
    modifier: Modifier = Modifier,
    seed: Int = 0,
    shape: Shape = RoundedCornerShape(24.dp),
    label: String? = null,
) {
    val palette = ScenePalettes[(seed and Int.MAX_VALUE) % ScenePalettes.size]
    Box(modifier = modifier.clip(shape)) {
        Canvas(Modifier.fillMaxSize()) {
            drawScene(seed = seed, palette = palette)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Ink.copy(alpha = 0.48f)),
                    ),
                ),
        )
        if (label != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.copy(alpha = 0.58f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = label,
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun DrawScope.drawScene(seed: Int, palette: ScenePalette) {
    val width = size.width
    val height = size.height

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.skyTop, palette.skyBottom),
        ),
    )

    // Moon glow + moon.
    val moonCenter = Offset(width * 0.72f, height * 0.30f)
    val moonRadius = minOf(width, height) * 0.145f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.glow.copy(alpha = 0.32f), Color.Transparent),
            center = moonCenter,
            radius = moonRadius * 3.2f,
        ),
        radius = moonRadius * 3.2f,
        center = moonCenter,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFFDF4), Color(0xFFD8E6FF)),
            center = moonCenter - Offset(moonRadius * 0.25f, moonRadius * 0.25f),
            radius = moonRadius * 1.4f,
        ),
        radius = moonRadius,
        center = moonCenter,
    )

    // Small drifting clouds.
    val cloudPath = Path().apply {
        moveTo(width * 0.02f, height * 0.34f)
        cubicTo(
            width * 0.2f,
            height * 0.29f,
            width * 0.28f,
            height * 0.38f,
            width * 0.44f,
            height * 0.33f,
        )
        cubicTo(
            width * 0.3f,
            height * 0.35f,
            width * 0.14f,
            height * 0.36f,
            width * 0.02f,
            height * 0.34f,
        )
        close()
    }
    drawPath(cloudPath, Color.White.copy(alpha = 0.055f))
    val cloudPathTwo = Path().apply {
        moveTo(width * 0.56f, height * 0.48f)
        cubicTo(
            width * 0.7f,
            height * 0.43f,
            width * 0.82f,
            height * 0.51f,
            width,
            height * 0.46f,
        )
        cubicTo(
            width * 0.82f,
            height * 0.5f,
            width * 0.66f,
            height * 0.52f,
            width * 0.56f,
            height * 0.48f,
        )
        close()
    }
    drawPath(cloudPathTwo, Color.White.copy(alpha = 0.045f))

    // Layered mountain ridges.
    val farRidge = Path().apply {
        moveTo(0f, height * 0.71f)
        lineTo(width * 0.18f, height * 0.55f)
        lineTo(width * 0.31f, height * 0.66f)
        lineTo(width * 0.45f, height * 0.49f)
        lineTo(width * 0.61f, height * 0.67f)
        lineTo(width * 0.78f, height * 0.57f)
        lineTo(width, height * 0.68f)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(
        path = farRidge,
        brush = Brush.verticalGradient(
            listOf(palette.ridgeFar.copy(alpha = 0.92f), palette.ridgeNear),
            startY = height * 0.5f,
            endY = height,
        ),
    )

    val nearRidge = Path().apply {
        moveTo(0f, height * 0.82f)
        cubicTo(
            width * 0.18f,
            height * 0.70f,
            width * 0.3f,
            height * 0.79f,
            width * 0.46f,
            height * 0.72f,
        )
        cubicTo(
            width * 0.64f,
            height * 0.65f,
            width * 0.77f,
            height * 0.8f,
            width,
            height * 0.7f,
        )
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(
        path = nearRidge,
        brush = Brush.verticalGradient(
            listOf(palette.ridgeNear.copy(alpha = 0.94f), Color(0xFF040A12)),
            startY = height * 0.68f,
            endY = height,
        ),
    )

    // Pagoda silhouette on the left.
    drawPagoda(
        centerX = width * 0.20f,
        baseY = height * 0.84f,
        height = height * 0.23f,
        color = Color(0xFF040A12).copy(alpha = 0.92f),
    )

    // Torii gate on the right.
    drawTorii(
        centerX = width * 0.79f,
        baseY = height * 0.88f,
        height = height * 0.30f,
        color = Color(0xFF03070D).copy(alpha = 0.95f),
        rimColor = palette.accent.copy(alpha = 0.22f),
    )

    // Water / mist at the bottom.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, palette.accent.copy(alpha = 0.12f), Color(0xFF03070D)),
            startY = height * 0.86f,
            endY = height,
        ),
        topLeft = Offset(0f, height * 0.86f),
        size = Size(width, height * 0.14f),
    )
    val reflectionX = width * 0.72f
    for (index in 0 until 4) {
        val y = height * (0.9f + index * 0.018f)
        val half = moonRadius * (0.5f - index * 0.08f)
        drawLine(
            color = palette.glow.copy(alpha = 0.14f - index * 0.025f),
            start = Offset(reflectionX - half, y),
            end = Offset(reflectionX + half, y),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    // Sakura petals in the foreground.
    val petalCount = 7 + (seed and 3)
    for (index in 0 until petalCount) {
        val normalizedX = ((seed * 31 + index * 67) % 100) / 100f
        val normalizedY = 0.1f + (((seed * 17 + index * 43) % 60) / 100f)
        val petalWidth = (3.5f + (index % 4) * 1.6f).dp.toPx()
        rotate(
            degrees = 20f + index * 23f,
            pivot = Offset(width * normalizedX, height * normalizedY),
        ) {
            drawOval(
                color = BrandPink.copy(alpha = 0.16f + (index % 3) * 0.04f),
                topLeft = Offset(
                    width * normalizedX - petalWidth,
                    height * normalizedY - petalWidth * 0.48f,
                ),
                size = Size(petalWidth * 2f, petalWidth * 0.9f),
            )
        }
    }
}

private fun DrawScope.drawTorii(
    centerX: Float,
    baseY: Float,
    height: Float,
    color: Color,
    rimColor: Color,
) {
    val halfSpan = height * 0.34f
    val postWidth = height * 0.065f
    val postTop = baseY - height * 0.70f
    val postHeight = height * 0.70f

    drawRoundRect(
        color = rimColor,
        topLeft = Offset(centerX - halfSpan - postWidth * 0.55f, postTop),
        size = Size(postWidth, postHeight),
        cornerRadius = CornerRadius(postWidth * 0.4f),
    )
    drawRoundRect(
        color = rimColor,
        topLeft = Offset(centerX + halfSpan - postWidth * 0.45f, postTop),
        size = Size(postWidth, postHeight),
        cornerRadius = CornerRadius(postWidth * 0.4f),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(centerX - halfSpan - postWidth * 0.65f, postTop),
        size = Size(postWidth, postHeight),
        cornerRadius = CornerRadius(postWidth * 0.4f),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(centerX + halfSpan - postWidth * 0.35f, postTop),
        size = Size(postWidth, postHeight),
        cornerRadius = CornerRadius(postWidth * 0.4f),
    )

    val topY = baseY - height * 0.82f
    val topBeam = Path().apply {
        moveTo(centerX - halfSpan * 1.12f, topY + height * 0.055f)
        quadraticBezierTo(
            centerX,
            topY - height * 0.045f,
            centerX + halfSpan * 1.12f,
            topY + height * 0.055f,
        )
    }
    drawPath(
        path = topBeam,
        color = color,
        style = Stroke(width = height * 0.085f, cap = StrokeCap.Round),
    )
    drawPath(
        path = topBeam,
        color = rimColor,
        style = Stroke(width = height * 0.018f, cap = StrokeCap.Round),
    )

    val beamY = topY + height * 0.21f
    drawLine(
        color = color,
        start = Offset(centerX - halfSpan, beamY),
        end = Offset(centerX + halfSpan, beamY),
        strokeWidth = height * 0.048f,
        cap = StrokeCap.Round,
    )
    drawRect(
        color = color,
        topLeft = Offset(centerX - height * 0.035f, topY + height * 0.105f),
        size = Size(height * 0.07f, height * 0.14f),
    )
}

private fun DrawScope.drawPagoda(
    centerX: Float,
    baseY: Float,
    height: Float,
    color: Color,
) {
    drawRect(
        color = color,
        topLeft = Offset(centerX - height * 0.07f, baseY - height * 0.62f),
        size = Size(height * 0.14f, height * 0.62f),
    )
    repeat(3) { tier ->
        val tierY = baseY - height * (0.38f + tier * 0.19f)
        val halfWidth = height * (0.16f + tier * 0.035f)
        val roof = Path().apply {
            moveTo(centerX - halfWidth, tierY)
            quadraticBezierTo(centerX, tierY - height * 0.09f, centerX + halfWidth, tierY)
            lineTo(centerX + halfWidth * 0.68f, tierY + height * 0.02f)
            lineTo(centerX - halfWidth * 0.68f, tierY + height * 0.02f)
            close()
        }
        drawPath(roof, color)
    }
    drawRect(
        color = color,
        topLeft = Offset(centerX - height * 0.16f, baseY - height * 0.03f),
        size = Size(height * 0.32f, height * 0.03f),
    )
}

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    glow: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                if (glow) {
                    Brush.radialGradient(
                        listOf(BrandPrimary.copy(alpha = 0.24f), Color.Transparent),
                    )
                } else {
                    Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = "Samehadaku Downloader",
            modifier = Modifier.size(size * 0.9f),
        )
    }
}
