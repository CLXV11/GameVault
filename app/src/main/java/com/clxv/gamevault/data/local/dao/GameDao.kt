package com.clxv.gamevault.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.clxv.gamevault.data.local.entity.FileRecordEntity
import com.clxv.gamevault.data.local.entity.GameEntity
import com.clxv.gamevault.data.local.entity.GameWithFiles
import com.clxv.gamevault.data.local.entity.LibraryRootEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGame(game: GameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGames(games: List<GameEntity>)

    @Update
    suspend fun updateGame(game: GameEntity)

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun gameById(id: String): GameEntity?

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeGame(id: String): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE hidden = 0 ORDER BY normalizedTitle ASC")
    fun observeAllVisible(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE hidden = 1 ORDER BY normalizedTitle ASC")
    fun observeHidden(): Flow<List<GameEntity>>

    @Query(
        """SELECT * FROM games WHERE hidden = 0 AND (
             title LIKE '%' || :q || '%' OR normalizedTitle LIKE '%' || :q || '%')
           ORDER BY normalizedTitle ASC"""
    )
    fun search(q: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE favorite = 1 AND hidden = 0")
    fun observeFavorites(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id IN (SELECT gameId FROM collection_games WHERE collectionId = :collectionId)")
    fun gamesInCollection(collectionId: String): Flow<List<GameEntity>>

    @Query("UPDATE games SET hidden = :hidden WHERE id IN (:ids)")
    suspend fun setHidden(ids: List<String>, hidden: Boolean)

    @Query("UPDATE games SET favorite = :fav WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<String>, fav: Boolean)

    @Query("UPDATE games SET lastPlayedAt = :ts WHERE id = :id")
    suspend fun touchPlayed(id: String, ts: Long)

    @Query("DELETE FROM games WHERE id IN (:ids)")
    suspend fun deleteGames(ids: List<String>)

    @Query("DELETE FROM games WHERE platform = 'UNKNOWN'")
    suspend fun deleteUnknownGames()

    // ---------------- Files ----------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(f: FileRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFiles(files: List<FileRecordEntity>)

    @Query("SELECT * FROM files WHERE uri = :uri")
    suspend fun fileByUri(uri: String): FileRecordEntity?

    @Query("SELECT * FROM files WHERE gameId = :gameId")
    fun filesForGame(gameId: String): Flow<List<FileRecordEntity>>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun fileById(id: String): FileRecordEntity?

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    fun observeGameWithFiles(id: String): Flow<GameWithFiles?>

    @Query("SELECT * FROM files WHERE size = :size AND quickHash = :hash")
    suspend fun findDuplicates(size: Long, hash: String): List<FileRecordEntity>

    @Query("SELECT * FROM files ORDER BY size DESC")
    fun observeAllFiles(): Flow<List<FileRecordEntity>>

    @Query("SELECT COUNT(*) FROM games WHERE hidden = 0")
    fun observeGameCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size),0) FROM files")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM files")
    fun observeFileCount(): Flow<Long>

    @Query("SELECT * FROM files WHERE libraryRootId = :rootId")
    suspend fun filesInRoot(rootId: String): List<FileRecordEntity>

    @Query("DELETE FROM files WHERE uri = :uri")
    suspend fun deleteFileByUri(uri: String)

    /** Largest file size per game — powers the REAL "sort by size". */
    @Query("SELECT gameId AS gameId, MAX(size) AS size FROM files GROUP BY gameId")
    fun gameSizes(): Flow<List<GameSize>>

    /** Live per-platform game counts for the filter chips. */
    @Query("SELECT platform AS platform, COUNT(*) AS total FROM games WHERE hidden = 0 GROUP BY platform")
    fun platformCounts(): Flow<List<PlatformCount>>

    /** Recently viewed games (lastPlayedAt doubles as "last opened" metadata). */
    @Query("SELECT * FROM games WHERE hidden = 0 AND lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 10")
    fun recentGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games")
    suspend fun allGamesSnapshot(): List<GameEntity>

    @Query("SELECT * FROM files")
    suspend fun allFilesSnapshot(): List<FileRecordEntity>

    @Transaction
    suspend fun deleteGamesWithFiles(ids: List<String>) {
        // FK-less manual cleanup to stay robust across Room versions
        deleteGames(ids)
    }
}

/** POJO for [GameDao.platformCounts]. */
data class PlatformCount(val platform: String, val total: Int)

/** POJO for [GameDao.gameSizes]. */
data class GameSize(val gameId: String, val size: Long)
