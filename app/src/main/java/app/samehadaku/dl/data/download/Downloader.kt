package app.samehadaku.dl.data.download

import android.content.Context
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.host.HostResolverRegistry
import app.samehadaku.dl.data.network.HttpClientFactory
import app.samehadaku.dl.data.parser.HostSelector
import app.samehadaku.dl.data.settings.SettingsRepository
import app.samehadaku.dl.domain.model.DownloadLinks
import app.samehadaku.dl.domain.model.DownloadTask
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.HostResolveResult
import app.samehadaku.dl.domain.model.VideoFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

data class DownloadProgress(
    val bytesDone: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val host: Host,
    val fileName: String,
)

fun interface DownloadProgressListener {
    fun onProgress(progress: DownloadProgress)
}

sealed interface DownloadOutcome {
    data class Done(
        val fileName: String,
        val alreadyExisted: Boolean,
        /** Quality actually downloaded, which may differ from the requested one. */
        val format: VideoFormat,
        val resolution: String,
        /** Server that actually served the file; kept for history and notifications. */
        val host: Host,
        val adjusted: Boolean = false,
    ) : DownloadOutcome

    data class Failed(val reasons: List<String>) : DownloadOutcome

    data class Skipped(val reason: String) : DownloadOutcome
}

@Singleton
class Downloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clients: HttpClientFactory,
    private val resolvers: HostResolverRegistry,
    private val storageWriter: StorageWriter,
    private val settings: SettingsRepository,
) {

    suspend fun download(
        task: DownloadTask,
        links: DownloadLinks,
        listener: DownloadProgressListener,
        selectedCandidates: List<HostSelector.Selection>? = null,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val allowQualityFallback = settings.currentQualityDowngrade()
        val candidates = selectedCandidates ?: HostSelector.findCandidates(
            links = links,
            format = task.format,
            resolution = task.resolution,
            preferredHost = task.preferredHost,
        ).filter { candidate -> !candidate.adjusted || allowQualityFallback }
        val choices = candidates.filter { candidate -> !candidate.adjusted || allowQualityFallback }

        if (choices.isEmpty()) {
            Timber.i(
                "No %s %s candidate for %s",
                task.format.label,
                task.resolution,
                task.episodeUrl,
            )
            return@withContext DownloadOutcome.Skipped(
                "Kualitas ${task.format.label} ${task.resolution} tidak tersedia",
            )
        }

        val downloadDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        if (!downloadDir.isDirectory) {
            return@withContext DownloadOutcome.Failed(
                listOf("Folder sementara tidak dapat dibuat"),
            )
        }

        val reasons = mutableListOf<String>()

        for (selection in choices) {
            val actualFormat = selection.format
            val actualResolution = selection.resolution
            Timber.i(
                "Try %s %s (%d host%s)%s for episode %s",
                actualFormat.label,
                actualResolution,
                selection.targets.size,
                if (selection.targets.size == 1) "" else "s",
                if (selection.adjusted) " [fallback format]" else "",
                task.episodeUrl,
            )
            for (target in selection.targets) {
                currentCoroutineContext().ensureActive()
                Timber.i("Try %s: %s", target.host.label, target.url)

                val resolved = when (val result = resolvers.resolve(target.host, target.url)) {
                    is HostResolveResult.Success -> result
                    is HostResolveResult.Failure -> {
                        Timber.w("Resolve %s failed: %s", target.host.label, result.reason)
                        reasons += "${target.host.label}: ${result.reason}"
                        continue
                    }
                }
                val directUrl = resolved.directUrl

                val extension = extensionFor(actualFormat, directUrl)
                val fileName = buildFileName(task, actualFormat, actualResolution, extension)

                when (
                    val result = downloadSingle(
                        host = target.host,
                        directUrl = directUrl,
                        headers = resolved.headers,
                        fileName = fileName,
                        animeSlug = task.animeSlug,
                        listener = listener,
                        downloadDir = downloadDir,
                    )
                ) {
                    is SingleOutcome.Success -> {
                        Timber.i(
                            "Downloaded %s %s from %s%s",
                            actualFormat.label,
                            actualResolution,
                            target.host.label,
                            if (selection.adjusted) " (format fallback)" else "",
                        )
                        return@withContext DownloadOutcome.Done(
                            fileName = fileName,
                            alreadyExisted = result.alreadyExisted,
                            format = actualFormat,
                            resolution = actualResolution,
                            host = target.host,
                            adjusted = selection.adjusted,
                        )
                    }
                    is SingleOutcome.HostFailure -> {
                        Timber.w("Host %s failed: %s", target.host.label, result.reason)
                        reasons += "${target.host.label}: ${result.reason}"
                        continue
                    }
                    is SingleOutcome.LocalFailure -> {
                        reasons += "${target.host.label}: ${result.reason}"
                        return@withContext DownloadOutcome.Failed(reasons.toList())
                    }
                }
            }
        }

        DownloadOutcome.Failed(reasons.toList())
    }

    private suspend fun downloadSingle(
        host: Host,
        directUrl: String,
        headers: Map<String, String>,
        fileName: String,
        animeSlug: String,
        listener: DownloadProgressListener,
        downloadDir: File,
    ): SingleOutcome {
        val partFile = File(downloadDir, "$fileName.part")
        var attempt = 0

        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val resumeFrom = if (partFile.exists()) partFile.length() else 0L
            val request = Request.Builder()
                .url(directUrl)
                .apply {
                    for ((name, value) in headers) header(name, value)
                    if (resumeFrom > 0L) header("Range", "bytes=$resumeFrom-")
                }
                .build()
            val call = clients.downloadClient.newCall(request)
            val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
                call.cancel()
            }

            var responseOutcome: ResponseOutcome? = null
            try {
                responseOutcome = call.execute().use { response ->
                    handleResponse(
                        response = response,
                        partFile = partFile,
                        fileName = fileName,
                        animeSlug = animeSlug,
                        host = host,
                        listener = listener,
                        resumeFrom = resumeFrom,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                if (attempt >= MAX_ATTEMPTS) {
                    partFile.delete()
                    return SingleOutcome.HostFailure(
                        "Koneksi ke host gagal: ${error.message ?: "tidak diketahui"}",
                    )
                }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                partFile.delete()
                return SingleOutcome.HostFailure(
                    "Gagal mengunduh dari host: ${error.message ?: "kesalahan tidak diketahui"}",
                )
            } finally {
                cancellationHandle?.dispose()
            }

            when (val outcome = responseOutcome) {
                is ResponseOutcome.Complete -> return outcome.outcome
                ResponseOutcome.Retry -> continue
                null -> continue
            }
        }

        partFile.delete()
        return SingleOutcome.HostFailure("Unduhan gagal setelah beberapa percobaan")
    }

    private suspend fun handleResponse(
        response: Response,
        partFile: File,
        fileName: String,
        animeSlug: String,
        host: Host,
        listener: DownloadProgressListener,
        resumeFrom: Long,
    ): ResponseOutcome {
        if (response.code == 416 && resumeFrom > 0L) {
            partFile.delete()
            return ResponseOutcome.Retry
        }

        if (!response.isSuccessful) {
            partFile.delete()
            return ResponseOutcome.Complete(
                SingleOutcome.HostFailure(httpReason(response.code)),
            )
        }

        val contentType = response.header("Content-Type").orEmpty().lowercase()
        if ("text/html" in contentType) {
            partFile.delete()
            return ResponseOutcome.Complete(
                SingleOutcome.HostFailure("Host mengirim halaman web, bukan file"),
            )
        }

        val body = response.body
        if (body == null) {
            partFile.delete()
            return ResponseOutcome.Complete(
                SingleOutcome.HostFailure("Respons kosong dari host"),
            )
        }

        val partial = response.code == 206
        val contentLength = body.contentLength()
        var totalBytes = if (partial && contentLength >= 0L) {
            resumeFrom + contentLength
        } else if (!partial && contentLength >= 0L) {
            contentLength
        } else {
            0L
        }

        if (totalBytes > 0L && storageWriter.existsWithSize(animeSlug, fileName, totalBytes)) {
            partFile.delete()
            return ResponseOutcome.Complete(SingleOutcome.Success(alreadyExisted = true))
        }

        val startBytes = if (partial) resumeFrom else 0L
        if (!partial) partFile.delete()

        listener.onProgress(
            DownloadProgress(
                bytesDone = startBytes,
                totalBytes = totalBytes,
                speedBytesPerSecond = 0L,
                host = host,
                fileName = fileName,
            ),
        )

        FileOutputStream(partFile, partial).use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var written = startBytes
                var lastBytes = startBytes
                var lastUpdate = System.currentTimeMillis()

                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    written += read

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
                        val elapsed = (now - lastUpdate).coerceAtLeast(1L)
                        val speed = ((written - lastBytes) * 1000L / elapsed).coerceAtLeast(0L)
                        listener.onProgress(
                            DownloadProgress(written, totalBytes, speed, host, fileName),
                        )
                        lastUpdate = now
                        lastBytes = written
                    }
                }
                output.flush()
            }
        }

        val finalSize = partFile.length()
        if (totalBytes <= 0L) totalBytes = finalSize

        if (totalBytes > 0L && finalSize < totalBytes) {
            return ResponseOutcome.Retry
        }
        if (totalBytes > 0L && finalSize > totalBytes) {
            partFile.delete()
            return ResponseOutcome.Complete(
                SingleOutcome.HostFailure("Ukuran file tidak cocok dengan server"),
            )
        }
        if (finalSize == 0L) {
            partFile.delete()
            return ResponseOutcome.Complete(
                SingleOutcome.HostFailure("Host mengirim file kosong"),
            )
        }

        listener.onProgress(
            DownloadProgress(finalSize, totalBytes, 0L, host, fileName),
        )

        val saved = storageWriter.save(partFile, animeSlug, fileName)
        if (!saved) {
            partFile.delete()
            return ResponseOutcome.Complete(
                SingleOutcome.LocalFailure("Gagal menyimpan file ke folder tujuan"),
            )
        }

        partFile.delete()
        return ResponseOutcome.Complete(SingleOutcome.Success(alreadyExisted = false))
    }

    private fun buildFileName(
        task: DownloadTask,
        format: VideoFormat,
        resolution: String,
        extension: String,
    ): String {
        val slug = UrlUtils.fileNameSafe(
            task.episodeSlug.ifBlank { "episode-${task.episodeNumber}" },
        )
        return "${slug}_${format.label}_${resolution}.$extension"
    }

    private fun extensionFor(format: VideoFormat, directUrl: String): String {
        var extension = if (format.label.contains("mp4", ignoreCase = true)) "mp4" else "mkv"
        val lower = directUrl.lowercase()
        if (".rar" in lower) {
            extension = "rar"
        } else if (".zip" in lower) {
            extension = "zip"
        }
        return extension
    }

    private fun httpReason(code: Int): String = when (code) {
        451 -> "diblokir/dihapus oleh host (takedown), bukan masalah aplikasi"
        404, 410 -> "file sudah dihapus dari host"
        429 -> "terkena limit host, coba lagi nanti"
        else -> "HTTP $code"
    }

    private sealed interface ResponseOutcome {
        data class Complete(val outcome: SingleOutcome) : ResponseOutcome
        data object Retry : ResponseOutcome
    }

    private sealed interface SingleOutcome {
        data class Success(val alreadyExisted: Boolean) : SingleOutcome
        data class HostFailure(val reason: String) : SingleOutcome
        data class LocalFailure(val reason: String) : SingleOutcome
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
