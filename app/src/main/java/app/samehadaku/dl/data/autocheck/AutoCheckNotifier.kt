package app.samehadaku.dl.data.autocheck

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.samehadaku.dl.MainActivity
import app.samehadaku.dl.R
import app.samehadaku.dl.data.notifications.NotificationRepository
import app.samehadaku.dl.domain.model.AppNotificationKind
import app.samehadaku.dl.domain.model.QualityPreference
import app.samehadaku.dl.domain.usecase.SubscriptionCheckResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Posts the result of a background subscription check. Each notification is also
 * stored in the inbox so the Beranda bell can show it again with the right route.
 */
@Singleton
class AutoCheckNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inbox: NotificationRepository,
) {

    suspend fun notifyNewEpisodes(result: SubscriptionCheckResult) {
        val count = result.newEpisodeCount
        if (count <= 0) return
        val title = if (count == 1) {
            "1 episode baru tersedia"
        } else {
            "$count episode baru tersedia"
        }
        val body = "${collapsedNames(result)} • Pilih kualitas dan server di Langganan " +
            "agar episode baru diunduh otomatis."
        inbox.add(
            kind = AppNotificationKind.NEW_EPISODES,
            title = title,
            message = body,
            route = MainActivity.ROUTE_SUBSCRIPTIONS,
        )
        notify(
            id = NEW_EPISODES_ID,
            title = title,
            text = body,
            bigText = "${expandedEpisodes(result)}\n$body",
            route = MainActivity.ROUTE_SUBSCRIPTIONS,
        )
    }

    /** Posted after the background check has queued the new episodes for download. */
    suspend fun notifyDownloadsQueued(
        result: SubscriptionCheckResult,
        queuedCount: Int,
        skippedCount: Int,
        quality: QualityPreference?,
    ) {
        if (queuedCount <= 0) return
        val title = if (queuedCount == 1) {
            "Mengunduh 1 episode baru"
        } else {
            "Mengunduh $queuedCount episode baru"
        }
        val qualityLine = quality?.let {
            "Kualitas: ${it.format.label} • ${it.resolution.uppercase()} • ${it.host.label}"
        }
        val body = buildString {
            append(collapsedNames(result))
            if (qualityLine != null) append(" • $qualityLine")
        }
        val bigText = buildString {
            append(expandedEpisodes(result))
            if (qualityLine != null) append("\n$qualityLine")
            if (skippedCount > 0) {
                append("\n$skippedCount episode sudah ada di antrean.")
            }
        }
        inbox.add(
            kind = AppNotificationKind.QUEUED,
            title = title,
            message = bigText,
            route = MainActivity.ROUTE_QUEUE,
        )
        notify(
            id = QUEUED_ID,
            title = title,
            text = body,
            bigText = bigText,
            route = MainActivity.ROUTE_QUEUE,
        )
    }

    private fun collapsedNames(result: SubscriptionCheckResult): String {
        val names = result.checks.map { it.subscription.name }
        return when {
            names.isEmpty() -> "Cek episode baru selesai."
            names.size <= 2 -> names.joinToString(" • ")
            else -> "${names.take(2).joinToString(" • ")} +${names.size - 2} anime"
        }
    }

    private fun expandedEpisodes(result: SubscriptionCheckResult): String =
        result.checks.joinToString("\n") { check ->
            val numbers = check.newEpisodes.map { it.number }.sorted()
            val label = when {
                numbers.isEmpty() -> "tanpa episode baru"
                numbers.size == 1 -> "Ep ${numbers.first()}"
                numbers.zipWithNext().all { (first, second) -> second == first + 1 } ->
                    "Ep ${numbers.first()}–${numbers.last()}"
                else -> numbers.joinToString(", ") { "Ep $it" }
            }
            "${check.subscription.name}: $label"
        }

    suspend fun notifyVerificationNeeded() {
        val message = "Cek episode baru tertunda. Buka aplikasi untuk verifikasi."
        inbox.add(
            kind = AppNotificationKind.VERIFICATION,
            title = "Verifikasi Cloudflare diperlukan",
            message = message,
            route = MainActivity.ROUTE_CLOUDFLARE,
        )
        notify(
            id = VERIFICATION_ID,
            title = "Verifikasi Cloudflare diperlukan",
            text = message,
            bigText = "Situs meminta verifikasi Cloudflare. Selesaikan verifikasi agar " +
                "pemeriksaan episode baru bisa berjalan lagi.",
            route = MainActivity.ROUTE_CLOUDFLARE,
        )
    }

    private fun notify(
        id: Int,
        title: String,
        text: String,
        bigText: String,
        route: String,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, route)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        runCatching { manager.notify(id, notification) }
            .onFailure { Timber.w(it, "Failed to post auto-check notification") }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Episode baru",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Episode baru dari anime yang diikuti otomatis masuk antrean unduhan."
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "subscription_updates"
        const val NEW_EPISODES_ID = 4201
        const val VERIFICATION_ID = 4202
        const val QUEUED_ID = 4203
    }
}
