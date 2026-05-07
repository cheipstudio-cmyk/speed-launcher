package org.cheipstudio.speedlauncher.data

import android.content.Context
import androidx.lifecycle.MutableLiveData

class SettingsRepository(context: Context) {

    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("speed_settings", Context.MODE_PRIVATE)
    private val homeLayoutPrefs = ctx.getSharedPreferences("speed_home_layout", Context.MODE_PRIVATE)

    val gridCols = MutableLiveData(prefs.getInt(KEY_COLS, 4))
    val gridRows = MutableLiveData(prefs.getInt(KEY_ROWS, 4))
    val showWidgetSlot = MutableLiveData(prefs.getBoolean(KEY_SHOW_WIDGETS, true))
    val hapticEnabled = MutableLiveData(prefs.getBoolean(KEY_HAPTIC, true))
    val tutorialSeen = MutableLiveData(prefs.getBoolean(KEY_TUTORIAL_SEEN, false))
    val searchMode = MutableLiveData(prefs.getString(KEY_SEARCH_MODE, MODE_APPS) ?: MODE_APPS)
    val searchBarStyle = MutableLiveData(prefs.getString(KEY_SEARCH_STYLE, STYLE_SYSTEM) ?: STYLE_SYSTEM)
    val swipeDownNotifications = MutableLiveData(prefs.getBoolean(KEY_SWIPE_DOWN, true))
    val showDock = MutableLiveData(false)
    val showSearchBar = MutableLiveData(true)

    fun setGrid(cols: Int, rows: Int) {
        prefs.edit().putInt(KEY_COLS, cols).putInt(KEY_ROWS, rows).apply()
        gridCols.postValue(cols); gridRows.postValue(rows)
    }
    fun setShowWidgetSlot(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WIDGETS, show).apply(); showWidgetSlot.postValue(show)
    }
    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply(); hapticEnabled.postValue(enabled)
    }
    fun setSearchMode(mode: String) {
        prefs.edit().putString(KEY_SEARCH_MODE, mode).apply(); searchMode.postValue(mode)
    }
    fun setSearchBarStyle(style: String) {
        prefs.edit().putString(KEY_SEARCH_STYLE, style).apply(); searchBarStyle.postValue(style)
    }
    fun setSwipeDownNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SWIPE_DOWN, enabled).apply(); swipeDownNotifications.postValue(enabled)
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
    /** v16: reset solo impostazioni — riporta tutto a default */
    fun resetSettings() {
        prefs.edit().clear().apply()
        gridCols.postValue(4); gridRows.postValue(4)
        showWidgetSlot.postValue(true); hapticEnabled.postValue(true)
        tutorialSeen.postValue(false); searchMode.postValue(MODE_APPS)
        searchBarStyle.postValue(STYLE_SYSTEM); swipeDownNotifications.postValue(true)
    }
    /** v16: reset completo app — layout + settings + tutorial */
    fun resetEverything() {
        homeLayoutPrefs.edit().clear().apply()
        resetSettings()
    }

    companion object {
        private const val KEY_COLS = "grid_cols"
        private const val KEY_ROWS = "grid_rows"
        private const val KEY_SHOW_WIDGETS = "show_widgets"
        private const val KEY_HAPTIC = "haptic_enabled"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
        private const val KEY_SEARCH_MODE = "search_mode"
        private const val KEY_SEARCH_STYLE = "search_style"
        private const val KEY_SWIPE_DOWN = "swipe_down_notifications"

        const val MODE_APPS = "apps"
        const val MODE_GOOGLE = "google"

        const val STYLE_SYSTEM = "system"
        const val STYLE_TRANSPARENT = "transparent"
        const val STYLE_DARK = "dark"
        const val STYLE_LIGHT = "light"
    }
}
