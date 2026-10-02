package com.najmulcodes.zapflick.domain.usecase

import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.model.FormatSelection
import com.najmulcodes.zapflick.domain.model.VideoMetadata
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StartDownloadUseCaseTest {

    private class RecordingQueue : DownloadQueue {
        val enqueued = mutableListOf<Pair<VideoMetadata, FormatSelection>>()

        override val downloads: Flow<List<DownloadRow>> = emptyFlow()

        override suspend fun enqueue(metadata: VideoMetadata, selection: FormatSelection): Long {
            enqueued += metadata to selection
            return enqueued.size.toLong()
        }

        override suspend fun pause(id: Long) = Unit
        override suspend fun resume(id: Long) = Unit
        override suspend fun cancel(id: Long) = Unit
        override suspend fun remove(id: Long) = Unit
        override suspend fun pauseAll() = Unit
        override suspend fun cancelAll() = Unit
        override suspend fun clearFinished() = Unit
        override suspend fun recover() = Unit
    }

    private val metadata = VideoMetadata(
        sourceUrl = "https://example.com/v",
        title = "Sample",
        thumbnailUrl = null,
        durationSeconds = 65,
        uploader = null,
    )

    @Test
    fun `defaults to best quality`() = runTest {
        val queue = RecordingQueue()

        val id = StartDownloadUseCase(queue)(metadata)

        assertEquals(1L, id)
        assertEquals(listOf(metadata to FormatSelection.Best), queue.enqueued)
    }

    @Test
    fun `passes the chosen quality through`() = runTest {
        val queue = RecordingQueue()

        StartDownloadUseCase(queue)(metadata, FormatSelection.Resolution(720))

        assertEquals(FormatSelection.Resolution(720), queue.enqueued.single().second)
    }
}
