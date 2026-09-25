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
    private val coverManager: com.clxv.gamevault.core.covers.CoverManager,
    private val repo: com.clxv.gamevault.data.repository.GameRepository,
) {
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    @Volatile private var cancelled = false

    fun cancel() { cancelled = true }

    private val JUNK_EXTENSIONS = DetectionEngine.JUNK_EXTENSIONS
    private val COVER_REGEX = DetectionEngine.COVER_NAME_REGEX

    /** Extensions that identify game content even when the platform stays unknown. */
    private val GAME_EXTENSIONS = setOf(
        "nes", "fds", "sfc", "smc", "fig", "swc", "z64", "n64", "v64",
        "gb", "gbc", "gba", "nds", "ids", "3ds", "cci", "cia", "cxi", "gcm",
        "wbfs", "rvz", "wud", "wux", "rpx", "xci", "nsp", "nro", "nca",
        "iso", "bin", "cue", "img", "mdf", "chd", "cdi", "gdi", "cso", "pbp", "pkg", "vpk",
        "xbe", "xex", "zar", "md", "smd", "gen", "sms", "gg", "32x",
        "adf", "adz", "ipf", "dms", "st", "msa", "tzx", "tap", "z80", "sna",
        "pce", "sgx", "ngc", "ngp", "ws", "wsc", "a26", "lnx", "lyx", "j64", "jag",
        "col", "cv", "int", "rom", "vec", "tgc", "sfo", "nrg",
    )

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
            val coverSidecars = mutableMapOf<String, Uri>()   // normalized base -> uri
            walk(tree, root, 0, foundUris, coverSidecars) { file ->
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

            // Attach user cover art: "Game (USA).cover.png" -> cover of "Game (USA).rvz"
            pairCoverSidecars(coverSidecars)

            // Emulator-style: auto-create platform groups (Wii, PS2, ...) after every scan
            repo.organizeByPlatform()

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

    /** Manually add one user-picked file to the library ("manual" pseudo-root).
     *  Returns false when the file is clearly not a game. */
    suspend fun addSingleFile(uri: Uri, title: String, platform: com.clxv.gamevault.core.model.Platform?): Boolean {
        val nm = queryDisplayName(uri) ?: uri.lastPathSegment ?: "file"
        if (nm.substringAfterLast('.', "").lowercase(java.util.Locale.US) in JUNK_EXTENSIONS) return false
        withContext(Dispatchers.IO) {
            val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "file"
            var size = 0L; var mtime = 0L
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val si = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    val mi = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (c.moveToFirst()) {
                        if (si >= 0) size = c.getLong(si)
                        if (mi >= 0) mtime = c.getLong(mi)
                    }
                }
            }
            val engine = DetectionEngine(SafByteReader(context, uri))
            val det = platform?.let {
                com.clxv.gamevault.core.model.DetectionResult(
                    platform = it, status = ScanStatus.MANUAL, confidence = 1f,
                    format = name.substringAfterLast('.', "").uppercase(), region = engine.extractRegion(name),
                )
            } ?: engine.detect(name, if (size > 0) size else 1, emptyList())

            val gameId = java.util.UUID.randomUUID().toString()
            val finalTitle = title.ifBlank { engine.normalizeTitle(name) }
            db.gameDao().upsertGame(com.clxv.gamevault.data.local.entity.GameEntity(
                id = gameId, title = finalTitle,
                normalizedTitle = finalTitle.lowercase(java.util.Locale.US),
                platform = det.platform.name, region = det.region.name,
                manualPlatform = platform != null,
            ))
            val hash = runCatching {
                DuplicateFinder.quickHash(if (size > 0) size else 1) { off, len -> SafByteReader(context, uri).readAt(off, len) }
            }.getOrDefault("unreadable")
            db.gameDao().upsertFile(com.clxv.gamevault.data.local.entity.FileRecordEntity(
                id = java.util.UUID.randomUUID().toString(), gameId = gameId, libraryRootId = "manual",
                uri = uri.toString(), documentId = uri.lastPathSegment ?: uri.toString(),
                displayPath = name, fileName = name,
                extension = name.substringAfterLast('.', "").lowercase(java.util.Locale.US),
                size = size, modifiedTime = mtime, format = det.format,
                confidence = det.confidence, status = det.status.name,
                detectionReasons = det.reasons.take(8).joinToString("\n"),
                quickHash = hash,
            ))
        }
        repo.organizeByPlatform()
        return true
    }

    /** Copies matching sidecar art into the game's private cover slot. */
    private suspend fun pairCoverSidecars(covers: Map<String, Uri>) {
        if (covers.isEmpty()) return
        val games = db.gameDao().allGamesSnapshot()
        val byNorm = games.associateBy { it.normalizedTitle }
        covers.forEach { (base, uri) ->
            val game = byNorm[base]
                ?: games.firstOrNull { base.startsWith(it.normalizedTitle) || it.normalizedTitle.startsWith(base) }
                ?: return@forEach
            if (game.customCoverPath != null) return@forEach   // never overwrite user art
            val bmp = coverManager.decodeSampled(uri) ?: return@forEach
            val path = coverManager.saveCustomCover(game.id, bmp)
            db.gameDao().upsertGame(game.copy(customCoverPath = path))
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }
    }.getOrNull()

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

        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)

        // Never create library entries from junk (wallpapers, scripts, docs...).
        if (ext in JUNK_EXTENSIONS) return ProcessResult.SKIPPED

        // The root folder's own name is a platform signal too ("ps1", "Wii", ...)
        val parents = listOf(root.displayName) + parentChain(file, root)
        val engine = DetectionEngine(SafByteReader(context, file.uri))
        val det = engine.detect(name, size, parents)

        // Files with no identifying signal at all are not games — skip them.
        if (det.status == ScanStatus.UNKNOWN && ext !in GAME_EXTENSIONS) {
            return ProcessResult.SKIPPED
        }

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
        coverSidecars: MutableMap<String, Uri>,
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
                walk(child, root, depth + 1, foundUris, coverSidecars, onFile)
            } else if (child.isFile) {
                if (nm.startsWith(".")) continue
                foundUris.add(child.uri.toString())
                // Cover-art sidecar? "Game (USA).cover.png" — never a game itself.
                val ext = nm.substringAfterLast('.', "")
                if (ext in DetectionEngine.IMAGE_EXTENSIONS &&
                    COVER_REGEX.containsMatchIn(nm.substringBeforeLast('.'))) {
                    val base = nm.substringBeforeLast('.').replace(COVER_REGEX, "")
                    val norm = DetectionEngine().normalizeTitle("$base.x")
                        .lowercase(java.util.Locale.US)
                    coverSidecars[norm] = child.uri
                    continue
                }
                onFile(child)
            }
        }
    }
}
