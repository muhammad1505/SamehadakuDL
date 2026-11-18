package app.samehadaku.dl.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.BuildConfig
import app.samehadaku.dl.core.SmartStoragePolicy
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.VideoFormat
import app.samehadaku.dl.presentation.components.AppBottomBar
import app.samehadaku.dl.presentation.components.AppTab
import app.samehadaku.dl.presentation.components.BrandMark
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.GradientButton
import app.samehadaku.dl.presentation.components.MetricTile
import app.samehadaku.dl.presentation.components.PillChip
import app.samehadaku.dl.presentation.components.pressable
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.SegmentTab
import app.samehadaku.dl.presentation.components.SegmentedTabs
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.BrandGold
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.Ink
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.theme.Warning
import app.samehadaku.dl.presentation.util.formatBytes
import app.samehadaku.dl.presentation.util.shortDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onTabSelected: (AppTab) -> Unit,
    onOpenCloudflare: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSmartStorage: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showCleanupConfirm by remember { mutableStateOf(false) }
    val backgroundScope = rememberCoroutineScope()
    val showBackgroundMessage: (String) -> Unit = { message ->
        backgroundScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Some providers grant only temporary access; saving still works for this session.
            }
            viewModel.setFolder(uri.toString())
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSession()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Pengaturan",
                subtitle = "Sesuaikan pengalaman unduhanmu",
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedTab = AppTab.SETTINGS,
                onTabSelected = onTabSelected,
            )
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SettingsSummary(state)
            }

            item {
                SettingSection(
                    title = "Penyimpanan",
                    subtitle = "Lokasi file hasil unduhan",
                    icon = Icons.Rounded.Folder,
                    tint = BrandPrimary,
                    badge = if (state.treeUri == null) "Default" else "Kustom",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StorageOption(
                            icon = Icons.Rounded.Download,
                            title = "Penyimpanan default",
                            subtitle = "Download/SamehadakuDL/<judul anime>",
                            selected = state.treeUri == null,
                            onClick = {
                                if (state.treeUri != null) viewModel.clearFolder()
                            },
                        )
                        StorageOption(
                            icon = Icons.Rounded.Folder,
                            title = "Folder pilihan sendiri",
                            subtitle = state.treeUri?.let(::prettyFolderPath)
                                ?: "Pilih folder di penyimpanan perangkat",
                            selected = state.treeUri != null,
                            badge = if (state.treeUri == null) "Pilih" else "Ganti",
                            onClick = { folderPicker.launch(state.treeUri?.let(Uri::parse)) },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Caption(
                        "File episode disimpan ke folder yang dipilih setelah unduhan selesai. " +
                            "Folder pilihan juga menjadi cadangan untuk membaca file lama bila " +
                            "izin akses file tidak diberikan.",
                    )
                }
            }

            item {
                val graceOptions = SmartStoragePolicy.graceDayOptions
                val selectedGrace = graceOptions
                    .indexOf(state.smartStorageGraceDays)
                    .coerceAtLeast(0)
                val lastCleanup = if (state.smartLastCleanupAt > 0L) {
                    shortDateTime(state.smartLastCleanupAt)
                } else {
                    "belum pernah"
                }
                SettingSection(
                    title = "Smart Storage",
                    subtitle = "Hapus episode yang sudah ditonton",
                    icon = Icons.Rounded.AutoDelete,
                    tint = BrandAccent,
                    badge = if (state.smartStorageEnabled) "Aktif" else "Nonaktif",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bersihkan otomatis",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Hanya video hasil unduhan aplikasi yang sudah ditandai " +
                                    "tonton. Pembersihan otomatis menunggu masa tenggang; " +
                                    "tombol manual menghapus sekarang.",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = state.smartStorageEnabled,
                            onCheckedChange = viewModel::setSmartStorageEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = BrandMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                                uncheckedBorderColor = Color.White.copy(alpha = 0.16f),
                            ),
                        )
                    }
                    if (state.smartStorageEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Masa tenggang pembersihan otomatis",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(7.dp))
                        SegmentedTabs(
                            tabs = graceOptions.map { days ->
                                SegmentTab(label = "$days hari")
                            },
                            selectedIndex = selectedGrace,
                            onSelect = { index ->
                                viewModel.setSmartStorageGraceDays(graceOptions[index])
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                        val limitOptions = SmartStoragePolicy.storageLimitOptions
                        val selectedLimit = limitOptions
                            .indexOf(state.smartStorageLimitGb)
                            .coerceAtLeast(0)
                        Text(
                            text = "Batas penyimpanan episode tonton",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(7.dp))
                        SegmentedTabs(
                            tabs = limitOptions.map { gigabytes ->
                                SegmentTab(
                                    label = if (gigabytes == 0) "Tanpa batas" else "$gigabytes GB",
                                )
                            },
                            selectedIndex = selectedLimit,
                            onSelect = { index ->
                                viewModel.setSmartStorageLimitGb(limitOptions[index])
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(
                                label = "Ditandai",
                                value = state.smartWatchedCount.toString(),
                                icon = Icons.Rounded.Visibility,
                                accent = BrandMint,
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "Terpantau",
                                value = formatBytes(state.smartWatchedBytes),
                                icon = Icons.Rounded.Schedule,
                                accent = BrandAccent,
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "Dibersihkan",
                                value = formatBytes(state.smartReclaimedBytes),
                                icon = Icons.Rounded.DeleteSweep,
                                accent = BrandGold,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        GlassButton(
                            text = "Kelola episode",
                            onClick = onOpenSmartStorage,
                            icon = Icons.Rounded.Visibility,
                            modifier = Modifier.fillMaxWidth(),
                            height = 44.dp,
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassButton(
                            text = if (state.smartCleaning) {
                                "Membersihkan…"
                            } else {
                                "Bersihkan sekarang"
                            },
                            onClick = { showCleanupConfirm = true },
                            icon = Icons.Rounded.AutoDelete,
                            modifier = Modifier.fillMaxWidth(),
                            height = 44.dp,
                            tint = BrandAccent,
                            enabled = !state.smartCleaning,
                        )
                        Spacer(Modifier.height(9.dp))
                        Caption(
                            "Terakhir diperiksa: $lastCleanup • ${state.smartDeletedCount} episode " +
                                "sudah dihapus. Tombol di atas menghapus episode tonton sekarang " +
                                "tanpa menunggu masa tenggang; masa tenggang hanya berlaku untuk " +
                                "pembersihan otomatis. Episode yang diputar lewat pemutar eksternal " +
                                "ditandai tonton saat kembali ke aplikasi setelah sekitar 80% durasi. " +
                                "Tanda tonton juga bisa diatur manual dari Koleksi. File yang " +
                                "ukurannya berubah atau ditolak penyimpanan tidak akan dihapus.",
                        )
                    } else {
                        Caption(
                            "Nonaktif secara default. Saat diaktifkan, aplikasi hanya menghapus " +
                                "video yang diunduh sendiri setelah ditandai tonton. Pembersihan " +
                                "otomatis menunggu masa tenggang; tombol manual bisa menghapus " +
                                "lebih cepat. Arsip, file pengguna lain, dan video di luar folder " +
                                "unduhan tidak disentuh.",
                        )
                    }
                }
            }

            item {
                SettingSection(
                    title = "Unduhan",
                    subtitle = "Jumlah unduhan bersamaan",
                    icon = Icons.Rounded.Download,
                    tint = BrandMint,
                    badge = "${state.concurrency}x",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Unduhan paralel",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = when (state.concurrency) {
                                    1 -> "Paling hemat data"
                                    2 -> "Seimbang"
                                    else -> "Paling cepat"
                                },
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassIconButton(
                                icon = Icons.Rounded.Remove,
                                contentDescription = "Kurangi",
                                onClick = {
                                    if (state.concurrency > 1) {
                                        viewModel.setConcurrency(state.concurrency - 1)
                                    }
                                },
                                tint = if (state.concurrency > 1) TextPrimary else TextMuted,
                                size = 36.dp,
                                iconSize = 17.dp,
                                enabled = state.concurrency > 1,
                            )
                            Text(
                                text = state.concurrency.toString(),
                                color = BrandMint,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                            GlassIconButton(
                                icon = Icons.Rounded.Add,
                                contentDescription = "Tambah",
                                onClick = {
                                    if (state.concurrency < 3) {
                                        viewModel.setConcurrency(state.concurrency + 1)
                                    }
                                },
                                tint = if (state.concurrency < 3) TextPrimary else TextMuted,
                                size = 36.dp,
                                iconSize = 17.dp,
                                enabled = state.concurrency < 3,
                            )
                        }
                    }
                    Caption("Nilai 1 paling hemat data. Nilai 3 mengunduh hingga tiga episode sekaligus.")
                }
            }

            item {
                val intervals = listOf(15, 30, 60, 180)
                val selectedIntervalIndex = intervals.indexOf(state.autoCheckIntervalMinutes)
                    .takeIf { it >= 0 }
                    ?: intervals.indices.minByOrNull {
                        kotlin.math.abs(intervals[it] - state.autoCheckIntervalMinutes)
                    } ?: 0
                val lastChecked = if (state.lastAutoCheckAt > 0L) {
                    shortDateTime(state.lastAutoCheckAt)
                } else {
                    "belum pernah"
                }
                SettingSection(
                    title = "Cek otomatis",
                    subtitle = "Episode baru dari anime yang diikuti",
                    icon = Icons.Rounded.NotificationsActive,
                    tint = BrandMint,
                    badge = if (state.autoCheckEnabled) {
                        intervalLabel(state.autoCheckIntervalMinutes)
                    } else {
                        "Nonaktif"
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Periksa di latar belakang",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Episode baru otomatis diunduh dengan kualitas " +
                                    "tersimpan, lalu notifikasi membuka antrean Unduhan.",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = state.autoCheckEnabled,
                            onCheckedChange = viewModel::setAutoCheckEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = BrandMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                                uncheckedBorderColor = Color.White.copy(alpha = 0.16f),
                            ),
                        )
                    }
                    if (state.autoCheckEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Interval pemeriksaan",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(7.dp))
                        SegmentedTabs(
                            tabs = intervals.map { minutes ->
                                SegmentTab(label = intervalLabel(minutes))
                            },
                            selectedIndex = selectedIntervalIndex,
                            onSelect = { index ->
                                viewModel.setAutoCheckInterval(intervals[index])
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        GlassButton(
                            text = "Cek sekarang",
                            onClick = viewModel::runAutoCheckNow,
                            icon = Icons.Rounded.Sync,
                            modifier = Modifier.fillMaxWidth(),
                            height = 44.dp,
                            tint = BrandMint,
                        )
                        Spacer(Modifier.height(9.dp))
                        Caption(
                            "Terakhir dicek: $lastChecked. Pemeriksaan juga dijalankan saat " +
                                "aplikasi dibuka bila jadwalnya sudah lewat, jadi episode baru " +
                                "cepat terlihat. Jika cek sering tertunda, atur izin di panel " +
                                "“Latar belakang & autostart” di bawah.",
                        )
                    } else {
                        Caption(
                            "Aktifkan agar aplikasi memeriksa episode baru secara berkala " +
                                "dan memberi notifikasi. Izin agar pemeriksaan tidak tertunda " +
                                "diatur di panel “Latar belakang & autostart” di bawah.",
                        )
                    }
                }
            }

            item {
                BackgroundAccessSection(onMessage = showBackgroundMessage)
            }

            item {
                SettingSection(
                    title = "Coba ulang otomatis",
                    subtitle = "Unduhan yang gagal karena link mati",
                    icon = Icons.Rounded.Restore,
                    tint = Warning,
                    badge = if (state.failedRetryEnabled) "Aktif" else "Nonaktif",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ulangi unduhan gagal",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Link host sering diunggah ulang beberapa jam kemudian. " +
                                    "Aplikasi memeriksa ulang lalu mengunduhnya sendiri.",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = state.failedRetryEnabled,
                            onCheckedChange = viewModel::setFailedRetryEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = BrandMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                                uncheckedBorderColor = Color.White.copy(alpha = 0.16f),
                            ),
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Caption(
                        "Maksimal 3 kali per episode, dengan jeda 12 jam sejak percobaan " +
                            "terakhir. Hanya unduhan gagal atau link mati yang diulang.",
                    )
                }
            }

            item {
                val quality = state.quality
                val resolutions = listOf("360p", "480p", "720p", "1080p")
                SettingSection(
                    title = "Kualitas unduhan",
                    subtitle = "Format, resolusi, dan server pilihan",
                    icon = Icons.Rounded.Download,
                    tint = BrandGold,
                    badge = quality?.let { "${it.resolution} • ${it.host.label}" } ?: "Otomatis",
                ) {
                    if (quality == null) {
                        Caption(
                            "Pilih sekali, lalu semua unduhan manual dan cek otomatis " +
                                "memakai pilihan ini.",
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        text = "Format",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    SegmentedTabs(
                        tabs = VideoFormat.entries.map { format ->
                            SegmentTab(label = format.label)
                        },
                        selectedIndex = quality?.format
                            ?.let { VideoFormat.entries.indexOf(it) } ?: -1,
                        onSelect = { index ->
                            viewModel.setQualityFormat(VideoFormat.entries[index])
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Resolusi",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    SegmentedTabs(
                        tabs = resolutions.map { resolution ->
                            SegmentTab(label = resolution.uppercase())
                        },
                        selectedIndex = quality?.resolution
                            ?.let { resolutions.indexOf(it) } ?: -1,
                        onSelect = { index ->
                            viewModel.setQualityResolution(resolutions[index])
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Server",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Host.entries.forEach { host ->
                            PillChip(
                                label = host.label,
                                selected = quality?.host == host,
                                onClick = { viewModel.setQualityHost(host) },
                            )
                        }
                    }
                    if (quality != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Kembalikan ke otomatis",
                            color = BrandAccent,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .pressable(pressedScale = 0.95f) {
                                    viewModel.clearQualityPreference()
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .padding(4.dp),
                        )
                    }
                }
            }

            item {
                SettingSection(
                    title = "Host didukung",
                    subtitle = "Server unduhan yang dikenali",
                    icon = Icons.Rounded.CloudDone,
                    tint = BrandMint,
                    badge = "${Host.entries.size} host",
                ) {
                    Host.entries.chunked(3).forEach { hosts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            hosts.forEach { host ->
                                StatusPill(
                                    text = host.label,
                                    color = if (host == state.quality?.host) {
                                        BrandMint
                                    } else {
                                        TextSecondary
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Caption(
                        "Server pilihan dipakai lebih dulu. Bila link mati, aplikasi " +
                            "berpindah otomatis ke server lain pada format dan resolusi yang sama.",
                    )
                }
            }

            item {
                SettingSection(
                    title = "Sesuaikan kualitas otomatis",
                    subtitle = "Bila format atau resolusi pilihan tidak tersedia",
                    icon = Icons.Rounded.Tune,
                    tint = BrandAccent,
                    badge = if (state.qualityDowngrade) "Otomatis" else "Tetap",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pakai link terdekat yang tersedia",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Misalnya pilihan X265 480p Pixeldrain tidak ada, " +
                                    "episode baru diunduh lewat MKV 480p Pixeldrain.",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = state.qualityDowngrade,
                            onCheckedChange = viewModel::setQualityDowngrade,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = BrandMint,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                                uncheckedBorderColor = Color.White.copy(alpha = 0.16f),
                            ),
                        )
                    }
                }
            }

            item {
                SettingSection(
                    title = "Cloudflare",
                    subtitle = "Sesi verifikasi situs",
                    icon = Icons.Rounded.Cloud,
                    tint = Warning,
                ) {
                    CloudflareStatusCard(active = state.sessionActive)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GradientButton(
                            text = if (state.sessionActive) {
                                "Verifikasi ulang"
                            } else {
                                "Verifikasi sekarang"
                            },
                            onClick = onOpenCloudflare,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Cloud,
                            height = 46.dp,
                        )
                        if (state.sessionActive) {
                            GlassButton(
                                text = "Reset",
                                onClick = viewModel::resetCloudflareSession,
                                modifier = Modifier.weight(0.48f),
                                icon = Icons.Rounded.DeleteOutline,
                                height = 46.dp,
                                tint = Danger,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Caption(
                        "Reset sesi bila situs terus menolak akses atau verifikasi sudah kedaluwarsa. " +
                            "Setelah reset, lakukan verifikasi ulang.",
                    )
                }
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(14.dp),
                    onClick = onOpenLogs,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            BrandAccent.copy(alpha = 0.24f),
                                            BrandAccent.copy(alpha = 0.06f),
                                        ),
                                    ),
                                )
                                .border(1.dp, BrandAccent.copy(alpha = 0.28f), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BugReport,
                                contentDescription = null,
                                tint = BrandAccent,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Log aplikasi",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Pantau proses pencarian, mirror, host, dan unduhan",
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        StatusPill(
                            text = "Buka",
                            color = BrandAccent,
                        )
                    }
                }
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(14.dp),
                    onClick = onOpenAbout,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            BrandGold.copy(alpha = 0.26f),
                                            BrandGold.copy(alpha = 0.07f),
                                        ),
                                    ),
                                )
                                .border(1.dp, BrandGold.copy(alpha = 0.30f), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = BrandGold,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tentang aplikasi",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = BuildConfig.APPLICATION_ID,
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        StatusPill(
                            text = "v${BuildConfig.VERSION_NAME}",
                            color = BrandGold,
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                    Text(
                        text = "Pengunduh episode Samehadaku untuk penggunaan pribadi. " +
                            "Aplikasi tidak meng-host konten apa pun. Hormati hak cipta pemilik konten.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showCleanupConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirm = false },
            title = {
                Text(
                    text = "Hapus episode tonton sekarang?",
                    color = TextPrimary,
                )
            },
            text = {
                Text(
                    text = "Episode yang ditandai tonton akan dihapus sekarang tanpa " +
                        "menunggu masa tenggang. Tindakan ini tidak bisa dibatalkan. " +
                        "Hanya video hasil unduhan aplikasi dengan ukuran yang masih " +
                        "sama yang dihapus.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCleanupConfirm = false
                        viewModel.cleanWatchedNow()
                    },
                ) {
                    Text("Hapus sekarang", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupConfirm = false }) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun SettingsSummary(state: SettingsUiState) {
    val storageLabel = if (state.treeUri == null) "Default" else "Kustom"
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(16.dp),
        glow = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 44.dp)
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextPrimary)) { append("Samehadaku ") }
                        withStyle(SpanStyle(color = BrandPrimary)) { append("Downloader") }
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Pengaturan • v${BuildConfig.VERSION_NAME}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                label = "Lokasi",
                value = storageLabel,
                icon = Icons.Rounded.Folder,
                accent = BrandPrimary,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Paralel",
                value = "${state.concurrency}x",
                icon = Icons.Rounded.Download,
                accent = BrandMint,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Kualitas",
                value = if (state.qualityDowngrade) "Otomatis" else "Tetap",
                icon = Icons.Rounded.Tune,
                accent = BrandAccent,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
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
                text = state.treeUri?.let(::prettyFolderPath) ?: "Download/SamehadakuDL",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusPill(
                text = if (state.sessionActive) "Cloudflare aktif" else "Cloudflare nonaktif",
                color = if (state.sessionActive) BrandMint else Warning,
            )
        }
    }
}

