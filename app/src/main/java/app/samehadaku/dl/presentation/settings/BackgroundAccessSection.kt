package app.samehadaku.dl.presentation.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.samehadaku.dl.core.BackgroundAccess
import app.samehadaku.dl.core.BackgroundAccessTarget
import app.samehadaku.dl.presentation.components.GlassButton
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.theme.Warning
import timber.log.Timber

/**
 * User-initiated access panel for background work. Android does not allow an app
 * to grant these exemptions itself, and the relevant screens differ by Android
 * version and OEM, so each action opens the most specific screen that exists and
 * falls back to battery optimization or app info.
 */
@Composable
internal fun BackgroundAccessSection(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val access = remember(context) { BackgroundAccess(context) }
    var status by remember { mutableStateOf(access.status()) }
    var notificationRequested by rememberSaveable { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationRequested = true
        status = access.status()
        if (!granted) {
            onMessage("Izin notifikasi belum diberikan. Aktifkan lewat pengaturan notifikasi.")
        }
    }

    // Covers returning from the system battery/autostart/notification screens.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = access.status()
    }

    val tint = if (status.needsAttention) Warning else BrandMint
    SettingSection(
        title = "Latar belakang & autostart",
        subtitle = "Agar cek episode baru tidak tertunda",
        icon = Icons.Rounded.BatterySaver,
        tint = tint,
        badge = if (status.needsAttention) "Perlu izin" else "Siap",
    ) {
        BackgroundStatusRow(
            title = "Optimasi baterai",
            subtitle = if (status.batteryOptimizationIgnored) {
                "Aplikasi dikecualikan dari pembatasan baterai."
            } else {
                "Sistem masih boleh menunda pekerjaan latar belakang."
            },
            ready = status.batteryOptimizationIgnored,
            badge = if (status.batteryOptimizationIgnored) "Aman" else "Batasi",
        )

        status.backgroundRestricted?.let { restricted ->
            Spacer(Modifier.height(8.dp))
            BackgroundStatusRow(
                title = "Batasan latar belakang",
                subtitle = if (restricted) {
                    "Aktivitas latar belakang sedang dibatasi sistem."
                } else {
                    "Tidak ada pembatasan latar belakang."
                },
                ready = !restricted,
                badge = if (restricted) "Dibatasi" else "Normal",
            )
        }

        status.dataSaverRestricted?.let { restricted ->
            Spacer(Modifier.height(8.dp))
            BackgroundStatusRow(
                title = "Hemat data",
                subtitle = if (restricted) {
                    "Pembatasan data dapat menunda cek otomatis."
                } else {
                    "Data latar belakang tidak dibatasi."
                },
                ready = !restricted,
                badge = if (restricted) "Aktif" else "Nonaktif",
            )
        }

        Spacer(Modifier.height(8.dp))
        BackgroundStatusRow(
            title = "Notifikasi",
            subtitle = if (status.notificationsEnabled) {
                "Episode baru dan hasil cek otomatis bisa diberitahukan."
            } else {
                "Notifikasi diblokir; hasil cek tidak terlihat."
            },
            ready = status.notificationsEnabled,
            badge = if (status.notificationsEnabled) "Diizinkan" else "Diblokir",
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassButton(
                text = if (status.batteryOptimizationIgnored) {
                    "Kelola baterai"
                } else {
                    "Matikan optimasi"
                },
                onClick = {
                    launchTargets(context, access.batteryTargets(), onMessage)
                    status = access.status()
                },
                icon = Icons.Rounded.BatterySaver,
                modifier = Modifier.weight(1f),
                height = 44.dp,
                tint = if (status.batteryOptimizationIgnored) TextSecondary else Warning,
            )
            GlassButton(
                text = "Autostart",
                onClick = {
                    launchTargets(context, access.autoStartTargets(), onMessage)
                    status = access.status()
                },
                icon = Icons.Rounded.Restore,
                modifier = Modifier.weight(1f),
                height = 44.dp,
                tint = BrandMint,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassButton(
                text = if (status.notificationsEnabled) {
                    "Notifikasi"
                } else {
                    "Izinkan notifikasi"
                },
                onClick = {
                    when {
                        !status.notificationsEnabled &&
                            status.notificationPermissionRequired &&
                            !notificationRequested -> {
                            notificationRequested = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        else -> launchTargets(
                            context = context,
                            targets = listOf(
                                BackgroundAccessTarget(
                                    label = "pengaturan notifikasi",
                                    intent = access.notificationSettingsIntent(),
                                ),
                            ),
                            onMessage = onMessage,
                        )
                    }
                },
                icon = Icons.Rounded.NotificationsActive,
                modifier = Modifier.weight(1f),
                height = 44.dp,
                tint = if (status.notificationsEnabled) TextSecondary else Warning,
            )
            val dataSaverRestricted = status.dataSaverRestricted == true
            GlassButton(
                text = if (dataSaverRestricted) "Hemat data" else "Info aplikasi",
                onClick = {
                    launchTargets(
                        context = context,
                        targets = if (dataSaverRestricted) {
                            access.dataSaverTargets()
                        } else {
                            listOf(
                                BackgroundAccessTarget(
                                    label = "info aplikasi",
                                    intent = access.appDetailsIntent(),
                                ),
                            )
                        },
                        onMessage = onMessage,
                    )
                },
                icon = if (dataSaverRestricted) {
                    Icons.Rounded.Sync
                } else {
                    Icons.Rounded.Info
                },
                modifier = Modifier.weight(1f),
                height = 44.dp,
                tint = if (dataSaverRestricted) Warning else TextSecondary,
            )
        }

        Spacer(Modifier.height(9.dp))
        Caption(
            "Target autostart: ${access.autoStartLabel()}. ${access.guide()} " +
                "Android tetap bisa menunda WorkManager saat Doze; aplikasi menjalankan " +
                "pemeriksaan susulan saat dibuka.",
        )
    }
}

@Composable
private fun BackgroundStatusRow(
    title: String,
    subtitle: String,
    ready: Boolean,
    badge: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = subtitle,
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(
            text = badge,
            color = if (ready) BrandMint else Warning,
        )
    }
}

/**
 * Starts the first target that the ROM accepts. `startActivity` is used instead
 * of resolving components first so Android 11 package visibility does not hide
 * an OEM settings activity that actually exists.
 */
private fun launchTargets(
    context: Context,
    targets: List<BackgroundAccessTarget>,
    onMessage: (String) -> Unit,
) {
    for (target in targets) {
        val started = runCatching {
            context.startActivity(
                Intent(target.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (started) {
            Timber.i("Membuka pengaturan %s", target.label)
            onMessage("Membuka ${target.label}…")
            return
        }
    }
    Timber.w("Tidak ada pengaturan latar belakang yang bisa dibuka")
    onMessage(
        "Pengaturan tidak tersedia di perangkat ini. Buka Info aplikasi secara manual.",
    )
}
