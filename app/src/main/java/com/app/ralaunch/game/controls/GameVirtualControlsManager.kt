package com.app.ralaunch.game.controls

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.app.ralaunch.R
import com.app.ralaunch.controls.bridges.SDLInputBridge
import com.app.ralaunch.controls.editors.ui.GameControlsOverlay
import com.app.ralaunch.controls.packs.ControlPackManager
import com.app.ralaunch.controls.views.ControlLayout
import com.app.ralaunch.data.SettingsAccess
import com.app.ralaunch.utils.AppLogger
import com.app.ralaunch.view.FPSDisplayView
import org.koin.java.KoinJavaComponent
import org.libsdl.app.SDLSurface
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import com.app.ralaunch.manager.DynamicColorManager
import com.app.ralaunch.shared.ui.theme.RaLaunchTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 负责虚拟按键和 FPS 叠层的初始化与显示控制
 * 使用 Compose UI 实现悬浮菜单
 */
class GameVirtualControlsManager {

    var controlLayout: ControlLayout? = null
        private set
    var inputBridge: SDLInputBridge? = null
        private set
    private var fpsDisplayView: FPSDisplayView? = null
    private var settingsManager: SettingsAccess? = null
    private var composeOverlay: ComposeView? = null
    private var onExitGameCallback: (() -> Unit)? = null
    
    // 悬浮球可见性切换事件流
    private val _toggleFloatingBallEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val toggleFloatingBallEvent = _toggleFloatingBallEvent.asSharedFlow()
    
    /**
     * 切换悬浮球可见性 (通过返回键触发)
     */
    fun toggleFloatingBall() {
        _toggleFloatingBallEvent.tryEmit(Unit)
    }

    fun initialize(
        activity: Activity,
        sdlLayout: ViewGroup?,
        sdlSurface: SDLSurface?,
        disableSDLTextInput: Runnable,
        onExitGame: () -> Unit = {}
    ) {
        try {
            settingsManager = SettingsAccess
            inputBridge = SDLInputBridge()
            onExitGameCallback = onExitGame

            val metrics = activity.resources.displayMetrics
            SDLInputBridge.setScreenSize(metrics.widthPixels, metrics.heightPixels)

            controlLayout = ControlLayout(activity).apply {
                this.inputBridge = this@GameVirtualControlsManager.inputBridge
                loadLayoutFromPackManager()
            }
            disableClippingRecursive(controlLayout!!)

            val params = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            sdlLayout?.let { layout ->
                // 添加 ControlLayout
                layout.addView(controlLayout, params)

                sdlSurface?.let { surface ->
                    controlLayout?.setSDLSurface(surface)
                    surface.setVirtualControlsManager(this)
                }

                // 添加 FPS 显示
                fpsDisplayView = FPSDisplayView(activity).apply {
                    setInputBridge(inputBridge)
                }
                layout.addView(fpsDisplayView, params)
                fpsDisplayView?.start()

                // 添加 Compose 悬浮菜单
                setupComposeOverlay(activity, layout)

                layout.postDelayed(disableSDLTextInput, 2000)
            }
        } catch (e: Exception) {
            AppLogger.error("GameVirtualControls", "Failed to initialize virtual controls", e)
        }
    }

    private fun setupComposeOverlay(activity: Activity, parentLayout: ViewGroup) {
        val packManager: ControlPackManager = KoinJavaComponent.get(ControlPackManager::class.java)
        val settings = settingsManager ?: return
        val control = controlLayout ?: return

        // 追踪编辑模式状态
        var isInEditMode = false
        // 追踪当前触摸序列由谁处理: 0=未定, 1=Compose, 2=ControlLayout
        var currentTouchTarget = 0
        // Compose 活跃触摸区域（菜单展开、属性面板显示时的边界）
        var composeActiveRect: android.graphics.RectF? = null

        composeOverlay = ComposeView(activity).apply {
            // 设置 ViewTree owners (必须，否则 Compose 不工作)
            if (activity is AppCompatActivity) {
                setViewTreeLifecycleOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
            }

            setContent {
                RaLaunchTheme {
                    GameControlsOverlay(
                        controlLayoutView = control,
                        packManager = packManager,
                        settingsManager = settings,
                        toggleFloatingBallEvent = toggleFloatingBallEvent,
                        onExitGame = { onExitGameCallback?.invoke() },
                        onEditModeChanged = { inEditMode -> 
                            isInEditMode = inEditMode
                            control.isModifiable = inEditMode
                        },
                        onActiveAreaChanged = { rect ->
                            composeActiveRect = rect
                        }
                    )
                }
            }
        }

        // 使用包装 FrameLayout 处理触摸事件分发
        val wrapperLayout = object : android.widget.FrameLayout(activity) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
                if (ev == null) return false
                
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        currentTouchTarget = 0
                        val x = ev.x
                        val y = ev.y
                        
                        // 检查触摸点是否在 Compose 活跃区域（菜单、属性面板等）
                        val inComposeActiveArea = composeActiveRect?.contains(x, y) == true
                        
                        if (inComposeActiveArea) {
                            // 触摸在 Compose 活跃区域，让 Compose 处理
                            val composeHandled = super.dispatchTouchEvent(ev)
                            if (composeHandled) {
                                currentTouchTarget = 1
                                return true
                            }
                        }
                        
                        if (isInEditMode) {
                            // 编辑模式：让 ControlLayout 尝试处理（点击控件进行编辑）
                            if (control.dispatchTouchEvent(ev)) {
                                currentTouchTarget = 2  // ControlLayout 处理了
                                return true
                            }
                        }
                        
                        // 最后让 Compose 处理（悬浮球等）
                        val composeHandled = super.dispatchTouchEvent(ev)
                        if (composeHandled) {
                            currentTouchTarget = 1
                            return true
                        }
                        
                        return false
                    }
                    android.view.MotionEvent.ACTION_MOVE,
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL,
                    android.view.MotionEvent.ACTION_POINTER_DOWN,
                    android.view.MotionEvent.ACTION_POINTER_UP -> {
                        return when (currentTouchTarget) {
                            2 -> control.dispatchTouchEvent(ev)  // ControlLayout
                            1 -> super.dispatchTouchEvent(ev)  // Compose
                            else -> super.dispatchTouchEvent(ev)
                        }
                    }
                    else -> return super.dispatchTouchEvent(ev)
                }
            }
        }
        
        wrapperLayout.addView(composeOverlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        parentLayout.addView(wrapperLayout, params)
    }

    fun onActivityResultReload() {
        controlLayout?.apply {
            loadLayoutFromPackManager()
            disableClippingRecursive(this)
        }
    }

    fun toggle(activity: Activity) {
        controlLayout?.let {
            val visible = !it.isControlsVisible
            it.isControlsVisible = visible
            Toast.makeText(
                activity,
                if (visible) R.string.game_menu_controls_on else R.string.game_menu_controls_off,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun setVisible(visible: Boolean) {
        controlLayout?.isControlsVisible = visible
    }

    fun stop() {
        fpsDisplayView?.stop()
        com.app.ralaunch.console.ConsoleManager.stop()
    }

    private fun disableClippingRecursive(view: View) {
        if (view is ViewGroup) {
            view.clipChildren = false
            view.clipToPadding = false
            for (i in 0 until view.childCount) {
                disableClippingRecursive(view.getChildAt(i))
            }
        }
        view.clipToOutline = false
        view.clipBounds = null
    }
}
