package app.samehadaku.dl.domain.usecase

import app.samehadaku.dl.data.download.StorageWriter
import app.samehadaku.dl.domain.model.DownloadTask
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteDownloadUseCase @Inject constructor(
    private val storageWriter: StorageWriter,
) {

    suspend operator fun invoke(task: DownloadTask): Result<Unit> = runCatching {
        val fileName = task.fileName?.takeIf { it.isNotBlank() }
            ?: error("Nama file belum tersedia untuk episode ini.")
        if (!storageWriter.deleteDownload(task.animeSlug, fileName)) {
            error("File tidak bisa dihapus. Mungkin sudah dipindahkan atau izin penyimpanan dicabut.")
        }
    }
}
