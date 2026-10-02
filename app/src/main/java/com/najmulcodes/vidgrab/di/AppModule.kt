package com.najmulcodes.vidgrab.di

import com.najmulcodes.vidgrab.data.repository.FilesDirWorkDirs
import com.najmulcodes.vidgrab.data.repository.MediaStoreSaver
import com.najmulcodes.vidgrab.data.repository.RoomDownloadRepository
import com.najmulcodes.vidgrab.data.ytdlp.YoutubeDlEngine
import com.najmulcodes.vidgrab.domain.engine.DownloadEngine
import com.najmulcodes.vidgrab.domain.queue.DownloadQueue
import com.najmulcodes.vidgrab.domain.queue.DownloadQueueManager
import com.najmulcodes.vidgrab.domain.queue.QueueHost
import com.najmulcodes.vidgrab.domain.queue.WorkDirProvider
import com.najmulcodes.vidgrab.domain.repository.DownloadRepository
import com.najmulcodes.vidgrab.domain.repository.MediaSaver
import com.najmulcodes.vidgrab.service.AndroidQueueHost
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
