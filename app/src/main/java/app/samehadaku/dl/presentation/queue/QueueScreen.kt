package app.samehadaku.dl.presentation.queue

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.domain.model.DownloadState
import app.samehadaku.dl.domain.model.DownloadTask
import app.samehadaku.dl.presentation.components.AppBottomBar
import app.samehadaku.dl.presentation.components.AppTab
import app.samehadaku.dl.presentation.components.EmptyState
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.IconDot
import app.samehadaku.dl.presentation.components.MetricTile
import app.samehadaku.dl.presentation.components.SearchField
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.SegmentTab
import app.samehadaku.dl.presentation.components.SegmentedTabs
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.BrandGold
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.SlateSurface
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.theme.Warning
import app.samehadaku.dl.presentation.theme.titleAccent
import app.samehadaku.dl.presentation.util.formatBytes
import app.samehadaku.dl.presentation.util.formatSpeed
import app.samehadaku.dl.presentation.util.prettyName
import app.samehadaku.dl.presentation.util.stateLabel
import kotlin.math.roundToInt

@Composable
fun QueueScreen(
    onTabSelected: (AppTab) -> Unit,
    onBrowse: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadTask?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Bersihkan riwayat?") },
            text = {
                Text(
                    "Semua entri selesai dan gagal dihapus dari daftar. " +
                        "File yang sudah diunduh tetap tersimpan di penyimpanan.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearHistory = false
                        viewModel.clearFinished()
                    },
                ) {
                    Text("Bersihkan", color = BrandAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Hapus file episode ${task.episodeNumber}?") },
            text = {
                Text(
                    "File ${task.fileName.orEmpty()} akan dihapus permanen dari " +
                        "penyimpanan dan entri ini hilang dari riwayat.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteFile(task)
                    },
                ) {
                    Text("Hapus file", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Unduhan",
                subtitle = when {
                    state.runningCount > 0 ->
                        "${state.runningCount} berjalan • ${state.finishedCount} di riwayat"
                    state.pausedCount > 0 -> "${state.pausedCount} dijeda"
                    state.finishedCount > 0 ->
                        "${state.finishedCount} unduhan tersimpan di riwayat"
                    else -> "Antrean, progres, dan riwayat unduhan"
                },
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedTab = AppTab.QUEUE,
                onTabSelected = onTabSelected,
            )
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        if (state.totalCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.Download,
                    title = "Belum ada unduhan",
                    message = "Tempel URL Samehadaku di Beranda, pilih episode, lalu mulai unduh. " +
                        "Unduhan yang selesai tetap tersimpan di tab ini sebagai riwayat.",
                    actionLabel = "Jelajahi anime",
                    onAction = onBrowse,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    QueueSummary(state = state)
                }
                item {
                    BulkActions(
                        state = state,
                        onPauseAll = viewModel::pauseAll,
                        onResumeAll = viewModel::resumeAll,
                        onRetryFailed = viewModel::retryFailed,
                        onRequestClear = { confirmClearHistory = true },
                    )
                }
                item {
                    SearchField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = "Cari episode di unduhan dan riwayat...",
                    )
                }
                item {
                    SegmentedTabs(
                        tabs = listOf(
                            SegmentTab(QueueTab.ALL.label),
                            SegmentTab(QueueTab.ACTIVE.label, state.runningCount + state.pausedCount),
                            SegmentTab(QueueTab.DONE.label, state.doneCount),
                            SegmentTab(QueueTab.FAILED.label, state.failedCount),
                        ),
                        selectedIndex = state.tab.ordinal,
                        onSelect = viewModel::onTabSelected,
                    )
                }

                if (state.tasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = tween(durationMillis = 220),
                                    fadeOutSpec = tween(durationMillis = 140),
                                )
                                .fillMaxWidth(),
                        ) {
                            EmptyState(
                                icon = Icons.Rounded.Download,
                                title = "Tidak ada task di tab ini",
                                message = "Ganti tab atau hapus filter pencarian untuk " +
                                    "melihat task lainnya.",
                            )
                        }
                    }
                } else {
                    items(state.tasks, key = { it.id }) { task ->
                        QueueTaskCard(
                            task = task,
                            // New cards fade in and the list reflows smoothly whenever
                            // the filter tab or the search query changes the result set.
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(durationMillis = 240),
                                placementSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                ),
                                fadeOutSpec = tween(durationMillis = 160),
                            ),
                            onPause = { viewModel.pause(task.id) },
                            onResume = { viewModel.resume(task.id) },
                            onCancel = { viewModel.cancel(task.id) },
                            onRetry = { viewModel.retry(task.id) },
                            onOpen = { viewModel.open(task) },
                            onDeleteFile = { pendingDelete = task },
                            onRemove = { viewModel.remove(task.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSummary(state: QueueUiState) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(16.dp),
        glow = state.hasActive,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDot(
                icon = Icons.Rounded.Download,
                size = 38.dp,
                tint = BrandPrimary,
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Progres Unduhan",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${state.totalCount} task • ${state.concurrency} paralel",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(
                text = "${(state.overallProgress * 100).roundToInt()}%",
                color = BrandPrimary,
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                label = "Aktif",
                value = state.runningCount.toString(),
                icon = Icons.Rounded.Download,
                accent = BrandPrimary,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Selesai",
                value = state.doneCount.toString(),
                icon = Icons.Rounded.CheckCircle,
                accent = BrandMint,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Gagal",
                value = state.failedCount.toString(),
                icon = Icons.Rounded.ErrorOutline,
                accent = Danger,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.overallProgress.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(BrandPrimary, Color(0xFF4BE1FF), BrandAccent),
                        ),
                    ),
            )
        }

        Spacer(Modifier.height(11.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BrandMint),
            )
            Text(
                text = "${formatBytes(state.doneBytes)} / ${formatBytes(state.totalBytes)} tersimpan",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (state.pausedCount > 0) {
                StatusPill(text = "${state.pausedCount} dijeda", color = Warning)
            }
        }
    }
}

