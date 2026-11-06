package app.samehadaku.dl.presentation.util

import app.samehadaku.dl.core.Names
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.domain.model.DownloadState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) {
        "${value.toLong()} ${units[index]}"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }
}

fun formatSpeed(bytesPerSecond: Long): String =
    if (bytesPerSecond <= 0L) "" else "${formatBytes(bytesPerSecond)}/dtk"

fun stateLabel(state: DownloadState): String = when (state) {
    DownloadState.QUEUED -> "Menunggu"
    DownloadState.RESOLVING -> "Mencari tautan"
    DownloadState.DOWNLOADING -> "Mengunduh"
    DownloadState.PAUSED -> "Dijeda"
    DownloadState.DONE -> "Selesai"
    DownloadState.FAILED -> "Gagal"
    DownloadState.SKIPPED -> "Dilewati"
    DownloadState.CANCELED -> "Dibatalkan"
}

/** Turns a slug such as `dragon-ball-super-beerus` into a readable title. */
fun prettyName(raw: String): String = Names.fromSlug(raw)

fun prettyTitleFromUrl(url: String): String {
    val segment = UrlUtils.lastPathSegment(url)
    return prettyName(segment.ifBlank { url })
}

fun isToday(timestamp: Long): Boolean {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
}

fun dateGroupLabel(timestamp: Long): String = if (isToday(timestamp)) {
    "Hari ini"
} else {
    SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(Date(timestamp))
}

fun shortDate(timestamp: Long): String =
    SimpleDateFormat("d MMM", Locale("id", "ID")).format(Date(timestamp))

fun shortDateTime(timestamp: Long): String =
    SimpleDateFormat("d MMM, HH.mm", Locale("id", "ID")).format(Date(timestamp))

