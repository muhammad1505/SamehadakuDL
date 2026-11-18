package app.samehadaku.dl.domain.usecase

import app.samehadaku.dl.core.SmartStoragePolicy
import app.samehadaku.dl.data.download.StorageWriter
import app.samehadaku.dl.data.smartstorage.SmartStorageRepository
import app.samehadaku.dl.domain.model.CleanupIssue
import app.samehadaku.dl.domain.model.CleanupIssueReason
import app.samehadaku.dl.domain.model.WatchedEpisode
import javax.inject.Inject
import timber.log.Timber

/** Outcome of one Smart Storage cleanup pass, shown in Settings and the log. */
data class SmartStorageCleanupResult(
    val deletedFiles: Int = 0,
    val reclaimedBytes: Long = 0L,
    val skippedFiles: Int = 0,
    val blockedFiles: Int = 0,
    val waitingFiles: Int = 0,
    val disabled: Boolean = false,
)

/**
 * Deletes watched episodes after their grace period, or immediately when the
 * user explicitly asks for it from Settings.
 *
 * Safety rules: only entries created by this app are considered, the file must
 * still be found in the app download folder, and its byte length must match the
 * size recorded when it was marked watched. A system confirmation request or a
 * provider denial keeps the watched marker so the file can be handled manually
 * from Koleksi instead of being silently forgotten.
 */
class CleanWatchedFilesUseCase @Inject constructor(
    private val repository: SmartStorageRepository,
    private val storage: StorageWriter,
) {

    suspend operator fun invoke(
        now: Long = System.currentTimeMillis(),
        ignoreGrace: Boolean = false,
    ): SmartStorageCleanupResult {
        val settings = repository.currentSettings()
        if (!settings.enabled) return SmartStorageCleanupResult(disabled = true)

        var deletedFiles = 0
        var reclaimedBytes = 0L
        var skippedFiles = 0
        var blockedFiles = 0
        var waitingFiles = 0
        val issues = mutableListOf<CleanupIssue>()

        fun issue(entry: WatchedEpisode, reason: CleanupIssueReason) {
            issues += CleanupIssue(
                folderName = entry.folderName,
                fileName = entry.fileName,
                size = entry.size,
                reason = reason,
                at = now,
            )
        }

        for (entry in repository.currentWatched()) {
            val shouldDelete = SmartStoragePolicy.shouldDelete(
                watchedAt = entry.watchedAt,
                now = now,
                graceDays = settings.graceDays,
                ignoreGrace = ignoreGrace,
            )
            if (!shouldDelete) {
                waitingFiles++
                continue
            }
            if (!SmartStoragePolicy.hasUsableSize(entry.size)) {
                skippedFiles++
                issue(entry, CleanupIssueReason.UNKNOWN_SIZE)
                continue
            }

            val uri = runCatching {
                storage.findDownloadUri(entry.folderName, entry.fileName)
            }.getOrNull()
            if (uri == null) {
                // The file is gone from every known location; drop the stale marker.
                repository.removeWatched(entry.folderName, entry.fileName)
                repository.forgetDownloadedFile(entry.folderName, entry.fileName)
                continue
            }

            val sizeMatches = runCatching {
                storage.existsWithSize(entry.folderName, entry.fileName, entry.size)
            }.getOrDefault(false)
            if (!sizeMatches) {
                skippedFiles++
                issue(entry, CleanupIssueReason.SIZE_MISMATCH)
                continue
            }

            val deletion = runCatching {
                storage.deleteDownload(entry.folderName, entry.fileName)
            }
            deletion.exceptionOrNull()?.let { error ->
                Timber.w(error, "Smart Storage delete failed for %s", entry.fileName)
            }
            val result = deletion.getOrNull()
            if (result == null) {
                blockedFiles++
                issue(entry, CleanupIssueReason.DENIED)
                continue
            }

            val fullyDeleted = result.deleted > 0 &&
                result.denied == 0 &&
                result.confirmation == null
            val alreadyGone = result.deleted == 0 &&
                result.absent > 0 &&
                result.denied == 0 &&
                result.confirmation == null

            when {
                fullyDeleted -> {
                    repository.removeWatched(entry.folderName, entry.fileName)
                    repository.forgetDownloadedFile(entry.folderName, entry.fileName)
                    deletedFiles++
                    reclaimedBytes += entry.size
                }

                alreadyGone -> {
                    repository.removeWatched(entry.folderName, entry.fileName)
                    repository.forgetDownloadedFile(entry.folderName, entry.fileName)
                }

                else -> {
                    blockedFiles++
                    issue(
                        entry,
                        if (result.confirmation != null) {
                            CleanupIssueReason.NEEDS_CONFIRMATION
                        } else {
                            CleanupIssueReason.DENIED
                        },
                    )
                    Timber.d(
                        "Smart Storage kept %s: deleted=%d absent=%d denied=%d confirmation=%s",
                        entry.fileName,
                        result.deleted,
                        result.absent,
                        result.denied,
                        result.confirmation != null,
                    )
                }
            }
        }

        repository.recordCleanup(
            deletedFiles = deletedFiles,
            reclaimedBytes = reclaimedBytes,
            at = now,
        )
        repository.recordIssues(issues)
        return SmartStorageCleanupResult(
            deletedFiles = deletedFiles,
            reclaimedBytes = reclaimedBytes,
            skippedFiles = skippedFiles,
            blockedFiles = blockedFiles,
            waitingFiles = waitingFiles,
        )
    }
}
