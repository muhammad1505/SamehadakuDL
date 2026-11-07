package app.samehadaku.dl.data.host

import app.samehadaku.dl.data.network.HttpClientFactory
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.HostResolveResult
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object GofileUrlParser {

    private val PATH_ID = Regex(
        """gofile\.io/d/([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val QUERY_ID = Regex(
        """[?&]c=([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun contentId(url: String): String? =
        PATH_ID.find(url)?.groupValues?.get(1)
            ?: QUERY_ID.find(url)?.groupValues?.get(1)
}

/**
 * Gofile protects its API with a short lived website token. The token is a
 * SHA-256 of the browser details, guest account token, a four hour time slot and
 * a salt that is embedded in https://gofile.io/js/wt.obf.js.
 */
object GofileWebsiteToken {

    const val LANGUAGE = "en-US"
    private const val SLOT_SECONDS = 4 * 60 * 60L

    fun generate(
        accountToken: String,
        salt: String,
        epochSeconds: Long = System.currentTimeMillis() / 1000L,
    ): String {
        val slot = epochSeconds / SLOT_SECONDS
        val raw = "${HttpClientFactory.BROWSER_UA}::$LANGUAGE::$accountToken::$slot::$salt"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

/**
 * The website token salt lives inside an obfuscated string array. Gofile rotates
 * the salt occasionally, so when the bundled salts stop working the script is
 * downloaded and the salt is recovered with the same custom base64 + RC4 scheme
 * used by the obfuscator.
 */
internal object GofileSaltExtractor {

    private const val ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/="

    private val DECODER_CALL = Regex(
        """\(\s*0x[0-9a-fA-F]+\s*,\s*'((?:\\x[0-9a-fA-F]{2})+)'\s*\)""",
    )
    private val ESCAPED_LITERAL = Regex("""'((?:\\x[0-9a-fA-F]{2})+)'""")
    private val JS_HEX_ESCAPE = Regex("""\\x([0-9a-fA-F]{2})""")
    private val SALT_PATTERN = Regex("""^[0-9a-fA-F]{12,32}$""")

    fun extract(source: String): List<String> {
        val keys = DECODER_CALL.findAll(source)
            .map { unescapeJs(it.groupValues[1]) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        if (keys.isEmpty()) return emptyList()

        val entries = ESCAPED_LITERAL.findAll(source)
            .map { unescapeJs(it.groupValues[1]) }
            .filter { value -> value.length >= 6 && value.all { it in ALPHABET } }
            .distinct()
            .toList()
        if (entries.isEmpty()) return emptyList()

        val salts = linkedSetOf<String>()
        for (key in keys) {
            for (entry in entries) {
                val decoded = decode(entry, key) ?: continue
                if (SALT_PATTERN.matches(decoded)) salts += decoded
            }
        }
        return salts.toList()
    }

    private fun unescapeJs(value: String): String =
        JS_HEX_ESCAPE.replace(value) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }

    private fun decode(encoded: String, key: String): String? {
        val bytes = customBase64Decode(encoded) ?: return null
        return rc4(bytes.toString(Charsets.UTF_8), key)
    }

    private fun customBase64Decode(encoded: String): ByteArray? {
        val output = ByteArrayOutputStream(encoded.length)
        var bits = 0
        var bitCount = 0
        for (char in encoded) {
            val value = ALPHABET.indexOf(char)
            if (value < 0) continue
            bits = (bits shl 6) or value
            bitCount += 6
            if (bitCount >= 8) {
                bitCount -= 8
                output.write((bits shr bitCount) and 0xFF)
            }
        }
        return output.toByteArray()
    }

    private fun rc4(value: String, key: String): String {
        if (key.isEmpty()) return value
        val state = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + state[i] + key[i % key.length].code) and 0xFF
            val tmp = state[i]
            state[i] = state[j]
            state[j] = tmp
        }

        var i = 0
        j = 0
        val result = StringBuilder(value.length)
        for (char in value) {
            i = (i + 1) and 0xFF
            j = (j + state[i]) and 0xFF
            val tmp = state[i]
            state[i] = state[j]
            state[j] = tmp
            val keyByte = state[(state[i] + state[j]) and 0xFF]
            result.append((char.code xor keyByte).toChar())
        }
        return result.toString()
    }
}

/**
 * Gofile page URL -> guest account -> content API -> authenticated direct link.
 * Gofile's CDN needs the guest account cookie on the final file request, which is
 * why the resolved link is returned together with headers.
 */
@Singleton
class GofileResolver @Inject constructor(
    private val clients: HttpClientFactory,
) : HostResolver {

    override val host: Host = Host.GOFILE

    private val tokenLock = Any()

    @Volatile
    private var guestToken: String? = null

    @Volatile
    private var workingSalt: String? = null

    @Volatile
    private var discoveredSalts: List<String> = emptyList()

    @Volatile
    private var discoveredSaltsAt: Long = 0L

    override suspend fun resolve(url: String): HostResolveResult = withContext(Dispatchers.IO) {
        val contentId = GofileUrlParser.contentId(url)
            ?: return@withContext HostResolveResult.Failure(
                "Link Gofile tidak valid",
                permanent = true,
            )

        var token = obtainGuestToken()
            ?: return@withContext HostResolveResult.Failure(
                "Tidak bisa membuat sesi tamu Gofile (limit atau jaringan)",
                permanent = false,
            )

        val bundledSalts = (listOfNotNull(workingSalt) + KNOWN_SALTS).distinct()
        var attempt = trySalts(contentId, token, bundledSalts)

        if (attempt is Attempt.BadToken) {
            val discovered = discoverSalts(force = false)
            val fresh = discovered.filter { it !in bundledSalts }
            if (fresh.isNotEmpty()) {
                attempt = trySalts(contentId, token, fresh)
            }
        }

        if (attempt is Attempt.BadToken) {
            token = obtainGuestToken(forceRefresh = true) ?: token
            val salts = (bundledSalts + discoveredSalts).distinct()
            attempt = trySalts(contentId, token, salts)
        }

        if (attempt is Attempt.BadToken) {
            val previous = discoveredSalts
            val forced = discoverSalts(force = true).filter { it !in previous }
            if (forced.isNotEmpty()) {
                attempt = trySalts(contentId, token, forced)
            }
        }

        when (attempt) {
            is Attempt.Ok -> {
                workingSalt = attempt.salt
                HostResolveResult.Success(
                    directUrl = attempt.link,
                    headers = mapOf("Cookie" to "accountToken=$token"),
                )
            }
            is Attempt.Permanent -> HostResolveResult.Failure(
                reason = attempt.reason,
                permanent = true,
            )
            is Attempt.Retryable -> HostResolveResult.Failure(
                reason = attempt.reason,
                permanent = false,
            )
            Attempt.BadToken -> HostResolveResult.Failure(
                "Token keamanan Gofile berubah; coba lagi nanti",
                permanent = false,
            )
        }
    }

    private fun trySalts(contentId: String, token: String, salts: List<String>): Attempt {
        var badToken = false
        var retryable: Attempt.Retryable? = null
        for (salt in salts.distinct()) {
            when (val attempt = fetchContent(contentId, token, salt)) {
                is Attempt.Ok -> return attempt
                is Attempt.Permanent -> return attempt
                is Attempt.Retryable -> retryable = attempt
                Attempt.BadToken -> badToken = true
            }
        }
        return retryable ?: if (badToken) Attempt.BadToken else Attempt.Retryable(
            "Gofile tidak memberikan jawaban yang bisa dipakai",
        )
    }

    private fun fetchContent(contentId: String, token: String, salt: String): Attempt {
        val request = Request.Builder()
            .url("$CONTENTS_URL/$contentId?page=1&pageSize=100&sortField=name&sortDirection=1")
            .header("Authorization", "Bearer $token")
            .header("X-Website-Token", GofileWebsiteToken.generate(token, salt))
            .header("X-BL", GofileWebsiteToken.LANGUAGE)
            .header("Accept", "application/json")
            .build()

        val response = try {
            clients.plainClient.newCall(request).execute()
        } catch (error: Exception) {
            return Attempt.Retryable(
                "Gagal menghubungi Gofile: ${error.message ?: "kesalahan jaringan"}",
            )
        }

        response.use {
            val code = response.code
            val body = try {
                response.body?.string().orEmpty()
            } catch (error: Exception) {
                return Attempt.Retryable(
                    "Gagal membaca jawaban Gofile: ${error.message ?: "kesalahan jaringan"}",
                )
            }

            if (code == 401 || code == 403) return Attempt.BadToken
            if (code == 404 || code == 410 || code == 451) {
                return Attempt.Permanent("File sudah dihapus dari Gofile")
            }
            if (code == 429) {
                return Attempt.Retryable("Terkena limit Gofile, coba lagi nanti")
            }
            if (!response.isSuccessful) {
                return Attempt.Retryable("Gofile mengembalikan HTTP $code")
            }

            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                return Attempt.Retryable("Jawaban Gofile tidak dapat dibaca")
            }

            when (json.optString("status")) {
                "ok" -> Unit
                "error-notFound" -> return Attempt.Permanent("File sudah dihapus dari Gofile")
                "error-notPremium" -> return Attempt.BadToken
                "error-rateLimit", "error-tooManyRequests" ->
                    return Attempt.Retryable("Terkena limit Gofile, coba lagi nanti")
                else -> {
                    val status = json.optString("status").ifBlank { "tidak dikenal" }
                    return Attempt.Retryable("Gofile: $status")
                }
            }

            val data = json.optJSONObject("data")
                ?: return Attempt.Retryable("Data Gofile kosong")
            if (data.has("canAccess") && !data.optBoolean("canAccess", true)) {
                return Attempt.Permanent("File Gofile tidak bisa diakses (private/dihapus)")
            }

            val file = findLargestFile(data)
                ?: return Attempt.Retryable("File di dalam link Gofile tidak ditemukan")

            return Attempt.Ok(
                link = file.link,
                salt = salt,
            )
        }
    }

    private fun findLargestFile(node: JSONObject): GofileFile? {
        val type = node.optString("type")
        val link = node.optString("link")
        if (type == "file" && link.isNotBlank()) {
            return GofileFile(
                link = link,
                size = node.optLong("size", 0L),
                name = node.optString("name"),
            )
        }

        val children = node.optJSONObject("children") ?: return null
        var best: GofileFile? = null
        for (key in children.keys()) {
            val child = children.optJSONObject(key) ?: continue
            if (child.has("canAccess") && !child.optBoolean("canAccess", true)) continue
            val file = findLargestFile(child) ?: continue
            if (best == null || file.size > best.size) best = file
        }
        return best
    }

    private fun obtainGuestToken(forceRefresh: Boolean = false): String? =
        synchronized(tokenLock) {
            if (!forceRefresh) {
                guestToken?.let { return it }
            }

            val request = Request.Builder()
                .url(ACCOUNTS_URL)
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()

            try {
                clients.plainClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val json = try {
                        JSONObject(response.body?.string().orEmpty())
                    } catch (_: Exception) {
                        return null
                    }
                    if (json.optString("status") != "ok") return null
                    val token = json.optJSONObject("data")?.optString("token").orEmpty()
                    if (token.isBlank()) return null
                    guestToken = token
                    return token
                }
            } catch (_: Exception) {
                return null
            }
        }

    private fun discoverSalts(force: Boolean): List<String> {
        val now = System.currentTimeMillis()
        if (!force && discoveredSalts.isNotEmpty() && now - discoveredSaltsAt < SALT_CACHE_MS) {
            return discoveredSalts
        }

        val request = Request.Builder().url(WT_SCRIPT_URL).build()
        val source = try {
            clients.plainClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return discoveredSalts
                response.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            return discoveredSalts
        }

        val extracted = GofileSaltExtractor.extract(source)
        if (extracted.isNotEmpty()) {
            discoveredSalts = extracted
            discoveredSaltsAt = now
            return extracted
        }
        return discoveredSalts
    }

    private data class GofileFile(
        val link: String,
        val size: Long,
        val name: String,
    )

    private sealed interface Attempt {
        data class Ok(val link: String, val salt: String) : Attempt
        data class Permanent(val reason: String) : Attempt
        data class Retryable(val reason: String) : Attempt
        data object BadToken : Attempt
    }

    private companion object {
        const val ACCOUNTS_URL = "https://api.gofile.io/accounts"
        const val CONTENTS_URL = "https://api.gofile.io/contents"
        const val WT_SCRIPT_URL = "https://gofile.io/js/wt.obf.js"
        const val SALT_CACHE_MS = 6 * 60 * 60 * 1000L

        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /**
         * Fallbacks when the website token script cannot be read. The first value
         * is the salt used by Gofile when this app was built; the second is the
         * older salt used by several downloaders.
         */
        val KNOWN_SALTS = listOf(
            "12af056dacea0b",
            "gf2026x",
        )
    }
}
