package app.samehadaku.dl.presentation.collections

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.data.download.StoredFile
import app.samehadaku.dl.data.download.StoredFolder
import app.samehadaku.dl.presentation.components.AppBottomBar
import app.samehadaku.dl.presentation.components.AppTab
import app.samehadaku.dl.presentation.components.EmptyState
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.IconDot
import app.samehadaku.dl.presentation.components.MetricTile
import app.samehadaku.dl.presentation.components.SearchField
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.BrandGold
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.SlateSurface
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.theme.titleAccent
import app.samehadaku.dl.presentation.util.formatBytes
import app.samehadaku.dl.presentation.util.prettyName
import app.samehadaku.dl.presentation.util.shortDate

@Composable
fun CollectionsScreen(
    onTabSelected: (AppTab) -> Unit,
    onBrowse: () -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var expandedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<StoredFolder?>(null) }
    var pendingDeleteFile by remember { mutableStateOf<PendingFileDelete?>(null) }

    val context = LocalContext.current
    // MediaStore only returns other apps' files (like our episodes) when the app
    // holds read access to shared storage; ask for it right when Koleksi opens.
    val readPermission = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            Manifest.permission.READ_MEDIA_VIDEO
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            Manifest.permission.READ_EXTERNAL_STORAGE
        else -> null
    }
    var storageDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        storageDenied = !granted
        viewModel.refresh()
    }
    LaunchedEffect(Unit) {
        val needsRequest = readPermission != null &&
            ContextCompat.checkSelfPermission(context, readPermission) !=
            PackageManager.PERMISSION_GRANTED
        if (needsRequest) {
            permissionLauncher.launch(readPermission)
        } else {
            storageDenied = false
        }
        viewModel.refresh()
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    pendingDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Hapus folder ${prettyName(folder.name)}?") },
            text = {
                Text(
                    "${folder.files.size} file (${formatBytes(folder.totalBytes)}) di folder ini " +
                        "akan dihapus permanen dari penyimpanan. Tindakan ini tidak bisa dibatalkan.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteFolder(folder)
                    },
                ) {
                    Text("Hapus folder", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    pendingDeleteFile?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDeleteFile = null },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Hapus file ini?") },
            text = {
                Text(
                    "${pending.file.name} (${formatBytes(pending.file.size)}) akan dihapus " +
                        "permanen dari penyimpanan. Tindakan ini tidak bisa dibatalkan.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteFile = null
                        viewModel.deleteFile(pending.folder, pending.file)
                    },
                ) {
                    Text("Hapus file", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFile = null }) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Koleksi",
                subtitle = when {
                    state.loading && state.allFolders.isEmpty() -> "Memindai penyimpanan…"
                    state.folders.isEmpty() -> "Folder anime hasil unduhan"
                    else -> "${state.folders.size} folder • ${state.fileCount} file"
                },
                actions = {
                    GlassIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Muat ulang koleksi",
                        onClick = viewModel::refresh,
                        tint = TextSecondary,
                    )
                },
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedTab = AppTab.COLLECTIONS,
                onTabSelected = onTabSelected,
            )
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        when {
            state.loading && state.allFolders.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = BrandPrimary,
                    strokeWidth = 3.dp,
                )
            }

            state.isEmpty -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (storageDenied) {
                    EmptyState(
                        icon = Icons.Rounded.FolderOpen,
                        title = "Izin penyimpanan diperlukan",
                        message = "Koleksi perlu izin baca penyimpanan untuk menampilkan " +
                            "file episode yang sudah diunduh. Izinkan akses lalu muat ulang.",
                        actionLabel = "Beri izin",
                        onAction = {
                            readPermission?.let { permissionLauncher.launch(it) }
                        },
                    )
                } else {
                    EmptyState(
                        icon = Icons.Rounded.FolderOpen,
                        title = "Koleksi masih kosong",
                        message = buildString {
                            append("File episode yang selesai diunduh otomatis muncul di sini ")
                            append("sebagai folder per judul anime. Tekan lama folder untuk aksi cepat.")
                            if (state.location.isNotBlank()) {
                                append("\n\nLokasi dipindai: ")
                                append(state.location)
                            }
                            if (state.location.startsWith("Folder kustom")) {
                                append("\nFolder kustom tidak terbaca? Pilih ulang folder di ")
                                append("Pengaturan › Penyimpanan agar izinnya segar.")
                            }
                        },
                        actionLabel = "Mulai unduh",
                        onAction = onBrowse,
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    CollectionsSummary(
                        folders = state.totalFolders,
                        files = state.totalFiles,
                        bytes = state.totalBytes,
                        location = state.location,
                        updatedAt = state.latestModified,
                    )
                }

                item {
                    SearchField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = "Cari judul anime atau nama file...",
                    )
                }

                if (state.hasNoMatch) {
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
                                icon = Icons.Rounded.FolderOpen,
                                title = "Koleksi tidak ditemukan",
                                message = "Coba kata kunci lain untuk menemukan folder anime.",
                            )
                        }
                    }
                } else {
                    items(state.folders, key = { it.name }) { folder ->
                        FolderGroup(
                            // Folders fade in/out and the list reflows smoothly while
                            // the user types in the search field.
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(durationMillis = 240),
                                placementSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                ),
                                fadeOutSpec = tween(durationMillis = 160),
                            ),
                            folder = folder,
                            cover = state.covers[folder.name],
                            expanded = expandedFolder == folder.name,
                            onToggle = {
                                expandedFolder = if (expandedFolder == folder.name) {
                                    null
                                } else {
                                    folder.name
                                }
                            },
                            onOpenLatest = { viewModel.openLatest(folder) },
                            onShareLatest = { viewModel.shareLatest(folder) },
                            onDeleteFolder = { pendingDelete = folder },
                            onOpenFile = { file -> viewModel.openFile(folder, file) },
                            onShareFile = { file -> viewModel.shareFile(folder, file) },
                            onDeleteFile = { file ->
                                pendingDeleteFile = PendingFileDelete(folder, file)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionsSummary(
    folders: Int,
    files: Int,
    bytes: Long,
    location: String,
    updatedAt: Long,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(16.dp),
        glow = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDot(
                icon = Icons.Rounded.FolderOpen,
                size = 38.dp,
                tint = BrandPrimary,
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Koleksi Tersimpan",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$folders folder • $files file",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (updatedAt > 0L) {
                Spacer(Modifier.width(8.dp))
                StatusPill(text = "Diperbarui ${shortDate(updatedAt)}", color = BrandMint)
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                label = "Folder",
                value = folders.toString(),
                icon = Icons.Rounded.Folder,
                accent = BrandPrimary,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "File",
                value = files.toString(),
                icon = Icons.Rounded.Movie,
                accent = BrandMint,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Ukuran",
                value = formatBytes(bytes),
                icon = Icons.Rounded.Storage,
                accent = BrandGold,
                modifier = Modifier.weight(1f),
            )
        }

        if (location.isNotBlank()) {
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
                    text = location,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class PendingFileDelete(
    val folder: StoredFolder,
    val file: StoredFile,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGroup(
    modifier: Modifier = Modifier,
    folder: StoredFolder,
    cover: Bitmap?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenLatest: () -> Unit,
    onShareLatest: () -> Unit,
    onDeleteFolder: () -> Unit,
    onOpenFile: (StoredFile) -> Unit,
    onShareFile: (StoredFile) -> Unit,
    onDeleteFile: (StoredFile) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = titleAccent(folder.name)
    val meta = buildList {
        if (folder.totalBytes > 0L) add(formatBytes(folder.totalBytes))
        if (folder.lastModified > 0L) add(shortDate(folder.lastModified))
    }.joinToString(" • ")

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
        highlighted = expanded,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClickLabel = if (expanded) "Tutup folder" else "Buka folder",
                    onLongClickLabel = "Aksi folder",
                    onClick = onToggle,
                    onLongClick = { menuOpen = true },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolderThumbnail(
                folder = folder,
                cover = cover,
                accent = accent,
                expanded = expanded,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prettyName(folder.name),
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
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Text(
                        text = meta.ifBlank { "Episode tersimpan" },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(
                text = "${folder.files.size} EP",
                color = accent,
            )
            Spacer(Modifier.width(2.dp))
            Box {
                GlassIconButton(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "Aksi folder ${prettyName(folder.name)}",
                    onClick = { menuOpen = true },
                    size = 30.dp,
                    iconSize = 16.dp,
                    tint = TextSecondary,
                )
                FolderMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    canOpen = folder.files.isNotEmpty(),
                    onOpenLatest = {
                        menuOpen = false
                        onOpenLatest()
                    },
                    onShareLatest = {
                        menuOpen = false
                        onShareLatest()
                    },
                    onDeleteFolder = {
                        menuOpen = false
                        onDeleteFolder()
                    },
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Tutup folder" else "Buka folder",
                tint = if (expanded) accent else TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }

        if (expanded) {
            Spacer(Modifier.height(11.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(accent.copy(alpha = 0.18f)),
            )
            Spacer(Modifier.height(9.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                folder.files.forEach { file ->
                    FileRow(
                        file = file,
                        accent = accent,
                        onOpen = { onOpenFile(file) },
                        onShare = { onShareFile(file) },
                        onDelete = { onDeleteFile(file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canOpen: Boolean,
    onOpenLatest: () -> Unit,
    onShareLatest: () -> Unit,
    onDeleteFolder: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Buka file terakhir") },
            enabled = canOpen,
            leadingIcon = {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = onOpenLatest,
        )
        DropdownMenuItem(
            text = { Text("Bagikan file terakhir") },
            enabled = canOpen,
            leadingIcon = {
                Icon(
                    Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = onShareLatest,
        )
        DropdownMenuItem(
            text = { Text("Hapus folder beserta file", color = Danger) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = Danger,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = onDeleteFolder,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: StoredFile,
    accent: Color,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TextPrimary.copy(alpha = 0.035f))
            .border(1.dp, accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = 24.dp,
                ),
            )
            Text(
                text = buildString {
                    append(formatBytes(file.size))
                    if (file.modifiedAt > 0L) {
                        append(" • ")
                        append(shortDate(file.modifiedAt))
                    }
                },
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlassIconButton(
            icon = Icons.Rounded.PlayArrow,
            contentDescription = "Buka ${file.name}",
            onClick = onOpen,
            tint = accent,
            size = 28.dp,
            iconSize = 14.dp,
        )
        GlassIconButton(
            icon = Icons.Rounded.Share,
            contentDescription = "Bagikan ${file.name}",
            onClick = onShare,
            tint = TextSecondary,
            size = 28.dp,
            iconSize = 14.dp,
        )
        GlassIconButton(
            icon = Icons.Rounded.DeleteOutline,
            contentDescription = "Hapus ${file.name}",
            onClick = onDelete,
            tint = Danger,
            size = 28.dp,
            iconSize = 14.dp,
        )
    }
}

@Composable
private fun FolderThumbnail(
    folder: StoredFolder,
    cover: Bitmap?,
    accent: Color,
    expanded: Boolean,
) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.07f)),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.30f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = "Cover ${prettyName(folder.name)}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = if (expanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
