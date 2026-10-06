package com.najmulcodes.zapflick.domain.queue

import com.najmulcodes.zapflick.di.ApplicationScope
import com.najmulcodes.zapflick.domain.engine.DownloadEngine
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.DownloadItem
import com.najmulcodes.zapflick.domain.model.DownloadProgress
import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.model.DownloadStatus
import com.najmulcodes.zapflick.domain.model.FormatSelection
import com.najmulcodes.zapflick.domain.model.SavedMedia
import com.najmulcodes.zapflick.domain.model.VideoMetadata
import com.najmulcodes.zapflick.domain.model.toDownloadError
import com.najmulcodes.zapflick.domain.repository.DownloadRepository
import com.najmulcodes.zapflick.domain.repository.MediaSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the queue: up to [QueueConfig.maxConcurrent] downloads at once, oldest first. The database
 * is the source of truth for each item's state; this class owns the coroutines that move items
 * between states.
 *
 * Every public operation runs in [scope], not in the caller's coroutine, so a screen that goes away
 * mid-call cannot leave an item half-updated. All state changes happen under [mutex].
 */
@Singleton
class DownloadQueueManager @Inject constructor(
    private val repository: DownloadRepository,
    private val engine: DownloadEngine,
    private val mediaSaver: MediaSaver,
    private val workDirs: WorkDirProvider,
    private val host: QueueHost,
    private val config: QueueConfig,
    @ApplicationScope private val scope: CoroutineScope,
) : DownloadQueue {

    /** Why a running download's coroutine was cancelled; decides what state it is left in. */
    private enum class StopReason { PAUSE, CANCEL, REMOVE }

    private class RunningDownload {
        lateinit var job: Job

        @Volatile
        var stop: StopReason? = null
    }

    private val mutex = Mutex()
    private val running = mutableMapOf<Long, RunningDownload>()
    private val progress = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())

    override val downloads: Flow<List<DownloadRow>> =
        combine(repository.observeAll(), progress) { items, progressById ->
            items.map { DownloadRow(item = it, progress = progressById[it.id]) }
        }

    override suspend fun enqueue(metadata: VideoMetadata, selection: FormatSelection): Long =
        scope.async {
            mutex.withLock {
                val id = repository.add(metadata, selection)
                fillSlots()
                id
            }
        }.await()

    override suspend fun pause(id: Long) = inScope { mutex.withLock { pauseLocked(id) } }

    override suspend fun resume(id: Long) = inScope { mutex.withLock { resumeLocked(id) } }

    override suspend fun cancel(id: Long) = inScope { mutex.withLock { cancelLocked(id) } }

    override suspend fun remove(id: Long) = inScope { mutex.withLock { removeLocked(id) } }

    override suspend fun pauseAll() = inScope { mutex.withLock { pauseAllLocked() } }

    override suspend fun cancelAll() = inScope { mutex.withLock { cancelAllLocked() } }

    override suspend fun clearFinished() = inScope {
        // Finished items are not running, so no lock is needed to drop their leftovers.
        repository.clearFinished().forEach { workDirs.delete(it) }
    }

    override suspend fun recover() = inScope {
        mutex.withLock {
            repository.requeueRunning()
            fillSlots()
        }
    }

    // --- operations; all of these run with [mutex] held ---

    private suspend fun pauseLocked(id: Long) {
        val handle = running[id]
        if (handle != null) {
            handle.stop = StopReason.PAUSE
            handle.job.cancel()
            return
        }
        if (repository.get(id)?.status == DownloadStatus.QUEUED) {
            repository.setStatus(id, DownloadStatus.PAUSED)
        }
    }

    private suspend fun resumeLocked(id: Long) {
        if (repository.requeue(id)) fillSlots()
    }

    private suspend fun cancelLocked(id: Long) {
        val handle = running[id]
        if (handle != null) {
            handle.stop = StopReason.CANCEL
            handle.job.cancel()
            return
        }
        val item = repository.get(id) ?: return
        if (item.status in CANCELLABLE) {
            repository.setStatus(id, DownloadStatus.CANCELLED)
            workDirs.delete(id)
        }
    }

    private suspend fun removeLocked(id: Long) {
        val handle = running[id]
        if (handle != null) {
            handle.stop = StopReason.REMOVE
            handle.job.cancel()
            return
        }
        repository.delete(id)
        workDirs.delete(id)
    }

    private suspend fun pauseAllLocked() {
        // Queued items first, so a slot freed by a pausing download cannot start one of them.
        repository.withStatus(DownloadStatus.QUEUED).forEach {
            repository.setStatus(it.id, DownloadStatus.PAUSED)
        }
        running.values.forEach {
            it.stop = StopReason.PAUSE
            it.job.cancel()
        }
    }

    private suspend fun cancelAllLocked() {
        repository.withStatus(DownloadStatus.QUEUED, DownloadStatus.PAUSED).forEach {
            repository.setStatus(it.id, DownloadStatus.CANCELLED)
            workDirs.delete(it.id)
        }
        running.values.forEach {
            it.stop = StopReason.CANCEL
            it.job.cancel()
        }
    }

    /** Starts whatever is waiting if there is room now: call after a limit or the Wi-Fi gate changed. */
    suspend fun refill() {
        mutex.withLock { fillSlots() }
    }

    private suspend fun fillSlots() {
        pumpLocked()
        if (running.isNotEmpty()) host.ensureServiceRunning()
    }

    /** Starts queued items until every slot is busy. */
    private suspend fun pumpLocked() {
        if (!config.canStart) return
        val free = config.maxConcurrent - running.size
        if (free <= 0) return
        for (item in repository.nextQueued(free)) {
            repository.setStatus(item.id, DownloadStatus.RUNNING)
            val handle = RunningDownload()
            handle.job = scope.launch { execute(item, handle) }
            running[item.id] = handle
        }
    }

    // --- one download's life ---

    private suspend fun execute(item: DownloadItem, handle: RunningDownload) {
        val workDir = workDirs.dirFor(item.id)
        var settled = false
        try {
            val downloaded: Result<File> = try {
                runEngine(item, workDir)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            // From here the file exists. A pause or cancel arriving now must not interrupt the save
            // halfway, or a later resume would leave a duplicate behind.
            withContext(NonCancellable) {
                val outcome = downloaded.fold(
                    onSuccess = { file -> mediaSaver.save(file) },
                    onFailure = { error -> Result.failure<SavedMedia>(error) },
                )
                settle(item, outcome)
                settled = true
            }
        } catch (e: CancellationException) {
            if (!settled) {
                withContext(NonCancellable) { settleStopped(item.id, handle.stop) }
            }
            throw e
        } finally {
            withContext(NonCancellable) { release(item.id) }
        }
    }

    private suspend fun runEngine(item: DownloadItem, workDir: File): Result<File> = coroutineScope {
        // The engine reports progress from a blocking thread; a conflated channel drops stale updates.
        val updates = Channel<DownloadProgress>(Channel.CONFLATED)
        launch { for (update in updates) publishProgress(item.id, update) }
        var lastEmit: Long? = null
        try {
            engine.download(item.toRequest(), workDir) { update ->
                val now = System.nanoTime() / NANOS_PER_MILLI
                val previous = lastEmit
                if (update.percent >= 100f || previous == null || now - previous >= PROGRESS_INTERVAL_MS) {
                    lastEmit = now
                    updates.trySend(update)
                }
            }
        } finally {
            updates.close()
        }
    }

    private suspend fun settle(item: DownloadItem, result: Result<SavedMedia>) {
        val media = result.getOrNull()
        if (media != null) {
            repository.markCompleted(item.id, media)
            workDirs.delete(item.id)
            host.onFinished(item, success = true)
            return
        }
        val error = result.exceptionOrNull()?.toDownloadError() ?: DownloadError.Unknown()
        if (error == DownloadError.Cancelled) {
            repository.setStatus(item.id, DownloadStatus.CANCELLED)
            workDirs.delete(item.id)
            return
        }
        repository.markFailed(item.id, error)
        // After a network failure the partial files are worth keeping: retry resumes from them.
        if (error != DownloadError.NetworkError) workDirs.delete(item.id)
        host.onFinished(item, success = false)
    }

    private suspend fun settleStopped(id: Long, reason: StopReason?) {
        when (reason) {
            StopReason.PAUSE -> repository.setStatus(id, DownloadStatus.PAUSED)
            StopReason.CANCEL -> {
                repository.setStatus(id, DownloadStatus.CANCELLED)
                workDirs.delete(id)
            }
            StopReason.REMOVE -> {
                repository.delete(id)
                workDirs.delete(id)
            }
            // Cancelled by something other than the user: put it back in line.
            null -> repository.setStatus(id, DownloadStatus.QUEUED)
        }
    }

    private suspend fun release(id: Long) {
        mutex.withLock {
            running.remove(id)
            progress.update { it - id }
            pumpLocked()
        }
    }

    private fun publishProgress(id: Long, update: DownloadProgress) {
        progress.update { it + (id to update) }
    }

    private suspend fun inScope(block: suspend () -> Unit) {
        scope.async { block() }.await()
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 700L
        const val NANOS_PER_MILLI = 1_000_000L
        val CANCELLABLE = setOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.FAILED)
    }
}
