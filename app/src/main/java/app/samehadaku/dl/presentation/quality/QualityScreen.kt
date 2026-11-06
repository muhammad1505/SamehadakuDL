package app.samehadaku.dl.presentation.quality

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.presentation.components.ActionBottomBar
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.GradientButton
import app.samehadaku.dl.presentation.components.InfoBanner
import app.samehadaku.dl.presentation.components.PillChip
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.SectionHeader
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.theme.Warning
import app.samehadaku.dl.presentation.util.prettyName

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QualityScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenCloudflare: () -> Unit,
    verified: Boolean,
    onVerifiedHandled: () -> Unit,
    viewModel: QualityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                QualityEvent.OpenQueue -> onOpenQueue()
            }
        }
    }

    LaunchedEffect(verified) {
        if (verified) {
            onVerifiedHandled()
            viewModel.onVerifiedReturned()
        }
    }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    val showStart = !state.loading &&
        !state.needsVerification &&
        state.error == null &&
        state.selectedFormat != null

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Pilih Kualitas",
                subtitle = prettyName(state.animeSlug),
                onBack = onBack,
                actions = {
                    GlassIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Muat ulang kualitas",
                        onClick = viewModel::load,
                    )
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(
                        icon = Icons.Rounded.Download,
                        contentDescription = "Antrean unduhan",
                        onClick = onOpenQueue,
                        tint = BrandPrimary,
                    )
                },
            )
        },
        bottomBar = {
            if (showStart) {
                ActionBottomBar {
                    GradientButton(
                        text = "Mulai Unduh (${state.selectedCount})",
                        onClick = viewModel::startDownload,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Download,
                        loading = state.starting,
                    )
                }
            }
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        when {
            state.loading -> LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            state.needsVerification -> VerificationState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onVerify = {
                    viewModel.prepareVerification()
                    onOpenCloudflare()
                },
                onBack = onBack,
            )
            state.error != null && state.formats.isEmpty() -> ErrorState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                message = state.error.orEmpty(),
                onRetry = viewModel::load,
            )
            else -> LazyQualityContent(
                state = state,
                padding = padding,
                viewModel = viewModel,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LazyQualityContent(
    state: QualityUiState,
    padding: PaddingValues,
    viewModel: QualityViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            glow = true,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(BrandMint.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = BrandMint,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${state.selectedCount} episode dipilih",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = prettyName(state.animeSlug),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(text = "Siap unduh", color = BrandMint)
            }
        }

        if (state.formats.isEmpty()) {
            InfoBanner(
                title = "Tautan belum tersedia",
                message = "Belum ada tautan unduhan yang bisa dibaca. Muat ulang halaman kualitas " +
                    "atau buka verifikasi Cloudflare.",
                icon = Icons.Rounded.ErrorOutline,
                accent = Warning,
                actionLabel = "Muat ulang",
                onAction = viewModel::load,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "Format", icon = Icons.Rounded.Movie)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.formats.forEach { format ->
                        PillChip(
                            label = format.label,
                            selected = state.selectedFormat == format,
                            onClick = { viewModel.selectFormat(format) },
                            icon = Icons.Rounded.Movie,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "Resolusi", icon = Icons.Rounded.HighQuality)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.resolutions.forEach { resolution ->
                        PillChip(
                            label = resolution.uppercase(),
                            selected = state.selectedResolution == resolution,
                            onClick = { viewModel.selectResolution(resolution) },
                            icon = Icons.Rounded.HighQuality,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "Server", icon = Icons.Rounded.Dns)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.hosts.forEach { host ->
                        PillChip(
                            label = host.label,
                            selected = state.selectedHost == host,
                            onClick = { viewModel.selectHost(host) },
                            icon = Icons.Rounded.Dns,
                        )
                    }
                }
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Ringkasan pilihan",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.height(11.dp))
                SummaryRow("Format", state.selectedFormat?.label ?: "—")
                SummaryRow("Resolusi", state.selectedResolution?.uppercase() ?: "—")
                SummaryRow("Server", state.selectedHost?.label ?: "—")
                Spacer(Modifier.height(9.dp))
                Text(
                    text = "Server pilihan dicoba lebih dulu, lalu server lain sebagai cadangan. " +
                        "Episode yang kualitasnya tidak tersedia akan dilewati.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (state.sampleEpisodeUrl != null) {
            Text(
                text = "Contoh kualitas dari: " +
                    state.sampleEpisodeUrl.trimEnd('/').substringAfterLast('/'),
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = BrandPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(BrandPrimary.copy(alpha = 0.18f), Color.Transparent),
                    ),
                )
                .border(1.dp, BrandPrimary.copy(alpha = 0.22f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(38.dp),
                strokeWidth = 3.dp,
                color = BrandPrimary,
                trackColor = Color.White.copy(alpha = 0.08f),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Memeriksa kualitas dari server…",
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tautan format, resolusi, dan server sedang dikumpulkan.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VerificationState(
    modifier: Modifier = Modifier,
    onVerify: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            glow = true,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Warning.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = Warning,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Verifikasi Cloudflare diperlukan",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "Situs meminta pemeriksaan Cloudflare sebelum tautan unduhan bisa dibaca. " +
                    "Selesaikan verifikasi, lalu kembali ke halaman ini.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = "Buka verifikasi",
                onClick = onVerify,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.CloudOff,
            )
            Spacer(Modifier.height(8.dp))
            GlassButton(
                text = "Kembali",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                height = 46.dp,
                tint = TextSecondary,
            )
        }
    }
}

@Composable
private fun ErrorState(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Danger.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = Danger,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Gagal memuat kualitas",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = "Coba lagi",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Refresh,
            )
        }
    }
}
