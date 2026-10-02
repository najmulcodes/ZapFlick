package com.najmulcodes.zapflick.di

import com.najmulcodes.zapflick.data.repository.FilesDirWorkDirs
import com.najmulcodes.zapflick.data.repository.MediaStoreSaver
import com.najmulcodes.zapflick.data.repository.RoomDownloadRepository
import com.najmulcodes.zapflick.data.ytdlp.YoutubeDlEngine
import com.najmulcodes.zapflick.domain.engine.DownloadEngine
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
    abstract fun bindMediaSaver(impl: MediaStoreSaver): MediaSaver

    @Binds
    abstract fun bindQueueHost(impl: AndroidQueueHost): QueueHost

    @Binds
    abstract fun bindWorkDirProvider(impl: FilesDirWorkDirs): WorkDirProvider
}