@Composable
private fun BulkActions(
    state: QueueUiState,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onRequestClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.hasActive || state.hasPaused || state.hasFailed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.hasActive -> GlassButton(
                        text = "Jeda semua",
                        onClick = onPauseAll,
                        icon = Icons.Rounded.Pause,
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                        tint = Warning,
                    )
                    state.hasPaused -> GlassButton(
                        text = "Lanjutkan",
                        onClick = onResumeAll,
                        icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                        tint = BrandMint,
                    )
                }
                if (state.hasFailed) {
                    GlassButton(
                        text = "Ulangi gagal",
                        onClick = onRetryFailed,
                        icon = Icons.Rounded.Refresh,
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                        tint = BrandGold,
                    )
                }
            }
        }
        if (state.hasFinished) {
            GlassButton(
                text = "Bersihkan riwayat",
                onClick = onRequestClear,
                icon = Icons.Rounded.History,
                modifier = Modifier.fillMaxWidth(),
                height = 40.dp,
                tint = BrandAccent,
            )
        }
    }
}

private data class TaskStyle(
    val color: Color,
    val icon: ImageVector,
)

private fun styleFor(state: DownloadState): TaskStyle = when (state) {
    DownloadState.DONE -> TaskStyle(BrandMint, Icons.Rounded.CheckCircle)
    DownloadState.FAILED -> TaskStyle(Danger, Icons.Rounded.ErrorOutline)
    DownloadState.PAUSED -> TaskStyle(Warning, Icons.Rounded.Pause)
    DownloadState.RESOLVING -> TaskStyle(BrandGold, Icons.Rounded.Wifi)
    DownloadState.DOWNLOADING -> TaskStyle(BrandPrimary, Icons.Rounded.Download)
    DownloadState.CANCELED -> TaskStyle(TextMuted, Icons.Rounded.Block)
    else -> TaskStyle(TextSecondary, Icons.Rounded.Download)
}

