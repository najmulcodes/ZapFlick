package com.najmulcodes.zapflick.di

import android.util.Log
import com.najmulcodes.zapflick.data.db.DownloadDao
import com.najmulcodes.zapflick.data.db.ZapFlickDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    fun provideDownloadDao(database: ZapFlickDatabase): DownloadDao = database.downloadDao()
}
