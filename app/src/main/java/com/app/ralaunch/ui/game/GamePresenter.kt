package com.app.ralaunch.ui.game

import android.content.Context
import android.os.Build
import com.app.ralaunch.R
import com.app.ralaunch.core.GameLauncher
import com.app.ralaunch.patch.PatchManager
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.utils.AppLogger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 游戏界面 Presenter
 * 处理游戏启动和崩溃报告相关的业务逻辑
 */
class GamePresenter : GameContract.Presenter {

    companion object {
        private const val TAG = "GamePresenter"
        private const val MAX_LOG_LINES = 200
        private const val MAX_LOG_LENGTH = 50000
    }

    private var viewRef: WeakReference<GameContract.View>? = null
    private val view: GameContract.View? get() = viewRef?.get()

    override fun attach(view: GameContract.View) {
        this.viewRef = WeakReference(view)
    }

    override fun detach() {
        this.viewRef = null
    }

    override fun launchGame(): Int {
        val view = this.view ?: return -1
        
        // 重置 GameLauncher 初始化状态，确保每次启动都重新初始化
        GameLauncher.resetInitializationState()
        
        return try {
            val intent = view.getActivityIntent()

            val assemblyPath = intent.getStringExtra("ASSEMBLY_PATH")

            if (assemblyPath.isNullOrEmpty()) {
                AppLogger.error(TAG, "Assembly path is null or empty")
                view.runOnMainThread {
                    view.showError(
                        view.getStringRes(R.string.game_launch_failed),
                        view.getStringRes(R.string.game_launch_assembly_path_empty)
                    )
                }
                return -1
            }

            val assemblyFile = File(assemblyPath)
            if (!assemblyFile.exists() || !assemblyFile.isFile) {
                AppLogger.error(TAG, "Assembly file not found: $assemblyPath")
                view.runOnMainThread {
                    view.showError(
                        view.getStringRes(R.string.game_launch_failed),
                        view.getStringRes(R.string.game_launch_assembly_not_exist, assemblyPath)
                    )
                }
                return -2
            }
            
            // 应用游戏特定的默认渲染器
            val defaultRenderer = intent.getStringExtra("DEFAULT_RENDERER")
            if (!defaultRenderer.isNullOrEmpty()) {
                AppLogger.info(TAG, "Applying game-specific renderer: $defaultRenderer")
                val appContext: Context = KoinJavaComponent.get(Context::class.java)
                com.app.ralaunch.renderer.RendererConfig.setRenderer(appContext, defaultRenderer)
            }

                    val enabledPatchIds = intent.getStringArrayListExtra("ENABLED_PATCH_IDS")
                    val patchManager: PatchManager? = try {
                        KoinJavaComponent.getOrNull(PatchManager::class.java)
                    } catch (e: Exception) { null }
                    val enabledPatches = enabledPatchIds?.takeIf { it.isNotEmpty() }?.let {
                        patchManager?.getPatchesByIds(it)
                    }
                    
            val exitCode = GameLauncher.launchDotNetAssembly(assemblyPath, emptyArray(), enabledPatches).also { code ->
                        onGameExit(code, GameLauncher.getLastErrorMessage())
            }

            if (exitCode == 0) {
                AppLogger.info(TAG, "Game exited successfully.")
            } else {
                AppLogger.error(TAG, "Failed to launch game: $exitCode")
            }
            exitCode
        } catch (e: Exception) {
            AppLogger.error(TAG, "Exception in launchGame: ${e.message}", e)
            view.runOnMainThread {
                view.showError(view.getStringRes(R.string.game_launch_failed), e.message ?: "Unknown error")
            }
            -3
        }
    }

    override fun onGameExit(exitCode: Int, errorMessage: String?) {
        val view = this.view ?: return

        view.runOnMainThread {
            if (exitCode == 0) {
                view.showToast(view.getStringRes(R.string.game_completed_successfully))
            } else {
                showCrashReport(exitCode, errorMessage)
            }
        }
    }

    private fun showCrashReport(exitCode: Int, errorMessage: String?) {
        val view = this.view ?: return

        try {
            val nativeError = try {
                GameLauncher.getLastErrorMessage()
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Failed to get native error", e)
                null
            }

            val logcatLogs = getRecentLogcatLogs()
            val message = buildExitMessage(view, exitCode, errorMessage)
            val errorDetails = buildErrorDetails(view, exitCode, nativeError, errorMessage)
            val stackTrace = buildStackTrace(exitCode, nativeError, logcatLogs, errorMessage)

            view.showCrashReport(
                stackTrace = stackTrace,
                errorDetails = errorDetails,
                exceptionClass = "GameExitException",
                exceptionMessage = message
            )
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to show crash report", e)
            val message = buildExitMessage(view, exitCode, errorMessage)
            view.showError(view.getStringRes(R.string.game_run_failed), message)
            view.finishActivity()
        }
    }

