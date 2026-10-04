package com.najmulcodes.zapflick.domain.queue

import com.najmulcodes.zapflick.domain.engine.DownloadEngine
import com.najmulcodes.zapflick.domain.model.AvailableFormats
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.DownloadException
import com.najmulcodes.zapflick.domain.model.DownloadItem
import com.najmulcodes.zapflick.domain.model.DownloadProgress
import com.najmulcodes.zapflick.domain.model.DownloadRequest
import com.najmulcodes.zapflick.domain.model.DownloadStatus
import com.najmulcodes.zapflick.domain.model.FormatSelection
import com.najmulcodes.zapflick.domain.model.SavedMedia
import com.najmulcodes.zapflick.domain.model.VideoMetadata
import com.najmulcodes.zapflick.domain.repository.DownloadRepository
import com.najmulcodes.zapflick.domain.repository.MediaSaver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueManagerTest {

    // --- fakes ---

    private class FakeRepository : DownloadRepository {
        private val items = MutableStateFlow<List<DownloadItem>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<DownloadItem>> =
            items.map { list -> list.sortedByDescending { it.id } }

        override suspend fun get(id: Long): DownloadItem? = items.value.firstOrNull { it.id == id }

        override suspend fun add(metadata: VideoMetadata, selection: FormatSelection): Long {
            val id = nextId++
            val item = DownloadItem(
                id = id,
                url = metadata.sourceUrl,
                title = metadata.title,
                thumbnailUrl = null,
                uploader = null,
                durationSeconds = null,
                selection = selection,
                status = DownloadStatus.QUEUED,
                error = null,
                saved = null,
                createdAt = id,
                updatedAt = id,
            )
            items.update { it + item }
            return id
        }

        override suspend fun nextQueued(limit: Int): List<DownloadItem> =
            items.value.filter { it.status == DownloadStatus.QUEUED }.sortedBy { it.id }.take(limit)

        override suspend fun withStatus(vararg statuses: DownloadStatus): List<DownloadItem> =
            items.value.filter { it.status in statuses }

        override suspend fun setStatus(id: Long, status: DownloadStatus) {
            change(id) { it.copy(status = status) }
        }

        override suspend fun requeue(id: Long): Boolean {
            val item = get(id) ?: return false
            val allowed = setOf(DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)
            if (item.status !in allowed) return false
            change(id) { it.copy(status = DownloadStatus.QUEUED, error = null) }
            return true
        }

        override suspend fun markCompleted(id: Long, media: SavedMedia) {
            change(id) { it.copy(status = DownloadStatus.COMPLETED, saved = media) }
        }

        override suspend fun markFailed(id: Long, error: DownloadError) {
            change(id) { it.copy(status = DownloadStatus.FAILED, error = error) }
        }

        override suspend fun requeueRunning() {
            items.update { list ->
                list.map { if (it.status == DownloadStatus.RUNNING) it.copy(status = DownloadStatus.QUEUED) else it }
            }
        }

        override suspend fun delete(id: Long) {
            items.update { list -> list.filterNot { it.id == id } }
        }

        override suspend fun clearFinished(): List<Long> {
            val ids = items.value.filter { it.status.isFinished }.map { it.id }
            items.update { list -> list.filterNot { it.status.isFinished } }
            return ids
        }

        fun statusOf(id: Long): DownloadStatus = items.value.first { it.id == id }.status

        private fun change(id: Long, transform: (DownloadItem) -> DownloadItem) {
            items.update { list -> list.map { if (it.id == id) transform(it) else it } }
        }
    }

    /** Each download blocks until the test calls [finish] for its URL. */
    private class FakeEngine : DownloadEngine {
        val started = mutableListOf<String>()
        private val gates = mutableMapOf<String, MutableList<CompletableDeferred<Result<File>>>>()

        override suspend fun initialize() = Unit

        override suspend fun fetchMetadata(url: String): Result<VideoMetadata> =
            Result.failure(UnsupportedOperationException())

        override suspend fun fetchFormats(url: String): Result<AvailableFormats> =
            Result.failure(UnsupportedOperationException())

        override suspend fun download(
            request: DownloadRequest,
            workDir: File,
            onProgress: (DownloadProgress) -> Unit,
        ): Result<File> {
            val gate = CompletableDeferred<Result<File>>()
            gates.getOrPut(request.url) { mutableListOf() }.add(gate)
            started += request.url
            return gate.await()
        }

        /** Completes the most recent download of [url]. */
        fun finish(url: String, result: Result<File> = Result.success(File("$url.mp4"))) {
            gates.getValue(url).last().complete(result)
        }
    }

    private class FakeSaver : MediaSaver {
        override suspend fun save(file: File): Result<SavedMedia> = Result.success(
            SavedMedia(uri = "content://media/${file.name}", displayName = file.name, location = "Movies/ZapFlick"),
        )
    }

    private class FakeWorkDirs : WorkDirProvider {
        val deleted = mutableListOf<Long>()

        override fun dirFor(id: Long): File = File("work/$id")

        override fun delete(id: Long) {
            deleted += id
        }
    }

    private class FakeHost : QueueHost {
        var serviceStarts = 0
        val finished = mutableListOf<Pair<Long, Boolean>>()

        override fun ensureServiceRunning() {
            serviceStarts++
        }

        override fun onFinished(item: DownloadItem, success: Boolean) {
            finished += item.id to success
        }
    }

    private class Harness(scope: CoroutineScope, maxConcurrent: Int = 2) {
        val repo = FakeRepository()
        val engine = FakeEngine()
        val dirs = FakeWorkDirs()
        val host = FakeHost()
        val manager = DownloadQueueManager(
            repository = repo,
            engine = engine,
            mediaSaver = FakeSaver(),
            workDirs = dirs,
            host = host,
            config = QueueConfig(maxConcurrent),
            scope = scope,
        )
    }

    private fun meta(n: Int) = VideoMetadata(
        sourceUrl = "u$n",
        title = "t$n",
        thumbnailUrl = null,
        durationSeconds = null,
        uploader = null,
    )

    // --- tests ---
    //
    // The manager runs in runTest's backgroundScope, and advanceUntilIdle() deliberately does not
    // wait for background work: it returns at once when only background tasks are left. Calls such
    // as enqueue() or pause() still worked because they suspend until the manager's scope has done
    // its part, which lets the test scheduler run everything queued. A bare finish(...) followed
    // by advanceUntilIdle() ran nothing, so the download looked unfinished. runCurrent() runs
    // every task that is due now, background or not, and nothing here uses virtual time.

    @Test
    fun `runs at most maxConcurrent downloads and starts the next when one finishes`() = runTest {
        val h = Harness(backgroundScope, maxConcurrent = 2)
        val ids = (1..3).map { h.manager.enqueue(meta(it), FormatSelection.Best) }
        runCurrent()

        assertEquals(listOf("u1", "u2"), h.engine.started)
        assertEquals(DownloadStatus.QUEUED, h.repo.statusOf(ids[2]))

        h.engine.finish("u1")
        runCurrent()

        assertEquals(listOf("u1", "u2", "u3"), h.engine.started)
        assertEquals(DownloadStatus.COMPLETED, h.repo.statusOf(ids[0]))
        assertEquals(DownloadStatus.RUNNING, h.repo.statusOf(ids[2]))
    }

    @Test
    fun `asks the host to keep the service running while work exists`() = runTest {
        val h = Harness(backgroundScope)
        h.manager.enqueue(meta(1), FormatSelection.Best)
        runCurrent()

        assertTrue(h.host.serviceStarts >= 1)
    }

    @Test
    fun `pausing keeps partial data and resuming continues the same download`() = runTest {
        val h = Harness(backgroundScope)
        val id = h.manager.enqueue(meta(1), FormatSelection.Best)
        runCurrent()

        h.manager.pause(id)
        runCurrent()

        assertEquals(DownloadStatus.PAUSED, h.repo.statusOf(id))
        assertTrue(h.dirs.deleted.isEmpty())

        h.manager.resume(id)
        runCurrent()

        assertEquals(listOf("u1", "u1"), h.engine.started)

        h.engine.finish("u1")
        runCurrent()

        assertEquals(DownloadStatus.COMPLETED, h.repo.statusOf(id))
        assertEquals(listOf(id), h.dirs.deleted)
    }

    @Test
    fun `cancelling a running download discards its partial data`() = runTest {
        val h = Harness(backgroundScope)
        val id = h.manager.enqueue(meta(1), FormatSelection.Best)
        runCurrent()

        h.manager.cancel(id)
        runCurrent()

        assertEquals(DownloadStatus.CANCELLED, h.repo.statusOf(id))
        assertEquals(listOf(id), h.dirs.deleted)
    }

    @Test
    fun `cancelling a queued download means it never starts`() = runTest {
        val h = Harness(backgroundScope, maxConcurrent = 1)
        h.manager.enqueue(meta(1), FormatSelection.Best)
        val second = h.manager.enqueue(meta(2), FormatSelection.Best)
        runCurrent()

        h.manager.cancel(second)
        h.engine.finish("u1")
        runCurrent()

        assertEquals(DownloadStatus.CANCELLED, h.repo.statusOf(second))
        assertEquals(listOf("u1"), h.engine.started)
    }

    @Test
    fun `a failed download records the error and tells the host`() = runTest {
        val h = Harness(backgroundScope)
        val id = h.manager.enqueue(meta(1), FormatSelection.Best)
        runCurrent()

        h.engine.finish("u1", Result.failure(DownloadException(DownloadError.Unavailable)))
        runCurrent()

        assertEquals(DownloadStatus.FAILED, h.repo.statusOf(id))
        assertEquals(DownloadError.Unavailable, h.repo.get(id)?.error)
        assertEquals(listOf(id to false), h.host.finished)
        assertEquals(listOf(id), h.dirs.deleted)
    }

    @Test
    fun `a network failure keeps partial data so a retry can continue`() = runTest {
        val h = Harness(backgroundScope)
        val id = h.manager.enqueue(meta(1), FormatSelection.Best)
        runCurrent()

        h.engine.finish("u1", Result.failure(DownloadException(DownloadError.NetworkError)))
        runCurrent()

        assertEquals(DownloadStatus.FAILED, h.repo.statusOf(id))
        assertTrue(h.dirs.deleted.isEmpty())

        h.manager.resume(id)
        runCurrent()

        assertEquals(listOf("u1", "u1"), h.engine.started)
        assertNull(h.repo.get(id)?.error)
    }

    @Test
    fun `removing a running download cancels it and deletes the row`() = runTest {
        val h = Harness(backgroundScope)
        val id = h.manager.enqueue(meta(1), FormatSelection.Best)
        runCurrent()

        h.manager.remove(id)
        runCurrent()

        assertNull(h.repo.get(id))
        assertEquals(listOf(id), h.dirs.deleted)
    }

    @Test
    fun `pause all stops running and queued downloads without starting new ones`() = runTest {
        val h = Harness(backgroundScope, maxConcurrent = 2)
        val ids = (1..3).map { h.manager.enqueue(meta(it), FormatSelection.Best) }
        runCurrent()

        h.manager.pauseAll()
        runCurrent()

        ids.forEach { assertEquals(DownloadStatus.PAUSED, h.repo.statusOf(it)) }
        assertEquals(2, h.engine.started.size)
    }

    @Test
    fun `recover restarts downloads that were running when the process died`() = runTest {
        val h = Harness(backgroundScope)
        val id = h.repo.add(meta(1), FormatSelection.Best)
        h.repo.setStatus(id, DownloadStatus.RUNNING)

        h.manager.recover()
        runCurrent()

        assertEquals(listOf("u1"), h.engine.started)
        assertEquals(DownloadStatus.RUNNING, h.repo.statusOf(id))
    }
}
