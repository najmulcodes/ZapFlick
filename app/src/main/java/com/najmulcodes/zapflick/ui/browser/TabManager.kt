package com.najmulcodes.zapflick.ui.browser

import android.os.Bundle
import com.najmulcodes.zapflick.data.browser.TabsStore
import com.najmulcodes.zapflick.di.ApplicationScope
import com.najmulcodes.zapflick.domain.browser.TabList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the open tabs. Only the tab on screen has a live WebView; every other tab is just its
 * entry in [state] plus a saved page state ([Bundle]) kept here in memory. The list itself (where
 * each tab was) is written to disk, so tabs survive the app being killed; page history does not.
 */
@OptIn(FlowPreview::class)
@Singleton
class TabManager @Inject constructor(
    private val store: TabsStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(TabList.fresh())
    val state: StateFlow<TabList> = _state.asStateFlow()

    private val pageStates = ConcurrentHashMap<Long, Bundle>()

    init {
        scope.launch {
            // Do not overwrite anything the person already did while the saved list was loading.
            store.load()?.let { loaded ->
                if (_state.value == TabList.fresh()) _state.value = loaded
            }
            _state.debounce(SAVE_DELAY_MS).collect { store.save(it) }
        }
    }

    /** False when 20 tabs are already open. */
    fun openNewTab(url: String = "", title: String = ""): Boolean {
        val current = _state.value
        if (!current.canAdd) return false
        _state.value = current.add(url, title)
        return true
    }

    fun select(id: Long) {
        _state.value = _state.value.select(id)
    }

    fun close(id: Long) {
        pageStates.remove(id)
        _state.value = _state.value.close(id)
    }

    fun closeAll() {
        pageStates.clear()
        _state.value = _state.value.closeAll()
    }

    fun update(id: Long, url: String? = null, title: String? = null) {
        _state.value = _state.value.update(id, url, title)
    }

    fun putPageState(id: Long, bundle: Bundle) {
        pageStates[id] = bundle
    }

    fun pageState(id: Long): Bundle? = pageStates[id]

    private companion object {
        const val SAVE_DELAY_MS = 500L
    }
}
