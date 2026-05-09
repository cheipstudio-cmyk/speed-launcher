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
    /** v138: posizione widget — top, middle, bottom */
    val widgetPosition = MutableLiveData(prefs.getString(KEY_WIDGET_POSITION, "top") ?: "top")
    /** v138: altezza widget in dp (60-320) */
    val widgetHeight = MutableLiveData(prefs.getInt(KEY_WIDGET_HEIGHT, 160))
    /** v138: larghezza widget percentuale (50, 75, 100) */
    val widgetWidthPercent = MutableLiveData(prefs.getInt(KEY_WIDGET_WIDTH_PERCENT, 100))
    /** v139: mostra etichette icone in home (default true) */
    val showHomeLabels = MutableLiveData(prefs.getBoolean(KEY_SHOW_HOME_LABELS, true))
    /** v139: mostra etichette icone nel drawer (default true) */
    val showDrawerLabels = MutableLiveData(prefs.getBoolean(KEY_SHOW_DRAWER_LABELS, true))
    /** v151: mostra etichette icone dentro le cartelle (default true) */
    val showFolderLabels = MutableLiveData(prefs.getBoolean(KEY_SHOW_FOLDER_LABELS, true))
    /** v151: mostra etichette icone nella dock raccomandate (default true) */
    val showDockLabels = MutableLiveData(prefs.getBoolean(KEY_SHOW_DOCK_LABELS, true))
    /** v139: lista feed RSS (URL CSV-separated) */
    val rssFeeds = MutableLiveData(
        prefs.getString(KEY_RSS_FEEDS, "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    )
    /** v140: sfocatura quando drawer è aperto (dietro la home) */
    val blurDrawer = MutableLiveData(prefs.getBoolean(KEY_BLUR_DRAWER, true))
    /** v140: sfocatura quando cartella è aperta (dietro il modal) */
    val blurFolder = MutableLiveData(prefs.getBoolean(KEY_BLUR_FOLDER, true))
    /** v140: pannello RSS attivabile con swipe da sinistra (stile Google Now) */
    val rssPanelEnabled = MutableLiveData(prefs.getBoolean(KEY_RSS_PANEL, false))
    /** v63: mostra barra di ricerca in home (default true) */
    val showSearchBar = MutableLiveData(prefs.getBoolean(KEY_SHOW_SEARCHBAR, true))
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
    // v38: dim wallpaper (0..100), 0 = nessuno
    val wallpaperDim = MutableLiveData(prefs.getInt(KEY_WALLPAPER_DIM, 0))
    // v41: sfocatura wallpaper (radius 0..50, 0 = nessuna)
    val wallpaperBlur = MutableLiveData(prefs.getInt(KEY_WALLPAPER_BLUR, 0))

    // v47: widget Speed Stats — tema (system/transparent/light/dark) + auto-refresh
    private val widgetPrefs = context.getSharedPreferences("speed_widget_prefs", Context.MODE_PRIVATE)
    val widgetTheme = MutableLiveData(widgetPrefs.getString(KEY_WIDGET_THEME, "system") ?: "system")
    val widgetAutoRefresh = MutableLiveData(widgetPrefs.getBoolean(KEY_WIDGET_AUTO_REFRESH, true))

    // v48: tema search bar + tema dock raccomandate (system/transparent/light/dark)
    val searchTheme = MutableLiveData(prefs.getString(KEY_SEARCH_THEME, "system") ?: "system")
    val dockTheme = MutableLiveData(prefs.getString(KEY_DOCK_THEME, "system") ?: "system")
    val drawerTheme = MutableLiveData(prefs.getString(KEY_DRAWER_THEME, "system") ?: "system")

    /** v59: pulitore memoria (button razzo + AI) */
    val memoryCleanerEnabled = MutableLiveData(prefs.getBoolean(KEY_MEMORY_CLEANER, true))

    /** v75: icon pack esterno (package name, "" = nessuno). Sperimentale. */
    val iconPackPackage = MutableLiveData(prefs.getString(KEY_ICON_PACK, "") ?: "")

    /** v84: modalità raccomandate — "ai" (auto da usage tracker) o "manual" (scelte dall\'utente) */
    val recommendedMode = MutableLiveData(prefs.getString(KEY_REC_MODE, REC_MODE_AI) ?: REC_MODE_AI)

    /** v84: lista app manuali per raccomandate (set di chiavi "packageName/componentName") */
    val recommendedManualApps = MutableLiveData(
        prefs.getStringSet(KEY_REC_MANUAL_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
    )
    /** v132: lista ordinata delle app raccomandate manuali (preserva ordine drag) */
    val recommendedManualOrder = MutableLiveData(
        prefs.getString(KEY_REC_MANUAL_ORDER, "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    )

    /** v85: drawer abilitato. Se false, tutte le app vengono auto-popolate in home (stile iOS). */
    val drawerEnabled = MutableLiveData(prefs.getBoolean(KEY_DRAWER_ENABLED, true))

    /** v88: aggiungi automaticamente nuove app installate alla home */
    val autoAddNewApps = MutableLiveData(prefs.getBoolean(KEY_AUTO_ADD_NEW_APPS, false))

    init {
        // v48: al primo run di v48 (dopo aggiornamento), imposta TUTTI i temi a "transparent" come default
        if (!prefs.getBoolean(KEY_FIRST_THEMES_SET, false)) {
            prefs.edit()
                .putString(KEY_SEARCH_THEME, "transparent")
                .putString(KEY_DOCK_THEME, "transparent")
                .putString(KEY_DRAWER_THEME, "system")
                .putBoolean(KEY_FIRST_THEMES_SET, true)
                .apply()
            widgetPrefs.edit().putString(KEY_WIDGET_THEME, "transparent").apply()
            searchTheme.postValue("transparent")
            dockTheme.postValue("transparent")
            widgetTheme.postValue("transparent")
        }
    }
    val showDock = MutableLiveData(false)

    fun setGrid(cols: Int, rows: Int) {
        prefs.edit().putInt(KEY_COLS, cols).putInt(KEY_ROWS, rows).apply()
        gridCols.postValue(cols); gridRows.postValue(rows)
    }
    fun setShowWidgetSlot(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WIDGETS, show).apply(); showWidgetSlot.postValue(show)
    }
    fun setWidgetPosition(pos: String) {
        prefs.edit().putString(KEY_WIDGET_POSITION, pos).apply()
        widgetPosition.postValue(pos)
    }
    fun setWidgetHeight(dp: Int) {
        prefs.edit().putInt(KEY_WIDGET_HEIGHT, dp).apply()
        widgetHeight.postValue(dp)
    }
    fun setWidgetWidthPercent(pct: Int) {
        prefs.edit().putInt(KEY_WIDGET_WIDTH_PERCENT, pct).apply()
        widgetWidthPercent.postValue(pct)
    }

    fun setShowHomeLabels(on: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HOME_LABELS, on).apply()
        showHomeLabels.postValue(on)
    }
    fun setShowDrawerLabels(on: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DRAWER_LABELS, on).apply()
        showDrawerLabels.postValue(on)
    }

    fun setRssFeeds(feeds: List<String>) {
        prefs.edit().putString(KEY_RSS_FEEDS, feeds.joinToString(",")).apply()
        rssFeeds.postValue(feeds)
    }

    fun setBlurDrawer(on: Boolean) {
        prefs.edit().putBoolean(KEY_BLUR_DRAWER, on).apply()
        blurDrawer.postValue(on)
    }
    fun setBlurFolder(on: Boolean) {
        prefs.edit().putBoolean(KEY_BLUR_FOLDER, on).apply()
        blurFolder.postValue(on)
    }

    fun setRssPanelEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_RSS_PANEL, on).apply()
        rssPanelEnabled.postValue(on)
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
        val current = (hiddenApps.value ?: mutableSetOf()).toMutableSet()
        current.add(appKey)
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, current).apply()
        hiddenApps.postValue(current)
    }
    fun unhideApp(appKey: String) {
        val current = (hiddenApps.value ?: mutableSetOf()).toMutableSet()
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
    fun setWallpaperDim(v: Int) {
        prefs.edit().putInt(KEY_WALLPAPER_DIM, v).apply(); wallpaperDim.postValue(v)
    }
    fun setWallpaperBlur(v: Int) {
        prefs.edit().putInt(KEY_WALLPAPER_BLUR, v).apply(); wallpaperBlur.postValue(v)
    }

    /** v47: widget settings */
    fun setWidgetTheme(v: String) {
        widgetPrefs.edit().putString(KEY_WIDGET_THEME, v).apply()
        widgetTheme.postValue(v)
    }
    fun setWidgetAutoRefresh(on: Boolean) {
        widgetPrefs.edit().putBoolean(KEY_WIDGET_AUTO_REFRESH, on).apply()
        widgetAutoRefresh.postValue(on)
    }

    fun setSearchTheme(v: String) {
        prefs.edit().putString(KEY_SEARCH_THEME, v).apply()
        searchTheme.postValue(v)
    }
    fun setDockTheme(v: String) {
        prefs.edit().putString(KEY_DOCK_THEME, v).apply()
        dockTheme.postValue(v)
    }

    fun setDrawerTheme(v: String) {
        prefs.edit().putString(KEY_DRAWER_THEME, v).apply()
        drawerTheme.postValue(v)
    }

    fun setMemoryCleanerEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_MEMORY_CLEANER, on).apply()
        memoryCleanerEnabled.postValue(on)
    }

    fun setIconPackPackage(pkg: String) {
        prefs.edit().putString(KEY_ICON_PACK, pkg).commit()
        iconPackPackage.postValue(pkg)
    }

    fun setRecommendedMode(mode: String) {
        prefs.edit().putString(KEY_REC_MODE, mode).commit()
        recommendedMode.postValue(mode)
    }

    fun setRecommendedManualApps(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_REC_MANUAL_APPS, keys).commit()
        recommendedManualApps.postValue(keys.toMutableSet())
        // v132: aggiorna l'ordine: tieni quelli ancora presenti, aggiungi i nuovi alla fine
        val currentOrder = recommendedManualOrder.value ?: emptyList()
        val filtered = currentOrder.filter { it in keys }
        val newOnes = keys.filter { it !in filtered }
        val finalOrder = filtered + newOnes
        prefs.edit().putString(KEY_REC_MANUAL_ORDER, finalOrder.joinToString(",")).commit()
        recommendedManualOrder.postValue(finalOrder)
    }
    
    /** v132: setta direttamente l'ordine (drag/drop) — sincronizza anche il Set */
    fun setRecommendedManualOrder(orderedKeys: List<String>) {
        prefs.edit().putString(KEY_REC_MANUAL_ORDER, orderedKeys.joinToString(",")).commit()
        recommendedManualOrder.postValue(orderedKeys)
        prefs.edit().putStringSet(KEY_REC_MANUAL_APPS, orderedKeys.toSet()).commit()
        recommendedManualApps.postValue(orderedKeys.toMutableSet())
    }

    fun setDrawerEnabled(enabled: Boolean) {
        // v86: commit sincrono — viene chiamato forceRestart subito dopo,
        // se usiamo apply() async la pref può non essere persistita.
        prefs.edit().putBoolean(KEY_DRAWER_ENABLED, enabled).commit()
        drawerEnabled.postValue(enabled)
    }

    fun setAutoAddNewApps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ADD_NEW_APPS, enabled).apply()
        autoAddNewApps.postValue(enabled)
    }

    fun unhideAllApps() {
        val empty = mutableSetOf<String>()
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, empty).apply()
        hiddenApps.postValue(empty)
    }
    fun markTutorialSeen() {
        prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, true).apply(); tutorialSeen.postValue(true)
    }
    fun setShowSearchBar(on: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SEARCHBAR, on).apply()
        showSearchBar.postValue(on)
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
        wallpaperDim.postValue(0)
        wallpaperBlur.postValue(0)
    }
    fun resetEverything() {
        // v60: reset TOTALE — cancella ogni SharedPreferences dell\'app, cache, e segnala riavvio
        try { homeLayoutPrefs.edit().clear().commit() } catch (_: Throwable) {}
        try { prefs.edit().clear().commit() } catch (_: Throwable) {}
        try { widgetPrefs.edit().clear().commit() } catch (_: Throwable) {}
        // Cancello anche le altre SharedPreferences create dall\'app
        try {
            ctx.getSharedPreferences("speed_prefill", Context.MODE_PRIVATE).edit().clear().commit()
        } catch (_: Throwable) {}
        try {
            ctx.getSharedPreferences("speed_app_usage", Context.MODE_PRIVATE).edit().clear().commit()
        } catch (_: Throwable) {}
        try {
            ctx.getSharedPreferences("speed_notification_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        } catch (_: Throwable) {}
        // Cancello la cache app
        try { clearDir(ctx.cacheDir) } catch (_: Throwable) {}
        try { clearDir(ctx.codeCacheDir) } catch (_: Throwable) {}
        // Cancello tutte le altre SharedPreferences sconosciute
        try {
            val prefsDir = java.io.File(ctx.applicationInfo.dataDir, "shared_prefs")
            if (prefsDir.exists()) {
                for (f in prefsDir.listFiles() ?: arrayOf<java.io.File>()) {
                    try { f.delete() } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    private fun clearDir(dir: java.io.File?) {
        if (dir == null || !dir.exists()) return
        for (f in dir.listFiles() ?: emptyArray()) {
            try {
                if (f.isDirectory) clearDir(f)
                f.delete()
            } catch (_: Throwable) {}
        }
    }

    
    fun setShowFolderLabels(on: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FOLDER_LABELS, on).apply()
        showFolderLabels.postValue(on)
    }
    
    fun setShowDockLabels(on: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DOCK_LABELS, on).apply()
        showDockLabels.postValue(on)
    }

    companion object {
        private const val KEY_SHOW_FOLDER_LABELS = "show_folder_labels"
        private const val KEY_SHOW_DOCK_LABELS = "show_dock_labels"
        private const val KEY_COLS = "grid_cols"
        private const val KEY_ROWS = "grid_rows"
        private const val KEY_SHOW_WIDGETS = "show_widgets"
        private const val KEY_WIDGET_POSITION = "widget_position"
        private const val KEY_WIDGET_HEIGHT = "widget_height"
        private const val KEY_WIDGET_WIDTH_PERCENT = "widget_width_percent"
        private const val KEY_SHOW_HOME_LABELS = "show_home_labels"
        private const val KEY_SHOW_DRAWER_LABELS = "show_drawer_labels"
        private const val KEY_RSS_FEEDS = "rss_feeds"
        private const val KEY_BLUR_DRAWER = "blur_drawer"
        private const val KEY_BLUR_FOLDER = "blur_folder"
        private const val KEY_RSS_PANEL = "rss_panel_enabled"
        private const val KEY_SHOW_SEARCHBAR = "show_searchbar"
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
        const val MODE_UNIVERSAL = "universal"

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
        const val ANIM_FAST = "fast"
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
        private const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        private const val KEY_WALLPAPER_BLUR = "wallpaper_blur"
        // v47: widget keys (in widgetPrefs separato — letto anche da WidgetProvider)
        private const val KEY_WIDGET_THEME = "widget_theme"
        private const val KEY_WIDGET_AUTO_REFRESH = "widget_auto_refresh"
        private const val KEY_SEARCH_THEME = "search_theme"
        private const val KEY_DOCK_THEME = "dock_theme"
        private const val KEY_DRAWER_THEME = "drawer_theme"
        private const val KEY_MEMORY_CLEANER = "memory_cleaner_enabled"
        private const val KEY_ICON_PACK = "icon_pack_package"
        private const val KEY_REC_MODE = "recommended_mode"
        private const val KEY_REC_MANUAL_APPS = "recommended_manual_apps"
        private const val KEY_REC_MANUAL_ORDER = "recommended_manual_order"

        const val REC_MODE_AI = "ai"
        const val REC_MODE_MANUAL = "manual"
        private const val KEY_DRAWER_ENABLED = "drawer_enabled"
        private const val KEY_AUTO_ADD_NEW_APPS = "auto_add_new_apps"
        private const val KEY_FIRST_THEMES_SET = "first_themes_set"

        // Default = arancione/rosso vivace (Material 3)
        const val DOT_DEFAULT = -0x4ab9d  // ~#FFB546... red-orange
    }
}