    private fun buildExitMessage(view: GameContract.View, exitCode: Int, errorMessage: String?): String {
        return if (!errorMessage.isNullOrEmpty()) {
            "$errorMessage\n${view.getStringRes(R.string.game_exit_code, exitCode)}"
        } else {
            view.getStringRes(R.string.game_exit_code, exitCode)
        }
    }

    private fun buildErrorDetails(
        view: GameContract.View,
        exitCode: Int,
        nativeError: String?,
        errorMessage: String?
    ): String {
        return buildString {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            append("发生时间: ${sdf.format(Date())}\n\n")

            val versionName = view.getAppVersionName() ?: "未知"
            append("应用版本: $versionName\n")
            append("设备型号: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android 版本: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n\n")

            append("错误类型: 游戏异常退出\n")
            append("退出代码: $exitCode\n")

            if (!nativeError.isNullOrEmpty()) {
                append("C层错误: $nativeError\n")
            }

            if (!errorMessage.isNullOrEmpty()) {
                append("错误信息: $errorMessage\n")
            }
        }
    }

    private fun buildStackTrace(
        exitCode: Int,
        nativeError: String?,
        logcatLogs: String?,
        errorMessage: String?
    ): String {
        return buildString {
            append("游戏进程异常退出\n")
            append("退出代码: $exitCode\n\n")

            if (!nativeError.isNullOrEmpty()) {
                append("=== C层错误信息 ===\n")
                append("$nativeError\n\n")
            }

            if (!logcatLogs.isNullOrEmpty()) {
                append("=== Logcat 日志（最近错误） ===\n")
                append("$logcatLogs\n\n")
            }

            if (!errorMessage.isNullOrEmpty()) {
                append("=== 错误详情 ===\n")
                append(errorMessage)
            }
        }
    }

    private fun getRecentLogcatLogs(): String? {
        return try {
            // 关键日志标签列表 - 用于捕获所有重要的运行时信息
            val importantTags = listOf(
                // 核心组件
                "GameLauncher", "GamePresenter", "RuntimeLibLoader", "RuntimeLibraryLoader",
                // 渲染器
                "RendererConfig", "RendererLoader",
                // .NET 运行时
                "NetCoreHost", "DotNetLauncher", "CoreCLR", "MonoGame",
                // SDL 和音频
                "SDL", "SDL_android", "SDLSurface", "FNA3D", "OpenAL", "FMOD",
                // 系统层
                "libc", "linker", "art", "dalvikvm",
                // 错误相关
                "FATAL", "AndroidRuntime", "System.err"
            )
            
            // 构建 logcat 过滤器
            val tagFilters = importantTags.flatMap { listOf("$it:V") }.toTypedArray()
            val cmd = arrayOf("logcat", "-d", "-v", "threadtime", "-t", "500", "*:S") + tagFilters
            
            val process = Runtime.getRuntime().exec(cmd)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val allLogs = reader.readLines()
            process.destroy()

            // 过滤和整理日志
            val filteredLogs = allLogs
                .filter { line ->
                    // 过滤掉空行和无关的系统日志
                    line.isNotBlank() && 
                    !line.contains("GC_") && 
                    !line.contains("Choreographer") &&
                    !line.contains("ViewRootImpl")
                }
                .takeLast(MAX_LOG_LINES)
                .joinToString("\n")

            // 如果没有找到有用的日志，尝试获取所有错误级别的日志
            if (filteredLogs.isEmpty()) {
                return getErrorLevelLogs()
            }

            var result = filteredLogs
            if (result.length > MAX_LOG_LENGTH) {
                result = "...[日志已截断]...\n" + result.takeLast(MAX_LOG_LENGTH)
            }

            result.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to get logcat logs", e)
            getErrorLevelLogs()
        }
    }

    /**
     * 获取错误级别的日志（备用方案）
     */
    private fun getErrorLevelLogs(): String? {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "threadtime", "-t", "300", "*:E")
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val logs = reader.readLines()
                .filter { line ->
                    line.isNotBlank() &&
                    (line.contains("ralaunch", ignoreCase = true) ||
                     line.contains("sdl", ignoreCase = true) ||
                     line.contains("runtime", ignoreCase = true) ||
                     line.contains("dotnet", ignoreCase = true) ||
                     line.contains("Error") ||
                     line.contains("Exception") ||
                     line.contains("FATAL"))
                }
                .takeLast(100)
                .joinToString("\n")
            
            process.destroy()
            logs.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}
