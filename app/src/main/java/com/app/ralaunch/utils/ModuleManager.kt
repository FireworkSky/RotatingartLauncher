package com.app.ralaunch.utils

import com.app.ralaunch.core.extractor.ArchiveExtractor
import com.app.ralaunch.models.ModuleEntry
import com.app.ralaunch.models.ModuleItem
import com.app.ralaunch.models.ModuleList
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.jsonconfig.ModuleListFlowJsonConfigGenerated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.apache.commons.compress.archivers.zip.ZipFile
import timber.log.Timber
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * 模块管理单例；应用启动时经 [initialize] 固定受管理目录，进程内共享同一份状态。
 * 与 [GameManager] 同构：直接管理 modules/module_manager.json，每个直属子目录是一个模块，
 * 目录内 meta.json 声明 id（安卓包名格式，必须等于目录名）与 type，其余字段由消费方按 type 解码。
 *
 * 管理器只持有/透传 meta 原始 [kotlinx.serialization.json.JsonObject]，永不回写 meta.json；
 * [ModuleItem.enabled] 是管理器状态，只存在于 module_manager.json。
 * 磁盘上的坏模块目录（meta 缺失/非法、id 不符）一律跳过并 Timber.w，不使 initialize/refresh 失败。
 *
 * ```
 * ModuleManager.initialize(modulesDirectory)
 * val module = ModuleManager.install(sourceDirectory) // 目录：外部复制；直属子目录仅登记
 * ModuleManager.setEnabled(module.id, false)
 * ModuleManager.install(moduleZip, force = true)      // zip：根层直接是 meta.json 与模块内容
 * ModuleManager.remove(module.id)
 * ```
 */
@OptIn(ExperimentalPathApi::class)
object ModuleManager {

    /** 模块 id：ASCII 包名格式，至少两段，每段以字母开头。 */
    private val MODULE_ID_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    @Volatile
    private var modulesDirectoryValue: Path? = null

    @Volatile
    private var indexConfigValue: ModuleListFlowJsonConfigGenerated? = null

    /** 受管理模块目录；[initialize] 之前访问会抛出 [IllegalStateException]。 */
    val modulesDirectory: Path
        get() = requireNotNull(modulesDirectoryValue) { "ModuleManager is not initialized" }

    /** 索引 module_manager.json：整个管理器固定一份（同 GameManager 模式）。 */
    private val indexConfig: ModuleListFlowJsonConfigGenerated
        get() = requireNotNull(indexConfigValue) { "ModuleManager is not initialized" }

    private val mutex = Mutex()
    private val moduleState = MutableStateFlow<List<ModuleItem>>(emptyList())

    /** 固定受管理目录并从磁盘重建内存状态；应用启动或测试准备时调用，可重复调用。 */
    @Synchronized
    fun initialize(modulesDirectory: Path) {
        val root = modulesDirectory.createDirectories().toRealPath()
        modulesDirectoryValue = root
        indexConfigValue = object : ModuleListFlowJsonConfigGenerated() {
            override val configPath = root.resolve(AppConstants.Files.MODULE_MANAGER).toString()
        }
        moduleState.value = scanLocked()
    }

    /** 每个订阅者取得独立列表快照；字段均不可变，浅拷贝即可。 */
    val modules: Flow<List<ModuleItem>> = moduleState.map { it.toList() }

    val currentModules: List<ModuleItem>
        get() = moduleState.value.toList()

    fun get(id: String): ModuleItem? = moduleState.value.find { it.id == id }

