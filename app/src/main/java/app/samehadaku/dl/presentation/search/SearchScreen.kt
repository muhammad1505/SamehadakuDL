package app.samehadaku.dl.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.domain.model.AnimeSearchResult
import app.samehadaku.dl.presentation.components.EmptyState
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GradientButton
import app.samehadaku.dl.presentation.components.IconDot
import app.samehadaku.dl.presentation.components.InfoBanner
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.SearchField
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.theme.Warning

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenEpisodes: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenCloudflare: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SearchEvent.OpenEpisodes -> onOpenEpisodes()
                SearchEvent.OpenQuality -> onOpenQuality()
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissMessage()
    }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Cari Anime",
                subtitle = "Unduh langsung atau jadikan langganan",
                onBack = onBack,
            )
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "Judul anime, mis. Blue Lock",
                    modifier = Modifier.focusRequester(focusRequester),
                    onClear = viewModel::clearQuery,
                )

                if (state.loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(50)),
                        color = BrandPrimary,
                        trackColor = Color.White.copy(alpha = 0.08f),
                    )
                }
            }

            if (state.needsVerification) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    InfoBanner(
                        title = "Verifikasi Cloudflare diperlukan",
                        message = "Selesaikan verifikasi dulu, lalu cari lagi.",
                        icon = Icons.Rounded.CloudOff,
                        accent = Warning,
                        actionLabel = "Buka verifikasi",
                        onAction = onOpenCloudflare,
                    )
                }
            }

            state.error?.let { error ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    InfoBanner(
                        title = "Pencarian belum berhasil",
                        message = error,
                        icon = Icons.Rounded.SearchOff,
                        accent = TextSecondary,
                        actionLabel = "Coba lagi",
                        onAction = viewModel::submit,
                    )
                }
            }

            when {
                state.loading && state.results.isEmpty() -> {
                    SearchLoading()
                }

                state.results.isNotEmpty() -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = BrandMint,
                            modifier = Modifier.width(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${state.results.size} anime ditemukan",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 22.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.results, key = { it.url }) { result ->
                            SearchResultCard(
                                result = result,
                                busyAction = state.busyAction.takeIf {
                                    state.busyUrl == result.url
                                },
                                onDownload = {
                                    keyboard?.hide()
                                    viewModel.download(result)
                                },
                                onSubscribe = {
                                    keyboard?.hide()
                                    viewModel.subscribe(result)
                                },
                            )
                        }
                    }
                }

                !state.searched -> {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "Cari anime favoritmu",
                        message = "Ketik minimal 2 huruf. Hasil bisa langsung diunduh atau " +
                            "ditambahkan ke Langganan.",
                        modifier = Modifier.padding(top = 30.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Mencari anime…",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        repeat(3) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(14.dp),
            ) {
                Text(
                    text = "Memuat hasil",
                    color = TextMuted,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Menyiapkan daftar anime",
                    color = TextMuted.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: AnimeSearchResult,
    busyAction: String?,
    onDownload: () -> Unit,
    onSubscribe: () -> Unit,
) {
    val busy = busyAction != null
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(13.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconDot(
                icon = Icons.Rounded.Movie,
                size = 40.dp,
                tint = BrandPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = result.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    result.type?.takeIf { it.isNotBlank() },
                    result.episodeLabel?.takeIf { it.isNotBlank() },
                )
                Text(
                    text = if (meta.isEmpty()) result.slug else meta.joinToString(" • "),
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(11.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton(
                text = if (busyAction == "download") "Menyiapkan…" else "Unduh",
                onClick = onDownload,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Download,
                enabled = !busy,
                loading = busyAction == "download",
                height = 44.dp,
            )
            GlassButton(
                text = if (busyAction == "subscribe") "Menyimpan…" else "Langganan",
                onClick = onSubscribe,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Bookmark,
                enabled = !busy,
                height = 44.dp,
                tint = BrandMint,
            )
        }
    }
}
