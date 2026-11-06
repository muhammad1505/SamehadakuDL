package app.samehadaku.dl.presentation.episodes

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.domain.model.Episode
import app.samehadaku.dl.presentation.components.ActionBottomBar
import app.samehadaku.dl.presentation.components.EmptyState
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.GlassTextField
import app.samehadaku.dl.presentation.components.GradientButton
import app.samehadaku.dl.presentation.components.NeonCheckbox
import app.samehadaku.dl.presentation.components.SceneryArt
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.SectionHeader
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Ink
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.util.prettyName

@Composable
fun EpisodesScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenQuality: () -> Unit,
    viewModel: EpisodesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                EpisodesEvent.OpenQuality -> onOpenQuality()
            }
        }
    }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    LaunchedEffect(state.notice) {
        val message = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissNotice()
    }

    val allSelected = state.episodes.isNotEmpty() &&
        state.selectedUrls.size == state.episodes.size

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Pilih Episode",
                subtitle = "Tentukan episode yang ingin diunduh",
                onBack = onBack,
                actions = {
                    GlassIconButton(
                        icon = if (state.isSubscribed) {
                            Icons.Rounded.Bookmark
                        } else {
                            Icons.Rounded.BookmarkBorder
                        },
                        contentDescription = if (state.isSubscribed) {
                            "Berhenti mengikuti anime"
                        } else {
                            "Ikuti anime ini"
                        },
                        onClick = viewModel::toggleSubscription,
                        tint = if (state.isSubscribed) BrandPrimary else TextSecondary,
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.Download,
                        contentDescription = "Antrean unduhan",
                        onClick = onOpenQueue,
                    )
                },
            )
        },
        bottomBar = {
            if (state.episodes.isNotEmpty()) {
                ActionBottomBar {
                    GradientButton(
                        text = if (state.selectedUrls.isEmpty()) {
                            "Pilih episode dulu"
                        } else {
                            "Lanjut ke Kualitas (${state.selectedUrls.size})"
                        },
                        onClick = viewModel::continueToQuality,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Tune,
                        enabled = state.selectedUrls.isNotEmpty(),
                    )
                }
            }
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        if (state.episodes.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                icon = Icons.Rounded.Movie,
                title = "Tidak ada episode",
                message = "Deteksi ulang URL anime dari halaman utama untuk memuat daftar episode.",
                actionLabel = "Kembali",
                onAction = onBack,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AnimeHeader(
                        animeSlug = state.animeSlug,
                        episodeCount = state.episodes.size,
                    )
                }

                item {
                    SelectionCard(
                        allSelected = allSelected,
                        selectedCount = state.selectedUrls.size,
                        totalCount = state.episodes.size,
                        onToggleAll = viewModel::selectAll,
                        onClear = viewModel::clearSelection,
                    )
                }

                item {
                    RangeCard(
                        rangeText = state.rangeText,
                        onRangeTextChange = viewModel::onRangeTextChange,
                        onApply = viewModel::applyRange,
                    )
                }

                item {
                    SectionHeader(
                        title = "Daftar Episode",
                        icon = Icons.Rounded.PlaylistAddCheck,
                        action = "${state.selectedUrls.size} dipilih",
                        onAction = viewModel::selectAll,
                    )
                }

                items(state.episodes, key = { it.url }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        checked = episode.url in state.selectedUrls,
                        onToggle = { viewModel.toggleEpisode(episode.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeHeader(
    animeSlug: String,
    episodeCount: Int,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp)
            .clip(shape)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.26f),
                        BrandPrimary.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                ),
                shape,
            ),
    ) {
        SceneryArt(
            modifier = Modifier.fillMaxSize(),
            seed = animeSlug.hashCode().let { if (it == Int.MIN_VALUE) 0 else it },
            shape = shape,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Ink.copy(alpha = 0.18f), Ink.copy(alpha = 0.92f)),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .clip(RoundedCornerShape(50))
                .background(BrandPrimary.copy(alpha = 0.16f))
                .border(1.dp, BrandPrimary.copy(alpha = 0.30f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = "DAFTAR EPISODE",
                color = BrandPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = prettyName(animeSlug),
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(text = "$episodeCount episode", color = BrandMint)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Pilih satu atau beberapa episode",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SelectionCard(
    allSelected: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onToggleAll: () -> Unit,
    onClear: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onToggleAll,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonCheckbox(checked = allSelected, size = 22.dp)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (allSelected) "Semua episode dipilih" else "Pilih semua episode",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "$selectedCount dari $totalCount episode",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (selectedCount > 0 && !allSelected) {
                Text(
                    text = "Kosongkan",
                    color = BrandPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RangeCard(
    rangeText: String,
    onRangeTextChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandPrimary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "#",
                    color = BrandPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    text = "Rentang cepat",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Contoh: 1-12, 15, 18-20",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            GlassTextField(
                value = rangeText,
                onValueChange = onRangeTextChange,
                modifier = Modifier.weight(1f),
                placeholder = "1-12, 15",
                leadingIcon = Icons.Rounded.Tune,
            )
            GlassButton(
                text = "Terapkan",
                onClick = onApply,
                icon = Icons.Rounded.Check,
                height = 54.dp,
                tint = BrandPrimary,
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
        highlighted = checked,
        onClick = onToggle,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonCheckbox(
                checked = checked,
                size = 22.dp,
                onCheckedChange = { onToggle() },
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (checked) {
                            Brush.linearGradient(
                                listOf(BrandPrimary.copy(alpha = 0.30f), Color(0xFF4BE1FF).copy(alpha = 0.10f)),
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.03f)),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = episode.number.toString(),
                    color = if (checked) BrandPrimary else TextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Episode ${episode.number}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = episode.slug,
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (checked) {
                IconDotCheck()
            }
        }
    }
}

@Composable
private fun IconDotCheck() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(50))
            .background(BrandPrimary.copy(alpha = 0.16f))
            .border(1.dp, BrandPrimary.copy(alpha = 0.35f), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = BrandPrimary,
            modifier = Modifier.size(14.dp),
        )
    }
}
