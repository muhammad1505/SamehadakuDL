package app.samehadaku.dl.data.smartstorage

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
import app.samehadaku.dl.domain.usecase.SmartStorageCleanupResult
import app.samehadaku.dl.presentation.util.formatBytes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Reports background cleanup results. The worker has no activity, so files that
 * need a system confirmation can only be surfaced here; tapping the notification
 * opens the Smart Storage screen where they can be handled individually.
 */
@Singleton
class SmartStorageNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun notifyCleanup(result: SmartStorageCleanupResult) {
        if (result.disabled) return
        if (result.deletedFiles <= 0 && result.blockedFiles <= 0) return

        val title = when {
            result.deletedFiles > 0 && result.blockedFiles > 0 ->
                "Smart Storage membersihkan ${result.deletedFiles} episode"
            result.deletedFiles > 0 ->
                "Smart Storage menghapus ${result.deletedFiles} episode"
            else -> "Sebagian file Smart Storage tertahan"
        }
        val body = buildList {
            if (result.deletedFiles > 0) {
                add("${formatBytes(result.reclaimedBytes)} ruang dikembalikan.")
            }
            if (result.blockedFiles > 0) {
                add(
                    "${result.blockedFiles} file menunggu konfirmasi atau ditolak " +
                        "penyimpanan.",
                )
            }
            if (result.skippedFiles > 0) {
                add("${result.skippedFiles} file dilewati karena ukurannya berubah.")
            }
        }.joinToString(" ")

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, MainActivity.ROUTE_SMART_STORAGE)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Timber.w(it, "Gagal menampilkan notifikasi Smart Storage") }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Smart Storage",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Hasil pembersihan episode tonton dan file yang perlu perhatian."
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "smart_storage_cleanup"
        const val NOTIFICATION_ID = 4301
    }
}
