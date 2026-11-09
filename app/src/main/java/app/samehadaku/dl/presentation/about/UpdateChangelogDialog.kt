package app.samehadaku.dl.presentation.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.samehadaku.dl.BuildConfig
import app.samehadaku.dl.presentation.theme.BrandPrimary
import app.samehadaku.dl.presentation.theme.SlateSurface
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary

@Composable
fun UpdateChangelogDialog(
    notes: List<ReleaseNote>,
    onDismiss: () -> Unit,
) {
    val current = notes.firstOrNull { it.version == BuildConfig.VERSION_NAME }
        ?: notes.firstOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Yang baru di v${BuildConfig.VERSION_NAME}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Pembaruan berhasil dipasang. Ini ringkasan perubahan terbaru:",
                    color = TextSecondary,
                )
                current?.changes.orEmpty().forEach { change ->
                    Text("• $change", color = TextMuted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Mengerti", color = BrandPrimary)
            }
        },
    )
}
