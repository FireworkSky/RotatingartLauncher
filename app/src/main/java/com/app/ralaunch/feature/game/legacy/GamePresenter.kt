package com.app.ralaunch.feature.game.legacy

import android.os.Build
import com.app.ralaunch.R
import com.app.ralaunch.core.platform.runtime.GameLauncher
import com.app.ralaunch.feature.patch.data.Patch
import com.app.ralaunch.feature.patch.data.PatchManager
import com.app.ralaunch.shared.core.contract.repository.GameRepositoryV2
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.core.common.util.AppLogger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Game Presenter
 * Handles business logic related to game launch and crash reporting
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

        // Reset GameLauncher initialization state
        GameLauncher.resetInitializationState()

        return try {
            val intent = view.getActivityIntent()
            val gameStorageId = normalizeOptional(intent.getStringExtra(GameActivity.EXTRA_GAME_STORAGE_ID))
            val gameExePath = normalizeOptional(intent.getStringExtra(GameActivity.EXTRA_GAME_EXE_PATH))

            when {
                gameStorageId != null && gameExePath != null -> {
                    AppLogger.error(TAG, "Invalid launch intent: both storage ID and direct launch params are provided")
                    showLaunchError(view, view.getStringRes(R.string.game_launch_invalid_params_conflict))
                    -1
                }
                gameStorageId != null -> launchFromStorageId(view, gameStorageId)
                gameExePath != null -> {
                    val gameArgs = intent.getStringArrayExtra(GameActivity.EXTRA_GAME_ARGS) ?: emptyArray()
                    val gameId = normalizeOptional(intent.getStringExtra(GameActivity.EXTRA_GAME_ID))
                    val rendererOverride = normalizeOptional(intent.getStringExtra(GameActivity.EXTRA_GAME_RENDERER_OVERRIDE))
                    val gameEnvVars = parseGameEnvVars(intent)
                    launchFromDirectParams(
                        view = view,
                        gameExePath = gameExePath,
                        gameArgs = gameArgs,
                        gameId = gameId,
                        gameRendererOverride = rendererOverride,
                        gameEnvVars = gameEnvVars
                    )
                }
                else -> {
                    AppLogger.error(TAG, "No supported launch parameters found in intent")
                    showLaunchError(view, view.getStringRes(R.string.game_launch_no_params))
                    -1
                }
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Exception in launchGame: ${e.message}", e)
            showLaunchError(view, e.message ?: view.getStringRes(R.string.common_unknown_error))
            -6
        }
    }

    private fun launchFromStorageId(view: GameContract.View, gameStorageId: String): Int {
        val gameRepository: GameRepositoryV2 = try {
            KoinJavaComponent.get(GameRepositoryV2::class.java)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to resolve GameRepositoryV2", e)
            showLaunchError(view, view.getStringRes(R.string.game_launch_repository_load_failed))
            return -2
        }

        val game = gameRepository.games.value.find { it.id == gameStorageId }
        if (game == null) {
            AppLogger.error(TAG, "Game not found for storage ID: $gameStorageId")
            showLaunchError(view, view.getStringRes(R.string.main_game_not_found, gameStorageId))
            return -3
        }

        val assemblyPath = game.gameExePathFull
        if (assemblyPath.isNullOrEmpty()) {
            AppLogger.error(TAG, "Assembly path is null or empty")
            showLaunchError(view, view.getStringRes(R.string.game_launch_assembly_path_empty))
            return -4
        }

        val assemblyFile = File(assemblyPath)
        if (!assemblyFile.exists() || !assemblyFile.isFile) {
            AppLogger.error(TAG, "Assembly file not found: $assemblyPath")
            showLaunchError(view, view.getStringRes(R.string.game_launch_assembly_not_exist, assemblyPath))
            return -5
        }

        val patchManager: PatchManager? = try {
            KoinJavaComponent.getOrNull(PatchManager::class.java)
        } catch (_: Exception) {
            null
        }
        
        // Pass File directly instead of Path
        val enabledPatches = patchManager
            ?.getApplicableAndEnabledPatches(game.gameId, assemblyFile)
            ?: emptyList()

        return launchAssembly(
            assemblyPath = assemblyPath,
            args = emptyArray(),
            enabledPatches = enabledPatches,
            rendererOverride = normalizeOptional(game.rendererOverride),
            gameEnvVars = game.gameEnvVars
        )
    }

    private fun launchFromDirectParams(
        view: GameContract.View,
        gameExePath: String,
        gameArgs: Array<String>,
        gameId: String?,
        gameRendererOverride: String?,
        gameEnvVars: Map<String, String?>
    ): Int {
        if (gameExePath.isBlank()) {
            AppLogger.error(TAG, "Direct launch assembly path is blank")
            showLaunchError(view, view.getStringRes(R.string.game_launch_assembly_path_empty))
            return -4
        }

        val assemblyFile = File(gameExePath)
        if (!assemblyFile.exists() || !assemblyFile.isFile) {
            AppLogger.error(TAG, "Direct launch assembly file not found: $gameExePath")
            showLaunchError(view, view.getStringRes(R.string.game_launch_assembly_not_exist, gameExePath))
            return -5
        }

        val patchManager: PatchManager? = try {
            KoinJavaComponent.getOrNull(PatchManager::class.java)
        } catch (_: Exception) {
            null
        }
        
        // Pass File directly instead of Path
        val enabledPatches = if (gameId != null) {
            patchManager?.getApplicableAndEnabledPatches(gameId, assemblyFile) ?: emptyList()
        } else {
            emptyList()
        }

        return launchAssembly(
            assemblyPath = gameExePath,
            args = gameArgs,
            enabledPatches = enabledPatches,
            rendererOverride = gameRendererOverride,
            gameEnvVars = gameEnvVars
        )
    }

    private fun launchAssembly(
        assemblyPath: String,
        args: Array<String>,
        enabledPatches: List<Patch>,
        rendererOverride: String?,
        gameEnvVars: Map<String, String?>
    ): Int {
        val exitCode = GameLauncher.launchDotNetAssembly(
            assemblyPath = assemblyPath,
            args = args,
            enabledPatches = enabledPatches,
            rendererOverride = rendererOverride,
            gameEnvVars = gameEnvVars
        ).also { code ->
            onGameExit(code, GameLauncher.getLastErrorMessage())
        }

        if (exitCode == 0) {
            AppLogger.info(TAG, "Game exited successfully.")
        } else {
            AppLogger.error(TAG, "Failed to launch game: $exitCode")
        }
        return exitCode
    }

    private fun showLaunchError(view: GameContract.View, message: String) {
        view.runOnMainThread {
            view.showError(view.getStringRes(R.string.game_launch_failed), message)
        }
    }

    private fun normalizeOptional(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun parseGameEnvVars(intent: android.content.Intent): Map<String, String?> {
        val rawMap = if (Build.VERSION.SDK_INT >= Build.VERSION.CODES.TIRAMISU) {
            intent.getSerializableExtra(GameActivity.EXTRA_GAME_ENV_VARS, HashMap::class.java)
        } else {
            intent.getSerializableExtra(GameActivity.EXTRA_GAME_ENV_VARS)
        } as? Map<*, *> ?: return emptyMap()

        val normalized = linkedMapOf<String, String?>()
        for ((rawKey, rawValue) in rawMap) {
            val key = (rawKey as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val value = when (rawValue) {
                null -> null
                is String -> rawValue
                else -> rawValue.toString()
            }
            normalized[key] = value
        }
        return normalized
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

            val logcatLogs = getRecentLogcatLogs(view)
            val message = buildExitMessage(view, exitCode, errorMessage)
            val errorDetails = buildErrorDetails(view, exitCode, nativeError, errorMessage)
            val stackTrace = buildStackTrace(view, exitCode, nativeError, logcatLogs, errorMessage)

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
            append("${view.getStringRes(R.string.crash_time_occurred)}: ${sdf.format(Date())}\n\n")

            val versionName = view.getAppVersionName() ?: view.getStringRes(R.string.crash_unknown)
            append("${view.getStringRes(R.string.crash_app_version)}: $versionName\n")
            append("${view.getStringRes(R.string.crash_device_model)}: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("${view.getStringRes(R.string.crash_android_version)}: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n\n")

            append("${view.getStringRes(R.string.crash_error_type_label)}: ${view.getStringRes(R.string.crash_game_exited_abnormally)}\n")
            append("${view.getStringRes(R.string.crash_exit_code_label)}: $exitCode\n")

            if (!nativeError.isNullOrEmpty()) {
                append("${view.getStringRes(R.string.crash_native_error_label)}: $nativeError\n")
            }

            if (!errorMessage.isNullOrEmpty()) {
                append("${view.getStringRes(R.string.crash_error_message_label)}: $errorMessage\n")
            }
        }
    }

    private fun buildStackTrace(
        view: GameContract.View,
        exitCode: Int,
        nativeError: String?,
        logcatLogs: String?,
        errorMessage: String?
    ): String {
        return buildString {
            append("${view.getStringRes(R.string.crash_game_exited_abnormally)}\n")
            append("${view.getStringRes(R.string.crash_exit_code_label)}: $exitCode\n\n")

            if (!nativeError.isNullOrEmpty()) {
                append("${view.getStringRes(R.string.crash_stacktrace_native_section)}\n")
                append("$nativeError\n\n")
            }

            if (!logcatLogs.isNullOrEmpty()) {
                append("${view.getStringRes(R.string.crash_stacktrace_logcat_section)}\n")
                append("$logcatLogs\n\n")
            }

            if (!errorMessage.isNullOrEmpty()) {
                append("${view.getStringRes(R.string.crash_stacktrace_error_details_section)}\n")
                append(errorMessage)
            }
        }
    }

    private fun getRecentLogcatLogs(view: GameContract.View): String? {
        return try {
            val importantTags = listOf(
                "GameLauncher", "GamePresenter", "RuntimeLibLoader", "RuntimeLibraryLoader",
                "RendererEnvironmentConfigurator", "RendererRegistry", "RendererLoader",
                "DotNetHost", "DotNetLauncher", "CoreCLR", "MonoGame",
                "SDL", "SDL_android", "SDLSurface", "FNA3D", "OpenAL", "FMOD",
                "libc", "linker", "art", "dalvikvm",
                "FATAL", "AndroidRuntime", "System.err"
            )
            
            val tagFilters = importantTags.flatMap { listOf("$it:V") }.toTypedArray()
            val cmd = arrayOf("logcat", "-d", "-v", "threadtime", "-t", "500", "*:S") + tagFilters
            
            val process = Runtime.getRuntime().exec(cmd)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val allLogs = reader.readLines()
            process.destroy()

            val filteredLogs = allLogs
                .filter { line ->
                    line.isNotBlank() && 
                    !line.contains("GC_") && 
                    !line.contains("Choreographer") &&
                    !line.contains("ViewRootImpl")
                }
                .takeLast(MAX_LOG_LINES)
                .joinToString("\n")

            if (filteredLogs.isEmpty()) {
                return getErrorLevelLogs()
            }

            var result = filteredLogs
            if (result.length > MAX_LOG_LENGTH) {
                result = view.getStringRes(R.string.crash_logcat_truncated_prefix) + "\n" + result.takeLast(MAX_LOG_LENGTH)
            }

            result.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to get logcat logs", e)
            getErrorLevelLogs()
        }
    }

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
