package app.samehadaku.dl.presentation.search

import android.graphics.Bitmap
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.InfoBanner
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.components.GradientButton
import app.samehadaku.dl.presentation.components.GlassTextField
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.SlateSurface
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.theme.Warning

@Composable
fun SearchDetailScreen(
    onBack: () -> Unit,
    onOpenEpisodes: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenCloudflare: () -> Unit,
    verified: Boolean = false,
    onVerifiedHandled: () -> Unit = {},
    viewModel: SearchDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSubscribeDialog by rememberSaveable { mutableStateOf(false) }
    var lastEpisode by rememberSaveable { mutableStateOf("0") }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SearchDetailEvent.OpenEpisodes -> onOpenEpisodes()
                SearchDetailEvent.OpenQuality -> onOpenQuality()
            }
        }
    }

    LaunchedEffect(verified) {
        if (verified) {
            onVerifiedHandled()
            viewModel.load()
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissMessage()
    }

    if (showSubscribeDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) showSubscribeDialog = false },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Tambah ke Langganan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Masukkan episode terakhir yang sudah kamu tonton atau unduh. " +
                            "Gunakan 0 jika ingin mulai dari episode pertama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    GlassTextField(
                        value = lastEpisode,
                        onValueChange = { lastEpisode = it.filter(Char::isDigit) },
                        placeholder = "Episode terakhir (0 jika belum ada)",
                        leadingIcon = Icons.Rounded.Tag,
                    )
                    state.error?.let { error ->
                        Text(error, color = Danger, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSubscribeDialog = false
                        viewModel.subscribe(lastEpisode)
                    },
                    enabled = !state.busy,
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BrandPrimary,
                        )
                    } else {
                        Text("Simpan langganan", color = BrandPrimary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubscribeDialog = false }, enabled = !state.busy) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Detail Anime",
                subtitle = state.result?.title,
                onBack = onBack,
                actions = {
                    GlassIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Muat ulang detail",
                        onClick = viewModel::load,
                        tint = TextSecondary,
                    )
                },
            )
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val result = state.result
            if (result == null) {
                InfoBanner(
                    title = "Hasil pencarian tidak tersedia",
                    message = "Kembali ke pencarian lalu buka hasilnya lagi.",
                    icon = Icons.Rounded.ErrorOutline,
                    accent = Warning,
                )
                return@Column
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(16.dp),
                glow = true,
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    DetailCover(cover = state.cover, title = result.title)
                    Spacer(Modifier.width(13.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            result.title,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        val meta = listOfNotNull(result.type, result.episodeLabel)
                            .filter { it.isNotBlank() }
                        Text(
                            meta.joinToString(" • ").ifBlank { result.slug },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        StatusPill(
                            text = state.episodeCount?.let { "$it episode terdeteksi" } ?:
                                if (state.inspecting) "Membaca episode…" else "Siap diproses",
                            color = if (state.episodeCount != null) BrandMint else BrandPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(13.dp))
                Text(
                    "${result.url}",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            state.needsVerification.let { needs ->
                if (needs) {
                    InfoBanner(
                        title = "Verifikasi Cloudflare diperlukan",
                        message = "Selesaikan verifikasi lalu kembali ke detail ini.",
                        icon = Icons.Rounded.CloudOff,
                        accent = Warning,
                        actionLabel = "Buka verifikasi",
                        onAction = onOpenCloudflare,
                    )
                }
            }

            state.error?.let { error ->
                InfoBanner(
                    title = "Detail belum lengkap",
                    message = error,
                    icon = Icons.Rounded.ErrorOutline,
                    accent = Warning,
                    actionLabel = "Coba lagi",
                    onAction = viewModel::load,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(
                    text = "Unduh",
                    onClick = viewModel::download,
                    enabled = !state.busy,
                    loading = state.busyAction == "download",
                    icon = Icons.Rounded.Download,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                )
                GlassButton(
                    text = "Langganan",
                    onClick = { showSubscribeDialog = true },
                    enabled = !state.busy,
                    icon = Icons.Rounded.Bookmark,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    tint = BrandMint,
                )
            }

            Text(
                "Format, resolusi, dan host unduhan mengikuti Pengaturan. Kamu tidak perlu " +
                    "memilih ulang di setiap menu.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DetailCover(cover: Bitmap?, title: String) {
    Box(
        modifier = Modifier
            .size(width = 92.dp, height = 126.dp)
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = "Cover $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}