@Composable
private fun StorageOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val iconTint = if (selected) BrandPrimary else TextSecondary
    Row(
        modifier = Modifier
            .pressable(role = Role.Button, onClick = onClick)
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    if (selected) {
                        listOf(BrandPrimary.copy(alpha = 0.15f), BrandAccent.copy(alpha = 0.05f))
                    } else {
                        listOf(Color.White.copy(alpha = 0.045f), Color.White.copy(alpha = 0.018f))
                    },
                ),
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    BrandPrimary.copy(alpha = 0.34f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(BrandPrimary.copy(alpha = 0.18f))
                    .border(1.dp, BrandPrimary.copy(alpha = 0.34f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Dipilih",
                    tint = BrandPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
        } else if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BrandPrimary.copy(alpha = 0.12f))
                    .border(1.dp, BrandPrimary.copy(alpha = 0.24f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = badge,
                    color = BrandPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CloudflareStatusCard(active: Boolean) {
    val tint = if (active) BrandMint else Warning
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(tint.copy(alpha = 0.15f), Color.White.copy(alpha = 0.025f)),
                ),
            )
            .border(1.dp, tint.copy(alpha = 0.30f), shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.07f)),
                    ),
                )
                .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (active) Icons.Rounded.CloudDone else Icons.Rounded.Cloud,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (active) "Sesi Cloudflare aktif" else "Belum ada sesi tersimpan",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (active) {
                    "Situs dapat diakses tanpa verifikasi ulang."
                } else {
                    "Verifikasi sekali agar akses situs lancar."
                },
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(
            text = if (active) "Aktif" else "Nonaktif",
            color = tint,
        )
    }
}

private fun intervalLabel(minutes: Int): String =
    if (minutes % 60 == 0) "${minutes / 60} jam" else "$minutes mnt"

private fun prettyFolderPath(treeUri: String): String {
    val documentId = runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
    }.getOrNull()
    if (documentId.isNullOrBlank()) return "Folder kustom aktif"
    val volume = documentId.substringBefore(':', missingDelimiterValue = "")
    val path = documentId.substringAfter(':', missingDelimiterValue = "")
    val root = when (volume.lowercase()) {
        "primary" -> "Penyimpanan internal"
        "home" -> "Beranda"
        else -> "Penyimpanan"
    }
    return if (path.isBlank()) root else "$root/$path"
}

@Composable
internal fun SettingSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    badge: String? = null,
    content: @Composable () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.07f)),
                        ),
                    )
                    .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(tint),
                    )
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                StatusPill(text = badge, color = tint)
            }
        }
        Spacer(Modifier.height(13.dp))
        content()
    }
}

@Composable
internal fun Caption(text: String) {
    Spacer(Modifier.height(2.dp))
    Text(
        text = text,
        color = TextMuted,
        style = MaterialTheme.typography.bodySmall,
    )
}
