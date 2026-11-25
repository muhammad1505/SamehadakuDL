package app.samehadaku.dl.data.download

import app.samehadaku.dl.data.network.HttpClientFactory
import app.samehadaku.dl.domain.model.Host
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber

/** One byte range of a file, downloaded over its own connection. */
data class Segment(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1L
}

/**
 * Splits [totalBytes] into at most [connections] contiguous ranges covering the file
 * exactly once. Files too small for the requested connection count collapse into a
 * single range, so a short clip never opens needless sockets.
 */
fun planSegments(
    totalBytes: Long,
    connections: Int,
    minChunkBytes: Long = MIN_TURBO_CHUNK_BYTES,
): List<Segment> {
    if (totalBytes <= 0L) return emptyList()
    val requested = connections.coerceIn(1, MAX_TURBO_CONNECTIONS)
    if (requested <= 1) return listOf(Segment(0L, totalBytes - 1L))
    val evenChunk = totalBytes / requested
    if (evenChunk < minChunkBytes) {
        val bySize = (totalBytes / minChunkBytes).toInt().coerceIn(1, requested)
        return splitEvenly(totalBytes, bySize)
    }
    return splitEvenly(totalBytes, requested)
}

private fun splitEvenly(totalBytes: Long, count: Int): List<Segment> {
    val chunk = totalBytes / count
    val segments = ArrayList<Segment>(count)
    var start = 0L
    for (index in 0 until count) {
        val endExclusive = if (index == count - 1) totalBytes else start + chunk
        if (endExclusive > start) segments += Segment(start, endExclusive - 1L)
        start = endExclusive
    }
    return segments
}

/**
 * Downloads one file over several byte ranges at once.
 *
 * A single TCP connection rarely saturates a modern mobile link, so a large episode
 * is fetched as up to [MAX_TURBO_CONNECTIONS] parallel ranges and written into the
 * same `.part` file at fixed offsets. Everything here is best-effort: when the host
 * ignores `Range`, answers with another code, or any segment fails, the caller falls
 * back to the ordinary single-connection download instead of surfacing an error.
 */
@Singleton
class TurboDownloader @Inject constructor(
    private val clients: HttpClientFactory,
) {

    /** Total size when the host honours byte ranges, otherwise null. */
    suspend fun probe(url: String, headers: Map<String, String>): Long? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .header("Range", "bytes=0-0")
                .build()
            try {
                clients.downloadClient.newCall(request).execute().use { response ->
                    if (response.code != 206) {
                        Timber.i("Turbo dilewati: HTTP %d untuk %s", response.code, url)
                        return@withContext null
                    }
                    val contentRange = response.header("Content-Range").orEmpty()
                    CONTENT_RANGE.find(contentRange)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toLongOrNull()
                        ?.takeIf { total -> total > 0L }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Timber.w(error, "Probe turbo gagal untuk %s", url)
                null
            }
        }

    /**
     * Fetches [totalBytes] of [url] into [partFile] over [connections] ranges.
     *
     * Returns false and removes the partial file when the host or the network cannot
     * finish the job, so the caller can retry with one connection.
     */
    suspend fun download(
        url: String,
        headers: Map<String, String>,
        partFile: File,
        totalBytes: Long,
        connections: Int,
        host: Host,
        fileName: String,
        listener: DownloadProgressListener,
    ): Boolean = withContext(Dispatchers.IO) {
        val segments = planSegments(totalBytes, connections)
        if (segments.size <= 1) return@withContext false

        partFile.parentFile?.mkdirs()
        partFile.delete()
        RandomAccessFile(partFile, "rw").use { file -> file.setLength(totalBytes) }

        val downloaded = AtomicLong(0L)
        try {
            coroutineScope {
                val reporter = launch {
                    var lastBytes = 0L
                    var lastAt = System.currentTimeMillis()
                    while (isActive) {
                        delay(PROGRESS_INTERVAL_MS)
                        val current = downloaded.get()
                        val now = System.currentTimeMillis()
                        val elapsed = (now - lastAt).coerceAtLeast(1L)
                        val speed = ((current - lastBytes) * 1000L / elapsed).coerceAtLeast(0L)
                        listener.onProgress(
                            DownloadProgress(current, totalBytes, speed, host, fileName),
                        )
                        lastBytes = current
                        lastAt = now
                    }
                }
                try {
                    segments.map { segment ->
                        async {
                            fetchSegment(url, headers, partFile, segment) { bytes ->
                                downloaded.addAndGet(bytes)
                            }
                        }
                    }.awaitAll()
                } finally {
                    reporter.cancel()
                }
            }
        } catch (cancellation: CancellationException) {
            partFile.delete()
            throw cancellation
        } catch (error: Exception) {
            Timber.w(error, "Unduhan turbo gagal, memakai satu koneksi untuk %s", fileName)
            partFile.delete()
            return@withContext false
        }

        if (partFile.length() != totalBytes) {
            partFile.delete()
            return@withContext false
        }
        listener.onProgress(DownloadProgress(totalBytes, totalBytes, 0L, host, fileName))
        true
    }

    private suspend fun fetchSegment(
        url: String,
        headers: Map<String, String>,
        partFile: File,
        segment: Segment,
        onBytes: (Long) -> Unit,
    ) {
        var written = 0L
        var lastError: Exception? = null
        for (attempt in 1..SEGMENT_ATTEMPTS) {
            val start = segment.start + written
            if (start > segment.endInclusive) return
            val request = Request.Builder()
                .url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .header("Range", "bytes=$start-${segment.endInclusive}")
                .build()
            val call = clients.downloadClient.newCall(request)
            val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
                call.cancel()
            }
            try {
                call.execute().use { response ->
                    if (response.code != 206) {
                        throw IOException("Host menolak rentang byte (HTTP ${response.code})")
                    }
                    val body = response.body ?: throw IOException("Respons kosong dari host")
                    body.byteStream().use { input ->
                        RandomAccessFile(partFile, "rw").use { file ->
                            file.seek(start)
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                file.write(buffer, 0, read)
                                written += read
                                onBytes(read.toLong())
                            }
                        }
                    }
                }
                if (written >= segment.length) return
                throw IOException("Segmen berhenti sebelum selesai")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                lastError = error
                Timber.w(
                    error,
                    "Segmen %d-%d gagal pada percobaan %d",
                    segment.start,
                    segment.endInclusive,
                    attempt,
                )
            } finally {
                cancellationHandle?.dispose()
            }
        }
        throw IOException(lastError?.message ?: "Segmen tidak dapat diunduh")
    }

    private companion object {
        val CONTENT_RANGE = Regex("""/(\d{1,18})""")
        const val SEGMENT_ATTEMPTS = 2
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 500L
    }
}

/** Range below this size is not worth its connection. */
const val MIN_TURBO_CHUNK_BYTES: Long = 4L * 1024 * 1024

/** Upper bound on simultaneous connections for one file. */
const val MAX_TURBO_CONNECTIONS: Int = 6

/** Connections used when the turbo option is enabled. */
const val TURBO_CONNECTIONS: Int = 4

/** Files smaller than this are left to the ordinary one-connection path. */
const val MIN_TURBO_TOTAL_BYTES: Long = 8L * 1024 * 1024