    /**
     * 重扫磁盘并发布内存状态；索引条目顺序优先，磁盘新发现的模块按 id 字典序追加，
     * 索引中有而磁盘缺失的模块剔除。与 load 一致，不在发现/剔除时落盘。
     */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock { moduleState.value = scanLocked() }
    }

    /**
     * 统一安装/更新入口：自动识别来源类型并分发。
     * - 常规文件 → zip 压缩包：压缩包内无顶层文件夹，meta.json 与模块内容直接位于压缩包根
     *   （同 patch.json 布局），解压安装，模块 id 只来自 meta.json；
     * - 外部目录 → 复制后登记，不移动或删除源目录；模块 id 来自源目录内 meta.json；
     * - 管理目录直属子目录 → 仅登记不复制（模块 id 必须等于目录名）。
     *
     * 版本控制（zip 与外部目录一致，按 "." 分段数字比较）：未登记 → 全新安装（enabled = true）；
     * 已登记且源版本更高 → 删除旧目录后原位替换，保留列表顺序与 enabled 管理状态；
     * 版本不高时跳过并返回当前条目；[force] 为 true 时无视版本强制重装。
     * 直属子目录登记幂等：已登记时直接返回当前条目（管理目录内直接改动经 [refresh] 生效）。
     * 拒绝以管理目录自身或其祖先作为来源；管理目录内的来源必须是直属子目录。
     */
    suspend fun install(source: Path, force: Boolean = false): ModuleItem =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                require(source.exists()) { "Module source not found: $source" }
                val real = source.toRealPath()
                when {
                    real.isRegularFile() -> {
                        val parsed = requireNotNull(readZipMeta(real)) {
                            "Invalid module archive meta: $real"
                        }
                        applyLocked(parsed, force) { target -> extractArchive(real, target) }
                    }
                    real.isDirectory() -> {
                        require(real != modulesDirectory && !modulesDirectory.startsWith(real)) {
                            "Cannot import the modules directory or its ancestor"
                        }
                        require(!real.startsWith(modulesDirectory) || real.parent == modulesDirectory) {
                            "Managed module directories must be direct children of $modulesDirectory"
                        }
                        val parsed = requireNotNull(parseMeta(real)) { "Invalid module meta: $real" }
                        if (real.parent == modulesDirectory) {
                            val current = moduleState.value.find { it.id == parsed.id }
                            if (current != null) {
                                current
                            } else {
                                val registered = parsed.copy(enabled = true)
                                persist(moduleState.value + registered)
                                registered
                            }
                        } else {
                            applyLocked(parsed, force) { target ->
                                target.createDirectories()
                                real.copyToRecursively(target, followLinks = false, overwrite = false)
                            }
                        }
                    }
                    else -> throw IllegalArgumentException("Not a module directory or archive: $real")
                }
            }
        }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(moduleState.value.any { it.id == id }) { "Unknown module: $id" }
            persist(moduleState.value.map { if (it.id == id) it.copy(enabled = enabled) else it })
        }
    }

    suspend fun reorder(from: Int, to: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = moduleState.value.toMutableList()
            if (from !in list.indices || to !in list.indices || from == to) return@withLock
            list.add(to, list.removeAt(from))
            persist(list)
        }
    }

    /** 总是删除模块目录并注销登记；拒绝根目录和路径穿越，符号链接只删除链接本身。 */
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val path = storagePath(id)
            require(moduleState.value.any { it.id == id }) { "Unknown module: $id" }
            path.deleteRecursively()
            persist(moduleState.value.filterNot { it.id == id })
        }
    }

    fun directory(id: String): Path {
        val directory = storagePath(id).toRealPath()
        require(directory.parent == modulesDirectory && directory.isDirectory()) {
            "Module directory escapes managed storage: $id"
        }
        return directory
    }

    /**
     * 重扫磁盘并与索引合并：索引条目顺序优先（enabled 取索引值，磁盘缺失则剔除），
     * 未登记的新目录按 id 字典序追加（enabled = true）。不落盘；发现/剔除仅在显式变更时固化。
     */
    private fun scanLocked(): List<ModuleItem> {
        indexConfig.load()
        val metas = LinkedHashMap<String, ModuleItem>()
        Files.newDirectoryStream(modulesDirectory).use { stream ->
            for (path in stream) {
                if (Files.isSymbolicLink(path)) {
                    Timber.w("Skipping module symbolic link: %s", path)
                    continue
                }
                if (!path.isDirectory()) continue
                parseMeta(path)?.let { metas[it.id] = it }
            }
        }
        val result = mutableListOf<ModuleItem>()
        val seen = HashSet<String>()
        for (entry in indexConfig.value.modules) {
            if (!seen.add(entry.id)) continue // 重复索引条目，同 GameManager 的 distinct 语义
            val item = metas[entry.id]
            if (item == null) {
                Timber.w("Registered module missing on disk: %s", entry.id)
                continue
            }
            result += item.copy(enabled = entry.enabled)
        }
        for (id in metas.keys.filterNot { it in seen }.sorted()) {
            result += metas.getValue(id).copy(enabled = true)
        }
        return result
    }

    /**
     * 解析目录内 meta.json 为 [ModuleItem]；[enabled] 占位 false，由调用方按索引合并。
     * meta 缺失、非法 JSON、id 非包名格式或不等于目录名、type 非非空白字符串 → 记录并返回 null。
     */
    private fun parseMeta(dir: Path): ModuleItem? {
        val metaFile = dir.resolve(AppConstants.Files.MODULE_META)
        val meta = runCatching { Json.parseToJsonElement(metaFile.readText()) }.getOrNull()
        if (meta !is JsonObject) {
            Timber.w("Skipping module directory with missing or invalid meta: %s", dir)
            return null
        }
        val item = metaItem(meta)
        if (item == null || item.id != dir.fileName.toString()) {
            Timber.w("Skipping module directory with invalid meta content: %s", dir)
            return null
        }
        return item
    }

    /**
     * 读取 zip 压缩包根层 meta.json 并解析；
     * 压缩包打不开、根层无 meta.json 或内容非法时记录并返回 null。
     */
    private fun readZipMeta(sourceZip: Path): ModuleItem? {
        return try {
            ZipFile(sourceZip.toFile()).use { zip ->
                val entry = zip.getEntry(AppConstants.Files.MODULE_META) ?: return null
                val meta = InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8).use {
                    Json.parseToJsonElement(it.readText())
                }
                if (meta !is JsonObject) return null
                metaItem(meta)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to read module archive meta: %s", sourceZip)
            null
        }
    }

    /** 校验并构造 [ModuleItem]（不校验目录名）；id 非包名格式或 type 非非空白字符串时返回 null。 */
    private fun metaItem(meta: JsonObject): ModuleItem? {
        val id = (meta["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val type = (meta["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (id == null || !MODULE_ID_REGEX.matches(id) || type.isNullOrBlank()) return null
        return ModuleItem(
            id = id,
            type = type,
            name = meta.metaString("name"),
            description = meta.metaString("description"),
            version = meta.metaString("version"),
            author = meta.metaString("author"),
            meta = meta,
            enabled = false
        )
    }

    /** meta 通用字段：仅接受字符串原始值，缺失或非字符串（null/数字/对象/数组）时回退空字符串。 */
    private fun JsonObject.metaString(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""

    /**
     * 在 mutex 内调用；[install] 的版本受控替换核心（外部目录复制与 zip 解压共用）：
     * 未登记 → 写入 modules/<id> 并末尾登记（enabled = true）；
     * 已登记且 !force 且源版本不高于当前 → 跳过并返回当前条目；
     * 否则删除旧目录后由 [replaceInto] 填入新内容，原位替换并保留 enabled。
     */
    private fun applyLocked(parsed: ModuleItem, force: Boolean, replaceInto: (Path) -> Unit): ModuleItem {
        val current = moduleState.value.find { it.id == parsed.id }
        return when {
            current == null -> {
                val target = modulesDirectory.resolve(parsed.id)
                require(!target.exists()) { "Module already registered: ${parsed.id}" }
                replaceInto(target)
                val installed = parsed.copy(enabled = true)
                persist(moduleState.value + installed)
                installed
            }
            !force && compareVersions(parsed.version, current.version) <= 0 -> {
                Timber.i(
                    "Module already up to date, skipping: %s (installed: %s, source: %s)",
                    parsed.id, current.version, parsed.version
                )
                current
            }
            else -> {
                Timber.i("Updating module: %s (%s -> %s)", parsed.id, current.version, parsed.version)
                val target = modulesDirectory.resolve(parsed.id)
                target.deleteRecursively()
                replaceInto(target)
                val updated = parsed.copy(enabled = current.enabled)
                persist(moduleState.value.map { if (it.id == updated.id) updated else it })
                updated
            }
        }
    }

    /** 经 ArchiveExtractor (apache commons compress) 解压压缩包到目标目录；失败抛 [IllegalStateException]。 */
    private fun extractArchive(sourceZip: Path, target: Path) {
        when (val result = ArchiveExtractor.builder().from(sourceZip).to(target).build().extract()) {
            is ArchiveExtractor.Result.Success -> Unit
            is ArchiveExtractor.Result.Failure ->
                throw IllegalStateException("Failed to extract module archive: ${result.message}", result.cause)
        }
    }

    /**
     * 比较两个版本号字符串（语义同 PatchManifest.compareVersions）。
     * 按 "." 分割后逐段比较数字大小，非数字段按 0 处理。
     *
     * @return 正数表示 v1 > v2，负数表示 v1 < v2，0 表示相等
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    /** 文件写入成功后才发布状态；异常传给变更操作的调用方。 */
    private fun persist(modules: List<ModuleItem>) {
        indexConfig.update { ModuleList(modules.map { ModuleEntry(id = it.id, enabled = it.enabled) }) }
        check(indexConfig.save()) { "Failed to save module list" }
        moduleState.value = modules
    }

    private fun storagePath(id: String): Path {
        val relative = Path(id)
        require(id.isNotBlank() && !relative.isAbsolute && relative.nameCount == 1 &&
            id != "." && id != "..") { "Invalid module directory ID: $id" }
        return modulesDirectory.resolve(relative)
    }
}
