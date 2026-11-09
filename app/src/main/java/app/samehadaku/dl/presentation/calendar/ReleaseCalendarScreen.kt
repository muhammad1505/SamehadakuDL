package app.samehadaku.dl.presentation.calendar

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.domain.model.ReleaseScheduleItem
import app.samehadaku.dl.presentation.components.EmptyState
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.InfoBanner
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.BrandGold
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import java.util.Locale

@Composable
fun ReleaseCalendarScreen(
    onBack: () -> Unit,
    onOpenCloudflare: () -> Unit,
    viewModel: ReleaseCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val groups = state.items.groupBy { it.day }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Jadwal Anime",
                subtitle = "Perkiraan rilis mingguan dari Samehadaku",
                onBack = onBack,
                actions = {
                    GlassIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Muat ulang jadwal",
                        onClick = viewModel::load,
                        tint = TextSecondary,
                    )
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandPrimary, strokeWidth = 3.dp)
            }
            state.needsVerification -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                InfoBanner(
                    title = "Verifikasi diperlukan",
                    message = "Selesaikan verifikasi Cloudflare untuk membaca jadwal rilis.",
                    icon = Icons.Rounded.CloudOff,
                    accent = BrandGold,
                    actionLabel = "Buka verifikasi",
                    onAction = onOpenCloudflare,
                )
            }
            state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                InfoBanner(
                    title = "Jadwal belum tersedia",
                    message = state.error.orEmpty(),
                    icon = Icons.Rounded.CalendarMonth,
                    accent = Danger,
                    actionLabel = "Coba lagi",
                    onAction = viewModel::load,
                )
            }
            state.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.CalendarMonth,
                    title = "Jadwal belum terbaca",
                    message = "Coba muat ulang beberapa saat lagi.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(13.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = BrandMint,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                "Waktu di situs bisa berubah jika anime mengalami delay atau libur.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                groups.forEach { (day, entries) ->
                    item(key = "header-$day") {
                        Text(
                            text = day.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(entries, key = { "${day}-${it.title}-${it.episode}" }) { item ->
                        ScheduleRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(item: ReleaseScheduleItem) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
                Text(
                    text = listOfNotNull(item.time, item.episode).joinToString(" • ")
                        .ifBlank { "Jadwal mingguan" },
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            item.status?.let { status ->
                Spacer(Modifier.width(7.dp))
                StatusPill(
                    text = status,
                    color = if (status.equals("Delay", true)) BrandGold else Danger,
                )
            }
        }
    }
}
