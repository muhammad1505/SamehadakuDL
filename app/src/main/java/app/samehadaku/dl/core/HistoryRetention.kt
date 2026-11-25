package app.samehadaku.dl.core

/**
 * Rules for the optional automatic cleanup of the download history list.
 *
 * This only decides which finished rows leave the list in `Unduhan`; the files on
 * disk are owned by Smart Storage and are never touched here.
 */
object HistoryRetention {

    const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

    /** 0 means "keep everything"; positive values are capped to one year. */
    const val MAX_DAYS: Int = 365

    fun normalizedDays(days: Int): Int = days.coerceIn(0, MAX_DAYS)

    /**
     * True when an entry whose last activity was [referenceMillis] is older than
     * [retentionDays] at [nowMillis].
     *
     * A missing timestamp (0) and a clock that moved backwards keep the entry:
     * losing history is worse than keeping one row a little longer.
     */
    fun isExpired(referenceMillis: Long, nowMillis: Long, retentionDays: Int): Boolean {
        val days = normalizedDays(retentionDays)
        if (days <= 0) return false
        if (referenceMillis <= 0L) return false
        val age = nowMillis - referenceMillis
        return age >= days * DAY_MILLIS
    }
}
