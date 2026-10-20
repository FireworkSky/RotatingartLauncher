package com.app.ralaunch.feature.game.ui.sdl2

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Display
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import android.view.KeyEvent
import android.view.Surface
import android.widget.Toast
import com.app.ralaunch.R
import com.app.ralaunch.core.common.GameFullscreenManager
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.common.util.DensityAdapter
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.core.error.ErrorHandler
import com.app.ralaunch.core.error.ui.CrashReportActivity
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.model.ThemeMode
import com.app.ralaunch.core.platform.runtime.GameLauncher
import com.app.ralaunch.core.platform.runtime.Sdl2Environment
import com.app.ralaunch.feature.game.GameVirtualControlsManager
import com.app.ralaunch.feature.game.legacy.GameContract
import com.app.ralaunch.feature.game.legacy.GamePresenter
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.app.ralaunch.core.ui.ComposeViewTreeOwner
import org.libsdl.app2.SDLActivity

/**
 * SDL2 游戏运行界面（Android 视频后端）
 *
 * SDL2 与 SDL3 各自需要一套 Java 胶水层，本工程用「包名 +2」把它们分开：
 *  - SDL3：[com.app.ralaunch.feature.game.ui.legacy.GameActivity] 继承 org.libsdl.app.SDLActivity
 *  - SDL2：本类继承 org.libsdl.app2.SDLActivity（SDL2 官方胶水层，包名整体 +2）
 * 两者因此可以同时存在于同一个 APK，而各自的 native 方法注册（JNI 前缀）互不干扰。
 *
 * 分工：
 *  - 窗口 / Surface / 触摸 / 按键 / 剪贴板 / 手柄等由 SDL 的 Java 胶水层负责，
 *    我们不再自己创建 SurfaceView，也不使用内嵌 Wayland 合成器；
 *  - 游戏本体（.NET/Mono 程序集）仍由 [GamePresenter] 启动，跑在独立线程上
 *    （SDL_Init 与 GL 上下文都在该线程上建立，因此不能占用 UI 线程，
 *     否则 Activity 无法完成首帧绘制，系统会一直保留启动窗口导致「有日志没画面」）。
 */
