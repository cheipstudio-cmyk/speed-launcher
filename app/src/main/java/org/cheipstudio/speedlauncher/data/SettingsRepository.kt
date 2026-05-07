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
    val doubleTapLock = MutableLiveData(prefs.getBoolean(KEY_DOUBLE_TAP_LOCK, false))
    val iconShape = MutableLiveData(prefs.getString(KEY_ICON_SHAPE, SHAPE_ORIGINAL) ?: SHAPE_ORIGINAL)
    val dotColor = MutableLiveData(prefs.getInt(KEY_DOT_COLOR, DOT_DEFAULT))
    val animationStyle = MutableLiveData(prefs.getString(KEY_ANIM_STYLE, ANIM_EXPRESSIVE) ?: ANIM_EXPRESSIVE)
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
    fun setDoubleTapLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOUBLE_TAP_LOCK, enabled).apply(); doubleTapLock.postValue(enabled)
    }
    fun setIconShape(shape: String) {
        prefs.edit().putString(KEY_ICON_SHAPE, shape).apply(); iconShape.postValue(shape)
    }
    fun setDotColor(color: Int) {
        prefs.edit().putInt(KEY_DOT_COLOR, color).apply(); dotColor.postValue(color)
    }
    fun setAnimationStyle(style: String) {
        prefs.edit().putString(KEY_ANIM_STYLE, style).apply(); animationStyle.postValue(style)
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
    fun resetSettings() {
        prefs.edit().clear().apply()
        gridCols.postValue(4); gridRows.postValue(4)
        showWidgetSlot.postValue(true); hapticEnabled.postValue(true)
        tutorialSeen.postValue(false); searchMode.postValue(MODE_APPS)
        searchBarStyle.postValue(STYLE_SYSTEM); swipeDownNotifications.postValue(true)
        doubleTapLock.postValue(false); iconShape.postValue(SHAPE_ORIGINAL)
        dotColor.postValue(DOT_DEFAULT); animationStyle.postValue(ANIM_EXPRESSIVE)
    }
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
        private const val KEY_DOUBLE_TAP_LOCK = "double_tap_lock"
        private const val KEY_ICON_SHAPE = "icon_shape"
        private const val KEY_DOT_COLOR = "dot_color"
        private const val KEY_ANIM_STYLE = "anim_style"

        const val MODE_APPS = "apps"
        const val MODE_GOOGLE = "google"

        const val STYLE_SYSTEM = "system"
        const val STYLE_TRANSPARENT = "transparent"
        const val STYLE_DARK = "dark"
        const val STYLE_LIGHT = "light"

        const val SHAPE_ORIGINAL = "original"
        const val SHAPE_SQUIRCLE = "squircle"
        const val SHAPE_CIRCLE = "circle"
        const val SHAPE_SQUARE = "square"
        const val SHAPE_TEARDROP = "teardrop"

        const val ANIM_STANDARD = "standard"
        const val ANIM_EXPRESSIVE = "expressive"
        const val ANIM_NONE = "none"

        // Default = arancione/rosso vivace (Material 3)
        const val DOT_DEFAULT = -0x4ab9d  // ~#FFB546... red-orange
    }
}
