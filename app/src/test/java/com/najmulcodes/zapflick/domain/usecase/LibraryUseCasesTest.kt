package com.najmulcodes.zapflick.domain.usecase

import com.najmulcodes.zapflick.domain.model.DownloadItem
import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.model.DownloadStatus
import com.najmulcodes.zapflick.domain.model.FormatSelection
import com.najmulcodes.zapflick.domain.model.SavedMedia
import com.najmulcodes.zapflick.domain.model.VideoMetadata
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import com.najmulcodes.zapflick.domain.repository.MediaFiles
import com.najmulcodes.zapflick.domain.repository.PrivateItemsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryUseCasesTest {

    private fun item(id: Long, uri: String? = "content://m/$id", isPrivate: Boolean = false) = DownloadItem(
        id = id, url = "u$id", title = "t$id", thumbnailUrl = null, uploader = null, durationSeconds = null,
        selection = FormatSelection.Best, status = DownloadStatus.COMPLETED, error = null,
        saved = uri?.let { SavedMedia(it, "t$id.mp4", "Movies/ZapFlick") },
        createdAt = id, updatedAt = id, isPrivate = isPrivate,
    )

    private class FakeFiles : MediaFiles {
        val deleteResult = HashMap<String, Boolean>()
        val deleted = ArrayList<String>()
        val moveFails = HashSet<String>()
        override suspend fun delete(media: SavedMedia): Boolean {
            deleted += media.uri
            return deleteResult[media.uri] ?: true
        }
        override suspend fun moveToPrivate(media: SavedMedia): Result<SavedMedia> =
            if (media.uri in moveFails) Result.failure(java.io.IOException("no")) else Result.success(SavedMedia("file:///p/${media.displayName}", media.displayName, "Private folder"))
        override suspend fun restoreFromPrivate(media: SavedMedia): Result<SavedMedia> =
            if (media.uri in moveFails) Result.failure(java.io.IOException("no")) else Result.success(SavedMedia("content://m/restored", media.displayName, "Movies/ZapFlick"))
    }

    private class FakeStore : PrivateItemsStore {
        val calls = ArrayList<Triple<Long, Boolean, String>>()
        override suspend fun setPrivate(id: Long, isPrivate: Boolean, media: SavedMedia) {
            calls += Triple(id, isPrivate, media.uri)
        }
    }

    private class FakeQueue : DownloadQueue {
        val removed = ArrayList<Long>()
        override val downloads: Flow<List<DownloadRow>> = MutableStateFlow(emptyList())
        override suspend fun enqueue(metadata: VideoMetadata, selection: FormatSelection) = 0L
        override suspend fun pause(id: Long) = Unit
        override suspend fun resume(id: Long) = Unit
        override suspend fun cancel(id: Long) = Unit
        override suspend fun remove(id: Long) { removed += id }
        override suspend fun pauseAll() = Unit
        override suspend fun cancelAll() = Unit
        override suspend fun clearFinished() = Unit
        override suspend fun recover() = Unit
    }

    @Test
    fun `delete removes the file and then the entry`() = runBlocking {
        val files = FakeFiles()
        val queue = FakeQueue()
        val result = DeleteFinishedUseCase(files, queue)(listOf(item(1), item(2)))
        assertEquals(BulkResult(2, 0), result)
        assertEquals(listOf("content://m/1", "content://m/2"), files.deleted)
        assertEquals(listOf(1L, 2L), queue.removed)
    }

    @Test
    fun `a file that cannot be deleted keeps its entry`() = runBlocking {
        val files = FakeFiles().apply { deleteResult["content://m/2"] = false }
        val queue = FakeQueue()
        val result = DeleteFinishedUseCase(files, queue)(listOf(item(1), item(2), item(3)))
        assertEquals(BulkResult(2, 1), result)
        assertEquals(listOf(1L, 3L), queue.removed)
    }

    @Test
    fun `an item with no saved file is just removed from the list`() = runBlocking {
        val files = FakeFiles()
        val queue = FakeQueue()
        val result = DeleteFinishedUseCase(files, queue)(listOf(item(5, uri = null)))
        assertEquals(BulkResult(1, 0), result)
        assertTrue(files.deleted.isEmpty())
        assertEquals(listOf(5L), queue.removed)
    }

    @Test
    fun `moving to private updates the entry to the new file`() = runBlocking {
        val store = FakeStore()
        val result = MoveToPrivateUseCase(FakeFiles(), store)(listOf(item(1), item(2)))
        assertEquals(BulkResult(2, 0), result)
        assertEquals(Triple(1L, true, "file:///p/t1.mp4"), store.calls[0])
    }

    @Test
    fun `a failed move leaves the entry alone and carries on`() = runBlocking {
        val files = FakeFiles().apply { moveFails += "content://m/1" }
        val store = FakeStore()
        val result = MoveToPrivateUseCase(files, store)(listOf(item(1), item(2)))
        assertEquals(BulkResult(1, 1), result)
        assertEquals(listOf(2L), store.calls.map { it.first })
    }

    @Test
    fun `items already private or without a file are not moved`() = runBlocking {
        val store = FakeStore()
        val result = MoveToPrivateUseCase(FakeFiles(), store)(listOf(item(1, isPrivate = true), item(2, uri = null)))
        assertEquals(BulkResult(0, 2), result)
        assertTrue(store.calls.isEmpty())
    }

    @Test
    fun `restoring makes the entry public again`() = runBlocking {
        val store = FakeStore()
        val result = RestoreFromPrivateUseCase(FakeFiles(), store)(listOf(item(1, "file:///p/x", isPrivate = true)))
        assertEquals(BulkResult(1, 0), result)
        assertEquals(Triple(1L, false, "content://m/restored"), store.calls.single())
    }

    @Test
    fun `restoring skips public items and survives a failure`() = runBlocking {
        val files = FakeFiles().apply { moveFails += "file:///p/bad" }
        val store = FakeStore()
        val result = RestoreFromPrivateUseCase(files, store)(
            listOf(item(1), item(2, "file:///p/bad", isPrivate = true), item(3, "file:///p/ok", isPrivate = true)),
        )
        assertEquals(BulkResult(1, 2), result)
        assertEquals(listOf(3L), store.calls.map { it.first })
    }
}
