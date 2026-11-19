package app.samehadaku.dl.presentation.about

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.samehadaku.dl.BuildConfig
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.IconDot
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.BrandGold
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReleaseNote(
    val version: String,
    val changes: List<String>,
)

@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val notes by produceState<List<ReleaseNote>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { readReleaseNotes(context) }
    }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Tentang",
                subtitle = "Samehadaku Downloader • changelog aplikasi",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AboutHero()
            }
            item {
                SectionLabel(
                    icon = Icons.Rounded.History,
                    title = "Riwayat perubahan",
                    subtitle = "Diambil dari catatan rilis aplikasi",
                )
            }
            if (notes == null) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = BrandPrimary, strokeWidth = 3.dp)
                    }
                }
            } else {
                items(notes.orEmpty(), key = { it.version }) { note ->
                    ReleaseNoteCard(note)
                }
            }
            item {
                Text(
                    text = "Aplikasi ini hanya untuk penggunaan pribadi dan tidak meng-host " +
                        "konten apa pun. Hormati hak cipta pemilik konten.",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutHero() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
        glow = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconDot(
                icon = Icons.Rounded.AutoAwesome,
                size = 48.dp,
                tint = BrandPrimary,
            )
            Text(
                text = "Samehadaku Downloader",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Pengunduh episode dengan pilihan kualitas, antrean, langganan, " +
                    "dan koleksi file yang rapi.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            StatusPill(text = "v${BuildConfig.VERSION_NAME}", color = BrandGold)
        }
    }
}

@Composable
private fun SectionLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDot(icon = icon, size = 34.dp, tint = BrandMint)
        Spacer(Modifier.width(3.dp))
        Column {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ReleaseNoteCard(note: ReleaseNote) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
    Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Rilis ${note.version}",
                color = BrandAccent,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (note.version == BuildConfig.VERSION_NAME) {
                StatusPill(text = "Terpasang", color = BrandMint)
            }
        }
        Spacer(Modifier.height(8.dp))
        note.changes.forEach { change ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("•", color = BrandPrimary, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = change,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
        }
    }
}

fun readReleaseNotes(context: android.content.Context): List<ReleaseNote> {
    val markdown = runCatching {
        context.assets.open("changelog.md").bufferedReader().use { it.readText() }
    }.getOrElse { "## Rilis ${BuildConfig.VERSION_NAME}\n- Perbaikan dan penyempurnaan aplikasi." }
    // Stop each release section at the next level-two heading, otherwise the
    // bullet lists from documentation sections (storage, background access, ...)
    // are appended to the last release note.
    val headings = Regex("(?m)^##\\s+.+?\\s*$").findAll(markdown).toList()
    return headings.mapIndexedNotNull { index, header ->
        if (!header.value.startsWith("## Rilis")) return@mapIndexedNotNull null
        val start = header.range.last + 1
        val end = headings.getOrNull(index + 1)?.range?.first ?: markdown.length
        val changes = markdown.substring(start, end)
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- ") }
            .map { it.removePrefix("- ").trim() }
            .toList()
        ReleaseNote(header.value.removePrefix("## Rilis").trim(), changes)
    }.reversed()
}
