package com.app.ralaunch.core

import android.content.Context
import android.os.Environment
import android.system.Os
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.data.SettingsManager
import com.app.ralaunch.dotnet.DotNetLauncher
import com.app.ralaunch.utils.AppLogger
import com.app.ralaunch.utils.NativeMethods
import com.app.ralaunch.renderer.RendererConfig
import com.app.ralib.patch.Patch
import com.app.ralib.patch.PatchManager
import com.app.ralaunch.box64.Box64Helper
import com.app.ralaunch.box64.NativeBridge
import com.app.ralaunch.service.ProcessLauncherService
import org.libsdl.app.SDL
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists

object GameLauncher {
    private const val TAG = "GameLauncher"

    private const val DEFAULT_DATA_DIR_NAME = "RALauncher"
    
    private var isBox64Initialized = false
    private var isSDLJNIInitialized = false

    // 静态加载 native 库
    init {
        try {
            // 加载 FMOD 和相关库
            // Celeste needs this
            System.loadLibrary("fmodL")
            System.loadLibrary("fmod")
            System.loadLibrary("fmodstudioL")
            System.loadLibrary("fmodstudio")

            System.loadLibrary("netcorehost")
            System.loadLibrary("FAudio")
            System.loadLibrary("theorafile")
            System.loadLibrary("SDL2")
            System.loadLibrary("main")
            System.loadLibrary("glibc_bridge")  // Box64 需要
            System.loadLibrary("openal32")
            System.loadLibrary("SkiaSharp")
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.error(TAG, "Failed to load native libraries: " + e.message)
        }
    }

    fun getLastErrorMessage(): String {
        // 未来可能扩展以包含更多错误来源
        return DotNetLauncher.hostfxrLastErrorMsg
    }
    
    /**
     * 启动 Box64 游戏 (x86_64 Linux 游戏，如 Starbound)
     * @param context Android Context
     * @param gamePath 游戏可执行文件路径
     * @return 退出代码
     */
    fun launchBox64Game(context: Context, gamePath: String): Int {
        try {
            AppLogger.info(TAG, "=== Starting Box64 Game Launch ===")
            AppLogger.info(TAG, "Game path: $gamePath")
            
            // 初始化 Box64 环境
            if (!initializeBox64(context)) {
                return -1
            }
            
            // 应用渲染器环境变量（必须在 Box64 启动前设置）
            AppLogger.debug(TAG, "Applying renderer environment...")
            RendererConfig.applyRendererEnvironment(context)
            AppLogger.debug(TAG, "Renderer environment applied: OK")
            
            // 初始化 SDL JNI 环境（传入 Context 给 SDL 音频系统使用）
            initializeSDLJNI(context)
            
            val filesDir = context.filesDir.absolutePath
            val rootfsPath = "$filesDir/rootfs"
            val gameDir = File(gamePath).parent ?: return -2
            
            // 设置 rootfs 路径
            System.setProperty("BOX64_ROOTFS", rootfsPath)
            
            AppLogger.info(TAG, "Launching via Box64...")
            AppLogger.info(TAG, "  Rootfs: $rootfsPath")
            AppLogger.info(TAG, "  Work dir: $gameDir")
            
            val result = Box64Helper.runBox64InProcess(arrayOf(gamePath), gameDir)
            
            AppLogger.info(TAG, "=== Box64 Game Launch Completed ===")
            AppLogger.info(TAG, "Exit code: $result")
            
            return result
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to launch Box64 game: $gamePath", e)
            return -3
        }
    }
    
    /**
     * 初始化 Box64 环境（提取 rootfs 等）
     * 可在安装时预先调用，以避免首次启动时的延迟
     */
    fun initializeBox64(context: Context): Boolean {
        if (isBox64Initialized) return true
        
        try {
            AppLogger.info(TAG, "Initializing Box64 environment...")
            
            val filesDir = context.filesDir.absolutePath
            val result = NativeBridge.init(context, filesDir)
            
            if (result == 0) {
                isBox64Initialized = true
                AppLogger.info(TAG, "Box64 initialized successfully")
                return true
            } else {
                AppLogger.error(TAG, "Box64 initialization failed with code: $result")
                return false
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to initialize Box64", e)
            return false
        }
    }
    
    private fun initializeSDLJNI(context: Context) {
        if (isSDLJNIInitialized) return
        
        try {
            AppLogger.info(TAG, "Initializing SDL JNI environment...")
            SDL.setupJNI()
            // 注意：不要调用 SDL.initialize()！
            // 当从 GameActivity（继承自 SDLActivity）启动时，SDLActivity.onCreate() 已经初始化了 SDL
            // 再次调用 SDL.initialize() 会把 mSingleton 和 mSurface 重置为 null，
            // 导致 Box64 的 SDL_CreateWindow() 无法获取 native window
            // SDL.initialize() // <-- 禁用！会重置 SDLActivity 的静态变量！
            
            // 只设置 Context，确保 SDL 音频等功能可以正常工作
            SDL.setContext(context)
            isSDLJNIInitialized = true
            AppLogger.info(TAG, "SDL JNI initialized successfully (without re-initializing SDLActivity)")
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Failed to initialize SDL JNI: ${e.message}")
        }
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

            val settings = SettingsManager.getInstance()

            // Log settings configuration
            AppLogger.debug(TAG, "Applying settings configuration...")
            AppLogger.debug(TAG, "  - Big core affinity: ${settings.setThreadAffinityToBigCoreEnabled}")
            AppLogger.debug(TAG, "  - Touch multitouch: ${settings.isTouchMultitouchEnabled}")
            AppLogger.debug(TAG, "  - Mouse right stick: ${settings.isMouseRightStickEnabled}")

            val startupHooks = if (enabledPatches != null && enabledPatches.isNotEmpty())
                PatchManager.constructStartupHooksEnvVar(enabledPatches) else null

            if (startupHooks != null) {
                AppLogger.info(TAG, "DOTNET_STARTUP_HOOKS configured with ${enabledPatches!!.size} patch(es)")
                AppLogger.debug(TAG, "DOTNET_STARTUP_HOOKS value: $startupHooks")
                // Count actual hooks (split by ':')
                val hookCount = startupHooks.split(":").filter { it.isNotEmpty() }.size
                AppLogger.debug(TAG, "DOTNET_STARTUP_HOOKS actual hook count: $hookCount")
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
                "RALCORE_MOUSE_RIGHT_STICK" to if (settings.isMouseRightStickEnabled) "1" else null,
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

    /**
     * 启动新的 .NET 进程（从 native 层调用）
     * @param assemblyPath 程序集路径
     * @param args 传递给程序集的参数
     * @param title 进程标题（用于日志）
     * @param gameId 游戏ID（用于匹配补丁）
     * @return 程序集退出代码
     */
    @JvmStatic
    fun launchNewDotNetProcess(assemblyPath: String, args: Array<String>, title: String, gameId: String): Int {
        try {
            AppLogger.info(TAG, "=== launchNewDotNetProcess called ===")
            AppLogger.info(TAG, "Assembly: $assemblyPath")
            AppLogger.info(TAG, "Title: $title")
            AppLogger.info(TAG, "Game ID: $gameId")
            AppLogger.info(TAG, "Arguments: ${args.joinToString(", ")}")

            ProcessLauncherService.launch(assemblyPath, args, title, gameId)

            return 0
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to launch new .NET process", e)
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