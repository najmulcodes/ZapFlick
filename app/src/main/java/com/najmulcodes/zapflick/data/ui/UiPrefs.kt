package com.najmulcodes.zapflick.data.ui

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small screen preferences that are not Settings: grid or list, and the private folder's "new" dot. */
@Singleton
class UiPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var finishedAsGrid: Boolean
        get() = prefs.getBoolean(KEY_GRID, false)
        set(value) = prefs.edit().putBoolean(KEY_GRID, value).apply()

    /** How many private items had been seen the last time the private folder was open. */
    var privateSeenCount: Int
        get() = prefs.getInt(KEY_PRIVATE_SEEN, 0)
        set(value) = prefs.edit().putInt(KEY_PRIVATE_SEEN, value).apply()

    private companion object {
        const val PREFS = "zapflick_ui"
        const val KEY_GRID = "finished_grid"
        const val KEY_PRIVATE_SEEN = "private_seen"
    }
}
