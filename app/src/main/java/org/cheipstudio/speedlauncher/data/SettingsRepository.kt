package org.cheipstudio.speedlauncher.data

import android.content.Context
import androidx.lifecycle.MutableLiveData

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("speed_settings", Context.MODE_PRIVATE)

    private val homeLayoutPrefs = context.applicationContext
        .getSharedPreferences("speed_home_layout", Context.MODE_PRIVATE)

    val gridCols = MutableLiveData(prefs.getInt(KEY_COLS, 4))
    val gridRows = MutableLiveData(prefs.getInt(KEY_ROWS, 4))
    val showWidgetSlot = MutableLiveData(prefs.getBoolean(KEY_SHOW_WIDGETS, true))
    val showSearchBar = MutableLiveData(prefs.getBoolean(KEY_SHOW_SEARCH, true))
    val hapticEnabled = MutableLiveData(prefs.getBoolean(KEY_HAPTIC, true))
    val tutorialSeen = MutableLiveData(prefs.getBoolean(KEY_TUTORIAL_SEEN, false))
    val searchMode = MutableLiveData(prefs.getString(KEY_SEARCH_MODE, MODE_APPS) ?: MODE_APPS)
    val showDock = MutableLiveData(false)

    fun setGrid(cols: Int, rows: Int) {
        prefs.edit().putInt(KEY_COLS, cols).putInt(KEY_ROWS, rows).apply()
        gridCols.postValue(cols); gridRows.postValue(rows)
    }
    fun setShowWidgetSlot(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WIDGETS, show).apply(); showWidgetSlot.postValue(show)
    }
    fun setShowSearchBar(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SEARCH, show).apply(); showSearchBar.postValue(show)
    }
    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply(); hapticEnabled.postValue(enabled)
    }
    fun setSearchMode(mode: String) {
        prefs.edit().putString(KEY_SEARCH_MODE, mode).apply(); searchMode.postValue(mode)
    }
    fun markTutorialSeen() {
        prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, true).apply(); tutorialSeen.postValue(true)
    }
    fun resetTutorial() {
        prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, false).apply(); tutorialSeen.postValue(false)
    }
    fun resetHomeLayout() {
        homeLayoutPrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_COLS = "grid_cols"
        private const val KEY_ROWS = "grid_rows"
        private const val KEY_SHOW_WIDGETS = "show_widgets"
        private const val KEY_SHOW_SEARCH = "show_search"
        private const val KEY_HAPTIC = "haptic_enabled"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
        private const val KEY_SEARCH_MODE = "search_mode"
        const val MODE_APPS = "apps"
        const val MODE_GOOGLE = "google"
    }
}
