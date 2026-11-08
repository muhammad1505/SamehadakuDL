package app.samehadaku.dl.presentation.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.samehadaku.dl.BuildConfig
import app.samehadaku.dl.R
import app.samehadaku.dl.presentation.components.BrandMark
import app.samehadaku.dl.presentation.theme.Abyss
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    var targetProgress by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "splash-progress",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "splash-alpha",
    )
    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.88f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "splash-scale",
    )

    LaunchedEffect(Unit) {
        started = true
        delay(60)
        targetProgress = 0.7f
        delay(380)
        targetProgress = 1f
        delay(170)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Abyss),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_wallpaper),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(horizontalBias = 0.55f, verticalBias = 0f),
            alpha = 0.90f,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Abyss.copy(alpha = 0.42f),
                            Abyss.copy(alpha = 0.64f),
                            Abyss.copy(alpha = 0.94f),
                        ),
                    ),
                ),
        )
        // Extra moon halo behind the logo lockup.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(320.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            BrandPrimary.copy(alpha = 0.14f),
                            BrandAccent.copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    alpha = contentAlpha
                },
            ) {
                BrandMark(size = 104.dp)
            }
            Spacer(Modifier.height(22.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha },
            ) {
                Text(
                    text = "Samehadaku",
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "DL",
                    color = BrandPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Downloader Anime dari Samehadaku",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha },
            )

            Spacer(Modifier.height(38.dp))

            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = contentAlpha },
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(BrandPrimary, Color(0xFF4BE1FF), BrandAccent),
                                ),
                            ),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Memuat aplikasi…",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        color = BrandPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Downloader Anime  •  v${BuildConfig.VERSION_NAME}",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha },
            )
        }
    }
}
