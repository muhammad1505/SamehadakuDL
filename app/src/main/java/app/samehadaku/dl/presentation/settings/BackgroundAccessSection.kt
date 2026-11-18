package app.samehadaku.dl.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.samehadaku.dl.core.BackgroundAccess
import app.samehadaku.dl.core.BackgroundAccessTarget
import app.samehadaku.dl.presentation.components.StatusPill
import app.samehadaku.dl.presentation.theme.BrandAccent
import app.samehadaku.dl.presentation.theme.BrandMint
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary
import app.samehadaku.dl.presentation.theme.TextSecondary
import app.samehadaku.dl.presentation.theme.Warning
import timber.log.Timber

/**
 * Compact access panel for background work. Android does not allow an app to grant
 * these exemptions itself, and the relevant screens differ by Android version and
 * OEM, so every row opens the most specific screen that exists and falls back to
 * battery optimization or app info. Rows that are already fine stay hidden unless
 * they still need an action, which keeps the panel short; the OEM guide is collapsed
 * until the user asks for it. Notification permission is intentionally not requested
 * here—the app asks once after the splash, and this panel only opens the system
 * settings for users who dismissed or blocked it.
 */
@Composable
internal fun BackgroundAccessSection(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val access = remember(context) { BackgroundAccess(context) }
    var status by remember { mutableStateOf(access.status()) }
    var showGuide by rememberSaveable { mutableStateOf(false) }

    // Covers returning from the system battery/autostart/notification screens.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = access.status()
    }

    val appInfoMode = access.prefersAppInfoForBackground()
    // Only checks the system can actually report are counted, so the summary
    // never implies that autostart (unreadable by design) is still off.
    val checks = listOfNotNull(
        status.batteryOptimizationIgnored,
        status.backgroundRestricted?.let { !it },
        status.dataSaverRestricted?.let { !it },
        status.notificationsEnabled,
    )
    val readyCount = checks.count { it }
    val totalCount = checks.size
    val allReady = readyCount == totalCount
    val tint = if (allReady) BrandMint else Warning
    val autoStartLabel = access.autoStartLabel()
    val autoStartSubtitle = if (autoStartLabel == "Info aplikasi") {
        "Buka Info aplikasi → Baterai."
    } else {
        "Buka pengaturan $autoStartLabel."
    }
    SettingSection(
        title = "Latar belakang & autostart",
        subtitle = if (appInfoMode) {
            "Izinkan latar belakang dan autostart"
        } else {
            "Agar cek episode baru tidak tertunda"
        },
        icon = Icons.Rounded.BatterySaver,
        tint = tint,
        badge = "$readyCount/$totalCount siap",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Status izin sistem",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (allReady) {
                        "Semua status yang bisa dibaca sudah siap."
                    } else {
                        "$readyCount dari $totalCount siap. Baris berlabel " +
                            "“Belum” masih perlu diatur."
                    },
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(
                text = if (allReady) "Siap" else "Belum lengkap",
                color = tint,
            )
        }
        Text(
            text = "Periksa ulang",
            color = BrandAccent,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clickable {
                    status = access.status()
                    onMessage("Status izin diperbarui.")
                }
                .clip(RoundedCornerShape(10.dp))
                .padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(2.dp))

        if (appInfoMode) {
            AccessRow(
                title = "Izin latar belakang",
                subtitle = if (status.batteryOptimizationIgnored) {
                    "Sudah dikecualikan dari optimasi baterai."
                } else {
                    "Belum diizinkan; buka Info aplikasi → Baterai."
                },
                badge = if (status.batteryOptimizationIgnored) "Sudah" else "Belum",
                ready = status.batteryOptimizationIgnored,
                onClick = {
                    launchTargets(context, access.batteryTargets(), onMessage)
                    status = access.status()
                },
            )

            AccessRow(
                title = "Autostart",
                subtitle = "Diatur manual di Info aplikasi; status tidak bisa dibaca otomatis.",
                badge = "Manual",
                badgeColor = TextSecondary,
                onClick = {
                    launchTargets(context, access.autoStartTargets(), onMessage)
                    status = access.status()
                },
            )
        } else {
            AccessRow(
                title = "Optimasi baterai",
                subtitle = if (status.batteryOptimizationIgnored) {
                    "Sudah dikecualikan; cek tidak mudah ditunda."
                } else {
                    "Masih dioptimalkan; cek otomatis bisa tertunda."
                },
                badge = if (status.batteryOptimizationIgnored) "Sudah" else "Belum",
                ready = status.batteryOptimizationIgnored,
                onClick = {
                    launchTargets(context, access.batteryTargets(), onMessage)
                    status = access.status()
                },
            )

            AccessRow(
                title = "Autostart",
                subtitle = autoStartSubtitle,
                badge = "Tidak terbaca",
                badgeColor = TextSecondary,
                onClick = {
                    launchTargets(context, access.autoStartTargets(), onMessage)
                    status = access.status()
                },
            )
        }

        if (status.backgroundRestricted == true) {
            AccessRow(
                title = "Batasan latar belakang",
                subtitle = "Aktivitas dibatasi sistem; atur lewat Info aplikasi.",
                badge = "Dibatasi",
                ready = false,
                onClick = {
                    launchTargets(
                        context = context,
                        targets = listOf(
                            BackgroundAccessTarget(
                                label = "info aplikasi",
                                intent = access.appDetailsIntent(),
                            ),
                        ),
                        onMessage = onMessage,
                    )
                },
            )
        }

        if (status.dataSaverRestricted == true) {
            AccessRow(
                title = "Hemat data",
                subtitle = "Data latar belakang dibatasi; cek otomatis bisa tertunda.",
                badge = "Aktif",
                ready = false,
                onClick = {
                    launchTargets(context, access.dataSaverTargets(), onMessage)
                    status = access.status()
                },
            )
        }

        if (!status.notificationsEnabled) {
            AccessRow(
                title = "Notifikasi",
                subtitle = if (status.notificationPermissionRequired) {
                    "Izin belum diberikan; buka pengaturan notifikasi."
                } else {
                    "Notifikasi diblokir; buka pengaturan notifikasi."
                },
                badge = "Diblokir",
                ready = false,
                onClick = {
                    launchTargets(
                        context = context,
                        targets = listOf(
                            BackgroundAccessTarget(
                                label = "pengaturan notifikasi",
                                intent = access.notificationSettingsIntent(),
                            ),
                        ),
                        onMessage = onMessage,
                    )
                    status = access.status()
                },
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showGuide = !showGuide }
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Panduan autostart",
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
                text = access.guide(),
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Android tetap bisa menunda pemeriksaan saat Doze; aplikasi " +
                    "menjalankannya lagi saat dibuka.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AccessRow(
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color? = null,
    ready: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
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
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            StatusPill(
                text = badge,
                color = badgeColor ?: if (ready) BrandMint else Warning,
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp),
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
