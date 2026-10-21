package com.app.ralaunch.feature.installer

import android.content.Context
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.platform.runtime.AssemblyPatcher
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.core.extractor.IconExtractor
import kotlinx.coroutines.Job
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 安装插件基类
 * 提供通用的安装工具方法，减少代码重复
 *
 * 使用新的存储结构: games/{GameDirName}/game_info.json
 */
abstract class BaseInstallPlugin : GameInstallPlugin {

    protected var installJob: Job? = null
    protected var isCancelled = false

    override fun cancel() {
        isCancelled = true
        installJob?.cancel()
    }

    /**
     * 从游戏目录提取图标
     * @param outputDir 游戏目录
     * @param definition 游戏定义（用于搜索模式）
     * @return 图标路径（相对路径），失败返回 null
     */
    protected fun extractIcon(outputDir: File, definition: GameDefinition): String? {
        return try {
            // 1. 检查预设图标
            val presetIcon = findPresetIcon(outputDir, definition)
            if (presetIcon != null) return presetIcon

            // 2. 查找图标源文件
            val iconSourceFile = findIconSourceFile(outputDir, definition)
            if (iconSourceFile == null || !iconSourceFile.exists()) return null

            // 3. 提取图标
            val iconOutputPath = "icon.png"
            val success = IconExtractor.extractIconToPng(iconSourceFile.absolutePath, File(outputDir, iconOutputPath).absolutePath)

            if (success && File(outputDir, iconOutputPath).exists() && File(outputDir, iconOutputPath).length() > 0) {
                iconOutputPath
            } else null
        } catch (e: Exception) {
            AppLog.e(TAG, "提取图标失败: ${e.message}")
            null
        }
    }

    private fun findPresetIcon(outputDir: File, definition: GameDefinition): String? {
        // 检查常见预设图标名称
        val presetNames = definition.iconPatterns.flatMap { pattern ->
            listOf("$pattern.png", "$pattern.ico", "${pattern}_icon.png")
        } + listOf("icon.png", "game_icon.png")

        for (name in presetNames) {
            val iconFile = outputDir.resolve(name)
            if (iconFile.exists()) return iconFile.name
        }
        return null
    }

    private fun findIconSourceFile(outputDir: File, definition: GameDefinition): File? {
        // 1. 先尝试启动目标
        val launchTarget = definition.launchTarget
        val targetFile = File(outputDir, launchTarget)

        // 如果是 DLL，尝试找对应的 EXE，如果没有则使用 DLL 本身
        if (launchTarget.lowercase().endsWith(".dll")) {
            val baseName = launchTarget.substringBeforeLast(".")
            val exeFile = File(outputDir, "$baseName.exe")
            if (exeFile.exists() && IconExtractor.hasIcon(exeFile.absolutePath)) {
                return exeFile
            }
            // DLL 文件本身也可能包含图标（PE 格式）
            if (targetFile.exists() && IconExtractor.hasIcon(targetFile.absolutePath)) {
                return targetFile
            }
        }

        if (targetFile.exists() && targetFile.extension.lowercase() == "exe") {
            return targetFile
        }

        // 2. 搜索匹配模式的 EXE/DLL 文件（PE 格式都可能包含图标）
        val peFiles = outputDir.walkTopDown()
            .filter { it.isFile && (it.extension.lowercase() == "exe" || it.extension.lowercase() == "dll") }
            .toList()

        if (peFiles.isEmpty()) return null

        // 优先选择名称包含游戏模式的 EXE 文件
        for (pattern in definition.iconPatterns) {
            // 先找 EXE
            val matchedExe = peFiles.find {
                it.extension.lowercase() == "exe" &&
                it.name.lowercase().contains(pattern.lowercase()) &&
                IconExtractor.hasIcon(it.absolutePath)
            }
            if (matchedExe != null) return matchedExe

            // 再找 DLL
            val matchedDll = peFiles.find {
                it.extension.lowercase() == "dll" &&
                it.name.lowercase().contains(pattern.lowercase()) &&
                IconExtractor.hasIcon(it.absolutePath)
            }
            if (matchedDll != null) return matchedDll
        }

        // 3. 返回第一个包含图标的 PE 文件（优先 EXE）
        val exeWithIcon = peFiles.filter { it.extension.lowercase() == "exe" }
            .find { IconExtractor.hasIcon(it.absolutePath) }
        if (exeWithIcon != null) return exeWithIcon

        val dllWithIcon = peFiles.filter { it.extension.lowercase() == "dll" }
            .find { IconExtractor.hasIcon(it.absolutePath) }
        if (dllWithIcon != null) return dllWithIcon

        // 4. 如果都没有图标，返回第一个 EXE（尝试提取）
        return peFiles.firstOrNull { it.extension.lowercase() == "exe" }
    }

