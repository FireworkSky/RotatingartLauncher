package com.app.ralaunch.controls.editors

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.bridges.DummyInputBridge
import com.app.ralaunch.controls.packs.ControlLayout
import com.app.ralaunch.controls.packs.ControlPackManager
import com.app.ralaunch.controls.views.ControlLayout as ControlLayoutView
import com.app.ralaunch.controls.views.GridOverlayView
import com.app.ralaunch.data.SettingsManager
import com.app.ralaunch.manager.DynamicColorManager
import com.app.ralaunch.utils.DensityAdapter
import com.app.ralaunch.utils.LocaleManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 控件编辑器 Activity
 *
 * 独立的控件编辑界面，用于在游戏外编辑控件布局。
 * 使用统一的 ControlEditorManager (MODE_STANDALONE) 管理编辑逻辑。
 */
class ControlEditorActivity : AppCompatActivity() {

    private val packManager: ControlPackManager
        get() = RaLaunchApplication.getControlPackManager()

    private var mEditorContainer: FrameLayout? = null
    private var mPreviewLayout: ControlLayoutView? = null
    private var mGridOverlay: GridOverlayView? = null

    // 统一的控件编辑管理器
    private var mEditorManager: ControlEditorManager? = null

    private var mCurrentLayout: ControlLayout? = null
    private var mCurrentPackId: String? = null

    private val mDummyBridge = DummyInputBridge()

