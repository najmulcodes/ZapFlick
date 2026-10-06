package com.najmulcodes.zapflick.domain.usecase

import com.najmulcodes.zapflick.domain.model.DownloadItem
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import com.najmulcodes.zapflick.domain.repository.MediaFiles
import com.najmulcodes.zapflick.domain.repository.PrivateItemsStore
import javax.inject.Inject

data class BulkResult(val succeeded: Int, val failed: Int)

/**
 * Deletes finished downloads: the file first, then the list entry. A file that is already gone
 * counts as deleted. When a file cannot be removed the entry is kept, so nothing is left behind
 * that the person can no longer see.
 */
class DeleteFinishedUseCase @Inject constructor(
    private val files: MediaFiles,
    private val queue: DownloadQueue,
) {
    suspend operator fun invoke(items: List<DownloadItem>): BulkResult {
        var ok = 0
        var failed = 0
        for (item in items) {
            val saved = item.saved
            if (saved == null || files.delete(saved)) {
                queue.remove(item.id)
                ok++
            } else {
                failed++
            }
        }
        return BulkResult(ok, failed)
    }
}

class MoveToPrivateUseCase @Inject constructor(
    private val files: MediaFiles,
    private val store: PrivateItemsStore,
) {
    suspend operator fun invoke(items: List<DownloadItem>): BulkResult {
        var ok = 0
        var failed = 0
        for (item in items) {
            val saved = item.saved
            if (saved == null || item.isPrivate) {
                failed++
                continue
            }
            val moved = files.moveToPrivate(saved).getOrNull()
            if (moved == null) {
                failed++
            } else {
                store.setPrivate(item.id, isPrivate = true, media = moved)
                ok++
            }
        }
        return BulkResult(ok, failed)
    }
}

class RestoreFromPrivateUseCase @Inject constructor(
    private val files: MediaFiles,
    private val store: PrivateItemsStore,
) {
    suspend operator fun invoke(items: List<DownloadItem>): BulkResult {
        var ok = 0
        var failed = 0
        for (item in items) {
            val saved = item.saved
            if (saved == null || !item.isPrivate) {
                failed++
                continue
            }
            val restored = files.restoreFromPrivate(saved).getOrNull()
            if (restored == null) {
                failed++
            } else {
                store.setPrivate(item.id, isPrivate = false, media = restored)
                ok++
            }
        }
        return BulkResult(ok, failed)
    }
}
