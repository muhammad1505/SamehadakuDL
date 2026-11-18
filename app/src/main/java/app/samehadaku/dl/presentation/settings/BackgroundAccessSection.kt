package app.samehadaku.dl.presentation.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.samehadaku.dl.core.BackgroundAccess
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary

private val OptimizeAmber = Color(0xFFF59E0B)
private val OptimizeGreen = Color(0xFF10B981)
private val OptimizeBlue = Color(0xFF2563EB)

/**
 * System optimization panel with an explicit before/after state for each switch.
 *
 * All Android versions start at App info (`ACTION_APPLICATION_DETAILS_SETTINGS`)
 * because the direct battery-optimization dialog can be reset silently by OEM
 * battery managers, while the App info switches persist. The three counted items
 * are battery, notifications and autostart; autostart cannot be read by Android,
 * so the panel asks for a one-tap confirmation after the OEM screen closes.
 */
@Composable
internal fun BackgroundAccessSection(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val access = remember(context) { BackgroundAccess(context) }
    val preferences = remember(context) { OptimizationPreferences(context) }
    var status by remember { mutableStateOf(access.status()) }
    var autoStartConfirmed by remember { mutableStateOf(preferences.autoStartConfirmed) }
    var autoStartPrompt by remember { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }

    fun refresh() {
        status = access.status()
        autoStartConfirmed = preferences.autoStartConfirmed
    }

    fun startSettings(intent: Intent, message: String) {
        val started = runCatching {
            context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        onMessage(if (started) message else "Pengaturan tidak bisa dibuka di perangkat ini.")
    }

    fun openAppInfo(message: String) = startSettings(
        access.appDetailsIntent(),
        message,
    )

    fun openNotificationSettings() = startSettings(
        access.notificationSettingsIntent(),
        "Buka pengaturan notifikasi Samehadaku DL.",
    )

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh()
        // Returning from the OEM autostart screen: ask the one question Android
        // cannot answer itself.
        if (preferences.autoStartPending) autoStartPrompt = true
    }

    val readyCount = listOf(
        status.batteryOptimizationIgnored,
        status.notificationsEnabled,
        autoStartConfirmed,
    ).count { it }
    val allReady = readyCount == 3
    val tint = if (allReady) OptimizeGreen else OptimizeAmber
    val autoStartLabel = access.autoStartLabel()

    SettingSection(
        title = "Optimasi sistem",
        subtitle = "Agar cek episode, unduhan, dan audio latar tidak ditunda",
        icon = Icons.Rounded.BatterySaver,
        tint = tint,
        badge = if (allReady) "✓ OPTIMAL (3/3)" else "PERLU ATUR ($readyCount/3)",
    ) {
        Text(
            text = "Pengaturan ini tidak dipaksakan saat aplikasi pertama dibuka. " +
                "Aktifkan saat memakai cek episode otomatis, unduhan latar, atau " +
                "background play. Semua versi Android memakai Info aplikasi sebagai " +
                "titik awal agar pengaturan bertahan.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))

        OptimizationRow(
            icon = Icons.Rounded.BatterySaver,
            title = "Baterai tanpa batas",
            ready = status.batteryOptimizationIgnored,
            notReady = "Baterai masih dibatasi sistem",
            readyText = "Aktivitas latar tidak dibatasi",
            onConfigure = { openAppInfo("Buka Info aplikasi → Baterai / Penggunaan baterai.") },
            onRecheck = {
                refresh()
                onMessage(
                    if (status.batteryOptimizationIgnored) {
                        "Baterai sudah tanpa batas."
                    } else {
                        "Baterai masih dibatasi; ikuti panduan di bawah."
                    },
                )
            },
        )

        OptimizationRow(
            icon = Icons.Rounded.NotificationsActive,
            title = "Notifikasi",
            ready = status.notificationsEnabled,
            notReady = if (status.notificationPermissionRequired) {
                "Izin notifikasi belum diberikan"
            } else {
                "Notifikasi diblokir di pengaturan"
            },
            readyText = "Episode baru dan unduhan bisa diberitahukan",
            onConfigure = { openNotificationSettings() },
            onRecheck = {
                refresh()
                onMessage(
                    if (status.notificationsEnabled) {
                        "Notifikasi sudah aktif."
                    } else {
                        "Notifikasi masih diblokir."
                    },
                )
            },
        )

        OptimizationRow(
            icon = Icons.Rounded.RocketLaunch,
            title = "Mulai otomatis (Autostart)",
            ready = autoStartConfirmed,
            notReady = "$autoStartLabel belum dikonfirmasi",
            readyText = "Mulai otomatis sudah dikonfirmasi",
            onConfigure = {
                preferences.autoStartPending = true
                autoStartPrompt = false
                openAppInfo("Buka Info aplikasi → Autostart / Mulai otomatis.")
            },
            onRecheck = {
                autoStartPrompt = true
                onMessage("Konfirmasi ulang apakah Autostart sudah aktif.")
            },
        )

        if (autoStartPrompt) {
            Spacer(Modifier.height(6.dp))
            AutostartConfirmation(
                onConfirm = {
                    preferences.autoStartConfirmed = true
                    preferences.autoStartPending = false
                    autoStartConfirmed = true
                    autoStartPrompt = false
                    onMessage("Autostart dikonfirmasi aktif.")
                },
                onDismiss = {
                    preferences.autoStartPending = false
                    autoStartPrompt = false
                    onMessage("Konfirmasi autostart ditunda.")
                },
            )
        }

        if (status.backgroundRestricted == true) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sistem juga menandai aktivitas latar dibatasi. Buka Info " +
                    "aplikasi → Baterai untuk melepasnya.",
                color = OptimizeAmber,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (status.dataSaverRestricted == true) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Hemat data membatasi data latar; cek episode otomatis bisa tertunda.",
                color = OptimizeAmber,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showGuide = !showGuide }
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Panduan sesuai versi Android & perangkat",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (showGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        if (showGuide) {
            Text(
                text = access.androidVersionGuide(),
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = access.oemGuide(),
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Autostart bawaan pabrik tidak bisa dibaca Android. Status hijau " +
                    "muncul setelah kamu menekan “Ya, Sudah” pada konfirmasi.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OptimizationRow(
    icon: ImageVector,
    title: String,
    ready: Boolean,
    notReady: String,
    readyText: String,
    onConfigure: () -> Unit,
    onRecheck: () -> Unit,
) {
    val tint = if (ready) OptimizeGreen else OptimizeAmber
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tint.copy(alpha = 0.13f))
                .border(1.dp, tint.copy(alpha = 0.32f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (ready) Icons.Rounded.Check else icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(if (ready) "Sudah disetting" else "Belum disetting")
                    }
                    append(" · ")
                    append(if (ready) readyText else notReady)
                },
                color = tint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (ready) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRecheck),
            ) {
                StatusPill(text = "✓ Aktif", color = OptimizeGreen)
            }
        } else {
            SolidActionButton(text = "Atur →", onClick = onConfigure)
        }
    }
}

@Composable
private fun AutostartConfirmation(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OptimizeGreen.copy(alpha = 0.08f))
            .border(1.dp, OptimizeGreen.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "Sudah mengaktifkan Mulai Otomatis?",
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Android tidak bisa membaca sakelar pabrik. Konfirmasi sekali agar " +
                "status visual di atas akurat.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SolidActionButton(
                text = "Ya, Sudah ✓",
                onClick = onConfirm,
                color = OptimizeGreen,
            )
            Text(
                text = "Nanti",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SolidActionButton(
    text: String,
    onClick: () -> Unit,
    color: Color = OptimizeBlue,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
