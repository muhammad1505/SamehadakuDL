package app.samehadaku.dl.domain.usecase

import android.content.IntentSender
import app.samehadaku.dl.core.SmartStoragePolicy
import app.samehadaku.dl.data.download.StorageWriter
import app.samehadaku.dl.data.smartstorage.SmartStorageRepository
import javax.inject.Inject
import timber.log.Timber

/** Outcome of an explicit "delete this episode now" request. */
sealed interface DeleteWatchedFileResult {
    data class Deleted(val fileName: String) : DeleteWatchedFileResult

    data class AlreadyGone(val fileName: String) : DeleteWatchedFileResult

    /** The provider needs an activity result before the file can be removed. */
    data class NeedsConfirmation(
        val fileName: String,
        val sender: IntentSender,
    ) : DeleteWatchedFileResult

    data class Failed(val fileName: String, val message: String) : DeleteWatchedFileResult
}

/**
 * Deletes exactly one watched episode without waiting for the grace period. The
 * marker is only cleared when the file is really gone, so a refused delete can
 * be retried from Koleksi or this screen.
 */
class DeleteWatchedFileUseCase @Inject constructor(
    private val repository: SmartStorageRepository,
    private val storage: StorageWriter,
) {

    suspend operator fun invoke(folderName: String, fileName: String): DeleteWatchedFileResult {
        val key = SmartStoragePolicy.key(folderName, fileName)
        val entry = repository.currentWatched().firstOrNull { watched ->
            SmartStoragePolicy.key(watched.folderName, watched.fileName) == key
        } ?: return DeleteWatchedFileResult.AlreadyGone(fileName)

        if (!SmartStoragePolicy.hasUsableSize(entry.size)) {
            return DeleteWatchedFileResult.Failed(
                fileName,
                "Ukuran file tidak diketahui, jadi file tidak bisa diverifikasi.",
            )
        }

        val stillThere = runCatching {
            storage.existsWithSize(entry.folderName, entry.fileName, entry.size)
        }.getOrDefault(false)
        if (!stillThere) {
            // The file disappeared or changed size. Never delete an unverified file.
            repository.removeWatched(entry.folderName, entry.fileName)
            repository.forgetDownloadedFile(entry.folderName, entry.fileName)
            repository.removeIssue(entry.folderName, entry.fileName)
            return DeleteWatchedFileResult.AlreadyGone(fileName)
        }

        val deletion = runCatching { storage.deleteDownload(entry.folderName, entry.fileName) }
        deletion.exceptionOrNull()?.let { error ->
            Timber.w(error, "Smart Storage explicit delete failed for %s", fileName)
        }
        val result = deletion.getOrNull()
            ?: return DeleteWatchedFileResult.Failed(fileName, "Penyimpanan menolak menghapus file.")

        result.confirmation?.let { sender ->
            return DeleteWatchedFileResult.NeedsConfirmation(fileName, sender)
        }

        val deleted = result.deleted > 0 && result.denied == 0
        val absent = result.deleted == 0 && result.absent > 0 && result.denied == 0
        if (deleted || absent) {
            repository.removeWatched(entry.folderName, entry.fileName)
            repository.forgetDownloadedFile(entry.folderName, entry.fileName)
            repository.removeIssue(entry.folderName, entry.fileName)
            return if (deleted) {
                DeleteWatchedFileResult.Deleted(fileName)
            } else {
                DeleteWatchedFileResult.AlreadyGone(fileName)
            }
        }

        return DeleteWatchedFileResult.Failed(
            fileName,
            "Penyimpanan menolak menghapus file. Coba hapus manual dari Koleksi.",
        )
    }
}
