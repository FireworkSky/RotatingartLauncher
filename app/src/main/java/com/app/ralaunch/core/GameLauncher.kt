package com.app.ralaunch.core

import android.os.Environment
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.data.SettingsManager
import com.app.ralaunch.dotnet.DotNetLauncher
import com.app.ralaunch.utils.AppLogger
import com.app.ralaunch.utils.NativeMethods
import com.app.ralaunch.renderer.RendererConfig
import com.app.ralib.patch.Patch
import com.app.ralib.patch.PatchManager
import com.app.ralaunch.game.AssemblyPatcher
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists

object GameLauncher {
    private const val TAG = "GameLauncher"

    private const val DEFAULT_DATA_DIR_NAME = "RALauncher"

    // 静态加载 native 库
    init {
        try {
            System.loadLibrary("netcorehost")
            System.loadLibrary("FAudio")
            System.loadLibrary("theorafile")
            System.loadLibrary("SDL2")
            System.loadLibrary("main")
            System.loadLibrary("openal32")
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.error(TAG, "Failed to load native libraries: " + e.message)
        }
    }

    fun getLastErrorMessage(): String {
        // 未来可能扩展以包含更多错误来源
        return DotNetLauncher.hostfxrLastErrorMsg
    }

    /**
     * 启动 .NET 程序集
     * 在这里只负责 **底层runtime环境变量外** 的游戏相关环境准备和调用底层启动器
     * 如果需要设置底层 runtime 环境变量，请在 DotNetLauncher.hostfxrLaunch 中设置
     * 不要在这里设置底层 runtime 环境变量，以免影响其他程序集的运行
     * @param assemblyPath 程序集路径
     * @param args 传递给程序集的参数
     * @param enabledPatches 启用的补丁列表
     * @return 程序集退出代码
     */
    fun launchDotNetAssembly(assemblyPath: String, args: Array<String>, enabledPatches: List<Patch>? = null): Int {
        try {
            AppLogger.info(TAG, "=== Starting .NET Assembly Launch ===")
            AppLogger.info(TAG, "Assembly path: $assemblyPath")
            AppLogger.info(TAG, "Arguments: ${args.joinToString(", ")}")
            AppLogger.info(TAG, "Enabled patches: ${enabledPatches?.size ?: 0}")

            // sanity check
            if (!Path(assemblyPath).exists()) {
                AppLogger.error(TAG, "Assembly file does not exist: $assemblyPath")
                return -1
            }
            AppLogger.debug(TAG, "Assembly file exists: OK")

            // 设置必要的环境变量, RaLaunchApplication 中已经设置过这部分变量，这里再设置一次以防万一
            AppLogger.debug(TAG, "Setting up environment variables...")
            EnvVarsManager.quickSetEnvVars(
                "PACKAGE_NAME" to RaLaunchApplication.getAppContext().packageName,
                "EXTERNAL_STORAGE_DIRECTORY" to Environment.getExternalStorageDirectory().path
            )

            // 切换工作目录到程序集所在目录
            val workingDir = Path(assemblyPath).parent.toString()
            AppLogger.debug(TAG, "Changing working directory to: $workingDir")
            NativeMethods.chdir(workingDir)
            AppLogger.debug(TAG, "Working directory changed: OK")

            // 设置数据目录
            AppLogger.debug(TAG, "Preparing data directory...")
            val dataDir = prepareDataDirectory(assemblyPath)
            val cacheDir = RaLaunchApplication.getAppContext().cacheDir.absolutePath
            AppLogger.info(TAG, "Data directory: $dataDir")


            EnvVarsManager.quickSetEnvVars(
                "HOME" to dataDir,
                "XDG_DATA_HOME" to dataDir,
                "XDG_CONFIG_HOME" to dataDir,
                "XDG_CACHE_HOME" to cacheDir

            )
            AppLogger.debug(TAG, "XDG environment variables set: OK")

            val settings = SettingsManager.getInstance(null)

            // Log settings configuration
            AppLogger.debug(TAG, "Applying settings configuration...")
            AppLogger.debug(TAG, "  - Big core affinity: ${settings.setThreadAffinityToBigCoreEnabled}")
            AppLogger.debug(TAG, "  - Touch multitouch: ${settings.isTouchMultitouchEnabled}")
            AppLogger.debug(TAG, "  - Mouse right stick: ${settings.isMouseRightStickEnabled}")

            val startupHooks = if (enabledPatches != null && enabledPatches.isNotEmpty())
                PatchManager.constructStartupHooksEnvVar(enabledPatches) else null

            if (startupHooks != null) {
                AppLogger.info(TAG, "DOTNET_STARTUP_HOOKS configured with ${enabledPatches!!.size} patch(es)")
            } else {
                AppLogger.debug(TAG, "No startup hooks configured")
            }

            // 设置 MonoMod 路径环境变量，供补丁的 AssemblyResolve 使用
            val monoModPath = AssemblyPatcher.getMonoModInstallPath().toString()
            AppLogger.info(TAG, "MonoMod path: $monoModPath")

            EnvVarsManager.quickSetEnvVars(
                // 不再通过环境变量设置大核亲和性
//                // 设置大核亲和性
//                "SET_THREAD_AFFINITY_TO_BIG_CORE" to if (settings.setThreadAffinityToBigCoreEnabled) "1" else "0",

                // 设置启动钩子
                "DOTNET_STARTUP_HOOKS" to startupHooks,
                
                // MonoMod 路径，供补丁的 AssemblyResolve 使用
                "MONOMOD_PATH" to monoModPath,

                // 触摸相关
                "SDL_TOUCH_MOUSE_EVENTS" to "1",
                "SDL_TOUCH_MOUSE_MULTITOUCH" to if (settings.isTouchMultitouchEnabled) "1" else "0",
                "RALCORE_MOUSE_RIGHT_STICK" to if (settings.isMouseRightStickEnabled) "1" else null
            )
            AppLogger.debug(TAG, "Game settings environment variables set: OK")

            AppLogger.debug(TAG, "Applying renderer environment...")
            RendererConfig.applyRendererEnvironment(RaLaunchApplication.getAppContext())
            AppLogger.debug(TAG, "Renderer environment applied: OK")

            // 设置线程亲和性到大核
            if (settings.setThreadAffinityToBigCoreEnabled) {
                AppLogger.debug(TAG, "Setting thread affinity to big cores...")
                val result = ThreadAffinityManager.setThreadAffinityToBigCores()
                AppLogger.debug(TAG, "Thread affinity to big cores set: Result=$result")
            } else {
                AppLogger.debug(TAG, "Thread affinity to big cores not enabled, skipping.")
            }

            AppLogger.info(TAG, "Launching .NET runtime with hostfxr...")
            val result = DotNetLauncher.hostfxrLaunch(assemblyPath, args)

            AppLogger.info(TAG, "=== .NET Assembly Launch Completed ===")
            AppLogger.info(TAG, "Exit code: $result")

            return result
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to launch assembly: $assemblyPath", e)
            e.printStackTrace()
            return -1
        }
    }