class GameActivitySdl2 : SDLActivity(), GameContract.View, LifecycleOwner, SavedStateRegistryOwner,
    ActivityResultRegistryOwner {

    companion object {
        private const val TAG = "GameActivitySdl2"
        private const val CONTROL_EDITOR_REQUEST_CODE = 2001

        // 与 GameActivity（SDL3）完全相同的 extras，保证 GamePresenter 的解析逻辑两边通用
        const val EXTRA_GAME_STORAGE_ID = "GAME_STORAGE_ID"
        const val EXTRA_GAME_EXE_PATH = "GAME_EXE_PATH"
        const val EXTRA_GAME_ARGS = "GAME_ARGS"
        const val EXTRA_GAME_ID = "GAME_ID"
        const val EXTRA_GAME_RENDERER_OVERRIDE = "GAME_RENDERER_OVERRIDE"
        const val EXTRA_GAME_ENV_VARS = "GAME_ENV_VARS"

        @Volatile
        var instance: GameActivitySdl2? = null
            private set

        @JvmStatic
        fun createLaunchIntent(context: Context, gameStorageId: String): Intent {
            require(gameStorageId.isNotBlank()) { "gameStorageId must not be blank" }
            return Intent(context, GameActivitySdl2::class.java).apply {
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
            return Intent(context, GameActivitySdl2::class.java).apply {
                putExtra(EXTRA_GAME_EXE_PATH, gameExePath)
                putExtra(EXTRA_GAME_ARGS, gameArgs)
                putExtra(EXTRA_GAME_ID, gameId)
                putExtra(EXTRA_GAME_RENDERER_OVERRIDE, gameRendererOverride)
                putExtra(EXTRA_GAME_ENV_VARS, HashMap(gameEnvVars))
            }
        }

        @JvmStatic
        fun launch(context: Context, gameStorageId: String) {
            val intent = createLaunchIntent(context, gameStorageId).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                context, gameExePath, gameArgs, gameId, gameRendererOverride, gameEnvVars
            ).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            (context as? Activity)?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    // MVP：复用与 SDL3 相同的 GamePresenter
    private val presenter: GamePresenter = GamePresenter()

    // Compose（虚拟按键/悬浮菜单）需要的 ViewTree owners：SDL 胶水层是普通 Activity，不带这些
    private val composeOwner by lazy { ComposeViewTreeOwner(this) }

    override val lifecycle: Lifecycle get() = composeOwner.lifecycle
    override val savedStateRegistry: SavedStateRegistry get() = composeOwner.savedStateRegistry
    override val activityResultRegistry: ActivityResultRegistry get() = composeOwner.activityResultRegistry

    /** 游戏线程是否已启动（SDL Surface 就绪后才拉起） */
    @Volatile
    private var gameThreadStarted = false

    // RAL 悬浮菜单（悬浮球）与虚拟按键：与 SDL3 的 GameActivity 完全一致
    private val virtualControlsManager = GameVirtualControlsManager()

    private var fullscreenManager: GameFullscreenManager? = null
    private var lastRequestedRefreshRate: Float = 0f

    // ==================== SDL 胶水层扩展点 ====================

    /**
     * 不启动 SDL 的 native main（SDL_main）线程：
     * RAL 的游戏入口是托管的 .NET/Mono 程序集，由 Java 侧在 Surface 就绪后拉起。
     * 这样窗口/Surface/输入仍由 SDL 胶水层负责，只是不用它跑 C 的 main()。
     */
    override fun shouldStartNativeMain(): Boolean = false

    /**
     * 强制横屏。
     *
     * SDL 会按「创建窗口时的宽高」自行请求方向（h > w 时请求竖屏），从而覆盖 manifest 里的
     * screenOrientation；与 SDL3 的 GameActivity 一样，这里固定传横屏 hint。
     */
    override fun setOrientationBis(w: Int, h: Int, resizable: Boolean, hint: String?) {
        super.setOrientationBis(w, h, resizable, "LandscapeLeft LandscapeRight")
    }

    /**
     * 只加载 SDL2 本体（不加载 "main"）：后者在本工程是 libmain.so（RAL 核心 + 合成器 JNI），
     * 由 [GameLauncher] 按需加载，不需要 SDL 把它当成 app 主库。
     */
    override fun getLibraries(): Array<String> = arrayOf("SDL2")

    // ==================== 生命周期 ====================

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        composeOwner.onCreate(savedInstanceState)
        DensityAdapter.adapt(this, true)
        applyThemeMode()
        // SDLActivity：创建 SurfaceView、注册输入与生命周期回调、加载 SDL2
        super.onCreate(savedInstanceState)

        instance = this
        presenter.attach(this)

        try {
            ErrorHandler.init(this)
        } catch (e: Exception) {
            AppLog.e(TAG, "设置 ErrorHandler 失败: ${e.message}")
        }

        try {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (_: Exception) {
        }

        // FNA3D 走 OpenGL/ES3，并清掉 Vulkan/SDL_GPU/Mesa/Wayland 残留（见 Sdl2Environment）
        Sdl2Environment.apply()

        // Compose（虚拟按键 / 悬浮菜单）需要 ViewTree owners：SDL 的胶水层是普通 Activity，
        // 不提供这两个 owner。这里照 ComponentActivity 的做法挂到 decorView 上 —— 必须挂在
        // 祖先节点：ComposeView 附加时会从「根视图」（此处为 SDL 的 RelativeLayout）向上查找，
        // 只挂在 ComposeView 自身是找不到的（实测 FATAL: ViewTreeLifecycleOwner not found）。
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        // SDL 的根布局也要挂一份：ComposeView 附加时是从「根视图」（这里是 SDL 的
        // RelativeLayout）开始向上找 owner 的，挂在最早能被查到的那一层最稳妥。
        mLayout?.let { layout ->
            layout.setViewTreeLifecycleOwner(this)
            layout.setViewTreeSavedStateRegistryOwner(this)
        }

        initializeFullscreenManager()
        initializeVirtualControls()
        requestHighRefreshRate("onCreate")

        AppLog.i(TAG, "=== GameActivitySdl2 (SDL2 · Android) Process Started ===")
        AppLog.i(TAG, "Game process PID: ${Process.myPid()}")
    }

    override fun onStart() {
        super.onStart()
        composeOwner.onStart()
    }

    override fun onResume() {
        super.onResume()
        composeOwner.onResume()
        requestHighRefreshRate("onResume")
        startGameWhenSurfaceReady()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        fullscreenManager?.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            requestHighRefreshRate("onWindowFocusChanged")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 先交给 Compose 的 ActivityResultRegistry（悬浮菜单里的图片选择器等）
        val handled = runCatching {
            composeOwner.dispatchResult(requestCode, resultCode, data)
        }.getOrDefault(false)
        if (handled) return
        if (requestCode == CONTROL_EDITOR_REQUEST_CODE && resultCode == RESULT_OK) {
            virtualControlsManager.onActivityResultReload()
        }
    }

    // ==================== RAL 悬浮菜单 / 虚拟按键 ====================

    private fun initializeFullscreenManager() {
        fullscreenManager = GameFullscreenManager(this).apply {
            enableFullscreen()
            configureIME()
        }
    }

    /**
     * 把 RAL 的虚拟按键层挂到 SDL 自己的视图树上（与 SDL3 的 GameActivity 同一套管理器）。
     * SDLActivity 已把 SDLSurface 放进 mLayout，这里再把 ControlLayout + 悬浮球叠上去。
     */
    private fun initializeVirtualControls() {
        val layout = mLayout
        val surface = mSurface
        if (layout == null || surface == null) {
            AppLog.w(TAG, "SDL 视图树尚未就绪，跳过虚拟按键初始化")
            return
        }
        virtualControlsManager.initialize(
            activity = this,
            sdlLayout = layout,
            sdlSurface = surface,
            onExitGame = { finish() }
        )
        AppLog.i(TAG, "虚拟按键与悬浮菜单已挂载")
    }


    /** 返回键：切换悬浮球可见性（与 SDL3 路径一致），不退出游戏 */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                virtualControlsManager.toggleFloatingBall()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * SDL 的 Surface 就绪后再拉起游戏。
     *
     * SDL_Init(SDL_INIT_VIDEO) 会向 Java 胶水层索取 Surface（EGL 窗口要用它），
     * 太早启动会拿到空 Surfac；这里等 SDLSurface 报告就绪。
     */
    private fun startGameWhenSurfaceReady() {
        if (gameThreadStarted) return

        val surfaceReady = runCatching { mSurface != null && mSurface.mIsSurfaceReady }
            .getOrDefault(false)
        if (!surfaceReady) {
            window.decorView.postDelayed({ startGameWhenSurfaceReady() }, 50L)
            return
        }

        gameThreadStarted = true
        AppLog.i(TAG, "SDL Surface 就绪，启动游戏线程")

        // 关键：游戏主循环不能占用 UI 线程，否则本 Activity 无法完成首帧绘制，
        // 系统会一直保留启动窗口（Splash Screen）并把本 Activity 的图层判为 hidden，
        // 现象就是「日志一切正常、屏幕上却没有画面」。
        Thread({
            val code = runCatching { presenter.launchGame() }.getOrElse { error ->
                AppLog.e(TAG, "launchGame 异常: ${error.message}", error)
                -1
            }
            AppLog.i(TAG, "launchGame() -> $code")
            if (code != 0) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.game_launch_failed_detail, GameLauncher.getLastErrorMessage()),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }, "ral-game").start()
    }

    private fun applyThemeMode() {
        val nightMode = when (SettingsAccess.themeMode) {
            ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 与 GameActivity 一致：返回键交给悬浮菜单，不做默认处理
    }

    override fun onPause() {
        composeOwner.onPause()
        super.onPause()
    }

    override fun onStop() {
        composeOwner.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        AppLog.d(TAG, "GameActivitySdl2.onDestroy()")
        composeOwner.onDestroy()
        virtualControlsManager.stop()
        instance = null
        presenter.detach()
        super.onDestroy()

        // .NET runtime 不支持多次初始化：本 Activity 运行在独立进程，直接终止以确保干净状态
        Handler(Looper.getMainLooper()).postDelayed({
            AppLog.d(TAG, "Terminating game process to ensure clean .NET runtime state")
            Process.killProcess(Process.myPid())
            System.exit(0)
        }, 100)
    }

    // ==================== GameContract.View ====================

    override fun showToast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    override fun showError(title: String, message: String) {
        runOnUiThread { showToast("$title: $message") }
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

    private fun requestHighRefreshRate(caller: String) {
        val display = getCurrentDisplay() ?: run {
            AppLog.w(TAG, "[$caller] Display is null, skip refresh vote")
            return
        }
        val targetMode = selectTargetMode(display)
        val targetRefresh = targetMode?.refreshRate ?: display.refreshRate

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && targetMode != null) {
            try {
                val params = window.attributes
                if (params.preferredDisplayModeId != targetMode.modeId) {
                    params.preferredDisplayModeId = targetMode.modeId
                    window.attributes = params
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "[$caller] 设置显示模式失败: ${e.message}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sdlSurface = mSurface?.holder?.surface
            if (sdlSurface != null && sdlSurface.isValid) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        sdlSurface.setFrameRate(
                            targetRefresh,
                            Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                            Surface.CHANGE_FRAME_RATE_ALWAYS
                        )
                    } else {
                        sdlSurface.setFrameRate(targetRefresh, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                    }
                } catch (e: Throwable) {
                    AppLog.w(TAG, "[$caller] Surface 刷新率投票失败: ${e.message}")
                }
            }
        }

        if (lastRequestedRefreshRate != targetRefresh) {
            lastRequestedRefreshRate = targetRefresh
            AppLog.i(TAG, "[$caller] 目标刷新率 ${targetRefresh}Hz")
        }
    }

    private fun getCurrentDisplay(): Display? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.let { return it }
        }
        val displayManager = getSystemService(DisplayManager::class.java) ?: return null
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    }

    private fun selectTargetMode(display: Display): Display.Mode? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return try {
            val currentMode = display.mode ?: return null
            val supportedModes = display.supportedModes ?: return null
            val sameResolutionModes = supportedModes.filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }
            val candidates = if (sameResolutionModes.isNotEmpty()) sameResolutionModes else supportedModes.toList()
            candidates.maxByOrNull { it.refreshRate }
        } catch (e: Exception) {
            AppLog.w(TAG, "选择显示模式失败: ${e.message}")
            null
        }
    }

    override fun getAppVersionName(): String? {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }
}
