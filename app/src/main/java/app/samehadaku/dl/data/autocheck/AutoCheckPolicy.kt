package app.samehadaku.dl.data.autocheck

import java.util.concurrent.TimeUnit

/** WorkManager refuses periodic work shorter than 15 minutes. */
internal const val MIN_AUTO_CHECK_MINUTES = 15

/**
 * Whether the resume safety net should enqueue a one-off check.
 *
 * Kept free of Android classes so the boundary cases can be unit tested. A
 * zero timestamp means the app has never checked; a clock that moved backwards
 * is treated as "not due" instead of starting an endless retry.
 */
internal fun isAutoCheckDue(
    lastCheckAt: Long,
    now: Long,
    intervalMinutes: Int,
): Boolean {
    if (lastCheckAt <= 0L) return true
    val interval = TimeUnit.MINUTES.toMillis(
        intervalMinutes.coerceAtLeast(MIN_AUTO_CHECK_MINUTES).toLong(),
    )
    return now - lastCheckAt >= interval
}
