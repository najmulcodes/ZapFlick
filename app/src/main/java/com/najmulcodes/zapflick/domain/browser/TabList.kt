package com.najmulcodes.zapflick.domain.browser

import java.net.URLDecoder
import java.net.URLEncoder

/** One browser tab. A blank [url] is the "new tab" page. */
data class TabInfo(val id: Long, val title: String, val url: String) {
    val isNewTabPage: Boolean get() = url.isBlank()
}

/**
 * The open tabs, as plain data: at most [MAX_TABS], always at least one, exactly one active.
 * Every change returns a new list.
 */
data class TabList(
    val tabs: List<TabInfo>,
    val activeId: Long,
    private val nextId: Long,
) {
    val active: TabInfo get() = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    val canAdd: Boolean get() = tabs.size < MAX_TABS

    /** Adds a tab and makes it the active one. Returns this list unchanged when it is full. */
    fun add(url: String = "", title: String = ""): TabList {
        if (!canAdd) return this
        val tab = TabInfo(nextId, title, url)
        return copy(tabs = tabs + tab, activeId = tab.id, nextId = nextId + 1)
    }

    fun select(id: Long): TabList = if (tabs.any { it.id == id }) copy(activeId = id) else this

    /** Closing the active tab selects its neighbour; closing the last tab leaves one empty tab. */
    fun close(id: Long): TabList {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return this
        val remaining = tabs.filterNot { it.id == id }
        if (remaining.isEmpty()) return fresh(nextId)
        val newActive = if (id == activeId) remaining[index.coerceAtMost(remaining.lastIndex)].id else activeId
        return copy(tabs = remaining, activeId = newActive)
    }

    fun closeAll(): TabList = fresh(nextId)

    fun update(id: Long, url: String? = null, title: String? = null): TabList =
        copy(
            tabs = tabs.map { tab ->
                if (tab.id == id) tab.copy(url = url ?: tab.url, title = title ?: tab.title) else tab
            },
        )

    companion object {
        const val MAX_TABS = 20

        fun fresh(startId: Long = 1L): TabList =
            TabList(listOf(TabInfo(startId, "", "")), activeId = startId, nextId = startId + 1)
    }
}

/**
 * Saves the tab list as text (id, url, title per tab) so it survives the app being killed. Page
 * history is not saved, only where each tab was. Fields are URL-encoded, so tabs and line breaks
 * inside a title cannot break the format.
 */
object TabListCodec {
    private const val HEADER = "tabs1"

    fun encode(list: TabList): String = buildString {
        append(HEADER).append('|').append(list.activeId).append('|').append(list.tabs.maxOf { it.id } + 1).append('\n')
        for (tab in list.tabs) {
            append(tab.id).append('\t').append(enc(tab.url)).append('\t').append(enc(tab.title)).append('\n')
        }
    }

    /** Null when the text is empty or damaged: the caller then starts with one empty tab. */
    fun decode(text: String?): TabList? {
        if (text.isNullOrBlank()) return null
        return try {
            val lines = text.lines().filter { it.isNotEmpty() }
            val header = lines.first().split('|')
            if (header.size != 3 || header[0] != HEADER) return null
            val activeId = header[1].toLong()
            val nextId = header[2].toLong()
            val tabs = lines.drop(1).take(TabList.MAX_TABS).map { line ->
                val parts = line.split('\t')
                if (parts.size != 3) return null
                TabInfo(parts[0].toLong(), dec(parts[2]), dec(parts[1]))
            }
            if (tabs.isEmpty() || tabs.map { it.id }.toSet().size != tabs.size) return null
            val active = if (tabs.any { it.id == activeId }) activeId else tabs.first().id
            TabList(tabs, active, maxOf(nextId, tabs.maxOf { it.id } + 1))
        } catch (e: NumberFormatException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun dec(value: String): String = URLDecoder.decode(value, "UTF-8")
}
