package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.os.Environment
import com.app.ralaunch.core.common.SettingsAccess
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.core.platform.runtime.dotnet.DotNetLauncher
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.util.NativeMethods
import com.app.ralaunch.core.platform.runtime.renderer.RendererEnvironmentConfigurator
import com.app.ralaunch.feature.patch.data.Patch
import com.app.ralaunch.feature.patch.data.PatchManager
import com.app.ralaunch.core.platform.android.ProcessLauncherService
import org.libsdl.app.SDL
import java.io.File  // Thay kotlin.io.path.*

object GameLauncher {

    private const val TAG = "GameLauncher"
    private const val DEFAULT_DATA_DIR_NAME = "RALauncher"
    private var isSDLJNIInitialized = false

    fun resetInitializationState() {
        isSDLJNIInitialized = false
        AppLogger.info(TAG, "初始化状态已重置 / Initialization state reset")
    }

    init {
        try {
            System.loadLibrary("fmodL")
            System.loadLibrary("fmod")
            System.loadLibrary("fmodstudioL")
            System.loadLibrary("fmodstudio")
            System.loadLibrary("dotnethost")
            System.loadLibrary("FAudio")
            System.loadLibrary("theorafile")
            System.loadLibrary("SDL2")
            System.loadLibrary("main")
            System.loadLibrary("openal32")
            System.loadLibrary("lwjgl_lz4")
            System.loadLibrary("SkiaSharp")
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.error(TAG, "加载 Native 库失败 / Failed to load native libraries: ${e.message}")
        }
    }

    fun getLastErrorMessage(): String {
        return DotNetLauncher.hostfxrLastErrorMsg
    }

    private fun initializeSDLJNI(context: Context) {
        if (isSDLJNIInitialized) return
        try {
            AppLogger.info(TAG, "正在初始化 SDL JNI 环境 / Initializing SDL JNI environment...")
            SDL.setupJNI()
            SDL.setContext(context)
            isSDLJNIInitialized = true
            AppLogger.info(TAG, "SDL JNI 初始化成功 / SDL JNI initialized successfully")
        } catch (e: Exception) {
            AppLogger.warn(TAG, "SDL JNI 初始化失败 / Failed to initialize SDL JNI: ${e.message}")
        }
    }

