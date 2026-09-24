package com.clxv.gamevault.data.repository

import com.clxv.gamevault.core.model.Platform
import com.clxv.gamevault.core.model.ScanStatus
import com.clxv.gamevault.core.scanner.DuplicateFinder
import com.clxv.gamevault.data.local.AppDatabase
import com.clxv.gamevault.data.local.entity.FileRecordEntity
import com.clxv.gamevault.data.local.entity.GameEntity
import com.clxv.gamevault.data.local.entity.GameWithFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(private val db: AppDatabase) {

    fun observeLibrary() = db.gameDao().observeAllVisible()
    fun observeHidden() = db.gameDao().observeHidden()
    fun observeFavorites() = db.gameDao().observeFavorites()
    fun search(query: String) = db.gameDao().search(query.trim())
    fun observeGame(id: String): Flow<GameEntity?> = db.gameDao().observeGame(id)
    fun observeGameWithFiles(id: String): Flow<GameWithFiles?> = db.gameDao().observeGameWithFiles(id)
    fun observeStats() = Triple(db.gameDao().observeGameCount(), db.gameDao().observeFileCount(), db.gameDao().observeTotalBytes())

    suspend fun updateGame(game: GameEntity) = db.gameDao().updateGame(game)

    suspend fun setFavorite(ids: List<String>, fav: Boolean) = db.gameDao().setFavorite(ids, fav)
    suspend fun setHidden(ids: List<String>, hidden: Boolean) = db.gameDao().setHidden(ids, hidden)

    suspend fun touchPlayed(gameId: String) =
        db.gameDao().touchPlayed(gameId, System.currentTimeMillis())

    /** Manual identification: user override, always wins over auto detection. */
    suspend fun identifyManually(gameId: String, platform: Platform) {
        val g = db.gameDao().gameById(gameId) ?: return
        db.gameDao().updateGame(g.copy(platform = platform.name, manualPlatform = true))
        db.gameDao().filesForGame(gameId).first().forEach { f ->
            db.gameDao().upsertFile(f.copy(
                status = ScanStatus.MANUAL.name,
                confidence = 1.0f,
                detectionReasons = "manually identified by user",
            ))
        }
    }

    suspend fun renameTitle(gameId: String, newTitle: String) {
        val g = db.gameDao().gameById(gameId) ?: return
        db.gameDao().updateGame(g.copy(title = newTitle, normalizedTitle = newTitle.lowercase()))
        // Physical file is never renamed — metadata only.
    }

    suspend fun setNotes(gameId: String, notes: String) {
        db.gameDao().gameById(gameId)?.let { db.gameDao().updateGame(it.copy(notes = notes)) }
    }

    /** Removes games and their file records; never touches the actual files on disk. */
    suspend fun removeFromLibrary(ids: List<String>) {
        db.collectionDao().removeGamesFromAll(ids)
        db.gameDao().deleteGamesWithFiles(ids)
    }

    suspend fun duplicates(): List<List<FileRecordEntity>> =
        DuplicateFinder.groupDuplicates(db.gameDao().allFilesSnapshot())

    // ---------------- Collections ----------------
    fun observeCollections() = db.collectionDao().observeAll()
    fun observeCollection(id: String) = db.collectionDao().observeWithGames(id)
    fun collectionIdsFor(gameId: String) = db.collectionDao().collectionIdsFor(gameId)

    suspend fun createCollection(name: String) =
        db.collectionDao().upsert(com.clxv.gamevault.data.local.entity.CollectionEntity(
            id = UUID.randomUUID().toString(), name = name.trim()))

    suspend fun addToCollection(collectionId: String, gameIds: List<String>) {
        gameIds.forEach { db.collectionDao().addGame(
            com.clxv.gamevault.data.local.entity.CollectionGameCrossRef(collectionId, it)) }
    }

    suspend fun removeFromCollection(collectionId: String, gameIds: List<String>) {
        gameIds.forEach { db.collectionDao().removeGame(collectionId, it) }
    }

    suspend fun deleteCollection(id: String) {
        db.collectionDao().clear(id)
        db.collectionDao().delete(id)
    }
}
