package com.najmulcodes.zapflick.domain.library

import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.model.DownloadStatus

/** Splits the one download list into what each screen shows. */
object DownloadLists {
    /** Progress: everything not finished successfully (waiting, running, paused, failed, cancelled). */
    fun inProgress(rows: List<DownloadRow>): List<DownloadRow> =
        rows.filter { it.item.status != DownloadStatus.COMPLETED && !it.item.isPrivate }

    /** Finished: completed downloads that are not in the private folder. */
    fun finished(rows: List<DownloadRow>): List<DownloadRow> =
        rows.filter { it.item.status == DownloadStatus.COMPLETED && !it.item.isPrivate }

    fun privateItems(rows: List<DownloadRow>): List<DownloadRow> =
        rows.filter { it.item.status == DownloadStatus.COMPLETED && it.item.isPrivate }
}

/** Which items are ticked in selection mode. */
data class SelectionState(val selected: Set<Long> = emptySet()) {
    val isSelecting: Boolean get() = selected.isNotEmpty()
    val count: Int get() = selected.size

    fun toggle(id: Long): SelectionState =
        SelectionState(if (id in selected) selected - id else selected + id)

    fun selectAll(ids: Collection<Long>): SelectionState = SelectionState(ids.toSet())

    fun clear(): SelectionState = SelectionState()

    /** Forgets ticked items that are no longer in the list (deleted or moved elsewhere). */
    fun retainOnly(visible: Collection<Long>): SelectionState {
        val kept = selected.intersect(visible.toSet())
        return if (kept.size == selected.size) this else SelectionState(kept)
    }

    fun isAllSelected(ids: Collection<Long>): Boolean = ids.isNotEmpty() && selected.containsAll(ids)
}

/** Used and total space for the footer of the Finished screen. */
data class StorageSummary(val usedBytes: Long, val totalBytes: Long) {
    /** 0 to 1, for a progress bar. */
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)

    companion object {
        fun of(totalBytes: Long, availableBytes: Long): StorageSummary {
            val total = totalBytes.coerceAtLeast(0L)
            val used = (total - availableBytes.coerceIn(0L, total)).coerceAtLeast(0L)
            return StorageSummary(used, total)
        }
    }
}

enum class OemFamily { VIVO, TECNO_INFINIX, XIAOMI, SAMSUNG, OTHER }

object OemGuide {
    fun familyOf(manufacturer: String?): OemFamily {
        val name = manufacturer?.trim()?.lowercase().orEmpty()
        return when {
            "vivo" in name || "iqoo" in name -> OemFamily.VIVO
            "tecno" in name || "infinix" in name || "itel" in name || "transsion" in name -> OemFamily.TECNO_INFINIX
            "xiaomi" in name || "redmi" in name || "poco" in name -> OemFamily.XIAOMI
            "samsung" in name -> OemFamily.SAMSUNG
            else -> OemFamily.OTHER
        }
    }
}