    /**
     * 创建游戏信息文件 (game_info.json)
     *
     * 注意：此方法处理两种情况：
     * 1. GOG .sh 等会创建嵌套目录结构的安装包：
     *    - storageRootDir: 由 IGameRepositoryServiceV3.createGameStorageRoot() 创建的目录
     *    - actualGameDir: 游戏实际解压到的子目录（如 data/noarch/game/）
     *    - game_info.json 将创建在 storageRootDir，路径相对于 storageRootDir
     *
     * 2. ZIP 等直接解压的安装包：
     *    - 使用简化版本，storageRootDir 和 actualGameDir 相同
     *
     * @param storageRootDir 存储根目录（由 IGameRepositoryServiceV3 创建的目录）
     * @param actualGameDir 实际游戏文件所在目录（可能是 storageRootDir 的子目录）
     * @param definition 游戏定义
     * @param iconPath 图标路径（相对于 actualGameDir）
     */
    protected fun createGameInfo(
        storageRootDir: File,
        actualGameDir: File,
        definition: GameDefinition,
        iconPath: String?,
        // RAL: tModLoader 1.3 用包装 exe 作为入口、并强制 mono 运行时
        launchTargetOverride: String? = null,
        runtimeOverride: String? = null,
        envVars: Map<String, String?> = emptyMap()
    ) {
        val infoFile = File(storageRootDir, "game_info.json")

        // 使用目录名作为存储 ID（与 GameRepositoryServiceV3 保持一致）
        val storageId = storageRootDir.name

        val launchTarget = launchTargetOverride ?: definition.launchTarget

        // 计算相对于 storageRootDir 的路径
        val gameExePathRelative = if (actualGameDir.canonicalPath == storageRootDir.canonicalPath) {
            launchTarget
        } else {
            val relativePath = actualGameDir.toRelativeString(storageRootDir)
            "$relativePath/$launchTarget"
        }

        val iconPathRelative = iconPath?.let { icon ->
            if (actualGameDir.canonicalPath == storageRootDir.canonicalPath) {
                icon
            } else {
                val relativePath = actualGameDir.toRelativeString(storageRootDir)
                "$relativePath/$icon"
            }
        }

        val gameItem = GameItem(
            id = storageId,  // 使用目录名作为存储 ID
            displayedName = definition.displayName,
            displayedDescription = "",
            gameId = definition.gameId,
            gameExePathRelative = gameExePathRelative,
            iconPathRelative = iconPathRelative,
            modLoaderEnabled = definition.isModLoader,
            dotNetRuntimeVersionOverride = runtimeOverride,
            gameEnvVars = envVars
        )

        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        val jsonString = json.encodeToString(gameItem)
        infoFile.writeText(jsonString)
    }

    /**
     * 创建游戏信息文件 (game_info.json) - 简化版本
     * 当 storageRootDir 和 actualGameDir 相同时使用
     * @param outputDir 游戏目录
     * @param definition 游戏定义
     * @param iconPath 图标路径（相对路径）
     */
    protected fun createGameInfo(outputDir: File, definition: GameDefinition, iconPath: String?) {
        createGameInfo(outputDir, outputDir, definition, iconPath)
    }

    /**
     * 安装 MonoMod 库到游戏目录
     * @param gameDir 游戏目录
     * @return 是否成功
     */
    protected fun installMonoMod(gameDir: File): Boolean {
        return try {
            val context: Context = KoinJavaComponent.get(Context::class.java)

            // 1. 解压 MonoMod 到目录
            val extractSuccess = AssemblyPatcher.extractMonoMod(context)
            if (!extractSuccess) {
                AppLog.w(TAG, "MonoMod 解压失败")
                return false
            }

            // 2. 应用补丁到游戏目录
            val patchedCount = AssemblyPatcher.applyMonoModPatches(context, gameDir.absolutePath, true)

            if (patchedCount >= 0) {
                AppLog.i(TAG, "MonoMod 已应用，替换了 $patchedCount 个文件")
                true
            } else {
                AppLog.w(TAG, "MonoMod 应用失败")
                false
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "MonoMod 安装异常", e)
            false
        }
    }