    private fun prepareDataDirectory(assemblyPath: String): String {
        var finalDataDir = Path(assemblyPath).parent
        AppLogger.debug(TAG, "Initial data directory (assembly parent): $finalDataDir")

        try {
            val defaultDataDirPath = android.os.Environment.getExternalStorageDirectory()
                .resolve(DEFAULT_DATA_DIR_NAME)
                .toPath()

            AppLogger.debug(TAG, "Target data directory: $defaultDataDirPath")

            if (!defaultDataDirPath.exists()) {
                AppLogger.debug(TAG, "Creating data directory: $defaultDataDirPath")
                defaultDataDirPath.createDirectories()
                AppLogger.debug(TAG, "Data directory created: OK")
            } else {
                AppLogger.debug(TAG, "Data directory already exists")
            }

            val nomediaFilePath = defaultDataDirPath.resolve(".nomedia")
            if (!nomediaFilePath.exists()) {
                AppLogger.debug(TAG, "Creating .nomedia file: $nomediaFilePath")
                nomediaFilePath.createFile()
                AppLogger.debug(TAG, ".nomedia file created: OK")
            } else {
                AppLogger.debug(TAG, ".nomedia file already exists")
            }

            finalDataDir = defaultDataDirPath
            AppLogger.info(TAG, "Using default data directory: $finalDataDir")
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to access/create default data directory, using assembly directory instead.", e)
            AppLogger.warn(TAG, "Fallback data directory: $finalDataDir")
        }

        return finalDataDir.toString()
    }
}