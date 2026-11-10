package app.samehadaku.dl.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.R
import app.samehadaku.dl.presentation.components.AppBottomBar
import app.samehadaku.dl.presentation.components.AppTab
import app.samehadaku.dl.presentation.components.BrandMark
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassTextField
import app.samehadaku.dl.presentation.components.GradientButton
import app.samehadaku.dl.presentation.components.IconDot
import app.samehadaku.dl.presentation.components.InfoBanner
import app.samehadaku.dl.presentation.components.pressable
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.components.TinyStatusDot
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.BrandGold
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPink
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenEpisodes: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenCloudflare: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCalendar: () -> Unit,
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
        topBar = {
            HomeHeader(
                unreadNotifications = state.unreadNotifications,
                onOpenSearch = onOpenSearch,
                onOpenNotifications = onOpenNotifications,
                onOpenCalendar = onOpenCalendar,
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedTab = AppTab.HOME,
                onTabSelected = onTabSelected,
            )
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            state.announcement?.let { announcement ->
                AnnouncementTicker(text = announcement)
            }

            HeroSection(
                modifier = Modifier.fillMaxWidth(),
                onAutoDetect = { pasteAndDetect() },
                onPickQuality = { onTabSelected(AppTab.SETTINGS) },
            )

            LinkComposerCard(
                url = state.url,
                loading = state.loading,
                onUrlChange = ::handleUrlChange,
                onClear = { viewModel.onUrlChange("") },
                onPaste = { pasteAndDetect() },
                onStart = ::startDetection,
            )

            if (state.loading) {
                DetectingStrip()
            }

            if (state.needsVerification) {
                InfoBanner(
                    title = "Verifikasi Cloudflare diperlukan",
                    message = "Buka halaman verifikasi, tunggu hingga selesai, lalu deteksi lagi.",
                    icon = Icons.Rounded.CloudOff,
                    accent = Warning,
                    actionLabel = "Buka verifikasi",
                    onAction = {
                        viewModel.prepareVerification()
                        onOpenCloudflare()
                    },
                )
            }

            Spacer(Modifier.height(9.dp))
            TipsCard()

            if (state.recentUrls.isNotEmpty()) {
                RecentUrlsSection(
                    urls = state.recentUrls,
                    onUse = { url ->
                        viewModel.onUrlChange(url)
                        startDetection()
                    },
                    onRemove = viewModel::removeRecentUrl,
                    onClear = { confirmClearRecent = true },
                )
            }

            // Breathing room so the artwork horizon stays visible under the content.
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HomeHeader(
    unreadNotifications: Int,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(size = 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextPrimary)) { append("Samehadaku ") }
                    withStyle(SpanStyle(color = BrandPrimary)) { append("Downloader") }
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Unduhan anime, rapi dan cepat",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        HeaderAction(
            icon = Icons.Rounded.Search,
            contentDescription = "Cari anime",
            onClick = onOpenSearch,
        )
        Spacer(Modifier.width(6.dp))
        HeaderAction(
            icon = Icons.Rounded.CalendarMonth,
            contentDescription = "Jadwal anime",
            onClick = onOpenCalendar,
        )
        Spacer(Modifier.width(6.dp))
        HeaderAction(
            icon = Icons.Rounded.NotificationsNone,
            contentDescription = "Notifikasi",
            onClick = onOpenNotifications,
            badgeCount = unreadNotifications,
        )
    }
}

@Composable
private fun AnnouncementTicker(text: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = "Pengumuman jadwal",
                tint = BrandGold,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = text,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = 26.dp,
                ).weight(1f),
            )
        }
    }
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badgeCount: Int = 0,
) {
    val shape = RoundedCornerShape(13.dp)
    Box {
        Box(
            modifier = Modifier
                .pressable(role = Role.Button, pressedScale = 0.92f, onClick = onClick)
                .size(38.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = TextPrimary,
                modifier = Modifier.size(19.dp),
            )
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(BrandPink),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun HeroSection(
    modifier: Modifier = Modifier,
    onAutoDetect: () -> Unit,
    onPickQuality: () -> Unit,
) {
    Column(
        modifier = modifier.padding(top = 24.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Ink.copy(alpha = 0.32f))
                    .border(1.dp, BrandPrimary.copy(alpha = 0.30f), RoundedCornerShape(50))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_crown),
                    contentDescription = null,
                    tint = BrandGold,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "PREMIUM DOWNLOADER",
                    color = TextPrimary.copy(alpha = 0.94f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            StatusPill(
                text = "Siap",
                color = BrandMint,
                icon = Icons.Rounded.Check,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "Unduh Anime",
                color = TextPrimary,
                fontSize = 29.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-0.5).sp,
            )
            Column {
                Text(
                    text = "Favoritmu",
                    style = TextStyle(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF38E1FF), Color(0xFF8A6BFF)),
                        ),
                        fontSize = 29.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = (-0.5).sp,
                    ),
                )
                HeroSwoosh()
            }
            Text(
                text = "Tempel URL anime satuan atau daftar (list) sekaligus. " +
                    "Episode dan kualitas terdeteksi otomatis.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 278.dp),
            )
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroActionChip(
                    icon = Icons.Rounded.Bolt,
                    label = "Deteksi Otomatis",
                    onClick = onAutoDetect,
                )
                HeroActionChip(
                    icon = Icons.Rounded.HighQuality,
                    label = "Pilih Kualitas",
                    onClick = onPickQuality,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(BrandPrimary),
                )
                Spacer(Modifier.width(5.dp))
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.26f)),
                    )
                    Spacer(Modifier.width(5.dp))
                }
            }
        }
    }
}

