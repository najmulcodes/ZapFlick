package com.najmulcodes.zapflick.di

import android.content.Context
import androidx.room.Room
import com.najmulcodes.zapflick.data.db.FavoriteDao
import com.najmulcodes.zapflick.data.db.HistoryDao
import com.najmulcodes.zapflick.data.db.Migrations
import com.najmulcodes.zapflick.data.db.ZapFlickDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    // No destructive fallback: a schema change must come with a migration, or the history would be wiped.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZapFlickDatabase =
        Room.databaseBuilder(context, ZapFlickDatabase::class.java, "zapflick.db")
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun provideFavoriteDao(db: ZapFlickDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideHistoryDao(db: ZapFlickDatabase): HistoryDao = db.historyDao()
}
