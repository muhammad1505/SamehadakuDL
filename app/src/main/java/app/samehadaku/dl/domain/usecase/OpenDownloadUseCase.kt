package app.samehadaku.dl.domain.usecase

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import app.samehadaku.dl.core.MimeTypes
import app.samehadaku.dl.data.download.StorageWriter
import app.samehadaku.dl.data.smartstorage.SmartStorageManager
import app.samehadaku.dl.domain.model.DownloadTask
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenDownloadUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageWriter: StorageWriter,
    private val smartStorage: SmartStorageManager,
) {

    suspend operator fun invoke(task: DownloadTask): Result<Unit> {
        val fileName = task.fileName?.takeIf { it.isNotBlank() }
            ?: return Result.failure(
                IllegalStateException("Nama file belum tersedia untuk episode ini."),
            )
        return open(task.animeSlug, fileName)
    }

    /** Opens a stored file by its collection folder and file name. */
    suspend fun open(folderName: String, fileName: String): Result<Unit> = runCatching {
        val uri = storageWriter.findDownloadUri(folderName, fileName)
            ?: error("File tidak ditemukan. Mungkin sudah dipindahkan atau dihapus.")
        val mimeType = context.contentResolver.getType(uri)
            ?: MimeTypes.forFile(fileName)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri(fileName, uri)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            error("Tidak ada aplikasi yang bisa membuka file ini.")
        }
        // The external player may start before this returns; recording after the
        // launch keeps a failed open from becoming a false "watched" candidate.
        runCatching { smartStorage.recordPlaybackStarted(folderName, fileName, uri) }
    }
}
