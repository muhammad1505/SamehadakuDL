package app.samehadaku.dl.data.download

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import app.samehadaku.dl.core.EpisodeFiles
import app.samehadaku.dl.core.MimeTypes
import app.samehadaku.dl.core.UrlUtils
import app.samehadaku.dl.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The outcome of a single-location delete attempt. */
enum class DeleteOutcome {
    /** The entry was removed from this location. */
    DELETED,

    /** Nothing lived here, so there was nothing to remove. */
    ABSENT,

    /**
     * The provider refused the write. On Android 10+ this usually means the row
     * belongs to another installation and needs a user confirmation intent.
     */
    DENIED,
}

/**
 * What a whole-folder or whole-file deletion ended up doing, plus the system
 * confirmation request the UI has to launch when [confirmation] is set.
 */
data class DeletionResult(
    val deleted: Int = 0,
    val absent: Int = 0,
    val denied: Int = 0,
    val confirmation: IntentSender? = null,
)

/** A single video file discovered inside a collection folder. */
data class StoredFile(
    val name: String,
    val size: Long,
    val modifiedAt: Long,
    val uri: Uri?,
    val path: String? = null,
)

/** A folder (one anime title) discovered in the download storage. */
data class StoredFolder(
    val name: String,
    val files: List<StoredFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.size }
    val lastModified: Long get() = files.maxOfOrNull { it.modifiedAt } ?: 0L
}

