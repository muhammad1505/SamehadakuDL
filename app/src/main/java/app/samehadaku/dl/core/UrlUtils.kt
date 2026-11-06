package app.samehadaku.dl.core

import java.net.URI

object UrlUtils {

    fun normalizeInput(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    fun isHttpUrl(url: String): Boolean {
        val scheme = try {
            URI(url).scheme
        } catch (_: Exception) {
            null
        }
        return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
    }

    fun normalizeUrl(url: String): String {
        val withoutFragment = url.substringBefore('#')
        val withoutQuery = withoutFragment.substringBefore('?')
        return withoutQuery.trimEnd('/') + "/"
    }

    fun lastPathSegment(pathOrUrl: String): String {
        val path = try {
            val uri = URI(pathOrUrl)
            uri.path ?: pathOrUrl
        } catch (_: Exception) {
            pathOrUrl
        }
        return path.split('/').lastOrNull { it.isNotEmpty() }.orEmpty()
    }

    fun hostOf(url: String): String? = try {
        URI(url).host
    } catch (_: Exception) {
        null
    }

    fun isSamehadakuHost(host: String?): Boolean =
        host?.contains("samehadaku", ignoreCase = true) == true

    fun isListUrl(url: String): Boolean = try {
        URI(url).path.orEmpty().contains("/anime/")
    } catch (_: Exception) {
        false
    }

    fun siteRoot(url: String): String = try {
        val uri = URI(url)
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: return "https://samehadaku.how/"
        val port = if (uri.port > 0) ":${uri.port}" else ""
        "$scheme://$host$port/"
    } catch (_: Exception) {
        "https://samehadaku.how/"
    }

    fun resolve(baseUrl: String, href: String): String? {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return null
        return try {
            URI(baseUrl).resolve(trimmed).toString()
        } catch (_: Exception) {
            try {
                java.net.URL(java.net.URL(baseUrl), trimmed).toString()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun withoutQueryAndFragment(url: String): String =
        url.substringBefore('#').substringBefore('?')

    fun fileNameSafe(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim()
}