    fun launchDotNetAssembly(
        assemblyPath: String,
        args: Array<String>,
        enabledPatches: List<Patch>? = null,
        rendererOverride: String? = null,
        gameEnvVars: Map<String, String?> = emptyMap()
    ): Int {
        try {
            AppLogger.info(TAG, "=== 开始启动 .NET 程序集 / Starting .NET Assembly Launch ===")
            AppLogger.info(TAG, "程序集路径 / Assembly path: $assemblyPath")

            // Thay Path(assemblyPath).exists() bằng File(assemblyPath).exists()
            if (!File(assemblyPath).exists()) {
                AppLogger.error(TAG, "程序集文件不存在 / Assembly file does not exist: $assemblyPath")
                return -1
            }

            val appContext: Context = KoinJavaComponent.get(Context::class.java)
            EnvVarsManager.quickSetEnvVars(
                "PACKAGE_NAME" to appContext.packageName,
                "EXTERNAL_STORAGE_DIRECTORY" to Environment.getExternalStorageDirectory().path
            )

            // Thay Path(assemblyPath).parent.toString() bằng File(assemblyPath).parent
            val workingDir = File(assemblyPath).parent ?: ""
            AppLogger.debug(TAG, "切换工作目录 / Changing working directory to: $workingDir")
            NativeMethods.chdir(workingDir)

            val dataDir = prepareDataDirectory(assemblyPath)
            val cacheDir = appContext.cacheDir.absolutePath
            AppLogger.info(TAG, "数据目录 / Data directory: $dataDir")

            EnvVarsManager.quickSetEnvVars(
                "HOME" to dataDir,
                "XDG_DATA_HOME" to dataDir,
                "XDG_CONFIG_HOME" to dataDir,
                "XDG_CACHE_HOME" to cacheDir,
                "TMPDIR" to cacheDir
            )

            val settings = SettingsAccess
            val startupHooks = if (enabledPatches != null && enabledPatches.isNotEmpty())
                PatchManager.constructStartupHooksEnvVar(enabledPatches) else null

            val monoModPath = AssemblyPatcher.getMonoModInstallPath().toString()

            EnvVarsManager.quickSetEnvVars(
                "DOTNET_STARTUP_HOOKS" to startupHooks,
                "MONOMOD_PATH" to monoModPath,
                "SDL_TOUCH_MOUSE_EVENTS" to "1",
                "SDL_TOUCH_MOUSE_MULTITOUCH" to if (settings.isTouchMultitouchEnabled) "1" else "0",
                "RALCORE_MOUSE_RIGHT_STICK" to if (settings.isMouseRightStickEnabled) "1" else null,
                "SDL_AAUDIO_LOW_LATENCY" to if (settings.isSdlAaudioLowLatency) "1" else "0",
                "RAL_AUDIO_BUFFERSIZE" to settings.ralAudioBufferSize?.toString(),
                "RAL_GL_DIAGNOSTICS" to if (
                    settings.isFnaGlPerfDiagnosticsEnabled && settings.isFPSDisplayEnabled
                ) "1" else "0",
                "RAL_GL_DIAG" to null,
                "RAL_GL_PATH" to null,
                "RAL_GL_TIMING" to null,
                "RAL_GL_COUNT_W" to null,
                "RAL_GL_UPLOAD_W" to null,
                "RAL_GL_COUNT_T" to null,
                "RAL_GL_UPLOAD_T" to null,
                "RAL_GL_UPLOAD_PATH" to null,
                "RAL_GL_MAP_WRITES_S" to null,
                "RAL_GL_SUBDATA_WRITES_S" to null,
                "RAL_GL_DRAW_S" to null,
                "RAL_GL_UPLOAD_MB_S" to null,
                "RAL_GL_DRAWS_FRAME" to null,
                "RAL_GL_FRAME_MS" to null,
                "RAL_GL_SWAP_MS" to null,
                "RAL_GL_SLEEP_MS" to null,
                "RAL_GL_MAP_RATIO" to null,
                "RAL_GL_MAP_ENABLED" to null,
            )

            RendererEnvironmentConfigurator.apply(
                context = appContext,
                rendererOverride = rendererOverride
            )

            if (settings.setThreadAffinityToBigCoreEnabled) {
                ThreadAffinityManager.setThreadAffinityToBigCores()
            }

            if (gameEnvVars.isNotEmpty()) {
                val availableInterpolations = linkedMapOf(
                    "PACKAGE_NAME" to appContext.packageName,
                    "EXTERNAL_STORAGE_DIRECTORY" to Environment.getExternalStorageDirectory().path,
                    "HOME" to dataDir,
                    "XDG_DATA_HOME" to dataDir,
                    "XDG_CONFIG_HOME" to dataDir,
                    "XDG_CACHE_HOME" to cacheDir,
                    "TMPDIR" to cacheDir,
                    "MONOMOD_PATH" to monoModPath
                ).apply {
                    startupHooks?.let { put("DOTNET_STARTUP_HOOKS", it) }
                }
                val resolvedGameEnvVars = EnvVarsManager.interpolateEnvVars(
                    envVars = gameEnvVars,
                    availableInterpolations = availableInterpolations
                )
                EnvVarsManager.quickSetEnvVars(resolvedGameEnvVars)
            }

            val result = DotNetLauncher.hostfxrLaunch(assemblyPath, args)
            AppLogger.info(TAG, "退出代码 / Exit code: $result")
            return result

        } catch (e: Exception) {
            AppLogger.error(TAG, "启动程序集失败 / Failed to launch assembly: $assemblyPath", e)
            return -1
        }
    }

    @JvmStatic
    fun launchNewDotNetProcess(assemblyPath: String, args: Array<String>, title: String, gameId: String): Int {
        return try {
            ProcessLauncherService.launch(assemblyPath, args, title, gameId)
            0
        } catch (e: Exception) {
            AppLogger.error(TAG, "启动新 .NET 进程失败 / Failed to launch new .NET process", e)
            -1
        }
    }

    private fun prepareDataDirectory(assemblyPath: String): String {
        // Thay Path(assemblyPath).parent bằng File(assemblyPath).parentFile
        var finalDataDir = File(assemblyPath).parentFile?.absolutePath ?: ""

        try {
            // Thay .resolve().toPath() bằng File()
            val defaultDataDir = File(
                Environment.getExternalStorageDirectory(),
                DEFAULT_DATA_DIR_NAME
            )

            // Thay .exists() và .createDirectories()
            if (!defaultDataDir.exists()) {
                defaultDataDir.mkdirs()
            }

            // Thay .resolve(".nomedia") và .createFile()
            val nomediaFile = File(defaultDataDir, ".nomedia")
            if (!nomediaFile.exists()) {
                nomediaFile.createNewFile()
            }

            finalDataDir = defaultDataDir.absolutePath
            AppLogger.info(TAG, "使用默认数据目录 / Using default data directory: $finalDataDir")

        } catch (e: Exception) {
            AppLogger.warn(TAG, "无法访问默认数据目录 / Failed to access default data directory, using fallback: $finalDataDir", e)
        }

        return finalDataDir
    }
}
