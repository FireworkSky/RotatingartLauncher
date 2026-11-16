package com.app.ralaunch.feature.game.ui.legacy

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.app.ralaunch.feature.game.GameVirtualControlsManager
import com.app.ralaunch.core.error.ui.CrashReportActivity
import com.app.ralaunch.feature.game.legacy.GameContract
import com.app.ralaunch.feature.game.legacy.GamePresenter
import org.koin.android.ext.android.inject
import com.app.ralaunch.core.platform.android.service.RefreshRateService
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.common.GameFullscreenManager
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.common.util.DensityAdapter
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.core.error.ErrorHandler
import com.app.ralaunch.core.model.ThemeMode
import org.libsdl.app.SDLActivity

/**
 * 游戏运行界面
 * 继承 SDLActivity，实现 MVP 的 View 层
 */
class GameActivity : SDLActivity(), GameContract.View {

    companion object {
        private const val TAG = "GameActivity"
        private const val CONTROL_EDITOR_REQUEST_CODE = 2001
        const val EXTRA_GAME_STORAGE_ID = "GAME_STORAGE_ID"
        const val EXTRA_GAME_EXE_PATH = "GAME_EXE_PATH"
        const val EXTRA_GAME_ARGS = "GAME_ARGS"
        const val EXTRA_GAME_ID = "GAME_ID"
        const val EXTRA_GAME_RENDERER_OVERRIDE = "GAME_RENDERER_OVERRIDE"
        const val EXTRA_GAME_ENV_VARS = "GAME_ENV_VARS"

        @JvmStatic
        var instance: GameActivity? = null
            private set

        @JvmStatic
        fun createLaunchIntent(
            context: Context,
            gameStorageId: String
        ): Intent {
            require(gameStorageId.isNotBlank()) { "gameStorageId must not be blank" }
            return Intent(context, GameActivity::class.java).apply {
                putExtra(EXTRA_GAME_STORAGE_ID, gameStorageId)
            }
        }

        @JvmStatic
        fun createLaunchIntent(
            context: Context,
            gameExePath: String,
            gameArgs: Array<String>,
            gameId: String?,
            gameRendererOverride: String?,
            gameEnvVars: Map<String, String?> = emptyMap()
        ): Intent {
            require(gameExePath.isNotBlank()) { "gameExePath must not be blank" }
            return Intent(context, GameActivity::class.java).apply {
                putExtra(EXTRA_GAME_EXE_PATH, gameExePath)
                putExtra(EXTRA_GAME_ARGS, gameArgs)
                putExtra(EXTRA_GAME_ID, gameId)
                putExtra(EXTRA_GAME_RENDERER_OVERRIDE, gameRendererOverride)
                putExtra(EXTRA_GAME_ENV_VARS, HashMap(gameEnvVars))
            }
        }

        @JvmStatic
        fun launch(
            context: Context,
            gameStorageId: String
        ) {
            val intent = createLaunchIntent(
                context = context,
                gameStorageId = gameStorageId
            ).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            (context as? Activity)?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        @JvmStatic
        fun launch(
            context: Context,
            gameExePath: String,
            gameArgs: Array<String>,
            gameId: String?,
            gameRendererOverride: String?,
            gameEnvVars: Map<String, String?> = emptyMap()
        ) {
            val intent = createLaunchIntent(
                context = context,
                gameExePath = gameExePath,
                gameArgs = gameArgs,
                gameId = gameId,
                gameRendererOverride = gameRendererOverride,
                gameEnvVars = gameEnvVars
            ).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            (context as? Activity)?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    // MVP
    private val presenter: GamePresenter = GamePresenter()

    // 管理器
    private var fullscreenManager: GameFullscreenManager? = null
    private val virtualControlsManager = GameVirtualControlsManager()
    private val refreshRateService: RefreshRateService by inject()

    // ==================== 生命周期 ====================

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DensityAdapter.adapt(this, true)
        applyThemeMode()
        super.onCreate(savedInstanceState)

        instance = this
        presenter.attach(this)

        initializeErrorHandler()
        forceLandscapeOrientation()
        initializeFullscreenManager()
        initializeVirtualControls()
        requestHighRefreshRate("onCreate")
        AppLog.i(TAG, "=== GameActivity Process Started ===")
        AppLog.i(TAG, "Game process PID: ${android.os.Process.myPid()}")
        AppLog.i(TAG, "GameActivity onCreate completed")
    }

    private fun applyThemeMode() {
        val themeMode = SettingsAccess.themeMode
        val nightMode = when (themeMode) {
            ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun initializeErrorHandler() {
        try {
            ErrorHandler.init(this)
        } catch (e: Exception) {
            AppLog.e(TAG, "设置 ErrorHandler 失败: ${e.message}")
        }
    }

    private fun forceLandscapeOrientation() {
        try {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (_: Exception) {
        }
    }

    private fun initializeFullscreenManager() {
        fullscreenManager = GameFullscreenManager(this).apply {
            enableFullscreen()
            configureIME()
        }
    }

    private fun initializeVirtualControls() {
        val layout = mLayout
        val surface = mSurface
        if (layout == null || surface == null) {
            AppLog.w(TAG, "SDL view hierarchy is not initialized; skip virtual controls")
            return
        }

        virtualControlsManager.initialize(
            activity = this,
            sdlLayout = layout,
            sdlSurface = surface,
            onExitGame = { exitGame() }
        )
    }

    private fun exitGame() {
        // 通过 Presenter 正常退出游戏
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        fullscreenManager?.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            requestHighRefreshRate("onWindowFocusChanged")
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighRefreshRate("onResume")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CONTROL_EDITOR_REQUEST_CODE && resultCode == RESULT_OK) {
            virtualControlsManager.onActivityResultReload()
        }
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 按下返回键不做任何操作，由悬浮菜单控制
        // 用户可以通过悬浮菜单退出游戏
    }

    override fun onDestroy() {
        AppLog.d(TAG, "GameActivity.onDestroy() called")

        virtualControlsManager.stop()
        presenter.detach()

        super.onDestroy()

        // [重要] .NET runtime 不支持多次初始化
        // GameActivity 运行在独立进程，终止不影响主应用
        Handler(Looper.getMainLooper()).postDelayed({
            AppLog.d(TAG, "Terminating game process to ensure clean .NET runtime state")
            Process.killProcess(Process.myPid())
            System.exit(0)
        }, 100)
    }

    // ==================== SDL 重写 ====================

    override fun setOrientationBis(w: Int, h: Int, resizable: Boolean, hint: String?) {
        super.setOrientationBis(w, h, resizable, "LandscapeLeft LandscapeRight")
    }

    override fun getMainFunction(): String = "SDL_main"

    override fun Main(args: Array<String>?) {
        presenter.launchGame()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 返回键处理：切换悬浮球可见性
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                virtualControlsManager.toggleFloatingBall()
            }
            return true  // 拦截返回键，不退出游戏
        }
        
        return super.dispatchKeyEvent(event)
    }

    // ==================== 公开方法 ====================

    fun toggleVirtualControls() {
        virtualControlsManager.toggle(this)
    }

    fun setVirtualControlsVisible(visible: Boolean) {
        virtualControlsManager.setVisible(visible)
    }

    // ==================== GameContract.View 实现 ====================

    override fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun showError(title: String, message: String) {
        showToast("$title: $message")
    }

    override fun showCrashReport(
        stackTrace: String,
        errorDetails: String,
        exceptionClass: String,
        exceptionMessage: String
    ) {
        CrashReportActivity.launch(
            context = this,
            stackTrace = stackTrace,
            errorDetails = errorDetails,
            exceptionClass = exceptionClass,
            exceptionMessage = exceptionMessage
        )
        finish()
    }

    override fun getStringRes(resId: Int): String = getString(resId)

    override fun getStringRes(resId: Int, vararg args: Any): String = getString(resId, *args)

    override fun runOnMainThread(action: () -> Unit) {
        runOnUiThread { action() }
    }

    override fun finishActivity() {
        finish()
    }

    override fun getActivityIntent(): Intent = intent

    override fun getAppVersionName(): String? {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }

    private fun requestHighRefreshRate(caller: String) {
        refreshRateService.requestGameRefreshRate(
            this,
            mSurface?.holder?.surface,
            SettingsAccess.unlockRefreshRateLimit,
            caller
        )
    }
}
