package com.clxv.gamevault.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A "game" is the logical title; a [FileRecord] is one physical copy.
 * Splitting them is what makes duplicate detection and multi-file
 * games (cue/bin pairs) clean.
 */
@Entity(
    tableName = "games",
    indices = [
        Index(value = ["normalizedTitle"]),
        Index(value = ["platform"]),
        Index(value = ["favorite"]),
        Index(value = ["hidden"]),
        Index(value = ["lastPlayedAt"]),
    ],
)
data class GameEntity(
    @PrimaryKey val id: String,                 // uuid
    val title: String,
    val normalizedTitle: String,
    val platform: String,                       // Platform.name
    val region: String,                         // Region.name
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val notes: String = "",
    val manualPlatform: Boolean = false,        // user manually identified
    val customCoverPath: String? = null,        // internal file, original untouched
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,             // metadata only; app never executes games
)

@Entity(
    tableName = "files",
    indices = [
        Index(value = ["gameId"]),
        Index(value = ["uri"], unique = true),
        Index(value = ["libraryRootId"]),
        Index(value = ["size", "quickHash"]),
    ],
)
data class FileRecordEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val libraryRootId: String,
    val uri: String,                            // persisted SAF uri
    val documentId: String,
    val displayPath: String,                    // human readable /storage/... path
    val fileName: String,
    val extension: String,
    val size: Long,
    val modifiedTime: Long,
    val format: String,
    val confidence: Float,
    val status: String,                         // ScanStatus.name
    val detectionReasons: String = "",          // newline-joined
    val quickHash: String,                      // for duplicate grouping
    val isPrimary: Boolean = true,              // primary file of the game (cue over bin)
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "collections", indices = [Index(value = ["name"], unique = true)])
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "collection_games", primaryKeys = ["collectionId", "gameId"],
          indices = [Index(value = ["gameId"])])
data class CollectionGameCrossRef(
    val collectionId: String,
    val gameId: String,
)

@Entity(tableName = "library_roots", indices = [Index(value = ["uri"], unique = true)])
data class LibraryRootEntity(
    @PrimaryKey val id: String,
    val uri: String,                            // persisted tree uri
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastScanAt: Long? = null,
)

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val filesSeen: Int = 0,
    val gamesAdded: Int = 0,
    val gamesUpdated: Int = 0,
    val filesRemoved: Int = 0,
    val status: String,                         // RUNNING / COMPLETED / CANCELLED / FAILED
    val error: String? = null,
)
