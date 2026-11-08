package app.samehadaku.dl.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.samehadaku.dl.presentation.theme.Abyss
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.BrandPink
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Midnight
import app.samehadaku.dl.presentation.theme.TextMuted
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                onClick = onBack,
            )
            Spacer(Modifier.width(11.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
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
}

@Composable
fun AppBottomBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Abyss.copy(alpha = 0.86f),
                        Abyss.copy(alpha = 0.98f),
                    ),
                ),
            )
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 20.dp),
    ) {
        // Neon orbit pill behind the tabs.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 30.dp,
                    shape = RoundedCornerShape(34.dp),
                    ambientColor = BrandPrimary.copy(alpha = 0.65f),
                    spotColor = BrandAccent.copy(alpha = 0.58f),
                )
                .clip(RoundedCornerShape(34.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0E2036).copy(alpha = 0.97f),
                            Color(0xFF060C1B).copy(alpha = 0.99f),
                        ),
                    ),
                )
                .border(
                    width = 1.8.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            BrandPrimary,
                            BrandAccent.copy(alpha = 0.92f),
                            BrandPink.copy(alpha = 0.72f),
                        ),
                    ),
                    shape = RoundedCornerShape(34.dp),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            AppTab.entries.forEach { tab ->
                if (tab == AppTab.HOME) {
                    HomeTabItem(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
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

@Composable
private fun NavTabItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (selected) BrandPrimary else TextMuted,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            color = if (selected) TextPrimary else TextMuted,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(bottom = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            modifier = Modifier.size(70.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Soft halo behind the raised button.
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                BrandPrimary.copy(alpha = if (selected) 0.32f else 0.20f),
                                BrandAccent.copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            // Neon ring hugging the raised button.
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = CircleShape,
                        ambientColor = BrandPrimary.copy(alpha = 0.85f),
                        spotColor = BrandPink.copy(alpha = 0.65f),
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            listOf(BrandPrimary, BrandPink, BrandAccent),
                        ),
                        shape = CircleShape,
                    ),
            )
            // Tilted orbit ring around the button.
            Canvas(modifier = Modifier.requiredSize(98.dp)) {
                val ringWidth = size.width * 0.94f
                val ringHeight = ringWidth * 0.36f
                rotate(degrees = -18f) {
                    drawOval(
                        brush = Brush.horizontalGradient(
                            listOf(BrandPrimary, BrandPink, BrandAccent),
                        ),
                        topLeft = Offset(
                            (size.width - ringWidth) / 2f,
                            (size.height - ringHeight) / 2f,
                        ),
                        size = Size(ringWidth, ringHeight),
                        style = Stroke(width = 2.2.dp.toPx()),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        ambientColor = BrandPrimary.copy(alpha = 0.70f),
                        spotColor = BrandAccent.copy(alpha = 0.60f),
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF2CE4FF),
                                Color(0xFF7A5CFF),
                                Color(0xFFC05CFF),
                            ),
                        ),
                    )
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF10224A), Color(0xFF08122A)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppTab.HOME.icon,
                    contentDescription = AppTab.HOME.label,
                    tint = if (selected) Color.White else Color(0xFF8EDFFF),
                    modifier = Modifier.size(27.dp),
                )
            }
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = AppTab.HOME.label,
            color = if (selected) Color.White else TextMuted,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
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
            content = content,
        )
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
