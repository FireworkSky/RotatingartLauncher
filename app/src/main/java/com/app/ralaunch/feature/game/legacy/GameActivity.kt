package com.app.ralaunch.feature.game.legacy

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.app.ralaunch.R
import com.app.ralaunch.feature.game.input.GameImeHelper
import com.app.ralaunch.feature.game.input.GameTouchBridge
import com.app.ralaunch.feature.game.GameVirtualControlsManager
import com.app.ralaunch.feature.crash.CrashReportActivity
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.common.GameFullscreenManager
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.util.DensityAdapter
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.core.common.ErrorHandler
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
            context.startActivity(
                createLaunchIntent(
                    context = context,
                    gameStorageId = gameStorageId
                )
            )
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
            context.startActivity(
                createLaunchIntent(
                    context = context,
                    gameExePath = gameExePath,
                    gameArgs = gameArgs,
                    gameId = gameId,
                    gameRendererOverride = gameRendererOverride,
                    gameEnvVars = gameEnvVars
                )
            )
            (context as? Activity)?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // ==================== 静态方法供 JNI/其他类调用 ====================

        @JvmStatic
        fun sendTextToGame(text: String) {
            GameImeHelper.sendTextToGame(text)
        }

        @JvmStatic
        fun sendBackspace() {
            GameImeHelper.sendBackspaceToGame()
        }

        @JvmStatic
        fun enableSDLTextInputForIME() {
            GameImeHelper.enableSDLTextInputForIME()
        }

        @JvmStatic
        fun disableSDLTextInput() {
            GameImeHelper.disableSDLTextInput()
        }

        @JvmStatic
        fun onGameExitWithMessage(exitCode: Int, errorMessage: String?) {
            instance?.presenter?.onGameExit(exitCode, errorMessage)
        }

        // Touch bridge native methods
        @JvmStatic
        fun nativeSetTouchDataBridge(count: Int, x: FloatArray, y: FloatArray, screenWidth: Int, screenHeight: Int) {
            nativeSetTouchData(count, x, y, screenWidth, screenHeight)
        }

        @JvmStatic
        fun nativeClearTouchDataBridge() {
            nativeClearTouchData()
        }

        @JvmStatic
        private external fun nativeSetTouchData(count: Int, x: FloatArray, y: FloatArray, screenWidth: Int, screenHeight: Int)

        @JvmStatic
        private external fun nativeClearTouchData()
    }

    // MVP
    private val presenter: GamePresenter = GamePresenter()

    // 管理器
    private var fullscreenManager: GameFullscreenManager? = null
    private val virtualControlsManager = GameVirtualControlsManager()
    private val touchBridge = GameTouchBridge()

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

        // 初始化日志系统 (游戏进程独立于主进程)
        initializeLogger()
        
        initializeErrorHandler()
        forceLandscapeOrientation()
        initializeFullscreenManager()
        initializeVirtualControls()
        
        AppLogger.info(TAG, "GameActivity onCreate completed")
    }
    
    private fun initializeLogger() {
        try {
            val logDir = java.io.File(getExternalFilesDir(null), "logs")
            AppLogger.init(logDir)
            AppLogger.info(TAG, "=== GameActivity Process Started ===")
            AppLogger.info(TAG, "Game process PID: ${android.os.Process.myPid()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize logger in game process", e)
        }
    }

    private fun applyThemeMode() {
        val themeMode = SettingsAccess.themeMode
        val nightMode = when (themeMode) {
            0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            1 -> AppCompatDelegate.MODE_NIGHT_YES
            2 -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun initializeErrorHandler() {
        try {
            ErrorHandler.setCurrentActivity(this)
        } catch (e: Exception) {
            AppLogger.error(TAG, "设置 ErrorHandler 失败: ${e.message}")
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
        virtualControlsManager.initialize(
            activity = this,
            sdlLayout = mLayout as ViewGroup,
            sdlSurface = mSurface,
            disableSDLTextInput = { disableSDLTextInput() },
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
        Log.d(TAG, "GameActivity.onDestroy() called")

        virtualControlsManager.stop()
        presenter.detach()

        super.onDestroy()

        // [重要] .NET runtime 不支持多次初始化
        // GameActivity 运行在独立进程，终止不影响主应用
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Terminating game process to ensure clean .NET runtime state")
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val result = super.dispatchTouchEvent(event)
        touchBridge.handleMotionEvent(event, resources)
        return result
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
        ErrorHandler.showWarning(title, message)
    }

    override fun showCrashReport(
        stackTrace: String,
        errorDetails: String,
        exceptionClass: String,
        exceptionMessage: String
    ) {
        val intent = Intent(this, CrashReportActivity::class.java).apply {
            putExtra("stack_trace", stackTrace)
            putExtra("error_details", errorDetails)
            putExtra("exception_class", exceptionClass)
            putExtra("exception_message", exceptionMessage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
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
}
