package app.samehadaku.dl.domain.usecase

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import app.samehadaku.dl.core.MimeTypes
import app.samehadaku.dl.data.download.StorageWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareDownloadUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageWriter: StorageWriter,
) {

    /** Shares a stored file by its collection folder and file name. */
    suspend operator fun invoke(folderName: String, fileName: String): Result<Unit> = runCatching {
        val uri = storageWriter.findDownloadUri(folderName, fileName)
            ?: error("File tidak ditemukan. Mungkin sudah dipindahkan atau dihapus.")
        val mimeType = context.contentResolver.getType(uri)
            ?: MimeTypes.forFile(fileName)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, fileName)
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Bagikan $fileName").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            error("Tidak ada aplikasi yang bisa membagikan file ini.")
        }
    }
}
