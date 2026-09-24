package com.clxv.gamevault.di

import android.content.Context
import androidx.room.Room
import com.clxv.gamevault.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "gamevault.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideGameDao(db: AppDatabase) = db.gameDao()
    @Provides fun provideCollectionDao(db: AppDatabase) = db.collectionDao()
    @Provides fun provideRootDao(db: AppDatabase) = db.libraryRootDao()
}
