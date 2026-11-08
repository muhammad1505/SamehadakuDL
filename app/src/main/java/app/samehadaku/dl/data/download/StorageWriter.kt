package app.samehadaku.dl.data.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
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

/** A single video file discovered inside a collection folder. */
data class StoredFile(
    val name: String,
    val size: Long,
    val modifiedAt: Long,
    val uri: Uri?,
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

    suspend fun deleteDownload(animeSlug: String, fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            val safeSlug = UrlUtils.fileNameSafe(animeSlug).ifBlank { "SamehadakuDL" }
            val treeUri = runCatching { settings.currentDownloadTreeUri() }.getOrNull()
            try {
                when {
                    !treeUri.isNullOrBlank() -> {
                        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
                            ?: return@withContext false
                        val directory = tree.findFile(safeSlug)?.takeIf { it.isDirectory }
                            ?: return@withContext true
                        val file = directory.findFile(fileName) ?: return@withContext true
                        file.delete()
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        val uri = findInMediaStore(safeSlug, fileName) ?: return@withContext true
                        context.contentResolver.delete(uri, null, null) > 0
                    }
                    else -> {
                        val file = legacyFile(safeSlug, fileName)
                        !file.exists() || file.delete()
                    }
                }
            } catch (_: Exception) {
                false
            }
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

    /** Deletes a collection folder and every file inside it, across all locations. */
    suspend fun deleteFolder(folderName: String): Int =
        withContext(Dispatchers.IO) {
            if (!isSafeName(folderName)) return@withContext 0
            var deleted = 0
            val treeUri = runCatching { settings.currentDownloadTreeUri() }.getOrNull()
            if (!treeUri.isNullOrBlank()) {
                deleted += runCatching { deleteFolderInTree(Uri.parse(treeUri), folderName) }
                    .getOrDefault(0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                deleted += runCatching { deleteFolderInMediaStore(folderName) }
                    .getOrDefault(0)
            }
            deleted += runCatching { deleteFolderInLegacy(folderName) }.getOrDefault(0)
            deleted
        }

    /** Deletes one stored file from every location that holds it. */
    suspend fun deleteStoredFile(folderName: String, fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!isSafeName(folderName) || !isSafeName(fileName)) return@withContext false
            var failure = false

            fun record(outcome: Boolean?) {
                if (outcome != null && !outcome) failure = true
            }

            val treeUri = runCatching { settings.currentDownloadTreeUri() }.getOrNull()
            if (!treeUri.isNullOrBlank()) {
                record(
                    runCatching { deleteFileInTree(Uri.parse(treeUri), folderName, fileName) }
                        .fold(onSuccess = { it }, onFailure = { false }),
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                record(
                    runCatching { deleteFileInMediaStore(folderName, fileName) }
                        .fold(onSuccess = { it }, onFailure = { false }),
                )
            }
            record(
                runCatching { deleteFileInLegacy(folderName, fileName) }
                    .fold(onSuccess = { it }, onFailure = { false }),
            )

            !failure
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
     * Lists finished MediaStore files that belong to this app.
     *
     * Rows are matched by our folder name or by the owning package instead of an
     * exact `RELATIVE_PATH` prefix: providers normalise the path per OEM, users move
     * the folder, and older builds wrote a slightly different layout. Matching the
     * prefix strictly made finished episodes disappear from Koleksi.
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
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
        )
        val folders = LinkedHashMap<String, MutableList<StoredFile>>()
        val selection = "${MediaStore.MediaColumns.IS_PENDING}=0 AND " +
            "(${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR " +
            "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=?)"
        val cursor = context.contentResolver.query(
            mediaFileCollection(),
            projection,
            selection,
            arrayOf("%/$APP_FOLDER/%", context.packageName),
            null,
        )
        if (cursor == null) {
            Log.w(TAG, "media query returned null cursor")
            return emptyList()
        }
        val packageName = context.packageName
        cursor.use {
            Log.d(TAG, "media cursor rows=${it.count}")
            val idIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val pendingIndex = it.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
            val ownerIndex = it.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
            while (it.moveToNext()) {
                if (pendingIndex >= 0 && it.getInt(pendingIndex) == 1) continue
                val name = it.getString(nameIndex)
                if (name.isNullOrBlank() || !EpisodeFiles.isEpisode(name)) continue
                val relativePath = it.getString(pathIndex)?.trim('/').orEmpty()
                val owner = if (ownerIndex >= 0) it.getString(ownerIndex) else null
                val inAppFolder = relativePath.contains(APP_FOLDER, ignoreCase = true)
                if (!inAppFolder && owner != packageName) continue
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
                        )
                    }
                    .orEmpty()
                if (files.isEmpty()) null else StoredFolder(directory.name, files)
            }
            .orEmpty()
    }

    private fun deleteFolderInTree(treeUri: Uri, folderName: String): Int {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        val directory = tree.findFile(folderName)?.takeIf { it.isDirectory } ?: return 0
        var deleted = 0
        directory.listFiles().filter { it.isFile }.forEach { file ->
            if (file.delete()) deleted++
        }
        if (directory.listFiles().isEmpty()) {
            runCatching { directory.delete() }
        }
        return deleted
    }

    /**
     * Deletes every file stored in one MediaStore collection folder. Files live in
     * `.../SamehadakuDL/<folder>/`, so the folder name must be compared with the
     * file's parent segment instead of its full relative path.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteFolderInMediaStore(folderName: String): Int {
        val collection = mediaFileCollection()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val ids = mutableListOf<Long>()
        context.contentResolver.query(
            collection,
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
        var deleted = 0
        for (id in ids) {
            runCatching {
                if (context.contentResolver.delete(
                        ContentUris.withAppendedId(collection, id),
                        null,
                        null,
                    ) > 0
                ) {
                    deleted++
                }
            }
        }
        return deleted
    }

    private fun deleteFolderInLegacy(folderName: String): Int {
        val directory = File(legacyBase(), "$APP_FOLDER/$folderName")
        if (!directory.isDirectory) return 0
        var deleted = 0
        directory.listFiles()?.filter { it.isFile }?.forEach { file ->
            if (file.delete()) deleted++
        }
        if (directory.listFiles()?.isNotEmpty() != true) {
            directory.delete()
        }
        return deleted
    }

    /** Returns null when the file is not present in that location. */
    private fun deleteFileInTree(treeUri: Uri, folderName: String, fileName: String): Boolean? {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val directory = tree.findFile(folderName)?.takeIf { it.isDirectory } ?: return null
        val file = directory.findFile(fileName) ?: return null
        return file.delete()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteFileInMediaStore(folderName: String, fileName: String): Boolean? {
        val uri = findInMediaStore(folderName, fileName) ?: return null
        return context.contentResolver.delete(uri, null, null) > 0
    }

    private fun deleteFileInLegacy(folderName: String, fileName: String): Boolean? {
        val file = File(legacyBase(), "$APP_FOLDER/$folderName/$fileName")
        if (!file.exists()) return null
        return file.delete()
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
