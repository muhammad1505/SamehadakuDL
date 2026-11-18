package app.samehadaku.dl.presentation.smartstorage

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.domain.model.CleanupIssueReason
import app.samehadaku.dl.domain.model.PendingPlayback
import app.samehadaku.dl.presentation.components.EmptyState
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.InfoBanner
import app.samehadaku.dl.presentation.components.MetricTile
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.SectionHeader
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.BrandGold
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.util.formatBytes
import app.samehadaku.dl.presentation.util.prettyName
import app.samehadaku.dl.presentation.util.shortDate

/**
 * Explains every watched marker: when it becomes due, what is currently being
 * tracked, and which files could not be removed automatically. Actions here only
 * touch files the cleaner already verified as app downloads.
 */
@Composable
fun SmartStorageDetailScreen(
    onBack: () -> Unit,
    viewModel: SmartStorageDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val startConfirmation = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.retryLastDelete()
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbar.showSnackbar(message) }
    }
    LaunchedEffect(Unit) {
        viewModel.confirmationRequests.collect { sender ->
            runCatching {
                startConfirmation.launch(IntentSenderRequest.Builder(sender).build())
            }
        }
    }

    val dueEntries = state.entries.filter { it.due && it.issue == null }
    val waitingEntries = state.entries.filter { !it.due && it.issue == null }
    val issueEntries = state.entries.filter { it.issue != null }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Smart Storage",
                subtitle = "${state.entries.size} episode ditandai • ${formatBytes(state.totalBytes)}",
                onBack = onBack,
            )
        },
        snackbarHostState = snackbar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(
                        label = "Siap dihapus",
                        value = state.dueCount.toString(),
                        icon = Icons.Rounded.AutoDelete,
                        accent = BrandMint,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "Menunggu",
                        value = state.waitingCount.toString(),
                        icon = Icons.Rounded.Schedule,
                        accent = BrandAccent,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "Perlu izin",
                        value = state.issueCount.toString(),
                        icon = Icons.Rounded.ErrorOutline,
                        accent = BrandGold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!state.enabled) {
                item {
                    InfoBanner(
                        title = "Smart Storage nonaktif",
                        message = "Tanda tonton tetap tersimpan, tetapi tidak ada file yang " +
                            "dihapus otomatis sampai fitur diaktifkan di Pengaturan.",
                        accent = BrandGold,
                    )
                }
            }

            if (issueEntries.isNotEmpty()) {
                item { SectionHeader("Perlu perhatian", icon = Icons.Rounded.ErrorOutline) }
                items(issueEntries, key = { it.key }) { entry ->
                    WatchedEntryCard(
                        entry = entry,
                        busy = state.busyKey == entry.key,
                        onDelete = { viewModel.deleteNow(entry) },
                        onUnmark = { viewModel.unmark(entry) },
                    )
                }
            }

            if (dueEntries.isNotEmpty()) {
                item { SectionHeader("Siap dihapus", icon = Icons.Rounded.AutoDelete) }
                items(dueEntries, key = { it.key }) { entry ->
                    WatchedEntryCard(
                        entry = entry,
                        busy = state.busyKey == entry.key,
                        onDelete = { viewModel.deleteNow(entry) },
                        onUnmark = { viewModel.unmark(entry) },
                    )
                }
            }

            if (waitingEntries.isNotEmpty()) {
                item { SectionHeader("Menunggu masa tenggang", icon = Icons.Rounded.Schedule) }
                items(waitingEntries, key = { it.key }) { entry ->
                    WatchedEntryCard(
                        entry = entry,
                        busy = state.busyKey == entry.key,
                        onDelete = { viewModel.deleteNow(entry) },
                        onUnmark = { viewModel.unmark(entry) },
                    )
                }
            }

            if (state.pending.isNotEmpty()) {
                item { SectionHeader("Sedang dilacak", icon = Icons.Rounded.PlayCircle) }
                items(state.pending, key = { "${it.folderName}/${it.fileName}" }) { pending ->
                    PendingPlaybackCard(pending)
                }
            }

            if (!state.loading && state.entries.isEmpty() && state.pending.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Visibility,
                        title = "Belum ada episode ditandai",
                        message = "Tandai episode tonton dari Koleksi, atau putar video lewat " +
                            "aplikasi lalu kembali ke sini setelah selesai menonton.",
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchedEntryCard(
    entry: SmartStorageEntry,
    busy: Boolean,
    onDelete: () -> Unit,
    onUnmark: () -> Unit,
) {
    val status = when (entry.issue?.reason) {
        CleanupIssueReason.UNKNOWN_SIZE ->
            "Ukuran file tidak diketahui; file tidak akan dihapus otomatis." to BrandGold
        CleanupIssueReason.SIZE_MISMATCH ->
            "Ukuran file berubah sejak ditandai; hapus manual dari Koleksi bila perlu." to BrandGold
        CleanupIssueReason.NEEDS_CONFIRMATION ->
            "Android meminta konfirmasi sebelum file bisa dihapus." to Danger
        CleanupIssueReason.DENIED ->
            "Penyimpanan menolak menghapus file ini." to Danger
        null -> if (entry.due) {
            "Masa tenggang berakhir; file bisa dihapus sekarang." to BrandMint
        } else {
            "Dihapus otomatis setelah ${shortDate(entry.dueAt)}." to TextMuted
        }
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = entry.fileName,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            StatusPill(
                text = formatBytes(entry.size),
                color = BrandAccent,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = "${prettyName(entry.folderName)} • ${if (entry.manual) "manual" else "otomatis"}",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = status.first,
            color = status.second,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = if (busy) "Menghapus…" else "Hapus sekarang",
                onClick = onDelete,
                enabled = !busy,
                tint = Danger,
                height = 40.dp,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = "Batalkan tanda",
                onClick = onUnmark,
                enabled = !busy,
                height = 40.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PendingPlaybackCard(pending: PendingPlayback) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = pending.fileName,
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = prettyName(pending.folderName),
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Terhitung ${minutes(pending.watchedMs)} dari " +
                "${minutes(pending.durationMs)} • tanda tonton setelah 80%.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun minutes(millis: Long): String {
    val total = (millis / 60_000L).coerceAtLeast(0L)
    return if (total < 1L) "kurang dari 1 menit" else "$total menit"
}