/** Gradient swoosh the mockup draws under the highlighted hero word. */
@Composable
private fun HeroSwoosh() {
    Canvas(
        modifier = Modifier
            .padding(start = 3.dp, top = 1.dp)
            .offset(y = 1.dp)
            .width(168.dp)
            .height(9.dp),
    ) {
        val swoosh = Path().apply {
            moveTo(0f, size.height * 0.70f)
            cubicTo(
                size.width * 0.30f,
                size.height * 0.02f,
                size.width * 0.58f,
                size.height * 1.04f,
                size.width,
                size.height * 0.22f,
            )
        }
        val brush = Brush.horizontalGradient(
            listOf(Color(0xFF38E1FF), Color(0xFF8A6BFF), Color(0xFFFF5CBE)),
        )
        drawPath(
            path = swoosh,
            brush = brush,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            alpha = 0.22f,
        )
        drawPath(
            path = swoosh,
            brush = brush,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun HeroActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .pressable(pressedScale = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), BrandPrimary.copy(alpha = 0.10f)),
                ),
            )
            .border(1.dp, BrandPrimary.copy(alpha = 0.42f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandPrimary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(CircleShape)
                .background(BrandMint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(10.dp),
            )
        }
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
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(14.dp),
        glow = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDot(
                icon = Icons.Rounded.Link,
                size = 36.dp,
                tint = BrandPrimary,
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tempel URL",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Link satuan atau daftar (multi-link)",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            StatusPill(
                text = "Otomatis",
                color = BrandMint,
                icon = Icons.Rounded.AutoAwesome,
            )
        }

        Spacer(Modifier.height(10.dp))

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
                                .pressable(
                                    enabled = !loading,
                                    onClickLabel = "Kosongkan link",
                                    pressedScale = 0.90f,
                                    onClick = onClear,
                                )
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "×",
                                color = TextMuted,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    PasteAction(enabled = !loading, onClick = onPaste)
                }
            },
            enabled = !loading,
            minHeight = 48.dp,
            maxLines = 1,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onStart() }),
        )

        Spacer(Modifier.height(10.dp))

        GradientButton(
            text = "Mulai Download",
            onClick = onStart,
            icon = Icons.Rounded.Download,
            enabled = url.isNotBlank(),
            loading = loading,
            modifier = Modifier.fillMaxWidth(),
            height = 50.dp,
        )

        Spacer(Modifier.height(10.dp))

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
                text = "Link akan diproses dan episode terdeteksi otomatis.",
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun TipsCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(14.dp),
        onClick = { expanded = !expanded },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconDot(
                icon = Icons.Rounded.Bolt,
                size = 40.dp,
                tint = BrandMint,
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Tips Mengunduh",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Kamu bisa menempel 1 link atau banyak link sekaligus " +
                        "(daftar episode) untuk mengunduh secara otomatis.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedVisibility(visible = expanded) {
                    Text(
                        text = "Ikuti anime di tab Langganan supaya episode baru masuk " +
                            "antrean sendiri, dan biarkan kualitas menyesuaikan bila " +
                            "server pilihan tidak tersedia.",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = if (expanded) "Sembunyikan tips" else "Lihat tips lain",
                tint = TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (expanded) 90f else 0f),
            )
        }
    }
}

@Composable
private fun RecentUrlsSection(
    urls: List<String>,
    onUse: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "URL Terakhir",
                color = TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Bersihkan",
                color = BrandPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .pressable(pressedScale = 0.94f, onClick = onClear)
                    .clip(RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            urls.take(5).forEach { url ->
                RecentUrlChip(
                    url = url,
                    onUse = { onUse(url) },
                    onRemove = { onRemove(url) },
                )
            }
        }
    }
}

@Composable
private fun RecentUrlChip(
    url: String,
    onUse: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .pressable(pressedScale = 0.97f, onClick = onUse)
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(start = 9.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AccessTime,
            contentDescription = null,
            tint = BrandAccent,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = prettyTitleFromUrl(url),
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 132.dp),
        )
        Box(
            modifier = Modifier
                .pressable(
                    onClickLabel = "Hapus dari riwayat",
                    pressedScale = 0.88f,
                    onClick = onRemove,
                )
                .size(26.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Hapus dari riwayat",
                tint = TextMuted,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun PasteAction(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier
            .pressable(enabled = enabled, pressedScale = 0.94f, onClick = onClick)
            .height(36.dp)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        listOf(Color(0xFF12B8E8), Color(0xFF2CE4FF)),
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.06f)),
                    )
                },
            )
            .then(
                if (enabled) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ContentPaste,
            contentDescription = null,
            tint = if (enabled) Color.White else TextMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "Tempel",
            color = if (enabled) Color.White else TextMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun looksLikeUrl(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        ("samehadaku" in trimmed.lowercase() && trimmed.contains('.'))
}
