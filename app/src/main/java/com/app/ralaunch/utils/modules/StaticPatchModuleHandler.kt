package com.app.ralaunch.utils.modules

import com.app.ralaunch.utils.ModuleManager
import timber.log.Timber
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile

/**
 * 静态补丁模块处理器：无状态的静态补丁查询/文件替换门面（单例，直接调用）。
 *
 * 静态补丁（type [TYPE]）只在游戏安装阶段执行：安装收尾时把模块携带的一组文件
 * 直接覆盖到游戏目录（覆盖式替换），启动阶段不再执行。
 * 安装、登记、启停（总开关）与删除统一走 [ModuleManager]；本 handler 只从
 * [com.app.ralaunch.models.ModuleItem.meta] 解析专属字段并提供查询与应用。
 *
 * 应用条件 = targetGames 匹配 ∧ [com.app.ralaunch.models.ModuleItem.enabled]。
 *
 * 覆盖是纯覆盖、不可还原：禁用/卸载后已替换文件保持现状，需重装游戏还原；
 * 重新安装游戏会重新解压游戏文件并再次应用当时适用的静态补丁。
 */
object StaticPatchModuleHandler : ModuleHandler {

    override val type = TYPE
    const val TYPE = "static_patch"

    /** 全部已安装静态补丁（无法解码清单的模块跳过）。 */
    fun staticPatches(): List<StaticPatch> =
        ModuleManager.currentModules.filter { it.type == TYPE }.mapNotNull(StaticPatch::fromModule)

    /** 对指定游戏适用的静态补丁（targetGames 含 "*" 或该 gameId），按 priority 降序。 */
    fun getApplicableStaticPatches(gameId: String): List<StaticPatch> =
        staticPatches()
            .filter { it.manifest.targetGames.any { g -> g == "*" || g == gameId } }
            .sortedByDescending { it.manifest.priority }

    /**
     * 安装阶段应用口径：对指定游戏适用且模块总开关开启的静态补丁
     * （游戏刚安装尚无按游戏启用状态，仅以模块总开关过滤）。
     */
    fun getApplicableAndEnabledStaticPatches(gameId: String): List<StaticPatch> =
        getApplicableStaticPatches(gameId).filter { it.module.enabled }

    /**
     * 把一组静态补丁的文件覆盖到游戏目录，返回成功替换的文件数。
     *
     * 按 [StaticPatchManifest.priority] 升序应用、后写胜出（priority 越高最终生效）。
     * 每个 [StaticPatchManifest.FileEntry]：
     * - target 不含 `*`/`?` → 定点替换字面相对路径（目标可原本不存在，覆盖 = 创建）；
     * - target 含通配符 → glob 替换所有"相对路径匹配模式"的已存在文件（不创建）。
     *
     * source 越界（绝对路径/`..` 穿越模块目录）或 target 越界/非法的条目 Timber.w 跳过；
     * 单条/单文件失败不中断其余替换。无备份、无还原：
     * 禁用/卸载后已替换文件保持现状，需重装游戏还原。
     */
    fun applyToGameDirectory(patches: List<StaticPatch>, gameDirectory: Path): Int {
        val gameDir = gameDirectory.toAbsolutePath().normalize()
        var replacedCount = 0

        for (patch in patches.sortedBy { it.manifest.priority }) {
            val moduleDir = try {
                ModuleManager.directory(patch.id)
            } catch (e: Exception) {
                Timber.w(e, "Static patch module directory unavailable: %s", patch.id)
                continue
            }

            for (entry in patch.manifest.files) {
                try {
                    if (isInvalidTarget(entry.target)) {
                        Timber.w("Skip invalid static patch target: %s!%s", patch.id, entry.target)
                        continue
                    }

                    val source = moduleDir.resolve(entry.source).normalize()
                    if (!source.startsWith(moduleDir) || !source.isRegularFile()) {
                        Timber.w("Skip missing or escaping static patch source: %s!%s", patch.id, entry.source)
                        continue
                    }

                    if (entry.target.contains('*') || entry.target.contains('?')) {
                        val regex = targetPatternRegex(entry.target)
                        if (regex == null) {
                            Timber.w("Skip invalid static patch glob: %s!%s", patch.id, entry.target)
                            continue
                        }
                        var entryCount = 0
                        Files.walk(gameDir).use { stream ->
                            stream.filter { it.isRegularFile() }.forEach { file ->
                                if (!regex.matches(gameDir.relativize(file).toString())) return@forEach
                                try {
                                    Files.copy(source, file, StandardCopyOption.REPLACE_EXISTING)
                                    replacedCount++
                                    entryCount++
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed to replace %s for %s", file, patch.id)
                                }
                            }
                        }
                        if (entryCount == 0) Timber.w("Static patch glob matched no file: %s!%s", patch.id, entry.target)
                    } else {
                        val target = gameDir.resolve(entry.target).normalize()
                        if (!target.startsWith(gameDir)) {
                            Timber.w("Skip escaping static patch target: %s!%s", patch.id, entry.target)
                            continue
                        }
                        target.parent?.createDirectories()
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                        replacedCount++
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to apply static patch entry: %s!%s", patch.id, entry.target)
                }
            }
        }
        return replacedCount
    }

    private fun isInvalidTarget(target: String): Boolean =
        target.isBlank() || target.startsWith("/") || target.contains("\\")

    /**
     * 把 target glob 模式翻译为锚定正则（gitignore 风格语义，自写翻译器；
     * 不用 java.nio.file.PathMatcher——其 `**` 段等价 `.*`，会让以 `**` 开头再接
     * `/lib.dll` 的模式漏掉根目录文件）：
     * - `**` 段 = 任意个路径段（含零个）：`**` 开头再接 `lib.dll` 同时匹配 `lib.dll` 与 `Sub/lib.dll`；
     * - 末尾 `**` 要求至少一个下级段：`Content` 加 `**` 不匹配名为 `Content` 的文件本身；
     * - 段内 `*` = 不含 `/` 的任意字符序列，`?` = 单个非 `/` 字符，其余字面匹配（大小写敏感）。
     * 模式非法（空白、以 `/` 开头、含 `\`）返回 null。
     */
    private fun targetPatternRegex(target: String): Regex? {
        if (isInvalidTarget(target)) return null
        val segments = target.split('/')
        val pattern = StringBuilder("^")
        var pendingSlash = false
        segments.forEachIndexed { index, segment ->
            if (segment == "**") {
                pendingSlash = false
                when {
                    index < segments.lastIndex -> pattern.append("(?:[^/]+/)*")
                    segments.size > 1 -> pattern.append("(?:/[^/]+)+")
                    else -> pattern.append(".+")
                }
            } else {
                if (pendingSlash) pattern.append('/')
                pendingSlash = true
                appendSegmentPattern(pattern, segment)
            }
        }
        return pattern.append('$').toString().toRegex()
    }

    /** 段内翻译：`*`→`[^/]*`、`?`→`[^/]`，其余字面连续段用 [Pattern.quote] 包裹。 */
    private fun appendSegmentPattern(pattern: StringBuilder, segment: String) {
        val literal = StringBuilder()
        fun flushLiteral() {
            if (literal.isNotEmpty()) {
                pattern.append(Pattern.quote(literal.toString()))
                literal.setLength(0)
            }
        }
        for (ch in segment) {
            when (ch) {
                '*' -> {
                    flushLiteral()
                    pattern.append("[^/]*")
                }
                '?' -> {
                    flushLiteral()
                    pattern.append("[^/]")
                }
                else -> literal.append(ch)
            }
        }
        flushLiteral()
    }
}
