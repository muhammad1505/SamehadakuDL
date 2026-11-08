package app.samehadaku.dl.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.samehadaku.dl.presentation.theme.Abyss
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.BrandPink
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Midnight
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary

enum class AppTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    QUEUE("queue", "Unduhan", Icons.Rounded.Download),
    COLLECTIONS("collections", "Koleksi", Icons.Rounded.Folder),
    HOME("home", "Beranda", Icons.Rounded.Home),
    SUBSCRIPTIONS("subscriptions", "Langganan", Icons.Rounded.Bookmark),
    SETTINGS("settings", "Pengaturan", Icons.Rounded.Settings),
}

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = {
                if (snackbarHostState != null) {
                    GlassSnackbarHost(hostState = snackbarHostState)
                }
            },
            content = content,
        )
    }
}

@Composable
fun ScreenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 7.dp, bottom = 5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    onClick = onBack,
                )
                Spacer(Modifier.width(11.dp))
            } else {
                // Main sections keep the same brand anchor as Beranda.
                BrandMark(size = 34.dp)
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            BrandPrimary.copy(alpha = 0.72f),
                            BrandAccent.copy(alpha = 0.28f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

/** Light ink used by the inactive tabs, matching the mockup's near-white labels. */
private val InactiveTabColor = Color(0xFFDCE7F7)

@Composable
fun AppBottomBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Abyss.copy(alpha = 0.55f),
                    1f to Abyss.copy(alpha = 0.92f),
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 10.dp)
            .padding(top = 10.dp, bottom = 14.dp),
    ) {
        // 76dp tall: a 60dp glass pill plus headroom for the raised Beranda button.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),
        ) {
            val compactHomeSize = when {
                maxWidth < 340.dp -> 56.dp
                maxWidth < 400.dp -> 62.dp
                else -> 68.dp
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(60.dp)
                    .shadow(
                        elevation = 26.dp,
                        shape = barShape,
                        ambientColor = BrandPrimary.copy(alpha = 0.55f),
                        spotColor = BrandPink.copy(alpha = 0.45f),
                    )
                    .clip(barShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF10203A).copy(alpha = 0.90f),
                                Color(0xFF060C1A).copy(alpha = 0.95f),
                            ),
                        ),
                    )
                    .border(
                        width = 1.4.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                BrandPrimary.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.22f),
                                BrandPink.copy(alpha = 0.85f),
                            ),
                        ),
                        shape = barShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.46f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                BrandPrimary.copy(alpha = 0.9f),
                                BrandPink.copy(alpha = 0.8f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
            ) {
                AppTab.entries.forEach { tab ->
                    if (tab == AppTab.HOME) {
                        HomeTabItem(
                            selected = tab == selectedTab,
                            onClick = { onTabSelected(tab) },
                            homeSize = compactHomeSize,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        NavTabItem(
                            tab = tab,
                            selected = tab == selectedTab,
                            onClick = { onTabSelected(tab) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) BrandPrimary else InactiveTabColor.copy(alpha = 0.92f)
    // A rounded press glow instead of the default rectangular ripple, which showed up
    // as a white box on the glass bar.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        label = "nav-tab-scale",
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed) 0.10f else 0f,
        label = "nav-tab-glow",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(
                            BrandPrimary.copy(alpha = 0.16f),
                            BrandAccent.copy(alpha = 0.10f),
                        ),
                    )
                } else {
                    Brush.solidColor(Color.White.copy(alpha = glow))
                },
                RoundedCornerShape(18.dp),
            )
            .then(
                if (selected) {
                    Modifier.border(
                        1.dp,
                        BrandPrimary.copy(alpha = 0.28f),
                        RoundedCornerShape(18.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(top = 13.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 28.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) BrandPrimary.copy(alpha = 0.18f) else Color.Transparent,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(if (selected) 22.dp else 23.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = tab.label,
            color = tint,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    homeSize: Dp,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        label = "home-tab-scale",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(top = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(y = (-9).dp)
                .size(homeSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            // Soft halo behind the raised button.
            Box(
                modifier = Modifier
                    .size(homeSize + 10.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                BrandPrimary.copy(alpha = if (selected) 0.30f else 0.16f),
                                BrandPink.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            // Bright ring hugging the raised button.
            Box(
                modifier = Modifier
                    .size(homeSize)
                    .shadow(
                        elevation = 20.dp,
                        shape = CircleShape,
                        ambientColor = BrandPrimary.copy(alpha = 0.90f),
                        spotColor = BrandPink.copy(alpha = 0.70f),
                    )
                    .border(
                        width = 2.4.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFFB9F6FF),
                                Color(0xFF6FD3FF),
                                BrandPink,
                                Color(0xFFB46BFF),
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
            // Violet disc holding both the home icon and its label.
            Box(
                modifier = Modifier
                    .size(homeSize - 5.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF2B1C86),
                                Color(0xFF5B2BD6),
                                Color(0xFF8A38E8),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = AppTab.HOME.icon,
                        contentDescription = AppTab.HOME.label,
                        tint = Color.White,
                        modifier = Modifier.size(25.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = AppTab.HOME.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF7CF3FF), BrandPink),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
fun ActionBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Midnight.copy(alpha = 0.94f), Abyss),
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                BrandPrimary.copy(alpha = 0.75f),
                                BrandPink.copy(alpha = 0.55f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            content()
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(BrandPrimary.copy(alpha = 0.16f), BrandAccent.copy(alpha = 0.09f)),
                    ),
                )
                .border(1.dp, BrandPrimary.copy(alpha = 0.18f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = message,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(14.dp))
            GradientButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp,
            )
        }
    }
}
