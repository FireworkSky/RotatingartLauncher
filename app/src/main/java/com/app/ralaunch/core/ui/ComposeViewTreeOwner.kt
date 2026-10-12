package com.app.ralaunch.core.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * 给「非 AppCompatActivity」的 Activity 补上 Compose 需要的 ViewTree owners。
 *
 * 背景：SDL2/SDL3 的 Java 胶水层（org.libsdl.app2.SDLActivity / org.libsdl.app.SDLActivity）
 * 都直接继承 android.app.Activity，不提供 LifecycleOwner / SavedStateRegistryOwner。
 * 而 ComposeView 在 onAttachedToWindow 时会向上查找 ViewTreeLifecycleOwner，
 * 找不到就抛 IllegalStateException（实测为 FATAL EXCEPTION: SDLActivity）：
 *   ViewTreeLifecycleOwner not found from android.widget.RelativeLayout{...}
 * RAL 的虚拟按键/悬浮菜单是 Compose 实现（[com.app.ralaunch.feature.game.GameVirtualControlsManager]），
 * 而游戏界面又必须继承 SDLActivity，所以在这里提供一个可委托的 owner：
 *
 * ```kotlin
 * private val composeOwner = ComposeViewTreeOwner()
 * override val lifecycle get() = composeOwner.lifecycle
 * override val savedStateRegistry get() = composeOwner.savedStateRegistry
 * // 并在 onCreate/onStart/onResume/onPause/onStop/onDestroy 里转发到 composeOwner
 * ```
 */
class ComposeViewTreeOwner(private val activity: Activity) :
    LifecycleOwner, SavedStateRegistryOwner, ActivityResultRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    /**
     * 精简版 ActivityResultRegistry：把 onLaunch 直接落到
     * Activity.startActivityForResult，结果经 [dispatchResult] 回灌。
     *
     * 悬浮菜单里的「选择控件贴图」用的是 rememberLauncherForActivityResult(GetContent())，
     * Compose 会从 LocalActivityResultRegistryOwner 取注册表；纯 Activity 没有 ComponentActivity
     * 提供的那一份，缺失时抛：
     *   IllegalStateException: No ActivityResultRegistryOwner was provided via LocalActivityResultRegistryOwner
     */
    override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            val intent = contract.createIntent(activity, input)
            ActivityCompat.startActivityForResult(activity, intent, requestCode, options?.toBundle())
        }
    }

    /** 把 Activity.onActivityResult 转交给上面的注册表 */
    fun dispatchResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean =
        activityResultRegistry.dispatchResult(requestCode, resultCode, data)

    fun onCreate(savedInstanceState: Bundle?) {
        savedStateController.performRestore(savedInstanceState)
    }

    fun onStart() {
        registry.currentState = Lifecycle.State.STARTED
    }

    fun onResume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun onPause() {
        registry.currentState = Lifecycle.State.STARTED
    }

    fun onStop() {
        registry.currentState = Lifecycle.State.CREATED
    }

    fun onDestroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
