package app.samehadaku.dl.presentation.logs

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.samehadaku.dl.BuildConfig
import app.samehadaku.dl.core.AppLog
import app.samehadaku.dl.presentation.components.EmptyState
import app.samehadaku.dl.presentation.components.GlassCard
import app.samehadaku.dl.presentation.components.GlassIconButton
import app.samehadaku.dl.presentation.components.ScreenScaffold
import app.samehadaku.dl.presentation.components.ScreenTopBar
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.Danger
import app.samehadaku.dl.presentation.theme.SlateSurface
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Read-only diagnostic log. Every line already carries the time, level, tag, and the
 * decision that was made, so the user can share one file when a download goes wrong.
 */
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val lines by AppLog.lines.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    fun share() {
        if (lines.isEmpty()) return
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Samehadaku DL v${BuildConfig.VERSION_NAME} log")
                putExtra(Intent.EXTRA_TEXT, AppLog.text())
            }
            context.startActivity(Intent.createChooser(intent, "Kirim log Samehadaku DL"))
        }.onFailure {
            scope.launch { snackbar.showSnackbar("Tidak ada aplikasi untuk mengirim log.") }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Hapus semua log?") },
            text = { Text("Log yang tersimpan di perangkat akan dihapus permanen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        AppLog.clear()
                    },
                ) {
                    Text("Hapus", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    ScreenScaffold(
        topBar = {
            ScreenTopBar(
                title = "Log aplikasi",
                subtitle = "${lines.size} baris • v${BuildConfig.VERSION_NAME}",
                onBack = onBack,
                actions = {
                    GlassIconButton(
                        icon = Icons.Rounded.ContentCopy,
                        contentDescription = "Salin semua log",
                        onClick = {
                            if (lines.isNotEmpty()) {
                                clipboard.setText(AnnotatedString(AppLog.text()))
                                scope.launch { snackbar.showSnackbar("Log disalin.") }
                            }
                        },
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.Share,
                        contentDescription = "Kirim log",
                        onClick = ::share,
                        tint = BrandAccent,
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "Hapus log",
                        onClick = { if (lines.isNotEmpty()) confirmClear = true },
                        tint = Danger,
                    )
                },
            )
        },
        snackbarHostState = snackbar,
    ) { padding ->
        if (lines.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Terminal,
                title = "Belum ada log",
                message = "Jalankan pencarian atau unduhan, lalu kembali ke sini. " +
                    "Semua langkah penting akan tercatat otomatis.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(lines.asReversed()) { line ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = line,
                            color = when {
                                " E/" in line -> Danger
                                " W/" in line -> TextMuted
                                else -> TextSecondary
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        }
    }
}
