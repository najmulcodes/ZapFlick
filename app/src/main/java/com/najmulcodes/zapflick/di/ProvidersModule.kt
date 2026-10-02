package com.najmulcodes.zapflick.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.najmulcodes.zapflick.data.db.DownloadDao
import com.najmulcodes.zapflick.data.db.ZapFlickDatabase
import com.najmulcodes.zapflick.domain.queue.QueueConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProvidersModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        // A failure in one background job must be logged, not take the whole app down.
        val handler = CoroutineExceptionHandler { _, throwable ->
            Log.e("ZapFlick", "Unhandled background failure", throwable)
        }
        return CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    }

    @Provides
    fun provideQueueConfig(): QueueConfig = QueueConfig()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZapFlickDatabase =
        Room.databaseBuilder(context, ZapFlickDatabase::class.java, "zapflick.db").build()

    @Provides
    fun provideDownloadDao(database: ZapFlickDatabase): DownloadDao = database.downloadDao()
}