@Composable
private fun QueueTaskCard(
    task: DownloadTask,
    modifier: Modifier = Modifier,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onDeleteFile: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember(task.id) { mutableStateOf(false) }
    val style = styleFor(task.state)
    val accent = titleAccent(task.animeSlug)
    // Colour and length transition instead of snapping when a task changes phase.
    val statusColor by animateColorAsState(
        targetValue = style.color,
        animationSpec = tween(durationMillis = 380),
        label = "taskStatusColor",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = task.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "taskProgress",
    )
    val active = task.state.isActive
    val showProgress = active || task.state == DownloadState.PAUSED ||
        task.state == DownloadState.DONE || task.bytesDone > 0L

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(14.dp),
        highlighted = active,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.07f)),
                        ),
                    )
                    .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
                // Semantic status dot keeps the state readable on the tinted tile.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(statusColor)
                        .border(1.5.dp, SlateSurface, RoundedCornerShape(50)),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Episode ${task.episodeNumber}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(accent),
                    )
                    Text(
                        text = prettyName(task.animeSlug),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TaskStatusBadge(text = stateLabel(task.state), color = style.color)
                if (task.alreadyExisted) {
                    StatusPill(text = "Sudah ada", color = BrandGold)
                }
            }
        }

        Spacer(Modifier.height(9.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            InfoChip(
                icon = Icons.Rounded.Download,
                text = "${task.format.label} • ${task.resolution.uppercase()}",
                color = accent,
            )
            task.currentHost?.let { host ->
                InfoChip(icon = Icons.Rounded.Wifi, text = host.label, color = TextSecondary)
            }
            if (task.state == DownloadState.DOWNLOADING && task.speedBytesPerSecond > 0L) {
                InfoChip(
                    icon = Icons.Rounded.Speed,
                    text = formatSpeed(task.speedBytesPerSecond),
                    color = BrandMint,
                )
            }
        }

        AnimatedVisibility(visible = showProgress) {
            Column {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(listOf(statusColor, BrandPrimary)),
                            ),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (task.totalBytes > 0L) {
                            "${formatBytes(task.bytesDone)} / ${formatBytes(task.totalBytes)}"
                        } else {
                            formatBytes(task.bytesDone)
                        },
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(animatedProgress * 100).roundToInt()}%",
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (task.note != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (task.alreadyExisted) {
                            BrandGold.copy(alpha = 0.10f)
                        } else {
                            BrandPrimary.copy(alpha = 0.08f)
                        },
                    )
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (task.alreadyExisted) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.Download
                    },
                    contentDescription = null,
                    tint = if (task.alreadyExisted) BrandGold else BrandPrimary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = task.note.orEmpty(),
                    color = if (task.alreadyExisted) BrandGold else BrandPrimary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (task.failureReasons.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Alasan kegagalan (${task.failureReasons.size})",
                    color = Danger,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Sembunyikan" else "Tampilkan",
                    tint = Danger,
                    modifier = Modifier.size(17.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    task.failureReasons.forEach { reason ->
                        Text(
                            text = "• $reason",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                task.state == DownloadState.DOWNLOADING ||
                    task.state == DownloadState.QUEUED ||
                    task.state == DownloadState.RESOLVING -> {
                    GlassButton(
                        text = "Jeda",
                        onClick = onPause,
                        icon = Icons.Rounded.Pause,
                        height = 40.dp,
                        modifier = Modifier.weight(1f),
                        tint = Warning,
                    )
                    GlassButton(
                        text = "Batalkan",
                        onClick = onCancel,
                        icon = Icons.Rounded.Close,
                        height = 40.dp,
                        modifier = Modifier.weight(1f),
                        tint = Danger,
                    )
                }
                task.state == DownloadState.PAUSED -> {
                    GlassButton(
                        text = "Lanjutkan",
                        onClick = onResume,
                        icon = Icons.Rounded.PlayArrow,
                        height = 40.dp,
                        modifier = Modifier.weight(1f),
                        tint = BrandMint,
                    )
                    GlassButton(
                        text = "Batalkan",
                        onClick = onCancel,
                        icon = Icons.Rounded.Close,
                        height = 40.dp,
                        modifier = Modifier.weight(1f),
                        tint = Danger,
                    )
                }
                task.state == DownloadState.DONE -> {
                    GlassButton(
                        text = if (task.fileName != null) "Buka file" else "Buka",
                        onClick = onOpen,
                        icon = Icons.Rounded.PlayArrow,
                        height = 40.dp,
                        modifier = Modifier.weight(1f),
                        tint = BrandMint,
                    )
                    if (task.fileName != null) {
                        GlassButton(
                            text = "Hapus file",
                            onClick = onDeleteFile,
                            icon = Icons.Rounded.DeleteForever,
                            height = 40.dp,
                            modifier = Modifier.weight(1f),
                            tint = Danger,
                        )
                    } else {
                        GlassIconButton(
                            icon = Icons.Rounded.History,
                            contentDescription = "Hapus dari riwayat",
                            onClick = onRemove,
                            tint = TextSecondary,
                            size = 40.dp,
                            iconSize = 18.dp,
                        )
                    }
                }
                else -> {
                    GlassButton(
                        text = "Ulangi",
                        onClick = onRetry,
                        icon = Icons.Rounded.Refresh,
                        height = 40.dp,
                        modifier = Modifier.weight(1f),
                        tint = BrandGold,
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.History,
                        contentDescription = "Hapus dari riwayat",
                        onClick = onRemove,
                        tint = TextSecondary,
                        size = 40.dp,
                        iconSize = 18.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskStatusBadge(
    text: String,
    color: Color,
) {
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(durationMillis = 380),
        label = "statusBadgeColor",
    )
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(animatedColor.copy(alpha = 0.14f))
            .border(1.dp, animatedColor.copy(alpha = 0.32f), shape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(animatedColor),
        )
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (fadeIn(tween(durationMillis = 200)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(durationMillis = 200)))
                    .togetherWith(fadeOut(tween(durationMillis = 120)))
            },
            contentAlignment = Alignment.Center,
            label = "statusBadgeLabel",
        ) { label ->
            Text(
                text = label,
                color = animatedColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    text: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
