package app.samehadaku.dl.domain.usecase

import app.samehadaku.dl.data.download.DeletionResult
import app.samehadaku.dl.data.download.StorageWriter
import app.samehadaku.dl.domain.model.DownloadTask
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteDownloadUseCase @Inject constructor(
    private val storageWriter: StorageWriter,
) {

    suspend operator fun invoke(task: DownloadTask): Result<DeletionResult> = runCatching {
        val fileName = task.fileName?.takeIf { it.isNotBlank() }
            ?: error("Nama file belum tersedia untuk episode ini.")
        storageWriter.deleteDownload(task.animeSlug, fileName)
    }
}
