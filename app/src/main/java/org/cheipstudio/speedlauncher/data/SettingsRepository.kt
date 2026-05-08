package org.cheipstudio.speedlauncher.data

import android.content.Context
import androidx.lifecycle.MutableLiveData

class SettingsRepository(context: Context) {

    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("speed_settings", Context.MODE_PRIVATE)
    private val homeLayoutPrefs = ctx.getSharedPreferences("speed_home_layout", Context.MODE_PRIVATE)

    // v30: tablet detection — sw >= 600dp considerato tablet
    private val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
    private val defaultCols = if (isTablet) 6 else 5
    private val defaultRows = if (isTablet) 6 else 5
    val gridCols = MutableLiveData(prefs.getInt(KEY_COLS, defaultCols))
    val gridRows = MutableLiveData(prefs.getInt(KEY_ROWS, defaultRows))
    val showWidgetSlot = MutableLiveData(prefs.getBoolean(KEY_SHOW_WIDGETS, false))
    val hapticEnabled = MutableLiveData(prefs.getBoolean(KEY_HAPTIC, true))
    val tutorialSeen = MutableLiveData(prefs.getBoolean(KEY_TUTORIAL_SEEN, false))
    val searchMode = MutableLiveData(prefs.getString(KEY_SEARCH_MODE, MODE_APPS) ?: MODE_APPS)
    val searchBarStyle = MutableLiveData(prefs.getString(KEY_SEARCH_STYLE, STYLE_SYSTEM) ?: STYLE_SYSTEM)
    val swipeDownNotifications = MutableLiveData(prefs.getBoolean(KEY_SWIPE_DOWN, true))
    val doubleTapLock = MutableLiveData(prefs.getBoolean(KEY_DOUBLE_TAP_LOCK, false))
    val iconShape = MutableLiveData(prefs.getString(KEY_ICON_SHAPE, SHAPE_ORIGINAL) ?: SHAPE_ORIGINAL)
    val dotColor = MutableLiveData(prefs.getInt(KEY_DOT_COLOR, DOT_DEFAULT))
    val animationStyle = MutableLiveData(prefs.getString(KEY_ANIM_STYLE, ANIM_EXPRESSIVE) ?: ANIM_EXPRESSIVE)
    val drawerLayout = MutableLiveData(prefs.getString(KEY_DRAWER_LAYOUT, DRAWER_LIST) ?: DRAWER_LIST)
    val folderBgStyle = MutableLiveData(prefs.getString(KEY_FOLDER_BG, FOLDER_BG_SYSTEM) ?: FOLDER_BG_SYSTEM)
    val notificationBadgeMode = MutableLiveData(prefs.getString(KEY_BADGE_MODE, BADGE_DOT) ?: BADGE_DOT)
    val hiddenApps = MutableLiveData(prefs.getStringSet(KEY_HIDDEN_APPS, emptySet())?.toMutableSet() ?: mutableSetOf())
    val firstRunDone = MutableLiveData(prefs.getBoolean(KEY_FIRST_RUN_DONE, false))
    // v30: AI Launcher Mode — sezione "Raccomandate" in home e drawer
    val aiLauncherMode = MutableLiveData(prefs.getBoolean(KEY_AI_LAUNCHER_MODE, true))
    // v30: orientamento landscape supportato (off = solo portrait)
    val landscapeAllowed = MutableLiveData(prefs.getBoolean(KEY_LANDSCAPE_ALLOWED, false))
    // v32: posizione della sezione Raccomandate (top o bottom)
    val recommendedPosition = MutableLiveData(prefs.getString(KEY_REC_POSITION, REC_POS_BOTTOM) ?: REC_POS_BOTTOM)
    // v37: numero di app raccomandate (4 o 5)
    val recommendedCount = MutableLiveData(prefs.getInt(KEY_REC_COUNT, 5))
    // v38: lingua manuale ("auto" segue il sistema, oppure codice ISO it/en/fr/...)
    val language = MutableLiveData(prefs.getString(KEY_LANGUAGE, "auto") ?: "auto")
    // v38: sfondo colorato sotto le icone (Material Expressive)
    val iconBgEnabled = MutableLiveData(prefs.getBoolean(KEY_ICON_BG, false))
    // v38: dim wallpaper (0..100), 0 = nessuno
    val wallpaperDim = MutableLiveData(prefs.getInt(KEY_WALLPAPER_DIM, 0))
    // v38: parallax wallpaper (segue lo scroll delle pagine)
    val wallpaperParallax = MutableLiveData(prefs.getBoolean(KEY_WALLPAPER_PARALLAX, true))
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
    fun setDrawerLayout(layout: String) {
        prefs.edit().putString(KEY_DRAWER_LAYOUT, layout).apply(); drawerLayout.postValue(layout)
    }
    fun setFolderBgStyle(style: String) {
        prefs.edit().putString(KEY_FOLDER_BG, style).apply(); folderBgStyle.postValue(style)
    }
    fun setNotificationBadgeMode(mode: String) {
        prefs.edit().putString(KEY_BADGE_MODE, mode).apply(); notificationBadgeMode.postValue(mode)
    }
    fun hideApp(appKey: String) {
        val current = hiddenApps.value ?: mutableSetOf()
        current.add(appKey)
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, current).apply()
        hiddenApps.postValue(current)
    }
    fun unhideApp(appKey: String) {
        val current = hiddenApps.value ?: mutableSetOf()
        current.remove(appKey)
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, current).apply()
        hiddenApps.postValue(current)
    }
    fun isAppHidden(appKey: String): Boolean = hiddenApps.value?.contains(appKey) == true
    fun markFirstRunDone() {
        prefs.edit().putBoolean(KEY_FIRST_RUN_DONE, true).apply()
        firstRunDone.postValue(true)
    }
    fun setAiLauncherMode(on: Boolean) {
        prefs.edit().putBoolean(KEY_AI_LAUNCHER_MODE, on).apply(); aiLauncherMode.postValue(on)
    }
    fun setLandscapeAllowed(on: Boolean) {
        prefs.edit().putBoolean(KEY_LANDSCAPE_ALLOWED, on).apply(); landscapeAllowed.postValue(on)
    }
    fun setRecommendedPosition(pos: String) {
        prefs.edit().putString(KEY_REC_POSITION, pos).apply(); recommendedPosition.postValue(pos)
    }
    fun setRecommendedCount(n: Int) {
        prefs.edit().putInt(KEY_REC_COUNT, n).apply(); recommendedCount.postValue(n)
    }
    fun setLanguage(code: String) {
        prefs.edit().putString(KEY_LANGUAGE, code).apply(); language.postValue(code)
    }
    fun setIconBgEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ICON_BG, on).apply(); iconBgEnabled.postValue(on)
    }
    fun setWallpaperDim(v: Int) {
        prefs.edit().putInt(KEY_WALLPAPER_DIM, v).apply(); wallpaperDim.postValue(v)
    }
    fun setWallpaperParallax(on: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER_PARALLAX, on).apply(); wallpaperParallax.postValue(on)
    }

    fun unhideAllApps() {
        val empty = mutableSetOf<String>()
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, empty).apply()
        hiddenApps.postValue(empty)
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
        gridCols.postValue(defaultCols); gridRows.postValue(defaultRows)
        showWidgetSlot.postValue(false); hapticEnabled.postValue(true)
        tutorialSeen.postValue(false); searchMode.postValue(MODE_APPS)
        searchBarStyle.postValue(STYLE_SYSTEM); swipeDownNotifications.postValue(true)
        doubleTapLock.postValue(false); iconShape.postValue(SHAPE_ORIGINAL)
        dotColor.postValue(DOT_DEFAULT); animationStyle.postValue(ANIM_EXPRESSIVE)
        drawerLayout.postValue(DRAWER_LIST)
        folderBgStyle.postValue(FOLDER_BG_SYSTEM)
        notificationBadgeMode.postValue(BADGE_DOT)
        hiddenApps.postValue(mutableSetOf())
        aiLauncherMode.postValue(true)
        landscapeAllowed.postValue(false)
        recommendedPosition.postValue(REC_POS_BOTTOM)
        recommendedCount.postValue(5)
        language.postValue("auto")
        iconBgEnabled.postValue(false)
        wallpaperDim.postValue(0)
        wallpaperParallax.postValue(true)
    }
    fun resetEverything() {
        homeLayoutPrefs.edit().clear().apply()
        resetSettings()
        // v27: reset anche del firstRunDone così la home si ripopola alla prossima apertura
        prefs.edit().putBoolean(KEY_FIRST_RUN_DONE, false).apply()
        firstRunDone.postValue(false)
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

        private const val KEY_DRAWER_LAYOUT = "drawer_layout"
        const val DRAWER_GRID3 = "grid3"
        const val DRAWER_GRID4 = "grid4"
        const val DRAWER_GRID5 = "grid5"
        const val DRAWER_LIST = "list"

        private const val KEY_FOLDER_BG = "folder_bg_style"
        const val FOLDER_BG_SYSTEM = "system"
        const val FOLDER_BG_TRANSPARENT = "transparent"
        const val FOLDER_BG_DARK = "dark"
        const val FOLDER_BG_LIGHT = "light"

        private const val KEY_BADGE_MODE = "badge_mode"
        const val BADGE_DOT = "dot"
        const val BADGE_COUNT = "count"
        const val BADGE_OFF = "off"

        private const val KEY_HIDDEN_APPS = "hidden_apps"
        private const val KEY_FIRST_RUN_DONE = "first_run_done"
        private const val KEY_AI_LAUNCHER_MODE = "ai_launcher_mode"
        private const val KEY_LANDSCAPE_ALLOWED = "landscape_allowed"
        private const val KEY_REC_POSITION = "recommended_position"
        const val REC_POS_TOP = "top"
        const val REC_POS_BOTTOM = "bottom"
        private const val KEY_REC_COUNT = "recommended_count"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_ICON_BG = "icon_bg_enabled"
        private const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        private const val KEY_WALLPAPER_PARALLAX = "wallpaper_parallax"

        // Default = arancione/rosso vivace (Material 3)
        const val DOT_DEFAULT = -0x4ab9d  // ~#FFB546... red-orange
    }
}
