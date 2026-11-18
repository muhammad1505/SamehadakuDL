package app.samehadaku.dl.core

import android.content.Context
import android.util.Log
import java.io.File
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * In-app diagnostic log. Timber forwards every decision made by the downloader here,
 * and the settings screen can show or share it without requiring a computer.
 *
 * Only the most recent lines are kept. The file is the source used after a restart,
 * so a problem that happened just before the app was closed is still reportable.
 */
object AppLog {

    private const val MAX_ENTRIES = 1_200
    private const val MAX_FILE_BYTES = 512 * 1024L

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private var file: File? = null

    fun init(context: Context) {
        if (file != null) return
        val target = File(context.filesDir, "samehadaku.log")
        file = target
        val restored = runCatching {
            if (target.exists()) target.readLines().takeLast(MAX_ENTRIES) else emptyList()
        }.getOrDefault(emptyList())
        _lines.value = restored
    }

    fun clear() {
        synchronized(this) {
            _lines.value = emptyList()
            runCatching { file?.writeText("") }
        }
    }

    fun text(): String = _lines.value.joinToString("\n")

    fun record(priority: Int, tag: String?, message: String?, throwable: Throwable?) {
        val body = buildString {
            if (!message.isNullOrBlank()) append(message)
            if (throwable != null) {
                if (isNotEmpty()) append('\n')
                val dnsFailure = throwable.dnsFailure()
                if (dnsFailure != null) {
                    // DNS outages repeat for every mirror and every subscription.
                    // One concise cause is enough; a stack trace would evict the
                    // more useful entries from the in-app log.
                    append("DNS: ${dnsFailure.message ?: "host tidak dapat ditemukan"}")
                } else {
                    append(Log.getStackTraceString(throwable).trimEnd())
                }
            }
        }
        if (body.isBlank()) return
        val line = "${timeFormat.format(Date())} ${priorityLabel(priority)}/${tag ?: "App"}: $body"
        synchronized(this) {
            val updated = (_lines.value + line).takeLast(MAX_ENTRIES)
            _lines.value = updated
            runCatching {
                val target = file ?: return@runCatching
                if (target.length() >= MAX_FILE_BYTES) {
                    target.writeText(updated.joinToString("\n") + "\n")
                } else {
                    target.appendText(line + "\n")
                }
            }
        }
    }

    private fun Throwable.dnsFailure(): UnknownHostException? =
        generateSequence(this) { it.cause }
            .filterIsInstance<UnknownHostException>()
            .firstOrNull()

    private fun priorityLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        else -> "?"
    }

    /** Timber tree that keeps every log line available inside the app. */
    class Tree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            record(priority, tag, message, t)
        }
    }
}
