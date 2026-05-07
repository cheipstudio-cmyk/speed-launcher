package org.cheipstudio.speedlauncher.data

import android.content.Context
import androidx.lifecycle.MutableLiveData

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("speed_settings", Context.MODE_PRIVATE)

    val gridCols = MutableLiveData(prefs.getInt(KEY_COLS, 4))
    val gridRows = MutableLiveData(prefs.getInt(KEY_ROWS, 4))
    val showDock = MutableLiveData(prefs.getBoolean(KEY_SHOW_DOCK, true))
    val showWidgetSlot = MutableLiveData(prefs.getBoolean(KEY_SHOW_WIDGETS, true))
    val tutorialSeen = MutableLiveData(prefs.getBoolean(KEY_TUTORIAL_SEEN, false))

    fun setGrid(cols: Int, rows: Int) {
        prefs.edit().putInt(KEY_COLS, cols).putInt(KEY_ROWS, rows).apply()
        gridCols.postValue(cols)
        gridRows.postValue(rows)
    }

    fun setShowDock(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DOCK, show).apply()
        showDock.postValue(show)
    }

    fun setShowWidgetSlot(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WIDGETS, show).apply()
        showWidgetSlot.postValue(show)
    }

    fun markTutorialSeen() {
        prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, true).apply()
        tutorialSeen.postValue(true)
    }

    companion object {
        private const val KEY_COLS = "grid_cols"
        private const val KEY_ROWS = "grid_rows"
        private const val KEY_SHOW_DOCK = "show_dock"
        private const val KEY_SHOW_WIDGETS = "show_widgets"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
    }
}
