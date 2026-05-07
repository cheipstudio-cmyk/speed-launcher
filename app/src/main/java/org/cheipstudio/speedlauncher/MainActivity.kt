package org.cheipstudio.speedlauncher

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.databinding.ActivityMainBinding
import org.cheipstudio.speedlauncher.ui.AppActionsSheet
import org.cheipstudio.speedlauncher.ui.AppDrawerSheet
import org.cheipstudio.speedlauncher.ui.HomeMenuSheet
import org.cheipstudio.speedlauncher.ui.TutorialOverlay
import org.cheipstudio.speedlauncher.widgets.WidgetHostController

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var widgetHostController: WidgetHostController
    private var drawerSheet: AppDrawerSheet? = null
    private var homeMenuSheet: HomeMenuSheet? = null
    private var appActionsSheet: AppActionsSheet? = null
    private var tutorialOverlay: TutorialOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        widgetHostController = WidgetHostController(this).also { it.start() }
        binding.homeView.attachWidgetHost(widgetHostController)

        SpeedApp.instance.notificationCounter.counts.observe(this) { binding.homeView.refreshDots() }
        SpeedApp.instance.appRepository.apps.observe(this) { binding.homeView.refreshApps(it) }

        binding.homeView.onSwipeUp = { openDrawer() }
        binding.homeView.onSearchTap = { openDrawerWithSearch() }
        binding.homeView.onHomeLongPress = { openHomeMenu() }
        binding.homeView.onAppMenuRequest = { app -> openAppActions(app) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                drawerSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
                homeMenuSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
                appActionsSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
            }
        })

        if (SpeedApp.instance.settingsRepository.tutorialSeen.value != true) {
            showTutorial()
        }
    }

    private fun showTutorial() {
        tutorialOverlay = TutorialOverlay(this).also {
            (binding.root as android.widget.FrameLayout).addView(
                it,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        widgetHostController.startListening()
        binding.homeView.reapplySettings()
    }

    override fun onPause() {
        super.onPause()
        widgetHostController.stopListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val isHome = Intent.ACTION_MAIN == intent.action && intent.hasCategory(Intent.CATEGORY_HOME)
        if (isHome) {
            drawerSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
            homeMenuSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
            appActionsSheet?.takeIf { it.isAdded }?.dismissAllowingStateLoss()
        }
    }

    private fun openDrawer() {
        if (drawerSheet?.isAdded == true) return
        drawerSheet = AppDrawerSheet().also {
            it.onAppLongPress = { app -> openAppActions(app) }
            it.show(supportFragmentManager, "drawer")
        }
    }

    private fun openDrawerWithSearch() {
        if (drawerSheet?.isAdded == true) return
        drawerSheet = AppDrawerSheet.newInstance(focusSearch = true).also {
            it.onAppLongPress = { app -> openAppActions(app) }
            it.show(supportFragmentManager, "drawer")
        }
    }

    private fun openHomeMenu() {
        if (homeMenuSheet?.isAdded == true) return
        homeMenuSheet = HomeMenuSheet().also {
            it.onSettings = {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            it.show(supportFragmentManager, "homemenu")
        }
    }

    private fun openAppActions(app: AppInfo) {
        if (appActionsSheet?.isAdded == true) return
        appActionsSheet = AppActionsSheet.newInstance(app).also {
            it.isPinned = { a -> binding.homeView.isPinned(a) }
            it.onPinToggle = { a ->
                if (binding.homeView.isPinned(a)) binding.homeView.unpinApp(a)
                else binding.homeView.pinApp(a)
            }
            it.show(supportFragmentManager, "appactions")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgetHostController.handleActivityResult(requestCode, resultCode, data)
    }
}
