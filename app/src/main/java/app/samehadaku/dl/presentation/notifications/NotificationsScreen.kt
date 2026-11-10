package app.samehadaku.dl.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.domain.model.AppNotification
import app.samehadaku.dl.domain.model.AppNotificationKind
import app.samehadaku.dl.presentation.components.EmptyState
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.SectionHeader
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.theme.Warning

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Notifikasi",
                subtitle = if (state.unreadCount > 0) {
                    "${state.unreadCount} belum dibaca"
                } else {
                    "Semua sudah dibaca"
                },
                onBack = onBack,
                actions = {
                    GlassIconButton(
                        icon = Icons.Rounded.DoneAll,
                        contentDescription = "Tandai semua dibaca",
                        enabled = state.unreadCount > 0,
                        onClick = viewModel::markAllRead,
                        tint = BrandMint,
                        size = 40.dp,
                        iconSize = 19.dp,
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.DeleteSweep,
                        contentDescription = "Bersihkan notifikasi",
                        enabled = state.items.isNotEmpty(),
                        onClick = viewModel::clearAll,
                        tint = TextSecondary,
                        size = 40.dp,
                        iconSize = 19.dp,
                    )
                },
            )
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        when {
            state.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassCard(
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(22.dp),
                        glow = true,
                    ) {
                        CircularProgressIndicator(
                            color = BrandPrimary,
                            trackColor = Color.White.copy(alpha = 0.10f),
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }

            state.items.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.NotificationsNone,
                    title = "Belum ada notifikasi",
                    message = "Episode baru, unduhan yang diantrekan, dan permintaan " +
                        "verifikasi akan muncul di sini.",
                    modifier = Modifier.padding(padding).padding(top = 26.dp),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    item {
                        SectionHeader(
                            title = "Terbaru",
                            icon = Icons.Rounded.NotificationsNone,
                            action = if (state.unreadCount > 0) "Tandai dibaca" else null,
                            onAction = viewModel::markAllRead,
                        )
                    }
                    items(state.items, key = { it.id }) { item ->
                        NotificationCard(
                            item = item,
                            onClick = {
                                viewModel.markRead(item)
                                if (item.route.isNotBlank()) onOpenRoute(item.route)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: AppNotification,
    onClick: () -> Unit,
) {
    val accent = accentFor(item.kind)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(13.dp),
        highlighted = !item.read,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(item.kind),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (item.read) FontWeight.SemiBold else FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!item.read) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BrandPrimary),
                        )
                    }
                }
                if (item.message.isNotBlank()) {
                    Text(
                        text = item.message,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = relativeTime(item.createdAt),
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun iconFor(kind: AppNotificationKind): ImageVector = when (kind) {
    AppNotificationKind.NEW_EPISODES -> Icons.Rounded.NewReleases
    AppNotificationKind.QUEUED -> Icons.Rounded.Download
    AppNotificationKind.COMPLETED -> Icons.Rounded.TaskAlt
    AppNotificationKind.VERIFICATION -> Icons.Rounded.CloudOff
    AppNotificationKind.INFO -> Icons.Rounded.NotificationsNone
}

private fun accentFor(kind: AppNotificationKind): Color = when (kind) {
    AppNotificationKind.NEW_EPISODES -> BrandPrimary
    AppNotificationKind.QUEUED -> BrandMint
    AppNotificationKind.COMPLETED -> BrandMint
    AppNotificationKind.VERIFICATION -> Warning
    AppNotificationKind.INFO -> Danger
}

private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Baru saja"
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000L
    return when {
        minutes < 1 -> "Baru saja"
        minutes < 60 -> "$minutes mnt lalu"
        minutes < 24 * 60 -> "${minutes / 60} jam lalu"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} hari lalu"
        else -> app.samehadaku.dl.presentation.util.shortDate(timestamp)
    }
}
