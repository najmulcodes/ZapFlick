package com.najmulcodes.zapflick.domain.library

import com.najmulcodes.zapflick.domain.model.DownloadItem
import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.model.DownloadStatus
import com.najmulcodes.zapflick.domain.model.FormatSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {

    private fun row(id: Long, status: DownloadStatus, private: Boolean = false) = DownloadRow(
        DownloadItem(
            id = id, url = "u$id", title = "t$id", thumbnailUrl = null, uploader = null, durationSeconds = null,
            selection = FormatSelection.Best, status = status, error = null, saved = null,
            createdAt = id, updatedAt = id, isPrivate = private,
        ),
        progress = null,
    )

    private val rows = listOf(
        row(1, DownloadStatus.RUNNING),
        row(2, DownloadStatus.COMPLETED),
        row(3, DownloadStatus.FAILED),
        row(4, DownloadStatus.COMPLETED, private = true),
        row(5, DownloadStatus.QUEUED),
        row(6, DownloadStatus.CANCELLED),
        row(7, DownloadStatus.PAUSED),
    )

    @Test
    fun `progress holds everything not finished successfully`() {
        assertEquals(listOf(1L, 3L, 5L, 6L, 7L), DownloadLists.inProgress(rows).map { it.item.id })
    }

    @Test
    fun `finished holds public completed items only`() {
        assertEquals(listOf(2L), DownloadLists.finished(rows).map { it.item.id })
    }

    @Test
    fun `private holds completed private items only`() {
        assertEquals(listOf(4L), DownloadLists.privateItems(rows).map { it.item.id })
    }

    @Test
    fun `a private item never shows in progress or finished`() {
        val all = DownloadLists.inProgress(rows) + DownloadLists.finished(rows)
        assertFalse(all.any { it.item.id == 4L })
    }

    @Test
    fun `selection toggles, selects all and clears`() {
        var s = SelectionState()
        assertFalse(s.isSelecting)
        s = s.toggle(1).toggle(2)
        assertEquals(2, s.count)
        assertTrue(s.isSelecting)
        s = s.toggle(1)
        assertEquals(setOf(2L), s.selected)
        s = s.selectAll(listOf(1, 2, 3))
        assertTrue(s.isAllSelected(listOf(1, 2, 3)))
        assertEquals(SelectionState(), s.clear())
    }

    @Test
    fun `select all is not true for an empty list`() {
        assertFalse(SelectionState().isAllSelected(emptyList()))
    }

    @Test
    fun `selection forgets items that left the list`() {
        val s = SelectionState(setOf(1, 2, 3)).retainOnly(listOf(2, 3, 4))
        assertEquals(setOf(2L, 3L), s.selected)
        val same = SelectionState(setOf(2)).retainOnly(listOf(2, 3))
        assertEquals(setOf(2L), same.selected)
    }

    @Test
    fun `storage summary computes used space and a fraction`() {
        val gb = 1024L * 1024 * 1024
        val s = StorageSummary.of(totalBytes = 238 * gb, availableBytes = 79 * gb)
        assertEquals(159 * gb, s.usedBytes)
        assertEquals(238 * gb, s.totalBytes)
        assertEquals(159f / 238f, s.fraction, 0.0001f)
    }

    @Test
    fun `storage summary survives odd numbers`() {
        assertEquals(0f, StorageSummary.of(0, 0).fraction, 0f)
        assertEquals(StorageSummary(0, 100), StorageSummary.of(100, 500))
        assertEquals(StorageSummary(100, 100), StorageSummary.of(100, -5))
        assertEquals(StorageSummary(0, 0), StorageSummary.of(-1, -1))
    }

    @Test
    fun `phone makers map to a setup guide`() {
        assertEquals(OemFamily.VIVO, OemGuide.familyOf("vivo"))
        assertEquals(OemFamily.VIVO, OemGuide.familyOf("iQOO"))
        assertEquals(OemFamily.TECNO_INFINIX, OemGuide.familyOf("TECNO"))
        assertEquals(OemFamily.TECNO_INFINIX, OemGuide.familyOf("Infinix"))
        assertEquals(OemFamily.XIAOMI, OemGuide.familyOf("Xiaomi"))
        assertEquals(OemFamily.XIAOMI, OemGuide.familyOf("POCO"))
        assertEquals(OemFamily.SAMSUNG, OemGuide.familyOf("samsung"))
        assertEquals(OemFamily.OTHER, OemGuide.familyOf("Google"))
        assertEquals(OemFamily.OTHER, OemGuide.familyOf(null))
    }
}