    /**
     * 复制目录内容
     * @param source 源目录
     * @param target 目标目录
     */
    protected fun copyDirectory(source: File, target: File) {
        if (!target.exists()) {
            target.mkdirs()
        }

        source.listFiles()?.forEach { file ->
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                copyDirectory(file, targetFile)
            } else {
                file.copyTo(targetFile, overwrite = true)
            }
        }
    }

    // ===== tModLoader 1.3（mono + FNA）平铺布局 =====
    //
    // tModLoader 1.3 无论是 GOG 的 ZIP 还是 Steam 的 1.3-legacy depot，落到磁盘上的
    // 布局必须是同一份：tModLoader.exe 平铺在 Terraria.exe 同级目录，启动入口换成
    // 包装 exe（[LEGACY_TML_LAUNCHER]），运行时走 mono。所以跳过规则、引导文件安装
    // 都放在基类里，GOG / Steam 两条安装路径共用。

    /**
     * tModLoader 1.3 发行包的根目录（含 tModLoader.exe 的那一层）
     */
    protected fun findLegacyRoot(extractedDir: File): File {
        if (File(extractedDir, "tModLoader.exe").exists()) return extractedDir
        val subdirs = extractedDir.listFiles { file -> file.isDirectory } ?: return extractedDir
        for (subdir in subdirs) {
            if (File(subdir, "tModLoader.exe").exists()) return subdir
        }
        return if (subdirs.size == 1) subdirs[0] else extractedDir
    }

    /**
     * 把 tModLoader 1.3 发行包平铺复制到 Terraria.exe 同级目录。
     *
     * 必须跳过的内容：
     * - mscorlib/System.* 等 mono BCL：会抢在 RAL 自带 BCL 之前被加载，导致运行时版本错乱
     * - lib/、lib64/：Linux glibc 平台库（带版本号的 .so），Android/bionic 用不到
     * - FNA.dll / FNA.dll.config：旧版 FNA（自带 mojoshader/OpenAL 路径），安装时会换成
     *   与新 FNA3D 配套的 FNA（见 [installLegacyBootstrap]）
     * - mono 启动脚本 / x86 宿主 / 调试符号
     *
     * @param keepExistingGameFiles 保留游戏目录里已有的本体文件（Steam 覆盖安装时用）：
     *        depot 自带一整份 Content/ 与 Terraria.exe，覆盖上去会顶掉现有的原版素材与
     *        Steam 版程序集，这里直接沿用已有的那份。
     */
    protected fun copyLegacyOverlay(
        source: File,
        destination: File,
        keepExistingGameFiles: Boolean = false
    ) {
        source.walkTopDown().forEach { file ->
            if (file.isDirectory) return@forEach
            val rel = file.toRelativeString(source).replace('\\', '/')
            if (shouldSkipLegacyEntry(rel, keepExistingGameFiles)) return@forEach
            val target = File(destination, rel)
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
        }
    }

    private fun shouldSkipLegacyEntry(rel: String, keepExistingGameFiles: Boolean): Boolean {
        val lower = rel.lowercase()
        if (lower.startsWith("lib/") || lower.startsWith("lib64/")) return true
        if (keepExistingGameFiles) {
            // Content/ = 原版素材、Terraria.exe = Steam 版本体，都不该被 tModLoader 的覆盖
            if (lower == "steam-shim" || lower.startsWith("steam-shim/")) return true
            if (lower == "content" || lower.startsWith("content/")) return true
            if (lower == "terraria.exe" || lower == "terrariaserver.exe") return true
            if (lower == "steam_appid.txt" || lower == "installscript.vdf") return true
        }
        val name = lower.substringAfterLast('/')
        if (name == "mscorlib.dll") return true
        if (name == "system.dll" || name.startsWith("system.")) return true
        if (name == "mono.posix.dll" || name == "mono.security.dll") return true
        if (name == "monoconfig" || name == "monomachineconfig") return true
        if (name == "fna.dll" || name == "fna.dll.config") return true
        if (name == "open-folder") return true
        if (name.startsWith("tmodloader.bin") || name.startsWith("tmodloaderserver.bin")) return true
        if (name == "tmodloader" || name == "tmodloader-kick" || name == "tmodloader-mono") return true
        if (name.startsWith("tmodloaderserver")) return true
        if (name.endsWith(".pdb")) return true
        return false
    }

    /**
     * 删掉游戏目录里的 `FNA.dll.config`。
     *
     * 这份 config 是 Linux 上的 mono dllmap（把 `DllImport("SDL2")` 之类映射到
     * `libSDL2-2.0.so.0` / `libFNA3D.so.0` 这种 glibc 平台 soname），Android/bionic 上这些名字
     * 都加载不到，留着会让 FNA 初始化直接失败。GOG 与 Steam 的 Terraria 包里都带它，装完必须删。
     */
    protected fun removeStaleFnaConfig(gameDir: File) {
        val config = File(gameDir, "FNA.dll.config")
        if (!config.exists()) return
        if (config.delete()) {
            AppLog.i(TAG, "已删除 FNA.dll.config（Linux dllmap，Android 用不到）: ${config.absolutePath}")
        } else {
            AppLog.w(TAG, "删除 FNA.dll.config 失败: ${config.absolutePath}")
        }
    }

    /**
     * 安装 tModLoader 1.3 的运行期引导文件（来自 assets/prebuilt/tml13）：
     * 包装 exe、配套 FNA（FNA3D/FAudio 版）。
     *
     * 说明：不再往游戏目录塞 MonoMod —— tModLoader 1.3 自带内嵌的 MonoMod 20.11，
     * 包装 exe 通过 AssemblyResolve 复用同一份，避免版本打架（19/25 都不适配 mono）。
     */
    protected fun installLegacyBootstrap(gameDir: File) {
        val assets = listOf(
            LEGACY_TML_LAUNCHER,
            "FNA.dll",
        )
        assets.forEach { name ->
            try {
                RaLaunchApp.getInstance().assets.open("prebuilt/tml13/$name").use { input ->
                    File(gameDir, name).outputStream().use { output -> input.copyTo(output) }
                }
                AppLog.i(TAG, "已安装 tModLoader 1.3 引导文件: $name")
            } catch (e: Exception) {
                AppLog.w(TAG, "安装引导文件失败 $name: ${e.message}")
            }
        }
    }

    /**
     * 从 GameDefinition 创建 GameItem
     *
     * @param definition 游戏定义
     * @param storageRootDir 存储根目录（由 IGameRepositoryServiceV3 创建的目录）
     * @param actualGameDir 实际游戏文件所在目录（可能是 storageRootDir 的子目录）
     * @param iconPath 图标路径（相对于 actualGameDir）
     */
    protected fun createGameItem(
        definition: GameDefinition,
        storageRootDir: File,
        actualGameDir: File,
        iconPath: String?,
        launchTargetOverride: String? = null,
        runtimeOverride: String? = null,
        envVars: Map<String, String?> = emptyMap()
    ): GameItem {
        // 使用目录名作为存储 ID（与 GameRepositoryServiceV3 保持一致）
        val storageId = storageRootDir.name

        val launchTarget = launchTargetOverride ?: definition.launchTarget

        // 计算相对于 storageRootDir 的路径
        val gameExePathRelative = if (actualGameDir.canonicalPath == storageRootDir.canonicalPath) {
            launchTarget
        } else {
            val relativePath = actualGameDir.toRelativeString(storageRootDir)
            "$relativePath/$launchTarget"
        }

        val iconPathRelative = iconPath?.let { icon ->
            if (actualGameDir.canonicalPath == storageRootDir.canonicalPath) {
                icon
            } else {
                val relativePath = actualGameDir.toRelativeString(storageRootDir)
                "$relativePath/$icon"
            }
        }

        AppLog.i(TAG, "Creating GameItem: id=$storageId, displayedName=${definition.displayName}, " +
                "gameExePathRelative=$gameExePathRelative, iconPathRelative=$iconPathRelative")

        return GameItem(
            id = storageId,  // 使用目录名作为存储 ID
            displayedName = definition.displayName,
            displayedDescription = "",
            gameId = definition.gameId,
            gameExePathRelative = gameExePathRelative,
            iconPathRelative = iconPathRelative,
            modLoaderEnabled = definition.isModLoader,
            dotNetRuntimeVersionOverride = runtimeOverride,
            gameEnvVars = envVars
        )
    }

    /**
     * 从 GameDefinition 创建 GameItem - 简化版本
     * 当 storageRootDir 和 actualGameDir 相同时使用
     *
     * @param definition 游戏定义
     * @param gameDir 游戏目录
     * @param iconPath 图标路径（相对路径）
     */
    protected fun createGameItem(
        definition: GameDefinition,
        gameDir: File,
        iconPath: String?
    ): GameItem {
        return createGameItem(definition, gameDir, gameDir, iconPath)
    }

    companion object {
        private const val TAG = "BaseInstallPlugin"

        /** tModLoader 1.3 的启动入口：包装 exe（安装时写入，game_info 指向它） */
        const val LEGACY_TML_LAUNCHER = "TML13Launcher.exe"

        /** tModLoader 1.3 必须走 mono 运行时（mono:<版本>） */
        const val LEGACY_TML_RUNTIME = "mono:4.5"
    }
}
