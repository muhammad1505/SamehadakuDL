package app.samehadaku.dl.core

object MimeTypes {

    fun forFile(fileName: String): String = when (
        fileName.substringAfterLast('.', "").lowercase()
    ) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "rar" -> "application/vnd.rar"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
