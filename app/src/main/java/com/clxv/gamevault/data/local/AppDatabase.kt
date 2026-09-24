package com.clxv.gamevault.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.clxv.gamevault.data.local.dao.CollectionDao
import com.clxv.gamevault.data.local.dao.GameDao
import com.clxv.gamevault.data.local.dao.LibraryRootDao
import com.clxv.gamevault.data.local.entity.CollectionEntity
import com.clxv.gamevault.data.local.entity.CollectionGameCrossRef
import com.clxv.gamevault.data.local.entity.FileRecordEntity
import com.clxv.gamevault.data.local.entity.GameEntity
import com.clxv.gamevault.data.local.entity.LibraryRootEntity
import com.clxv.gamevault.data.local.entity.ScanHistoryEntity

@Database(
    entities = [
        GameEntity::class,
        FileRecordEntity::class,
        CollectionEntity::class,
        CollectionGameCrossRef::class,
        LibraryRootEntity::class,
        ScanHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun collectionDao(): CollectionDao
    abstract fun libraryRootDao(): LibraryRootDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gamevault.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
