package com.app.ralaunch.controls.editors

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.app.ralaunch.controls.editors.ui.ControlEditorScreen
import com.app.ralaunch.shared.ui.theme.AppThemeState
import com.app.ralaunch.shared.ui.theme.RaLaunchTheme
import com.app.ralaunch.controls.packs.ControlPackManager
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.data.SettingsAccess
import com.app.ralaunch.manager.DynamicColorManager
import com.app.ralaunch.controls.textures.TextureLoader
import com.app.ralaunch.utils.DensityAdapter
import com.app.ralaunch.utils.LocaleManager
import java.io.File
import java.io.FileOutputStream

/**
 * 控件编辑器 Activity
 * 已重构为 Jetpack Compose 架构
 * 
 * 数据恢复策略：
 * - ViewModel 在配置变化（如屏幕旋转）时保持存活
 * - 在 onStop 时自动保存布局草稿，防止进程被杀死导致数据丢失
 * - 使用 SavedStateHandle (可选) 保存编辑状态
 */
class ControlEditorActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ControlEditorActivity"
        const val EXTRA_LAYOUT_ID = "layout_id"

        fun start(context: Context, layoutId: String?) {
            val intent = android.content.Intent(context, ControlEditorActivity::class.java)
            intent.putExtra(EXTRA_LAYOUT_ID, layoutId)
            context.startActivity(intent)
        }
    }

    private val viewModel: ControlEditorViewModel by viewModels()
    private val packManager: ControlPackManager by lazy {
        KoinJavaComponent.get(ControlPackManager::class.java)
    }

    // 图片选择器
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImagePicked(it) }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LocaleManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DensityAdapter.adapt(this, true)

        // 应用动态颜色主题
        val dynamicColorManager = DynamicColorManager.getInstance()
        val settingsManager = SettingsAccess
        dynamicColorManager.applyCustomThemeColor(this, settingsManager.themeColor)

        // 应用其他主题设置
        AppCompatDelegate.setDefaultNightMode(
            if (settingsManager.themeMode == 0) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM 
            else if (settingsManager.themeMode == 1) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        // 设置全屏沉浸模式
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val packId = intent.getStringExtra(EXTRA_LAYOUT_ID)
        if (packId.isNullOrEmpty()) {
            finish()
            return
        }
        
        viewModel.loadLayout(packId)

        setContent {
            val layout by viewModel.layoutState.collectAsState()
            val selectedControl by viewModel.selectedControl.collectAsState()
            val isPropertyPanelVisible by viewModel.isPropertyPanelVisible.collectAsState()
            val isPaletteVisible by viewModel.isPaletteVisible.collectAsState()
            val pickImageRequest by viewModel.pickImageRequest.collectAsState()
            
            // 获取主题状态
            val themeMode by AppThemeState.themeMode.collectAsState()
            val themeColor by AppThemeState.themeColor.collectAsState()

            // 监听图片选择请求
            LaunchedEffect(pickImageRequest) {
                if (pickImageRequest) {
                    viewModel.clearPickImageRequest()
                    pickImageLauncher.launch("image/*")
                }
            }

            RaLaunchTheme(
                themeMode = themeMode,
                themeColor = themeColor
            ) {
                ControlEditorScreen(
                    viewModel = viewModel,
                    layout = layout,
                    selectedControl = selectedControl,
                    isPropertyPanelVisible = isPropertyPanelVisible,
                    isPaletteVisible = isPaletteVisible,
                    onExit = { finish() }
                )
            }
        }
    }

    /**
     * 获取当前编辑的控件包ID
     */
    fun getCurrentPackId(): String? = intent.getStringExtra(EXTRA_LAYOUT_ID)

    /**
     * 处理图片选择结果
     * 将图片复制到控件包的 assets/textures 目录
     */
    private fun handleImagePicked(uri: Uri) {
        try {
            val packId = getCurrentPackId() ?: return
            
            // 获取或创建控件包的 assets 目录
            val assetsDir = packManager.getOrCreatePackAssetsDir(packId)
            val texturesDir = File(assetsDir, "textures")
            if (!texturesDir.exists()) {
                texturesDir.mkdirs()
            }

            // 生成唯一文件名
            val fileName = "texture_${System.currentTimeMillis()}.png"
            val destFile = File(texturesDir, fileName)

            // 复制图片到目标目录
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // 清除纹理缓存，确保新纹理立即生效
            val textureLoader = TextureLoader.getInstance(this)
            textureLoader.evictFromCache(destFile.absolutePath)

            // 通知 ViewModel 图片已选择
            val relativePath = "textures/$fileName"
            viewModel.onImagePicked(relativePath)
            
            Log.d(TAG, "Image copied to: ${destFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image", e)
        }
    }

    /**
     * 更新控件数据并同步到布局视图，然后保存
     */
    fun updateControlData(data: com.app.ralaunch.controls.data.ControlData) {
        viewModel.updateControl(data)
        viewModel.saveLayout()
    }

    /**
     * 刷新控件包的 assets 目录
     */
    fun refreshPackAssetsDir() {
        // Compose 架构下，assets 目录的刷新逻辑可以通过 ViewModel 状态驱动
        // 暂时留空，后续在 Screen 中处理
    }

    /**
     * Activity 进入后台时自动保存布局
     * 防止进程被系统杀死导致数据丢失
     */
    override fun onStop() {
        super.onStop()
        // 自动保存草稿（如果有未保存的更改）
        if (viewModel.hasUnsavedChanges.value) {
            Log.d(TAG, "onStop: Auto-saving layout draft...")
            viewModel.saveLayout()
        }
    }

    /**
     * Activity 被销毁时保存布局
     * 确保配置变化或进程终止时数据不丢失
     */
    override fun onDestroy() {
        // 在非配置变化的销毁情况下保存数据
        if (!isChangingConfigurations && viewModel.hasUnsavedChanges.value) {
            Log.d(TAG, "onDestroy: Saving layout before destruction...")
            viewModel.saveLayout()
        }
        super.onDestroy()
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 检查未保存变更，会显示确认对话框
        if (!viewModel.requestExit()) {
            // 对话框已显示，等待用户操作
            return
        }
        finish()
    }
}
