package com.najmulcodes.zapflick.di

import com.najmulcodes.zapflick.data.repository.FilesDirWorkDirs
import com.najmulcodes.zapflick.data.network.SettingsQueueConfig
import com.najmulcodes.zapflick.data.repository.AndroidMediaFiles
import com.najmulcodes.zapflick.data.repository.SettingsAwareMediaSaver
import com.najmulcodes.zapflick.data.security.DataStorePinStore
import com.najmulcodes.zapflick.data.settings.DataStoreSettingsRepository
import com.najmulcodes.zapflick.data.repository.RoomDownloadRepository
import com.najmulcodes.zapflick.data.ytdlp.YoutubeDlEngine
import com.najmulcodes.zapflick.domain.engine.DownloadEngine
import com.najmulcodes.zapflick.domain.engine.YtDlpUpdater
import com.najmulcodes.zapflick.domain.queue.QueueConfig
import com.najmulcodes.zapflick.domain.repository.MediaFiles
import com.najmulcodes.zapflick.domain.repository.PrivateItemsStore
import com.najmulcodes.zapflick.domain.security.PinStore
import com.najmulcodes.zapflick.domain.settings.SettingsRepository
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import com.najmulcodes.zapflick.domain.queue.DownloadQueueManager
import com.najmulcodes.zapflick.domain.queue.QueueHost
import com.najmulcodes.zapflick.domain.queue.WorkDirProvider
import com.najmulcodes.zapflick.domain.repository.DownloadRepository
import com.najmulcodes.zapflick.domain.repository.MediaSaver
import com.najmulcodes.zapflick.service.AndroidQueueHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindDownloadEngine(impl: YoutubeDlEngine): DownloadEngine

    @Binds
    abstract fun bindDownloadRepository(impl: RoomDownloadRepository): DownloadRepository

    @Binds
    abstract fun bindDownloadQueue(impl: DownloadQueueManager): DownloadQueue

    @Binds
    abstract fun bindYtDlpUpdater(impl: YoutubeDlEngine): YtDlpUpdater

    // Where a finished file goes depends on Settings: gallery, a chosen folder, or app storage.
    @Binds
    abstract fun bindMediaSaver(impl: SettingsAwareMediaSaver): MediaSaver

    @Binds
    abstract fun bindMediaFiles(impl: AndroidMediaFiles): MediaFiles

    @Binds
    abstract fun bindPrivateItemsStore(impl: RoomDownloadRepository): PrivateItemsStore

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindQueueConfig(impl: SettingsQueueConfig): QueueConfig

    @Binds
    abstract fun bindPinStore(impl: DataStorePinStore): PinStore

    @Binds
    abstract fun bindQueueHost(impl: AndroidQueueHost): QueueHost

    @Binds
    abstract fun bindWorkDirProvider(impl: FilesDirWorkDirs): WorkDirProvider
}
