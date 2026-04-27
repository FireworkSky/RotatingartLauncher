/**
 * 游戏启动器模块
 * Game Launcher Module
 *
 * 本模块是应用的核心组件，负责启动和管理 .NET/FNA 游戏进程。
 * This module is the core component of the application, responsible for launching
 * and managing .NET/FNA game processes.
 *
 * 支持的游戏类型：
 * Supported game types:
 * - .NET/FNA 游戏（通过 CoreCLR 运行时）
 *   .NET/FNA games (via CoreCLR runtime)
 *
 * @see DotNetLauncher .NET 运行时启动器 / .NET runtime launcher
 */
package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.os.Environment
import com.app.ralaunch.core.common.SettingsAccess
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.core.platform.runtime.dotnet.DotNetLauncher
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.common.util.NativeMethods
import com.app.ralaunch.core.platform.runtime.RendererEnvironmentConfigurator
import com.app.ralaunch.feature.patch.data.Patch
import com.app.ralaunch.feature.patch.data.PatchManager
import com.app.ralaunch.core.platform.android.ProcessLauncherService
import org.libsdl.app.SDL
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists

/**
 * 游戏启动器 - 统一管理游戏启动流程
 * Game Launcher - Unified game launch process management
 *
 * 提供以下核心功能：
 * Provides the following core features:
 * - 启动 .NET/FNA 游戏并配置运行时环境
 *   Launch .NET/FNA games with runtime environment configuration
 * - 管理游戏数据目录和环境变量
 *   Manage game data directories and environment variables
 * - 配置补丁和启动钩子
 *   Configure patches and startup hooks
 */
object GameLauncher {

    private const val TAG = "GameLauncher"

    /**
     * 默认数据目录名称
     * Default data directory name
     */
    private const val DEFAULT_DATA_DIR_NAME = "RALauncher"

    /**
     * SDL JNI 环境是否已初始化
     * Whether SDL JNI environment is initialized
     */
    private var isSDLJNIInitialized = false

    /**
     * 重置初始化状态
     * Reset initialization state
     *
     * 在每次启动新游戏前调用，确保环境被正确重新初始化
     * Called before launching a new game to ensure environment is properly re-initialized
     */
    fun resetInitializationState() {
        isSDLJNIInitialized = false
        AppLog.i(TAG, "初始化状态已重置 / Initialization state reset")
    }

