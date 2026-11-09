package app.samehadaku.dl.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.samehadaku.dl.core.Names
import app.samehadaku.dl.core.SiteMirrors
import app.samehadaku.dl.data.network.HtmlFetcher
import app.samehadaku.dl.data.network.HttpClientFactory
import app.samehadaku.dl.data.parser.AnimeCoverParser
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Loads and memory-caches small anime poster previews for the Koleksi screen. */
@Singleton
class AnimeCoverRepository @Inject constructor(
    private val fetcher: HtmlFetcher,
    private val clients: HttpClientFactory,
) {

    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    suspend fun load(slug: String): Bitmap? {
        val key = Names.toSlug(slug)
        cache[key]?.let { return it }
        if (key in misses) return null

        val cover = withContext(Dispatchers.IO) {
            loadFromMirrors(key)
        }
        if (cover == null) misses += key else cache[key] = cover
        return cover
    }

    /** Loads a poster URL that was already extracted from a schedule/search card. */
    suspend fun loadImage(imageUrl: String, referer: String? = null): Bitmap? {
        val key = imageUrl.trim()
        if (key.isBlank()) return null
        cache[key]?.let { return it }
        if (key in misses) return null
        val image = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(key)
                    .apply { referer?.takeIf(String::isNotBlank)?.let { header("Referer", it) } }
                    .build()
                clients.siteClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.bytes()?.let(::decodePreview)
                }
            }.getOrNull()
        }
        if (image == null) misses += key else cache[key] = image
        return image
    }

    private suspend fun loadFromMirrors(slug: String): Bitmap? {
        for (host in SiteMirrors.knownHosts().take(MAX_MIRRORS)) {
            val pageUrl = "https://$host/anime/$slug/"
            val page = runCatching {
                fetcher.fetch(pageUrl, referer = "https://$host/")
            }.getOrNull() ?: continue
            val imageUrl = AnimeCoverParser.parse(page.html, page.finalUrl) ?: continue
            val image = loadImage(imageUrl, page.finalUrl)
            if (image != null) return image
        }
        return null
    }

    private fun decodePreview(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = calculateSample(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, options)
    }

    private fun calculateSample(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > PREVIEW_SIZE * 2 || height / sample > PREVIEW_SIZE * 2) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val MAX_MIRRORS = 3
        const val PREVIEW_SIZE = 256
    }
}