@Singleton
class StorageWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    suspend fun save(partFile: File, animeSlug: String, fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            val safeSlug = UrlUtils.fileNameSafe(animeSlug).ifBlank { "SamehadakuDL" }
            val treeUri = runCatching { settings.currentDownloadTreeUri() }.getOrNull()
            try {
                if (!treeUri.isNullOrBlank()) {
                    saveToTree(partFile, Uri.parse(treeUri), safeSlug, fileName)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(partFile, safeSlug, fileName)
                } else {
                    saveToLegacyFolder(partFile, safeSlug, fileName)
                }
            } catch (_: Exception) {
                false
            }
        }

    suspend fun existsWithSize(animeSlug: String, fileName: String, size: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (size <= 0L) return@withContext false
            val safeSlug = UrlUtils.fileNameSafe(animeSlug).ifBlank { "SamehadakuDL" }
            val treeUri = runCatching { settings.currentDownloadTreeUri() }.getOrNull()
            try {
                when {
                    !treeUri.isNullOrBlank() -> existsInTree(
                        Uri.parse(treeUri),
                        safeSlug,
                        fileName,
                        size,
                    )
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                        existsInMediaStore(safeSlug, fileName, size)
                    else -> legacyFile(safeSlug, fileName).length() == size
                }
            } catch (_: Exception) {
                false
            }
        }

    suspend fun findDownloadUri(animeSlug: String, fileName: String): Uri? =
        withContext(Dispatchers.IO) {
            val safeSlug = UrlUtils.fileNameSafe(animeSlug).ifBlank { "SamehadakuDL" }
            val treeUri = runCatching { settings.currentDownloadTreeUri() }.getOrNull()
            try {
                val fromTree = if (!treeUri.isNullOrBlank()) {
                    findInTree(Uri.parse(treeUri), safeSlug, fileName)
                } else {
                    null
                }
                val fromMediaStore = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    findInMediaStore(safeSlug, fileName)
                } else {
                    null
                }
                fromTree ?: fromMediaStore ?: legacyUri(safeSlug, fileName)
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Deletes one episode from wherever it is stored. It shares the collection path so
     * a file written by an earlier installation can be removed after the system
     * confirmation instead of failing with "izin penyimpanan dicabut".
     */
    suspend fun deleteDownload(animeSlug: String, fileName: String): DeletionResult {
        val safeSlug = UrlUtils.fileNameSafe(animeSlug).ifBlank { "SamehadakuDL" }
        return deleteStoredFile(safeSlug, fileName)
    }

    /**
     * Scans every known download location and merges the results, so the collection
     * never comes back empty just because one storage backend is unavailable
     * (expired SAF permission, MediaStore quirks, missing legacy folder, ...).
     */
    suspend fun scanCollections(): List<StoredFolder> =
        withContext(Dispatchers.IO) {
            val merged = LinkedHashMap<String, LinkedHashMap<String, StoredFile>>()

            fun absorb(folders: List<StoredFolder>) {
                for (folder in folders) {
                    val target = merged.getOrPut(folder.name) { LinkedHashMap() }
                    for (file in folder.files) {
                        target.putIfAbsent("${file.name.lowercase()}|${file.size}", file)
                    }
                }
            }

            val treeUri = runCatching { settings.currentDownloadTreeUri() }.getOrNull()
            if (!treeUri.isNullOrBlank()) {
                absorb(
                    runCatching { scanTreeCollections(Uri.parse(treeUri)) }
                        .onFailure { Log.w(TAG, "tree scan failed", it) }
                        .getOrDefault(emptyList()),
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                absorb(
                    runCatching { scanMediaStoreCollections() }
                        .onFailure { Log.w(TAG, "mediastore scan failed", it) }
                        .getOrDefault(emptyList()),
                )
            }
            absorb(
                runCatching { scanLegacyCollections() }
                    .onFailure { Log.w(TAG, "legacy scan failed", it) }
                    .getOrDefault(emptyList()),
            )

            val result = merged.map { (name, files) ->
                StoredFolder(
                    name = name,
                    files = files.values.sortedByDescending { it.modifiedAt },
                )
            }.filter { it.files.isNotEmpty() }
                .sortedBy { it.name.lowercase() }
            Log.d(TAG, "scanCollections -> ${result.size} folders, tree=$treeUri")
            result
        }

    /** Human readable description of the active download location. */
    suspend fun describeActiveLocation(): String =
        withContext(Dispatchers.IO) {
            val treeUri = runCatching { settings.currentDownloadTreeUri() }.getOrNull()
            when {
                !treeUri.isNullOrBlank() -> "Folder kustom: ${prettyTreePath(Uri.parse(treeUri))}"
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> "Download/SamehadakuDL"
                else -> "Android/data/${context.packageName}/files/Download/SamehadakuDL"
            }
        }

    /**
     * Deletes a collection folder and every file inside it, across all locations.
     *
     * MediaStore refuses silent deletes for rows this installation did not create, so
     * the result can carry a [DeletionResult.confirmation] the caller must launch
     * before retrying; that is what previously looked like "permission revoked".
     */
    suspend fun deleteFolder(folderName: String): DeletionResult =
        withContext(Dispatchers.IO) {
            if (!isSafeName(folderName)) return@withContext DeletionResult(denied = 1)
            var deleted = 0
            var absent = 0
            var denied = 0
            var confirmation: IntentSender? = null

            val treeUri = runCatching { settings.currentDownloadTreeUri() }.getOrNull()
            if (!treeUri.isNullOrBlank()) {
                val tree = runCatching { deleteFolderInTree(Uri.parse(treeUri), folderName) }
                    .getOrElse {
                        Log.w(TAG, "tree folder delete failed", it)
                        DeletionResult(denied = 1)
                    }
                deleted += tree.deleted
                absent += tree.absent
                denied += tree.denied
                confirmation = confirmation ?: tree.confirmation
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val media = runCatching { deleteFolderInMediaStore(folderName) }
                    .getOrElse {
                        Log.w(TAG, "media folder delete failed", it)
                        DeletionResult(denied = 1)
                    }
                deleted += media.deleted
                absent += media.absent
                denied += media.denied
                confirmation = confirmation ?: media.confirmation
            }
            when (deleteFolderInLegacy(folderName)) {
                DeleteOutcome.DELETED -> deleted++
                DeleteOutcome.ABSENT -> Unit
                DeleteOutcome.DENIED -> denied++
            }

            DeletionResult(deleted, absent, denied, confirmation)
        }

    /** Deletes one stored file from every location that holds it. */
    suspend fun deleteStoredFile(folderName: String, fileName: String): DeletionResult =
        withContext(Dispatchers.IO) {
            if (!isSafeName(folderName) || !isSafeName(fileName)) {
                return@withContext DeletionResult(denied = 1)
            }
            var deleted = 0
            var absent = 0
            var denied = 0
            var confirmation: IntentSender? = null

            fun record(outcome: DeleteOutcome) {
                when (outcome) {
                    DeleteOutcome.DELETED -> deleted++
                    DeleteOutcome.ABSENT -> absent++
                    DeleteOutcome.DENIED -> denied++
                }
            }

            val treeUri = runCatching { settings.currentDownloadTreeUri() }.getOrNull()
            if (!treeUri.isNullOrBlank()) {
                record(
                    runCatching { deleteFileInTree(Uri.parse(treeUri), folderName, fileName) }
                        .getOrElse {
                            Log.w(TAG, "tree file delete failed", it)
                            DeleteOutcome.DENIED
                        },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val media = runCatching { deleteFileInMediaStore(folderName, fileName) }
                    .getOrElse { error ->
                        val sender = recoverableIntent(error)
                        if (sender != null) {
                            confirmation = sender
                            Pair(DeleteOutcome.ABSENT, null as IntentSender?)
                        } else {
                            Log.w(TAG, "media file delete failed", error)
                            Pair(DeleteOutcome.DENIED, null as IntentSender?)
                        }
                    }
                deleted += if (media.first == DeleteOutcome.DELETED) 1 else 0
                absent += if (media.first == DeleteOutcome.ABSENT) 1 else 0
                denied += if (media.first == DeleteOutcome.DENIED) 1 else 0
                confirmation = confirmation ?: media.second
            }
            when (val outcome = runCatching { deleteFileInLegacy(folderName, fileName) }
                    .getOrElse {
                        Log.w(TAG, "legacy file delete failed", it)
                        DeleteOutcome.DENIED
                    }) {
                DeleteOutcome.DELETED -> deleted++
                DeleteOutcome.ABSENT -> absent++
                DeleteOutcome.DENIED -> denied++
            }

            DeletionResult(deleted, absent, denied, confirmation)
        }

    /**
     * Scans the picked folder and, when the user selected a parent such as Download,
     * the app folder inside it. Only folders that actually hold episode files become
     * collections, so unrelated folders in a shared download location stay hidden.
     */
    private fun scanTreeCollections(treeUri: Uri): List<StoredFolder> {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val folders = mutableListOf<StoredFolder>()
        folders += treeCollectionFolders(tree)
        val appFolder = tree.findFile(APP_FOLDER)?.takeIf { it.isDirectory }
        if (appFolder != null) folders += treeCollectionFolders(appFolder)
        return folders
    }

    private fun treeCollectionFolders(root: DocumentFile): List<StoredFolder> =
        root.listFiles()
            .filter { it.isDirectory }
            .filter { directory ->
                val name = directory.name.orEmpty()
                name.isNotBlank() &&
                    !name.startsWith(".") &&
                    !name.equals(APP_FOLDER, ignoreCase = true)
            }
            .mapNotNull { directory ->
                val folderName = directory.name ?: return@mapNotNull null
                val files = directory.listFiles()
                    .filter { it.isFile && EpisodeFiles.isEpisode(it.name.orEmpty()) }
                    .mapNotNull { file ->
                        val name = file.name ?: return@mapNotNull null
                        StoredFile(
                            name = name,
                            size = file.length(),
                            modifiedAt = file.lastModified(),
                            uri = file.uri,
                        )
                    }
                if (files.isEmpty()) null else StoredFolder(folderName, files)
            }

    /**
     * Read access to shared storage goes through the `files` collection, never through
     * `downloads`: providers index a finished episode by its MIME type, so an `.mkv`
     * becomes a video row and disappears from the `downloads` collection even though
     * it sits in `Download/SamehadakuDL/`. That mismatch is what left Koleksi empty
     * while the folder was clearly on disk. Writes keep using [MediaStore.Downloads].
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaFileCollection(): Uri =
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /**
     * Lists finished MediaStore files inside the app download folder.
     *
     * The relative path is the stable link between the app and its files. Matching
     * only `OWNER_PACKAGE_NAME` made Koleksi empty on providers that leave that
     * column empty or redact it. With READ_MEDIA_VIDEO/READ_EXTERNAL_STORAGE this
     * query also returns episodes written by an earlier installation, so old
     * downloads reappear without SAF. The path filter keeps Koleksi scoped to our
     * own folder instead of turning it into a gallery browser.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun scanMediaStoreCollections(): List<StoredFolder> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_PENDING,
        )
        val folders = LinkedHashMap<String, MutableList<StoredFile>>()
        val selection = "${MediaStore.MediaColumns.IS_PENDING}=0 AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val cursor = context.contentResolver.query(
            mediaFileCollection(),
            projection,
            selection,
            arrayOf("%/$APP_FOLDER/%"),
            null,
        )
        if (cursor == null) {
            Log.w(TAG, "media query returned null cursor")
            return emptyList()
        }
        cursor.use {
            Log.d(TAG, "media cursor rows=${it.count}")
            val idIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val pendingIndex = it.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
            while (it.moveToNext()) {
                if (pendingIndex >= 0 && it.getInt(pendingIndex) == 1) continue
                val name = it.getString(nameIndex)
                if (name.isNullOrBlank() || !EpisodeFiles.isEpisode(name)) continue
                val relativePath = it.getString(pathIndex)?.trim('/').orEmpty()
                // The folder containing the file is the anime folder in every layout
                // we write, including when the file sits directly in our root folder.
                val folderName = relativePath.substringAfterLast('/')
                if (folderName.isBlank()) continue
                if (folderName.equals(APP_FOLDER, ignoreCase = true)) continue
                folders.getOrPut(folderName) { mutableListOf() }.add(
                    StoredFile(
                        name = name,
                        size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L,
                        modifiedAt = if (dateIndex >= 0) {
                            it.getLong(dateIndex) * 1000L
                        } else {
                            0L
                        },
                        uri = ContentUris.withAppendedId(
                            mediaFileCollection(),
                            it.getLong(idIndex),
                        ),
                    ),
                )
            }
        }
        Log.d(TAG, "media scan folders=${folders.size} files=${folders.values.sumOf { it.size }}")
        return folders.map { (name, files) -> StoredFolder(name, files) }
    }

    private fun scanLegacyCollections(): List<StoredFolder> {
        val root = File(legacyBase(), APP_FOLDER)
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { directory ->
                val files = directory.listFiles()
                    ?.filter { it.isFile && EpisodeFiles.isEpisode(it.name) }
                    ?.map { file ->
                        StoredFile(
                            name = file.name,
                            size = file.length(),
                            modifiedAt = file.lastModified(),
                            uri = null,
                            path = file.absolutePath,
                        )
                    }
                    .orEmpty()
                if (files.isEmpty()) null else StoredFolder(directory.name, files)
            }
            .orEmpty()
    }

    /**
     * Deletes every entry inside one SAF folder, then the folder itself. Anything the
     * provider refuses is reported as denied so the caller never claims success while
     * files are still sitting on disk.
     */
    private fun deleteFolderInTree(treeUri: Uri, folderName: String): DeletionResult {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return DeletionResult()
        val directory = tree.findFile(folderName)?.takeIf { it.isDirectory }
            ?: return DeletionResult()
        var deleted = 0
        var denied = 0
        directory.listFiles().forEach { entry ->
            if (entry.delete()) deleted++ else denied++
        }
        if (denied == 0) runCatching { directory.delete() }
        return DeletionResult(deleted = deleted, denied = denied)
    }

    /**
     * Deletes every file stored in one MediaStore collection folder. Files live in
     * `.../SamehadakuDL/<folder>/`, so the folder name must be compared with the
     * file's parent segment instead of its full relative path. The path filter also
     * works when an OEM leaves `OWNER_PACKAGE_NAME` empty.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteFolderInMediaStore(folderName: String): DeletionResult {
        val ids = mediaIdsInFolder(folderName)
        if (ids.isEmpty()) return DeletionResult(absent = 1)
        val result = deleteMediaRows(ids)
        // The anime directory itself is not indexed by MediaStore; drop it when the
        // provider no longer reports any file inside it.
        if (result.denied == 0 && result.confirmation == null) pruneEmptyMediaFolder(folderName)
        return result
    }

    /**
     * Deletes MediaStore rows in one batch through the `Downloads` collection.
     *
     * A batch delete can return zero rows without throwing anything: providers ignore
     * rows this installation does not own instead of raising
     * [RecoverableSecurityException]. Treating that zero as "already gone" is what made
     * the UI claim "sudah tidak ada" while the file was still on disk, so anything the
     * batch leaves behind is retried row by row and reported as denied when it survives.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteMediaRows(ids: List<Long>): DeletionResult {
        if (ids.isEmpty()) return DeletionResult(absent = 1)
        val selection = "${MediaStore.MediaColumns._ID} IN (${ids.joinToString(",")})"
        val removed = try {
            context.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                selection,
                null,
            )
        } catch (error: Exception) {
            val sender = recoverableIntent(error)
            if (sender != null) {
                Log.i(TAG, "media delete needs user confirmation for ${ids.size} rows")
                return DeletionResult(denied = ids.size, confirmation = sender)
            }
            Log.w(TAG, "media bulk delete failed", error)
            0
        }
        Log.d(TAG, "media bulk delete requested=${ids.size} removed=$removed")

        val remaining = ids.filter { mediaRowExists(it) }
        val gone = ids.size - remaining.size
        if (remaining.isEmpty()) return DeletionResult(deleted = gone)

        // Retry what the batch did not touch: some providers raise the confirmation
        // exception only for single-item deletes.
        val single = fallbackDeleteOneByOne(remaining)
        Log.d(
            TAG,
            "media delete retry requested=${remaining.size} deleted=${single.deleted} " +
                "absent=${single.absent} denied=${single.denied}",
        )
        return DeletionResult(
            deleted = gone + single.deleted,
            absent = single.absent,
            denied = single.denied,
            confirmation = single.confirmation,
        )
    }

    /** Retries each row separately, collecting the first confirmation request found. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun fallbackDeleteOneByOne(ids: List<Long>): DeletionResult {
        var deleted = 0
        var absent = 0
        var denied = 0
        var confirmation: IntentSender? = null
        for (id in ids) {
            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            val removed = try {
                context.contentResolver.delete(uri, null, null)
            } catch (error: Exception) {
                val sender = recoverableIntent(error)
                if (sender != null && confirmation == null) confirmation = sender
                denied++
                continue
            }
            when {
                removed > 0 -> deleted++
                // Zero deletes with the row still there means the provider refused,
                // not that the file was already gone.
                mediaRowExists(id) -> denied++
                else -> absent++
            }
        }
        return DeletionResult(deleted, absent, denied, confirmation)
    }

    /** True when MediaStore still lists the row, used to tell denial apart from absence. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaRowExists(id: Long): Boolean =
        context.contentResolver.query(
            ContentUris.withAppendedId(mediaFileCollection(), id),
            arrayOf(MediaStore.MediaColumns._ID),
            null,
            null,
            null,
        )?.use { cursor -> cursor.moveToFirst() } ?: false

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaIdsInFolder(folderName: String): List<Long> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val ids = mutableListOf<Long>()
        context.contentResolver.query(
            mediaFileCollection(),
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("%/$APP_FOLDER%"),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val path = cursor.getString(pathIndex)?.trim('/') ?: continue
                if (!path.contains(APP_FOLDER, ignoreCase = true)) continue
                val parent = path.substringAfterLast('/')
                if (parent.equals(folderName, ignoreCase = true)) ids.add(cursor.getLong(idIndex))
            }
        }
        return ids
    }

    /** Removes the leftover directory on disk once MediaStore has no rows for it. */
    private fun pruneEmptyMediaFolder(folderName: String) {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$APP_FOLDER/$folderName",
        )
        if (directory.isDirectory && directory.listFiles().isNullOrEmpty()) {
            runCatching { directory.delete() }
        }
    }

    /** Unwraps the system confirmation intent carried by a recoverable security error. */
    private fun recoverableIntent(error: Throwable): IntentSender? {
        var current: Throwable? = error
        while (current != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                current is RecoverableSecurityException
            ) {
                @Suppress("DEPRECATION")
                return current.userAction?.actionIntent?.intentSender
            }
            current = current.cause
        }
        return null
    }

    private fun deleteFolderInLegacy(folderName: String): DeleteOutcome {
        val directory = File(legacyBase(), "$APP_FOLDER/$folderName")
        if (!directory.isDirectory) return DeleteOutcome.ABSENT
        var removed = 0
        var blocked = false
        directory.listFiles()?.filter { it.isFile }?.forEach { file ->
            if (file.delete()) removed++ else blocked = true
        }
        if (!blocked && directory.listFiles()?.isEmpty() == true) {
            if (!directory.delete()) blocked = true
        }
        return when {
            blocked -> DeleteOutcome.DENIED
            removed > 0 || directoryAbsentOrGone(directory) -> DeleteOutcome.DELETED
            else -> DeleteOutcome.ABSENT
        }
    }

    private fun directoryAbsentOrGone(directory: File): Boolean = !directory.exists()

    /** Returns [DeleteOutcome.ABSENT] when the file simply is not in that location. */
    private fun deleteFileInTree(treeUri: Uri, folderName: String, fileName: String): DeleteOutcome {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return DeleteOutcome.ABSENT
        val directory = tree.findFile(folderName)?.takeIf { it.isDirectory }
            ?: return DeleteOutcome.ABSENT
        val file = directory.findFile(fileName) ?: return DeleteOutcome.ABSENT
        return if (file.delete()) DeleteOutcome.DELETED else DeleteOutcome.DENIED
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteFileInMediaStore(
        folderName: String,
        fileName: String,
    ): Pair<DeleteOutcome, IntentSender?> {
        val ids = mediaIdsInFolder(folderName).filter { id ->
            mediaRowHasName(id, fileName)
        }
        if (ids.isEmpty()) return Pair(DeleteOutcome.ABSENT, null)
        val result = deleteMediaRows(ids)
        val outcome = when {
            result.deleted > 0 -> DeleteOutcome.DELETED
            result.denied > 0 || result.confirmation != null -> DeleteOutcome.DENIED
            else -> DeleteOutcome.ABSENT
        }
        return Pair(outcome, result.confirmation)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaRowHasName(id: Long, fileName: String): Boolean {
        val uri = ContentUris.withAppendedId(mediaFileCollection(), id)
        return context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.moveToFirst() && cursor.getString(0).equals(fileName, ignoreCase = true)
        } ?: false
    }

    private fun deleteFileInLegacy(folderName: String, fileName: String): DeleteOutcome {
        val file = File(legacyBase(), "$APP_FOLDER/$folderName/$fileName")
        if (!file.exists()) return DeleteOutcome.ABSENT
        return if (file.delete()) DeleteOutcome.DELETED else DeleteOutcome.DENIED
    }

    private fun prettyTreePath(treeUri: Uri): String {
        val documentId = runCatching {
            android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull()
        if (documentId.isNullOrBlank()) return "folder pilihan"
        val volume = documentId.substringBefore(':', missingDelimiterValue = "")
        val path = documentId.substringAfter(':', missingDelimiterValue = "")
        val root = when (volume.lowercase()) {
            "primary" -> "Penyimpanan internal"
            "home" -> "Beranda"
            else -> "Penyimpanan"
        }
        return if (path.isBlank()) root else "$root/$path"
    }

    private fun isSafeName(value: String): Boolean =
        value.isNotBlank() &&
            !value.contains('/') &&
            !value.contains('\\') &&
            !value.contains("..")

    private fun saveToTree(
        partFile: File,
        treeUri: Uri,
        animeSlug: String,
        fileName: String,
    ): Boolean {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val directory = tree.findFile(animeSlug)?.takeIf { it.isDirectory }
            ?: tree.createDirectory(animeSlug)
            ?: return false

        val existing = directory.findFile(fileName)
        if (existing != null) {
            if (existing.isFile && existing.length() == partFile.length()) return true
            if (!existing.delete()) return false
        }

        val target = directory.createFile(MimeTypes.forFile(fileName), fileName) ?: return false
        val output = context.contentResolver.openOutputStream(target.uri)
            ?: run {
                target.delete()
                return false
            }
        val copied = output.use { out ->
            partFile.inputStream().use { input ->
                input.copyTo(out, BUFFER_SIZE)
            }
            true
        }
        if (!copied || target.length() != partFile.length()) {
            target.delete()
            return false
        }
        return true
    }

    private fun existsInTree(
        treeUri: Uri,
        animeSlug: String,
        fileName: String,
        size: Long,
    ): Boolean {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val directory = tree.findFile(animeSlug)?.takeIf { it.isDirectory } ?: return false
        val file = directory.findFile(fileName) ?: return false
        return file.isFile && file.length() == size
    }

    private fun findInTree(
        treeUri: Uri,
        animeSlug: String,
        fileName: String,
    ): Uri? {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val directory = tree.findFile(animeSlug)?.takeIf { it.isDirectory } ?: return null
        return directory.findFile(fileName)?.takeIf { it.isFile }?.uri
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun findInMediaStore(animeSlug: String, fileName: String): Uri? {
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/SamehadakuDL/$animeSlug/"
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?, ?)"
        return context.contentResolver.query(
            mediaFileCollection(),
            projection,
            selection,
            arrayOf(fileName, relativePath, relativePath.trimEnd('/')),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(mediaFileCollection(), cursor.getLong(0))
            } else {
                null
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(
        partFile: File,
        animeSlug: String,
        fileName: String,
    ): Boolean {
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/SamehadakuDL/$animeSlug/"
        deleteMediaStoreEntry(fileName, relativePath)
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MimeTypes.forFile(fileName))
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return false

        return try {
            resolver.openOutputStream(uri)?.use { output ->
                partFile.inputStream().use { input -> input.copyTo(output, BUFFER_SIZE) }
            } ?: throw IOException("Tidak dapat membuka file tujuan")

            val complete = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, complete, null, null)
            true
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun existsInMediaStore(
        animeSlug: String,
        fileName: String,
        size: Long,
    ): Boolean {
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/SamehadakuDL/$animeSlug/"
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?, ?)"
        return context.contentResolver.query(
            mediaFileCollection(),
            projection,
            selection,
            arrayOf(fileName, relativePath, relativePath.trimEnd('/')),
            null,
        )?.use { cursor ->
            cursor.moveToFirst() && cursor.getLong(0) == size
        } ?: false
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteMediaStoreEntry(fileName: String, relativePath: String) {
        val collection = mediaFileCollection()
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?, ?)"
        val ids = mutableListOf<Long>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            arrayOf(fileName, relativePath, relativePath.trimEnd('/')),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getLong(0))
        }
        for (id in ids) {
            runCatching {
                context.contentResolver.delete(
                    android.content.ContentUris.withAppendedId(collection, id),
                    null,
                    null,
                )
            }
        }
    }

    private fun saveToLegacyFolder(
        partFile: File,
        animeSlug: String,
        fileName: String,
    ): Boolean {
        val directory = File(legacyBase(), "$APP_FOLDER/$animeSlug").apply { mkdirs() }
        if (!directory.isDirectory) return false
        val target = File(directory, fileName)
        if (target.exists() && target.length() == partFile.length()) return true
        partFile.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
        }
        return target.length() == partFile.length()
    }

    private fun legacyBase(): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir

    private fun legacyFile(animeSlug: String, fileName: String): File =
        File(legacyBase(), "$APP_FOLDER/$animeSlug/$fileName")

    private fun legacyUri(animeSlug: String, fileName: String): Uri? {
        val file = legacyFile(animeSlug, fileName)
        if (!file.isFile) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private companion object {
        const val TAG = "StorageWriter"
        const val BUFFER_SIZE = 64 * 1024
        /** Sub folder created under the public Download directory for the collection. */
        const val APP_FOLDER = "SamehadakuDL"
    }
}