    override fun attachBaseContext(newBase: Context?) {
        // 应用语言设置
        super.attachBaseContext(LocaleManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DensityAdapter.adapt(this, true)

        // 应用动态颜色主题（必须在 super.onCreate 之前，避免闪烁）
        val dynamicColorManager = DynamicColorManager.getInstance()
        val settingsManager = SettingsManager.getInstance(this)
        dynamicColorManager.applyCustomThemeColor(this, settingsManager.themeColor)

        // 应用其他主题设置（深色/浅色模式等）
        AppCompatDelegate.setDefaultNightMode(
            if (settingsManager.themeMode == 0) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM 
            else if (settingsManager.themeMode == 1) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        // 设置全屏沉浸模式并隐藏刘海屏
        window.decorView.setSystemUiVisibility(
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        )

        // Android P+ 隐藏刘海屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_control_editor)

        // 获取要编辑的布局ID
        val packId = intent.getStringExtra(EXTRA_LAYOUT_ID)
        if (packId.isNullOrEmpty()) {
            Log.e(TAG, "No pack ID provided to editor, finishing activity")
            finish()
            return
        }
        
        mCurrentPackId = packId
        mCurrentLayout = packManager.getPackLayout(packId)
        
        if (mCurrentLayout == null) {
            Log.e(TAG, "Failed to load layout for pack ID: $packId, finishing activity")
            finish()
            return
        }

        initUI()

        // 延迟加载布局
        mEditorContainer!!.post { loadLayout() }
    }

    private fun initUI() {
        mEditorContainer = findViewById<FrameLayout>(R.id.editor_container)

        // 设置按钮点击显示设置弹窗，并支持拖动
        val drawerButton = findViewById<View?>(R.id.drawer_button)
        setupDraggableButton(drawerButton) {
            mEditorManager?.showSettingsDialog()
        }
    }

    /**
     * 设置可拖动的按钮
     */
    private fun setupDraggableButton(button: View?, onClickAction: Runnable?) {
        if (button == null) return

        button.setOnClickListener { v: View? ->
            onClickAction?.run()
        }

        button.setOnTouchListener(object : View.OnTouchListener {
            private var mLastX = 0f
            private var mLastY = 0f
            private var mInitialTouchX = 0f
            private var mInitialTouchY = 0f
            private var mInitialButtonX = 0f
            private var mInitialButtonY = 0f
            private var mIsDragging = false
            private val DRAG_THRESHOLD = 10f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        mLastX = event.rawX
                        mLastY = event.rawY
                        mInitialTouchX = event.rawX
                        mInitialTouchY = event.rawY

                        val location = IntArray(2)
                        v.getLocationOnScreen(location)
                        mInitialButtonX = location[0].toFloat()
                        mInitialButtonY = location[1].toFloat()

                        mIsDragging = false
                        return false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - mLastX
                        val deltaY = event.rawY - mLastY
                        val distance = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()

                        if (distance > DRAG_THRESHOLD) {
                            if (!mIsDragging) {
                                mIsDragging = true
                                v.parent.requestDisallowInterceptTouchEvent(true)
                            }

                            var newScreenX = mInitialButtonX + (event.rawX - mInitialTouchX)
                            var newScreenY = mInitialButtonY + (event.rawY - mInitialTouchY)

                            val metrics = resources.displayMetrics
                            val maxX = metrics.widthPixels - v.width
                            val maxY = metrics.heightPixels - v.height
                            newScreenX = max(0f, min(newScreenX, maxX.toFloat()))
                            newScreenY = max(0f, min(newScreenY, maxY.toFloat()))

                            val parentLocation = IntArray(2)
                            (v.parent as View).getLocationOnScreen(parentLocation)
                            val newX = newScreenX - parentLocation[0]
                            val newY = newScreenY - parentLocation[1]

                            if (v.parent is FrameLayout) {
                                v.x = newX
                                v.y = newY
                            } else {
                                val params = v.layoutParams as ViewGroup.MarginLayoutParams
                                params.leftMargin = newX.toInt()
                                params.topMargin = newY.toInt()
                                v.layoutParams = params
                            }

                            mLastX = event.rawX
                            mLastY = event.rawY
                            return true
                        }
                        return false
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (mIsDragging) {
                            mIsDragging = false
                            v.parent.requestDisallowInterceptTouchEvent(false)
                            return true
                        }
                        return false
                    }
                }
                return false
            }
        })
    }

    /**
     * 加载布局
     */
    private fun loadLayout() {
        displayLayout()
    }

    private fun displayLayout() {
        // 清除现有视图
        mEditorContainer!!.removeAllViews()

        // 添加网格覆盖层
        mGridOverlay = GridOverlayView(this)
        mEditorContainer!!.addView(
            mGridOverlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // 创建预览布局
        mPreviewLayout = ControlLayoutView(context = this)
        mPreviewLayout!!.inputBridge = mDummyBridge
        mPreviewLayout!!.loadLayout(mCurrentLayout)
        mPreviewLayout!!.isControlsVisible = true

        // 禁用裁剪
        disableClippingRecursive(mPreviewLayout!!)

        mEditorContainer!!.addView(
            mPreviewLayout, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // 创建或更新编辑管理器（使用独立模式，自动进入编辑状态）
        if (mEditorManager == null) {
            mEditorManager = ControlEditorManager(
                this, mPreviewLayout, mEditorContainer,
                ControlEditorManager.Mode.STANDALONE
            )
            mEditorManager?.setOnLayoutChangedListener(object : ControlEditorManager.OnLayoutChangedListener {
                override fun onLayoutChanged() {
                    // Layout changed callback
                }
            })
        } else {
            // 布局重新创建后更新引用
            mEditorManager?.setControlLayout(mPreviewLayout)
        }

        // 初始化设置对话框
        mEditorManager?.initEditorSettingsDialog()
    }

    /**
     * 递归禁用所有子视图的裁剪
     */
    private fun disableClippingRecursive(view: View) {
        if (view is ViewGroup) {
            val viewGroup = view
            viewGroup.clipChildren = false
            viewGroup.clipToPadding = false

            for (i in 0..<viewGroup.childCount) {
                disableClippingRecursive(viewGroup.getChildAt(i))
            }
        }

        view.clipToOutline = false
        view.clipBounds = null
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 先检查设置弹窗
        val editorManager = mEditorManager
        if (editorManager != null && editorManager.isSettingsDialogShowing) {
            editorManager.hideSettingsDialog()
            return
        }

        // 再检查控件编辑弹窗
        if (editorManager != null && editorManager.isEditDialogShowing) {
            editorManager.dismissEditDialog()
            return
        }

        // 检查是否有未保存的更改
        if (editorManager != null && editorManager.hasUnsavedChanges()) {
            // 有未保存的更改，显示退出确认对话框
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.editor_exit_title))
                .setMessage(getString(R.string.editor_exit_save_confirm))
                .setPositiveButton(getString(R.string.editor_save_and_exit)) { _, _ ->
                    editorManager.saveLayout(mCurrentPackId)
                    finish()
                }
                .setNegativeButton(getString(R.string.editor_exit)) { _, _ ->
                    finish()
                }
                .setNeutralButton(getString(R.string.cancel), null)
                .show()
        } else {
            // 没有未保存的更改，直接退出
            finish()
        }
    }

    companion object {
        private const val TAG = "ControlEditorActivity"

        const val EXTRA_LAYOUT_ID = "layout_id"

        fun start(context: Context, layoutId: String?) {
            val intent = android.content.Intent(context, ControlEditorActivity::class.java)
            intent.putExtra(EXTRA_LAYOUT_ID, layoutId)
            context.startActivity(intent)
        }
    }
}
