package com.app.ralaunch.feature.installer

import android.content.Context
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
        iconPath: String?
    ) {
        val infoFile = File(storageRootDir, "game_info.json")

        // 使用目录名作为存储 ID（与 GameRepositoryServiceV3 保持一致）
        val storageId = storageRootDir.name

        // 计算相对于 storageRootDir 的路径
        val gameExePathRelative = if (actualGameDir.canonicalPath == storageRootDir.canonicalPath) {
            definition.launchTarget
        } else {
            val relativePath = actualGameDir.toRelativeString(storageRootDir)
            "$relativePath/${definition.launchTarget}"
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
            modLoaderEnabled = definition.isModLoader
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
        iconPath: String?
    ): GameItem {
        // 使用目录名作为存储 ID（与 GameRepositoryServiceV3 保持一致）
        val storageId = storageRootDir.name

        // 计算相对于 storageRootDir 的路径
        val gameExePathRelative = if (actualGameDir.canonicalPath == storageRootDir.canonicalPath) {
            definition.launchTarget
        } else {
            val relativePath = actualGameDir.toRelativeString(storageRootDir)
            "$relativePath/${definition.launchTarget}"
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
            modLoaderEnabled = definition.isModLoader
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
    }
}