    /**
     * 静态初始化块 - 加载所有必需的 Native 库
     * Static initializer - Load all required native libraries
     *
     * 加载顺序很重要，某些库依赖于其他库。
     * Loading order matters, some libraries depend on others.
     *
     * 包含的库：
     * Included libraries:
     * - FMOD: 音频引擎（Celeste 等游戏需要）/ Audio engine (required by games like Celeste)
     * - SDL2: 跨平台多媒体库 / Cross-platform multimedia library
     * - FAudio: XAudio2 替代实现 / XAudio2 reimplementation
     * - SkiaSharp: 2D 图形库 / 2D graphics library
     */
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
            AppLog.e(TAG, "加载 Native 库失败 / Failed to load native libraries: ${e.message}")
        }
    }

    /**
     * 获取最后一次错误信息
     * Get the last error message
     *
     * @return 错误信息字符串，如果没有错误则返回空字符串
     *         Error message string, or empty string if no error
     */
    fun getLastErrorMessage(): String {
        return DotNetLauncher.hostfxrLastErrorMsg
    }

    /**
     * 启动 .NET 程序集
     * Launch a .NET assembly
     *
     * 此方法负责准备游戏运行环境并调用 .NET 运行时启动游戏。
     * 只处理游戏相关的环境配置，底层运行时环境变量由 DotNetLauncher 处理。
     *
     * This method prepares the game runtime environment and calls .NET runtime to launch the game.
     * Only handles game-related environment configuration, low-level runtime environment
     * variables are handled by DotNetLauncher.
     *
     * 启动流程：
     * Launch process:
     * 1. 验证程序集文件存在
     *    Verify assembly file exists
     * 2. 设置环境变量（包名、存储路径等）
     *    Set environment variables (package name, storage path, etc.)
     * 3. 切换工作目录到程序集所在目录
     *    Change working directory to assembly location
     * 4. 准备数据目录（HOME、XDG_* 等）
     *    Prepare data directories (HOME, XDG_*, etc.)
     * 5. 配置补丁和启动钩子
     *    Configure patches and startup hooks
     * 6. 配置渲染器和线程亲和性
     *    Configure renderer and thread affinity
     * 7. 调用 hostfxr 启动 .NET 运行时
     *    Call hostfxr to launch .NET runtime
     *
     * @param assemblyPath 程序集（.dll）的完整路径
     *                     Full path to the assembly (.dll)
     * @param args 传递给程序集的命令行参数
     *             Command line arguments to pass to the assembly
     * @param enabledPatches 要启用的补丁列表，null 表示不使用补丁
     *                       List of patches to enable, null means no patches
     * @param rendererOverride 可选的渲染器覆盖（null 表示使用全局设置）
     *                         Optional renderer override (null means use global setting)
     * @param dotNetRuntimeVersionOverride 可选的 .NET 运行时版本覆盖（null 表示使用全局设置）
     *                                     Optional .NET runtime version override (null means use global setting)
     * @param gameEnvVars 游戏环境变量（null 值表示在启动前 unset 对应变量）
     *                    Game environment variables (null value means unset before launch)
     * @return 程序集退出代码：
     *         Assembly exit code:
     *         - 0 或正数：正常退出码 / Normal exit code
     *         - -1：启动失败（文件不存在或发生异常）/ Launch failed (file not found or exception)
     */
    fun launchDotNetAssembly(
        assemblyPath: String,
        args: Array<String>,
        enabledPatches: List<Patch>? = null,
        rendererOverride: String? = null,
        dotNetRuntimeVersionOverride: String? = null,
        gameEnvVars: Map<String, String?> = emptyMap()
    ): Int {
        try {
            AppLog.i(TAG, "=== 开始启动 .NET 程序集 / Starting .NET Assembly Launch ===")
            AppLog.i(TAG, "程序集路径 / Assembly path: $assemblyPath")
            AppLog.i(TAG, "启动参数 / Arguments: ${args.joinToString(", ")}")
            AppLog.i(TAG, "启用补丁数 / Enabled patches: ${enabledPatches?.size ?: 0}")

            // 步骤1：验证程序集文件存在
            // Step 1: Verify assembly file exists
            if (!Path(assemblyPath).exists()) {
                AppLog.e(TAG, "程序集文件不存在 / Assembly file does not exist: $assemblyPath")
                return -1
            }
            AppLog.d(TAG, "程序集文件验证通过 / Assembly file exists: OK")

            // 步骤2：设置基础环境变量
            // Step 2: Set basic environment variables
            AppLog.d(TAG, "设置环境变量 / Setting up environment variables...")
            val appContext: Context = KoinJavaComponent.get(Context::class.java)
            EnvVarsManager.quickSetEnvVars(
                "PACKAGE_NAME" to appContext.packageName,
                "EXTERNAL_STORAGE_DIRECTORY" to Environment.getExternalStorageDirectory().path
            )

            // 步骤3：切换工作目录
            // Step 3: Change working directory
            val workingDir = Path(assemblyPath).parent.toString()
            AppLog.d(TAG, "切换工作目录 / Changing working directory to: $workingDir")
            NativeMethods.chdir(workingDir)
            AppLog.d(TAG, "工作目录切换完成 / Working directory changed: OK")

            // 步骤4：准备数据目录
            // Step 4: Prepare data directory
            AppLog.d(TAG, "准备数据目录 / Preparing data directory...")
            val dataDir = prepareDataDirectory(assemblyPath)
            val cacheDir = appContext.cacheDir.absolutePath
            AppLog.i(TAG, "数据目录 / Data directory: $dataDir")

            EnvVarsManager.quickSetEnvVars(
                "HOME" to dataDir,
                "XDG_DATA_HOME" to dataDir,
                "XDG_CONFIG_HOME" to dataDir,
                "XDG_CACHE_HOME" to cacheDir,
                "TMPDIR" to cacheDir
            )
            AppLog.d(TAG, "XDG 环境变量设置完成 / XDG environment variables set: OK")

            // 步骤5：应用用户设置
            // Step 5: Apply user settings
            val settings = SettingsAccess
            AppLog.d(TAG, "应用用户设置 / Applying settings configuration...")
            AppLog.d(TAG, "  - 大核亲和性 / Big core affinity: ${settings.setThreadAffinityToBigCoreEnabled}")
            AppLog.d(TAG, "  - 多点触控 / Touch multitouch: ${settings.isTouchMultitouchEnabled}")

            // 步骤6：配置启动钩子（补丁）
            // Step 6: Configure startup hooks (patches)
            val startupHooks = if (enabledPatches != null && enabledPatches.isNotEmpty())
                PatchManager.constructStartupHooksEnvVar(enabledPatches) else null

            if (startupHooks != null) {
                AppLog.i(TAG, "已配置 ${enabledPatches!!.size} 个补丁的启动钩子 / DOTNET_STARTUP_HOOKS configured with ${enabledPatches.size} patch(es)")
                AppLog.d(TAG, "DOTNET_STARTUP_HOOKS 值 / value: $startupHooks")
                val hookCount = startupHooks.split(":").filter { it.isNotEmpty() }.size
                AppLog.d(TAG, "实际钩子数量 / Actual hook count: $hookCount")
            } else {
                AppLog.d(TAG, "未配置启动钩子 / No startup hooks configured")
            }

            // 步骤7：设置 MonoMod 路径（供补丁使用）
            // Step 7: Set MonoMod path (for patches)
            val monoModPath = AssemblyPatcher.getMonoModInstallPath().toString()
            AppLog.i(TAG, "MonoMod 路径 / path: $monoModPath")

            EnvVarsManager.quickSetEnvVars(
                // 启动钩子配置
                // Startup hooks configuration
                "DOTNET_STARTUP_HOOKS" to startupHooks,

                // MonoMod 路径，供补丁的 AssemblyResolve 使用
                // MonoMod path, used by patch's AssemblyResolve
                "MONOMOD_PATH" to monoModPath,

                // 触摸输入配置
                // Touch input configuration
                "SDL_TOUCH_MOUSE_EVENTS" to "1",
                "SDL_TOUCH_MOUSE_MULTITOUCH" to if (settings.isTouchMultitouchEnabled) "1" else "0",

                // 音频配置
                // Audio configuration
                "SDL_AAUDIO_LOW_LATENCY" to if (settings.isSdlAaudioLowLatency) "1" else "0",
                "RAL_AUDIO_BUFFERSIZE" to settings.ralAudioBufferSize?.toString(),

                // OpenGL 运行时诊断（用于 FPS 旁性能分析）
                // OpenGL runtime diagnostics (for FPS-adjacent performance analysis)
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
            AppLog.d(TAG, "游戏设置环境变量配置完成 / Game settings environment variables set: OK")

            // 步骤8：配置渲染器
            // Step 8: Configure renderer
            AppLog.d(TAG, "配置渲染器环境 / Applying renderer environment...")
            RendererEnvironmentConfigurator.apply(
                context = appContext,
                rendererOverride = rendererOverride
            )
            AppLog.d(TAG, "渲染器环境配置完成 / Renderer environment applied: OK")

            // 步骤9：设置线程亲和性
            // Step 9: Set thread affinity
            if (settings.setThreadAffinityToBigCoreEnabled) {
                AppLog.d(TAG, "设置线程亲和性到大核 / Setting thread affinity to big cores...")
                val result = ThreadAffinityManager.setThreadAffinityToBigCores()
                AppLog.d(TAG, "线程亲和性设置完成 / Thread affinity to big cores set: Result=$result")
            } else {
                AppLog.d(TAG, "未启用大核亲和性，跳过 / Thread affinity to big cores not enabled, skipping.")
            }

            // 步骤10：应用游戏级环境变量（优先级高于全局/渲染器配置）
            // Step 10: Apply per-game env vars (higher priority than global/renderer config)
            if (gameEnvVars.isNotEmpty()) {
                AppLog.d(TAG, "应用游戏环境变量 / Applying per-game env vars: ${gameEnvVars.size} item(s)")
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
                AppLog.d(TAG, "游戏环境变量应用完成 / Per-game env vars applied: OK")
            }

            // 步骤11：启动 .NET 运行时
            // Step 11: Launch .NET runtime
            AppLog.i(TAG, "通过 hostfxr 启动 .NET 运行时 / Launching .NET runtime with hostfxr...")
            val result = DotNetLauncher.hostfxrLaunch(
                assemblyPath = assemblyPath,
                args = args,
                dotNetRuntimeVersionOverride = dotNetRuntimeVersionOverride
            )

            AppLog.i(TAG, "=== .NET 程序集启动完成 / .NET Assembly Launch Completed ===")
            AppLog.i(TAG, "退出代码 / Exit code: $result")

            return result
        } catch (e: Exception) {
            AppLog.e(TAG, "启动程序集失败 / Failed to launch assembly: $assemblyPath", e)
            return -1
        }
    }

    /**
     * 在新进程中启动 .NET 程序集
     * Launch a .NET assembly in a new process
     *
     * 此方法由 Native 层调用，用于在独立进程中启动子程序集。
     * 例如：某些游戏可能需要启动额外的工具或服务器进程。
     *
     * This method is called from native layer to launch sub-assemblies in separate processes.
     * For example: some games may need to launch additional tools or server processes.
     *
     * @param assemblyPath 程序集的完整路径
     *                     Full path to the assembly
     * @param args 传递给程序集的命令行参数
     *             Command line arguments for the assembly
     * @param title 进程标题，用于日志和调试
     *              Process title for logging and debugging
     * @param gameId 游戏标识符，用于匹配相关补丁
     *               Game identifier for matching related patches
     * @return 启动结果：0 表示成功，-1 表示失败
     *         Launch result: 0 for success, -1 for failure
     */
    @JvmStatic
    fun launchNewDotNetProcess(assemblyPath: String, args: Array<String>, title: String, gameId: String): Int {
        try {
            AppLog.i(TAG, "=== 收到新进程启动请求 / launchNewDotNetProcess called ===")
            AppLog.i(TAG, "程序集 / Assembly: $assemblyPath")
            AppLog.i(TAG, "标题 / Title: $title")
            AppLog.i(TAG, "游戏ID / Game ID: $gameId")
            AppLog.i(TAG, "参数 / Arguments: ${args.joinToString(", ")}")

            ProcessLauncherService.launch(assemblyPath, args, title, gameId)

            return 0
        } catch (e: Exception) {
            AppLog.e(TAG, "启动新 .NET 进程失败 / Failed to launch new .NET process", e)
            return -1
        }
    }

    /**
     * 准备游戏数据目录
     * Prepare game data directory
     *
     * 创建并返回游戏存档、配置等数据的存储目录。
     * 默认使用外部存储的 RALauncher 目录，如果无法访问则回退到程序集所在目录。
     *
     * Creates and returns the storage directory for game saves, configs, etc.
     * Defaults to RALauncher directory in external storage,
     * falls back to assembly directory if inaccessible.
     *
     * 目录结构：
     * Directory structure:
     * - /storage/emulated/0/RALauncher/
     *   - .nomedia（防止媒体扫描 / Prevents media scanning）
     *   - [游戏存档和配置 / Game saves and configs]
     *
     * @param assemblyPath 程序集路径，用于获取回退目录
     *                     Assembly path for fallback directory
     * @return 数据目录的绝对路径
     *         Absolute path to the data directory
     */
    private fun prepareDataDirectory(assemblyPath: String): String {
        // 初始回退目录为程序集所在目录
        // Initial fallback is the assembly's parent directory
        var finalDataDir = Path(assemblyPath).parent
        AppLog.d(TAG, "初始数据目录（程序集父目录）/ Initial data directory (assembly parent): $finalDataDir")

        try {
            // 尝试使用外部存储的默认数据目录
            // Try to use default data directory in external storage
            val defaultDataDirPath = android.os.Environment.getExternalStorageDirectory()
                .resolve(DEFAULT_DATA_DIR_NAME)
                .toPath()

            AppLog.d(TAG, "目标数据目录 / Target data directory: $defaultDataDirPath")

            // 创建目录（如果不存在）
            // Create directory if it doesn't exist
            if (!defaultDataDirPath.exists()) {
                AppLog.d(TAG, "创建数据目录 / Creating data directory: $defaultDataDirPath")
                defaultDataDirPath.createDirectories()
                AppLog.d(TAG, "数据目录创建成功 / Data directory created: OK")
            } else {
                AppLog.d(TAG, "数据目录已存在 / Data directory already exists")
            }

            // 创建 .nomedia 文件防止媒体扫描
            // Create .nomedia file to prevent media scanning
            val nomediaFilePath = defaultDataDirPath.resolve(".nomedia")
            if (!nomediaFilePath.exists()) {
                AppLog.d(TAG, "创建 .nomedia 文件 / Creating .nomedia file: $nomediaFilePath")
                nomediaFilePath.createFile()
                AppLog.d(TAG, ".nomedia 文件创建成功 / .nomedia file created: OK")
            } else {
                AppLog.d(TAG, ".nomedia 文件已存在 / .nomedia file already exists")
            }

            finalDataDir = defaultDataDirPath
            AppLog.i(TAG, "使用默认数据目录 / Using default data directory: $finalDataDir")
        } catch (e: Exception) {
            // 无法访问外部存储，使用程序集目录作为回退
            // Cannot access external storage, use assembly directory as fallback
            AppLog.w(TAG, "无法访问默认数据目录，使用程序集目录 / Failed to access default data directory, using assembly directory instead.", e)
            AppLog.w(TAG, "回退数据目录 / Fallback data directory: $finalDataDir")
        }

        return finalDataDir.toString()
    }
}
