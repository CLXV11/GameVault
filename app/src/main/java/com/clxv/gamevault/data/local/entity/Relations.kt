package com.clxv.gamevault.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class GameWithFiles(
    @Embedded val game: GameEntity,
    @Relation(parentColumn = "id", entityColumn = "gameId")
    val files: List<FileRecordEntity>,
)

data class CollectionWithGames(
    @Embedded val collection: CollectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            CollectionGameCrossRef::class,
            parentColumn = "collectionId",
            entityColumn = "gameId",
        ),
    )
    val games: List<GameEntity>,
)
