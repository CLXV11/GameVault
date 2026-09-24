package com.clxv.gamevault.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clxv.gamevault.data.local.entity.LibraryRootEntity
import com.clxv.gamevault.data.local.entity.ScanHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryRootDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(root: LibraryRootEntity)

    @Query("SELECT * FROM library_roots ORDER BY addedAt")
    fun observeAll(): Flow<List<LibraryRootEntity>>

    @Query("SELECT * FROM library_roots")
    suspend fun all(): List<LibraryRootEntity>

    @Query("SELECT * FROM library_roots WHERE id = :id")
    suspend fun byId(id: String): LibraryRootEntity?

    @Query("UPDATE library_roots SET lastScanAt = :ts WHERE id = :id")
    suspend fun markScanned(id: String, ts: Long)

    @Query("DELETE FROM library_roots WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(h: ScanHistoryEntity)

    @Query("UPDATE scan_history SET finishedAt = :finishedAt, status = :status, error = :error, filesSeen = :filesSeen, gamesAdded = :added, gamesUpdated = :updated, filesRemoved = :removed WHERE id = :id")
    suspend fun finishScan(id: String, finishedAt: Long, status: String, error: String?, filesSeen: Int, added: Int, updated: Int, removed: Int)

    @Query("SELECT * FROM scan_history ORDER BY startedAt DESC LIMIT 20")
    fun observeHistory(): Flow<List<ScanHistoryEntity>>
}
