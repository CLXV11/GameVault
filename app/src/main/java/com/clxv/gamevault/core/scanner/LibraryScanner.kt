package com.clxv.gamevault.core.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.clxv.gamevault.core.detection.DetectionEngine
import com.clxv.gamevault.core.detection.SafByteReader
import com.clxv.gamevault.core.model.ScanStatus
import com.clxv.gamevault.data.local.AppDatabase
import com.clxv.gamevault.data.local.entity.FileRecordEntity
import com.clxv.gamevault.data.local.entity.GameEntity
import com.clxv.gamevault.data.local.entity.LibraryRootEntity
import com.clxv.gamevault.data.local.entity.ScanHistoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ScanProgress(
    val running: Boolean = false,
    val rootName: String = "",
    val filesScanned: Int = 0,
    val gamesFound: Int = 0,
    val currentPath: String = "",
)

@Singleton
class LibraryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val saf: com.clxv.gamevault.core.saf.SafManager,
) {
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    @Volatile private var cancelled = false

    fun cancel() { cancelled = true }

    private val SKIPPED_DIRS = setOf(
        ".", "..", "android", "lost.dir", ".trash", ".cache", ".thumbnails", "music", "pictures", "dcim", "movies",
    )
    private val MAX_DEPTH = 10
    private val MAX_FILE_SIZE = 512L * 1024 * 1024 * 1024   // sanity ceiling

    suspend fun scanAllRoots() {
        val roots = db.libraryRootDao().all()
        for (root in roots) {
            currentCoroutineContext().ensureActive()
            scanRoot(root)
        }
    }

    suspend fun scanRoot(root: LibraryRootEntity) = withContext(Dispatchers.IO) {
        cancelled = false
        val history = ScanHistoryEntity(
            id = UUID.randomUUID().toString(),
            startedAt = System.currentTimeMillis(),
            status = "RUNNING",
        )
        db.libraryRootDao().insertScan(history)

        var seen = 0; var added = 0; var updated = 0
        try {
            _progress.value = ScanProgress(true, root.displayName)
            val tree = saf.documentFile(Uri.parse(root.uri))
            if (tree == null || !tree.exists()) {
                db.libraryRootDao().finishScan(history.id, System.currentTimeMillis(), "FAILED",
                    "Folder unavailable (permission revoked?)", seen, added, updated, 0)
                return@withContext
            }

            val foundUris = mutableSetOf<String>()
            walk(tree, root, 0, foundUris) { file ->
                seen++
                _progress.value = _progress.value.copy(
                    filesScanned = seen,
                    currentPath = file.name ?: "",
                    gamesFound = added,
                )
                val result = processFile(file, root)
                if (result == ProcessResult.ADDED) added++
                if (result == ProcessResult.UPDATED) updated++
            }

            // Broken/missing file detection: any DB record under this root that no
            // longer resolves on disk is flagged, not silently deleted.
            val stale = db.gameDao().filesInRoot(root.id).filter { it.uri !in foundUris }
            for (f in stale) {
                db.gameDao().deleteFileByUri(f.uri)
            }

            db.libraryRootDao().markScanned(root.id, System.currentTimeMillis())
            db.libraryRootDao().finishScan(history.id, System.currentTimeMillis(), "COMPLETED", null,
                seen, added, updated, stale.size)
        } catch (e: CancellationException) {
            db.libraryRootDao().finishScan(history.id, System.currentTimeMillis(), "CANCELLED", null,
                seen, added, updated, 0)
            throw e
        } catch (e: Exception) {
            db.libraryRootDao().finishScan(history.id, System.currentTimeMillis(), "FAILED", e.message,
                seen, added, updated, 0)
        } finally {
            _progress.value = ScanProgress()
        }
    }

    private enum class ProcessResult { ADDED, UPDATED, SKIPPED }

    private suspend fun processFile(file: DocumentFile, root: LibraryRootEntity): ProcessResult {
        if (!file.isFile) return ProcessResult.SKIPPED
        val name = file.name ?: return ProcessResult.SKIPPED
        val uri = file.uri.toString()
        val size = file.length()
        val mtime = file.lastModified()
        if (size <= 0 || size > MAX_FILE_SIZE) return ProcessResult.SKIPPED

        // Incremental: unchanged (uri, size, mtime) -> skip entirely.
        val existing = db.gameDao().fileByUri(uri)
        if (existing != null && existing.size == size && existing.modifiedTime == mtime) {
            return ProcessResult.SKIPPED
        }

        // Cue/bin pairing: .bin handled via its .cue; large multi-file games group by folder+title.
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext == "bin") {
            // Only treat loose .bin as game when no sibling .cue references it.
            // (Directory scans pair them below via buildGamesFromFiles.)
        }

        val parents = parentChain(file, root)
        val engine = DetectionEngine(SafByteReader(context, file.uri))
        val det = engine.detect(name, size, parents)

        val hash = runCatching {
            DuplicateFinder.quickHash(size) { off, len ->
                SafByteReader(context, file.uri).readAt(off, len)
            }
        }.getOrDefault("unreadable")

        val gameId = existing?.gameId ?: UUID.randomUUID().toString()
        val title = engine.normalizeTitle(name)
        val now = System.currentTimeMillis()

        db.gameDao().upsertGame(
            GameEntity(
                id = gameId,
                title = title,
                normalizedTitle = title.lowercase(Locale.US),
                platform = det.platform.name,
                region = det.region.name,
            )
        )
        val wasExisting = existing != null
        db.gameDao().upsertFile(
            FileRecordEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                gameId = gameId,
                libraryRootId = root.id,
                uri = uri,
                documentId = file.uri.lastPathSegment ?: uri,
                displayPath = buildDisplayPath(file, root),
                fileName = name,
                extension = ext,
                size = size,
                modifiedTime = mtime,
                format = det.format,
                confidence = det.confidence,
                status = det.status.name,
                detectionReasons = det.reasons.take(8).joinToString("\n"),
                quickHash = hash,
            )
        )
        return if (wasExisting) ProcessResult.UPDATED else ProcessResult.ADDED
    }

    private fun parentChain(file: DocumentFile, root: LibraryRootEntity): List<String> {
        // DocumentFile has no parent API; derive folder names from the document id,
        // e.g. "primary:Games/PS3_GAME/USRDIR/EBOOT.BIN" -> ["Games","PS3_GAME","USRDIR"].
        val docId = com.clxv.gamevault.core.saf.DocumentsContractCompat.getDocumentId(context, file.uri)
            ?: return emptyList()
        return docId.substringAfter(':', "").split('/').dropLast(1)
            .map { java.net.URLDecoder.decode(it, "UTF-8") }.filter { it.isNotBlank() }
    }

    private fun buildDisplayPath(file: DocumentFile, root: LibraryRootEntity): String {
        val docId = com.clxv.gamevault.core.saf.DocumentsContractCompat.getDocumentId(context, file.uri)
        val path = docId?.substringAfter(':', "")
        return if (!path.isNullOrBlank()) "/$path" else (file.name ?: file.uri.toString())
    }

    private suspend fun walk(
        dir: DocumentFile,
        root: LibraryRootEntity,
        depth: Int,
        foundUris: MutableSet<String>,
        onFile: suspend (DocumentFile) -> Unit,
    ) {
        if (cancelled) throw CancellationException("scan cancelled")
        if (depth > MAX_DEPTH) return
        val children = withContext(Dispatchers.IO) { runCatching { dir.listFiles() }.getOrNull() } ?: return
        for (child in children) {
            currentCoroutineContext().ensureActive()
            val nm = child.name?.lowercase(Locale.US) ?: continue
            if (child.isDirectory) {
                if (nm in SKIPPED_DIRS || nm.startsWith(".")) continue
                walk(child, root, depth + 1, foundUris, onFile)
            } else if (child.isFile) {
                if (nm.startsWith(".")) continue
                foundUris.add(child.uri.toString())
                onFile(child)
            }
        }
    }
}
