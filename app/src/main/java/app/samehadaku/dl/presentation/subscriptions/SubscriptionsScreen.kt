package app.samehadaku.dl.presentation.subscriptions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.domain.model.Episode
import app.samehadaku.dl.domain.model.Subscription
import app.samehadaku.dl.presentation.components.EmptyState
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.GlassTextField
import app.samehadaku.dl.presentation.components.GradientButton
import app.samehadaku.dl.presentation.components.InfoBanner
import app.samehadaku.dl.presentation.components.NeonCheckbox
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.SlateSurface
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.theme.Warning
import app.samehadaku.dl.presentation.util.shortDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    onOpenEpisodes: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenCloudflare: () -> Unit,
    verified: Boolean,
    onVerifiedHandled: () -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var addUrl by rememberSaveable { mutableStateOf("") }
    var addLast by rememberSaveable { mutableStateOf("") }
    var pendingLast by remember { mutableStateOf<Subscription?>(null) }
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                snackbarHostState.showSnackbar("File tidak bisa dibaca.")
            } else {
                viewModel.importFromScript(text)
            }
        }
    }

    fun pickImportFile() {
        importLauncher.launch(arrayOf("*/*"))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SubscriptionsEvent.Added -> {
                    showAddDialog = false
                    addUrl = ""
                    addLast = ""
                }
                SubscriptionsEvent.OpenEpisodes -> onOpenEpisodes()
                SubscriptionsEvent.OpenQueue -> onOpenQueue()
            }
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissMessage()
    }

    LaunchedEffect(verified) {
        if (verified) {
            onVerifiedHandled()
            viewModel.checkForUpdates()
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.addInProgress) {
                    showAddDialog = false
                    viewModel.dismissAddError()
                }
            },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Tambah langganan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Tempel URL halaman anime (bukan URL episode). Aplikasi akan " +
                            "mengingat episode terakhir dan mengecek episode baru saat " +
                            "tombol Lanjutkan ditekan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    GlassTextField(
                        value = addUrl,
                        onValueChange = {
                            addUrl = it
                            viewModel.dismissAddError()
                        },
                        placeholder = "https://…/anime/judul/",
                        leadingIcon = Icons.Rounded.Link,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    GlassTextField(
                        value = addLast,
                        onValueChange = { addLast = it.filter(Char::isDigit) },
                        placeholder = "Episode terakhir (opsional, 0 = dari awal)",
                        leadingIcon = Icons.Rounded.Tag,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    state.addError?.let { error ->
                        Text(
                            text = error,
                            color = Danger,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (state.needsVerification) {
                        TextButton(onClick = onOpenCloudflare) {
                            Text("Buka verifikasi Cloudflare", color = BrandPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.addSubscription(addUrl, addLast) },
                    enabled = !state.addInProgress,
                ) {
                    if (state.addInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BrandPrimary,
                        )
                    } else {
                        Text("Tambah", color = BrandPrimary)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        viewModel.dismissAddError()
                    },
                    enabled = !state.addInProgress,
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    pendingLast?.let { subscription ->
        var number by remember(subscription.slug) {
            mutableStateOf(subscription.lastEpisode.toString())
        }
        AlertDialog(
            onDismissRequest = { pendingLast = null },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Episode terakhir") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Atur sampai episode berapa ${subscription.name} sudah diunduh. " +
                            "Lanjutkan akan mengambil episode setelah angka ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    GlassTextField(
                        value = number,
                        onValueChange = { number = it.filter(Char::isDigit) },
                        placeholder = "0",
                        leadingIcon = Icons.Rounded.Tag,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = number.toIntOrNull() ?: 0
                        pendingLast = null
                        viewModel.setLastEpisode(subscription, value)
                    },
                ) {
                    Text("Simpan", color = BrandPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLast = null }) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    pendingDelete?.let { subscription ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Hentikan langganan?") },
            text = {
                Text(
                    "${subscription.name} tidak akan dicek lagi oleh tombol Lanjutkan. " +
                        "File yang sudah diunduh tetap tersimpan di Koleksi.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.removeSubscription(subscription)
                    },
                ) {
                    Text("Hentikan", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    val result = state.result
    if (result != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Episode baru ditemukan") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    result.checks.forEach { check ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.toggleCheck(check.subscription.slug) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            NeonCheckbox(
                                checked = check.subscription.slug in state.selectedSlugs,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = check.subscription.name,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = episodesLabel(check.newEpisodes),
                                    color = TextMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    if (result.upToDate.isNotEmpty()) {
                        Text(
                            text = "Sudah terbaru: " +
                                result.upToDate.joinToString { it.name },
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (result.failures.isNotEmpty()) {
                        Text(
                            text = "Gagal diperiksa: " +
                                result.failures.joinToString { it.subscription.name },
                            color = Warning,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    state.quality?.let { quality ->
                        Text(
                            text = "Kualitas: ${quality.format.label} • " +
                                "${quality.resolution} • ${quality.host.label}" +
                                if (state.qualityAutomatic) " (otomatis)" else "",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::downloadSelected,
                    enabled = state.selectedEpisodeCount > 0 && !state.enqueueing,
                ) {
                    if (state.enqueueing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BrandPrimary,
                        )
                    } else {
                        Text(
                            text = "Unduh (${state.selectedEpisodeCount})",
                            color = BrandPrimary,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissResult) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    val checkReport = state.checkReport
    if (checkReport != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCheckReport,
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    if (checkReport.failures.isEmpty()) {
                        "Semua episode terbaru"
                    } else {
                        "Tidak ada episode baru"
                    },
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BrandPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TaskAlt,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            text = if (checkReport.upToDate.isNotEmpty()) {
                                "${checkReport.upToDate.size} anime yang diikuti sudah berada " +
                                    "di episode terbaru. Tidak ada yang perlu diunduh."
                            } else {
                                "Tidak ada episode yang bisa diunduh saat ini."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    checkReport.upToDate.forEach { subscription ->
                        Text(
                            text = "• ${subscription.name} — Ep ${subscription.lastEpisode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                    if (checkReport.failures.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${checkReport.failures.size} anime gagal diperiksa:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Warning,
                        )
                        checkReport.failures.forEach { failure ->
                            Text(
                                text = "• ${failure.subscription.name} — ${failure.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissCheckReport) {
                    Text("Oke", color = BrandPrimary)
                }
            },
        )
    }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Langganan",
                subtitle = if (state.subscriptions.isEmpty()) {
                    "Ikuti anime favoritmu"
                } else {
                    "${state.subscriptions.size} anime diikuti"
                },
                onBack = onBack,
                actions = {
                    GlassIconButton(
                        icon = Icons.Rounded.FileDownload,
                        contentDescription = "Impor langganan dari script",
                        onClick = ::pickImportFile,
                        tint = TextSecondary,
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Tambah langganan",
                        onClick = { showAddDialog = true },
                    )
                },
            )
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandPrimary, strokeWidth = 3.dp)
            }

            state.isEmpty -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    icon = Icons.Rounded.Bookmark,
                    title = "Belum ada langganan",
                    message = "Ikuti anime yang sedang kamu tonton. Aplikasi mengingat " +
                        "episode terakhir, lalu tombol Lanjutkan mengunduh episode baru " +
                        "saja — persis seperti mode subscribe di script.",
                )
                GradientButton(
                    text = "Tambah langganan",
                    onClick = { showAddDialog = true },
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = "Impor dari script (.samehadaku_subs.json)",
                    onClick = ::pickImportFile,
                    icon = Icons.Rounded.FileDownload,
                    modifier = Modifier.fillMaxWidth(),
                    height = 46.dp,
                    tint = TextSecondary,
                )
                Spacer(Modifier.height(24.dp))
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item {
                    GradientButton(
                        text = if (state.checking) {
                            state.checkProgress.ifBlank { "Memeriksa langganan…" }
                        } else {
                            "Lanjutkan — cek episode baru"
                        },
                        onClick = viewModel::checkForUpdates,
                        loading = state.checking,
                        icon = Icons.Rounded.Sync,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    Text(
                        text = state.quality?.let {
                            "Kualitas: ${it.format.label} • ${it.resolution} • ${it.host.label}"
                        } ?: "Kualitas dipilih otomatis saat pertama kali melanjutkan.",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (state.autoCheckEnabled) {
                            "Cek otomatis setiap ${state.autoCheckIntervalHours} jam • " +
                                "episode baru langsung diunduh"
                        } else {
                            "Cek otomatis nonaktif • atur di Pengaturan"
                        },
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                if (state.needsVerification) {
                    item {
                        InfoBanner(
                            title = "Verifikasi Cloudflare diperlukan",
                            message = "Situs meminta pemeriksaan Cloudflare. Buka halaman " +
                                "verifikasi, tunggu hingga selesai, lalu tekan Lanjutkan lagi.",
                            icon = Icons.Rounded.CloudOff,
                            accent = Warning,
                            actionLabel = "Buka verifikasi",
                            onAction = onOpenCloudflare,
                        )
                    }
                }

                items(state.subscriptions, key = { it.slug }) { subscription ->
                    SubscriptionRow(
                        subscription = subscription,
                        onOpenEpisodes = { viewModel.openEpisodes(subscription) },
                        onMarkLast = { pendingLast = subscription },
                        onRemove = { pendingDelete = subscription },
                    )
                }

                item {
                    GlassButton(
                        text = "Impor dari script",
                        onClick = ::pickImportFile,
                        icon = Icons.Rounded.FileDownload,
                        modifier = Modifier.fillMaxWidth(),
                        height = 44.dp,
                        tint = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    subscription: Subscription,
    onOpenEpisodes: () -> Unit,
    onMarkLast: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
        onClick = onOpenEpisodes,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(BrandPrimary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = null,
                    tint = BrandPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("Terakhir Ep ")
                        append(subscription.lastEpisode)
                        if (subscription.updatedAt > 0L) {
                            append(" • ")
                            append(shortDate(subscription.updatedAt))
                        }
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box {
                GlassIconButton(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "Aksi ${subscription.name}",
                    onClick = { menuOpen = true },
                    size = 30.dp,
                    iconSize = 16.dp,
                    tint = TextSecondary,
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Buka daftar episode") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.PlaylistPlay,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onOpenEpisodes()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Atur episode terakhir") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onMarkLast()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hentikan langganan", color = Danger) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = Danger,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

private fun episodesLabel(episodes: List<Episode>): String {
    if (episodes.isEmpty()) return "Tidak ada episode"
    val numbers = episodes.map { it.number }.sorted()
    val consecutive = numbers.zipWithNext().all { (first, second) -> second == first + 1 }
    return when {
        numbers.size == 1 -> "Ep ${numbers.first()}"
        consecutive -> "Ep ${numbers.first()}–${numbers.last()} • ${numbers.size} episode"
        else -> numbers.joinToString(", ") { "Ep $it" }
    }
}
