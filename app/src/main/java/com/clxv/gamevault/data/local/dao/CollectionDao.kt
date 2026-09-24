package com.clxv.gamevault.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.clxv.gamevault.data.local.entity.CollectionEntity
import com.clxv.gamevault.data.local.entity.CollectionGameCrossRef
import com.clxv.gamevault.data.local.entity.CollectionWithGames
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: CollectionEntity)

    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun byId(id: String): CollectionEntity?

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addGame(ref: CollectionGameCrossRef)

    @Query("DELETE FROM collection_games WHERE collectionId = :collectionId AND gameId = :gameId")
    suspend fun removeGame(collectionId: String, gameId: String)

    @Query("DELETE FROM collection_games WHERE gameId IN (:gameIds)")
    suspend fun removeGamesFromAll(gameIds: List<String>)

    @Query("DELETE FROM collection_games WHERE collectionId = :collectionId")
    suspend fun clear(collectionId: String)

    @Transaction
    @Query("SELECT * FROM collections WHERE id = :id")
    fun observeWithGames(id: String): Flow<CollectionWithGames?>

    @Query("SELECT collectionId FROM collection_games WHERE gameId = :gameId")
    fun collectionIdsFor(gameId: String): Flow<List<String>>
}
