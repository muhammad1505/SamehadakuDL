package app.samehadaku.dl.presentation.home

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.BuildConfig
import app.samehadaku.dl.presentation.components.AppBottomBar
import app.samehadaku.dl.presentation.components.AppTab
import app.samehadaku.dl.presentation.components.BrandMark
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.GlassTextField
import app.samehadaku.dl.presentation.components.GradientButton
import app.samehadaku.dl.presentation.components.IconDot
import app.samehadaku.dl.presentation.components.InfoBanner
import app.samehadaku.dl.presentation.components.SceneryArt
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.SectionHeader
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.components.TinyStatusDot
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.Ink
import app.samehadaku.dl.presentation.theme.SlateSurface
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.theme.Warning
import app.samehadaku.dl.presentation.util.prettyTitleFromUrl
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenEpisodes: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenCloudflare: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onTabSelected: (AppTab) -> Unit,
    verified: Boolean,
    onVerifiedHandled: () -> Unit,
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var showAllRecent by rememberSaveable { mutableStateOf(false) }
    var confirmClearRecent by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.OpenEpisodes -> onOpenEpisodes()
                HomeEvent.OpenQuality -> onOpenQuality()
            }
        }
    }

    LaunchedEffect(verified) {
        if (verified) {
            onVerifiedHandled()
            viewModel.onVerifiedReturned()
        }
    }

    LaunchedEffect(sharedUrl) {
        val url = sharedUrl?.trim().orEmpty()
        if (url.isNotEmpty()) {
            viewModel.onUrlChange(url)
            viewModel.detect()
            onSharedUrlConsumed()
        }
    }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    fun startDetection() {
        if (state.loading) return
        keyboard?.hide()
        focusManager.clearFocus()
        viewModel.detect()
    }

    fun pasteAndDetect() {
        val text = clipboard.getText()?.text?.trim().orEmpty()
        if (!looksLikeUrl(text)) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Clipboard kosong atau bukan link Samehadaku. Salin link dulu, ya.",
                )
            }
            return
        }
        viewModel.onUrlChange(text)
        startDetection()
    }

    fun handleUrlChange(value: String) {
        val previous = state.url
        viewModel.onUrlChange(value)
        if (state.loading) return
        val inserted = value.trim().length - previous.trim().length
        if (inserted >= 8 && looksLikeUrl(value) && value.trim() != previous.trim()) {
            startDetection()
        }
    }

    if (confirmClearRecent) {
        AlertDialog(
            onDismissRequest = { confirmClearRecent = false },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Bersihkan riwayat URL?") },
            text = {
                Text(
                    "Semua URL terakhir akan dihapus dari beranda. Unduhan dan koleksi " +
                        "tidak terpengaruh.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearRecent = false
                        viewModel.clearRecentUrls()
                    },
                ) {
                    Text("Bersihkan", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearRecent = false }) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    ScreenScaffold(
        topBar = { HomeHeader(onOpenSubscriptions = onOpenSubscriptions) },
        bottomBar = {
            AppBottomBar(
                selectedTab = AppTab.HOME,
                onTabSelected = onTabSelected,
            )
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HeroCard(onOpenDownloads = onOpenDownloads)
            }

            item {
                LinkComposerCard(
                    url = state.url,
                    loading = state.loading,
                    onUrlChange = ::handleUrlChange,
                    onClear = { viewModel.onUrlChange("") },
                    onPaste = { pasteAndDetect() },
                    onStart = ::startDetection,
                )
            }

            if (state.loading) {
                item { DetectingStrip() }
            }

            if (state.needsVerification) {
                item {
                    InfoBanner(
                        title = "Verifikasi Cloudflare diperlukan",
                        message = "Situs meminta pemeriksaan Cloudflare. Buka halaman verifikasi, " +
                            "tunggu hingga selesai, lalu deteksi lagi.",
                        icon = Icons.Rounded.CloudOff,
                        accent = Warning,
                        actionLabel = "Buka verifikasi",
                        onAction = {
                            viewModel.prepareVerification()
                            onOpenCloudflare()
                        },
                    )
                }
            }

            if (state.recentUrls.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "URL Terakhir",
                        icon = Icons.Rounded.History,
                        action = if (state.recentUrls.size > 3) {
                            if (showAllRecent) "Ringkas" else "Semua"
                        } else {
                            null
                        },
                        onAction = if (state.recentUrls.size > 3) {
                            { showAllRecent = !showAllRecent }
                        } else {
                            null
                        },
                    )
                }
                val visibleUrls = if (showAllRecent) state.recentUrls else state.recentUrls.take(3)
                items(
                    count = visibleUrls.size,
                    key = { index -> visibleUrls[index] },
                ) { index ->
                    val url = visibleUrls[index]
                    RecentUrlRow(
                        url = url,
                        onUse = {
                            viewModel.onUrlChange(url)
                            startDetection()
                        },
                        onRemove = { viewModel.removeRecentUrl(url) },
                    )
                }
                item {
                    GlassButton(
                        text = "Bersihkan riwayat URL",
                        onClick = { confirmClearRecent = true },
                        icon = Icons.Rounded.DeleteOutline,
                        modifier = Modifier.fillMaxWidth(),
                        height = 42.dp,
                        tint = Danger,
                    )
                }
            } else if (!state.loading && !state.needsVerification) {
                item {
                    InfoBanner(
                        title = "Tips mengunduh",
                        message = "Tempel link daftar anime untuk mengambil banyak episode " +
                            "sekaligus, atau link satu episode untuk mengunduh satu file saja.",
                        icon = Icons.Rounded.Bolt,
                        accent = BrandMint,
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(BrandPrimary.copy(alpha = 0.22f)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Aplikasi ini hanya untuk penggunaan pribadi.",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Hormati hak cipta pemilik konten. v${BuildConfig.VERSION_NAME}",
                        color = TextMuted.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onOpenSubscriptions: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(size = 46.dp)
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Samehadaku",
                    color = TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "DL",
                    color = BrandPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                )
            }
            Text(
                text = "Unduhan anime, rapi dan cepat",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        GlassIconButton(
            icon = Icons.Rounded.Bookmark,
            contentDescription = "Langganan",
            onClick = onOpenSubscriptions,
            size = 40.dp,
            iconSize = 19.dp,
        )
        Spacer(Modifier.width(8.dp))
        ReadyChip()
    }
}

@Composable
private fun ReadyChip() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(BrandMint.copy(alpha = 0.12f))
            .border(1.dp, BrandMint.copy(alpha = 0.26f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TinyStatusDot(color = BrandMint)
        Text(
            text = "Siap",
            color = BrandMint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HeroCard(onOpenDownloads: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(216.dp)
            .clip(shape)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.30f),
                        BrandPrimary.copy(alpha = 0.20f),
                        Color.Transparent,
                    ),
                ),
                shape,
            ),
    ) {
        SceneryArt(
            modifier = Modifier.fillMaxSize(),
            seed = 7,
            shape = shape,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Ink.copy(alpha = 0.18f),
                            Color.Transparent,
                            Ink.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(Ink.copy(alpha = 0.35f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(BrandPrimary),
            )
            Text(
                text = "PREMIUM DOWNLOADER",
                color = TextPrimary.copy(alpha = 0.92f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                .clickable(onClick = onOpenDownloads)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "Unduhan",
                color = TextPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = "Unduh anime favoritmu",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Tempel URL daftar anime atau episode dari Samehadaku. " +
                    "Episode dan kualitas terdeteksi otomatis.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroMetaChip(icon = Icons.Rounded.Bolt, label = "Deteksi otomatis")
                HeroMetaChip(icon = Icons.Rounded.HighQuality, label = "Pilih kualitas")
            }
        }
    }
}

@Composable
private fun HeroMetaChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandPrimary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun LinkComposerCard(
    url: String,
    loading: Boolean,
    onUrlChange: (String) -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onStart: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDot(
                icon = Icons.Rounded.Link,
                size = 38.dp,
                tint = BrandPrimary,
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tempel Link",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Daftar anime atau satu episode",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(text = "Otomatis", color = BrandMint)
        }

        Spacer(Modifier.height(14.dp))

        GlassTextField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = "https://samehadaku.email/anime/...",
            leadingIcon = Icons.Rounded.Link,
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (url.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable(
                                    enabled = !loading,
                                    onClickLabel = "Kosongkan link",
                                    onClick = onClear,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "×",
                                color = TextMuted,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    PasteAction(enabled = !loading, onClick = onPaste)
                }
            },
            enabled = !loading,
            maxLines = 2,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onStart() }),
        )

        Spacer(Modifier.height(12.dp))

        GradientButton(
            text = "Mulai Download",
            onClick = onStart,
            icon = Icons.Rounded.Download,
            enabled = url.isNotBlank(),
            loading = loading,
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp,
        )

        Spacer(Modifier.height(9.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "Link langsung terdeteksi setelah ditempel.",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetectingStrip() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape),
            color = BrandPrimary,
            trackColor = Color.White.copy(alpha = 0.08f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            TinyStatusDot(color = BrandPrimary)
            Text(
                text = "Mendeteksi episode dan kualitas…",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun RecentUrlRow(
    url: String,
    onUse: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
        onClick = onUse,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccessTime,
                    contentDescription = null,
                    tint = BrandAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prettyTitleFromUrl(url),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = url,
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                GlassIconButton(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "Opsi URL",
                    onClick = { menuOpen = true },
                    size = 32.dp,
                    iconSize = 17.dp,
                    tint = TextSecondary,
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Gunakan URL") },
                        onClick = {
                            menuOpen = false
                            onUse()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus dari riwayat", color = Danger) },
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

@Composable
private fun PasteAction(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(
                BrandPrimary.copy(alpha = if (enabled) 0.15f else 0.06f),
            )
            .border(
                1.dp,
                BrandPrimary.copy(alpha = if (enabled) 0.36f else 0.12f),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ContentPaste,
            contentDescription = null,
            tint = if (enabled) BrandPrimary else TextMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "Tempel",
            color = if (enabled) BrandPrimary else TextMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun looksLikeUrl(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        ("samehadaku" in trimmed.lowercase() && trimmed.contains('.'))
}
