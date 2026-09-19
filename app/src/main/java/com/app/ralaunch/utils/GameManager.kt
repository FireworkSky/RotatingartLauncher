package com.app.ralaunch.utils

import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.core.model.GameList
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.feature.game.ui.legacy.GameActivity
import com.app.ralaunch.jsonconfig.GameListFlowJsonConfigGenerated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * 游戏管理单例；应用启动时经 [initialize] 固定受管理目录，进程内共享同一份状态。
 *
 * 直接管理 game_list.json / <id>/game_info.json，兼容已有数据，无需迁移。
 * 配置读写经 FlowJsonConfig：索引固定路径；各游戏信息以显式文件参数读写。
 * 外部目录添加时复制到受管理目录；移除总是删除游戏目录并注销登记。
 * 平台启动直接调用 GameActivity.launch，不经过容器或回调注入。调用方负责
 * Android Activity / 原生运行时等平台生命周期，异常直接传给调用方。
 *
 * ```
 * GameManager.initialize(gamesDirectory)
 * val game = GameManager.add(sourceDirectory, "celeste", "Celeste.exe")
 * GameManager.configure(game.id) {
 *     displayedName = "Celeste"
 *     rendererOverride = "native"
 * }
 * GameManager.launch(game.id).apply {
 *     renderer = null // 仅本次跟随全局设置，不修改磁盘
 *     arguments = listOf("--example")
 * }.start()
 * ```
 */
@OptIn(ExperimentalPathApi::class)
object GameManager {

    @Volatile
    private var gamesDirectoryValue: Path? = null

    @Volatile
    private var indexConfigValue: GameListFlowJsonConfigGenerated? = null

    /** 受管理游戏目录；[initialize] 之前访问会抛出 [IllegalStateException]。 */
    val gamesDirectory: Path
        get() = requireNotNull(gamesDirectoryValue) { "GameManager is not initialized" }

    /** 索引 game_list.json：整个管理器固定一份（同 AppConfig 模式）。 */
    private val indexConfig: GameListFlowJsonConfigGenerated
        get() = requireNotNull(indexConfigValue) { "GameManager is not initialized" }

    private val mutex = Mutex()
    private val gameState = MutableStateFlow<List<GameItem>>(emptyList())

    /** 固定受管理目录并从磁盘重建内存状态；应用启动或测试准备时调用。 */
    @Synchronized
    fun initialize(gamesDirectory: Path) {
        val root = gamesDirectory.createDirectories().toRealPath()
        gamesDirectoryValue = root
        indexConfigValue = object : GameListFlowJsonConfigGenerated() {
            override val configPath = root.resolve(AppConstants.Files.GAME_LIST).toString()
        }
        gameState.value = loadGames()
    }

    /** 每个订阅者取得独立快照，修改 GameItem 不会改变管理器中的状态。 */
    val games: Flow<List<GameItem>> = gameState.map { list -> list.map(::snapshot) }

    val currentGames: List<GameItem>
        get() = gameState.value.map(::snapshot)

    fun get(id: String): GameItem? = gameState.value.find { it.id == id }?.let(::snapshot)

    /** 分配空目录，供安装插件或外部导入复制使用；完成后由调用方通过 [save] 或 [add] 登记，不提前发布半成品。 */
    suspend fun createDirectory(gameId: String): Path = withContext(Dispatchers.IO) {
        require(gameId.isNotBlank()) { "Game type must not be blank" }
        val baseName = gameId.replace(UNSAFE_DIRECTORY_CHARACTERS, "_")
        createTempDirectory(gamesDirectory, "${baseName}_")
    }

    /**
     * 登记已有直属子目录，或复制外部目录后登记。不会移动或删除源目录。
     * [executable] 必须是目录内现有文件的相对路径，支持嵌套目录。
     */
    suspend fun add(
        directory: Path,
        gameId: String,
        executable: String,
        name: String = directory.toAbsolutePath().fileName.toString()
    ): GameItem = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(gameId.isNotBlank()) { "Game type must not be blank" }
            require(name.isNotBlank()) { "Game name must not be blank" }
            val source = directory.toRealPath()
            require(source.isDirectory()) { "Not a game directory: $source" }
            resolveFile(source, executable)
            require(source != gamesDirectory && !gamesDirectory.startsWith(source)) {
                "Cannot import the games directory or its ancestor"
            }
            require(!source.startsWith(gamesDirectory) || source.parent == gamesDirectory) {
                "Managed game directories must be direct children of $gamesDirectory"
            }
            val target = if (source.parent == gamesDirectory) {
                source
            } else {
                createDirectory(gameId).also {
                    source.copyToRecursively(it, followLinks = false, overwrite = false)
                }
            }
            val id = target.fileName.toString()
            require(gameState.value.none { it.id == id }) { "Game already registered: $id" }
            val game = GameItem(
                id = id,
                displayedName = name,
                gameId = gameId,
                gameExePathRelative = executable
            )
            saveLocked(game, 0)
        }
    }

    /** 保存安装器生成的游戏信息，或更新已有游戏；[index] 按移除旧条目后的列表计算。 */
    suspend fun save(game: GameItem, index: Int = 0): GameItem = withContext(Dispatchers.IO) {
        mutex.withLock { saveLocked(game, index) }
    }

    /** 持久化游戏独立配置；回调只修改快照，失败不会污染管理器中的对象。 */
    suspend fun configure(id: String, configure: GameItem.() -> Unit): GameItem =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val game = requireNotNull(get(id)) { "Unknown game: $id" }.apply(configure)
                saveLocked(game, gameState.value.indexOfFirst { it.id == id })
            }
        }

    suspend fun reorder(from: Int, to: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = gameState.value.toMutableList()
            if (from !in list.indices || to !in list.indices || from == to) return@withLock
            list.add(to, list.removeAt(from))
            persist(list)
        }
    }

    /** 总是删除游戏目录并注销登记；拒绝根目录和路径穿越，符号链接只删除链接本身。 */
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val path = storagePath(id)
            require(gameState.value.any { it.id == id }) { "Unknown game: $id" }
            path.deleteRecursively()
            persist(gameState.value.filterNot { it.id == id })
        }
    }

    fun directory(id: String): Path {
        val directory = storagePath(id).toRealPath()
        require(directory.parent == gamesDirectory && directory.isDirectory()) {
            "Game directory escapes managed storage: $id"
        }
        return directory
    }

    /** 捕获独立配置快照；使用标准 apply DSL 进行本次启动覆盖，然后调用 start。 */
    fun launch(id: String): Launch = Launch(requireNotNull(get(id)) { "Unknown game: $id" })

    class Launch internal constructor(private val game: GameItem) {
        var renderer: String? = game.rendererOverride
        var runtimeVersion: String? = game.dotNetRuntimeVersionOverride
        var environment: Map<String, String?> = game.gameEnvVars.toMap()
        var arguments: List<String> = emptyList()

        suspend fun start() {
            require(get(game.id) != null) { "Game is no longer registered: ${game.id}" }
            val effectiveGame = game.copy(
                rendererOverride = renderer,
                dotNetRuntimeVersionOverride = runtimeVersion,
                gameEnvVars = environment.toMap()
            )
            val root = directory(game.id)
            val executable = resolveFile(root, effectiveGame.gameExePathRelative)
            execute(LaunchRequest(effectiveGame, root, executable, arguments.toList()))
        }
    }

    /** 绝对路径已解析，无需管理器反向引用或 Koin 即可执行。 */
    data class LaunchRequest(
        val game: GameItem,
        val directory: Path,
        val executable: Path,
        val arguments: List<String>
    )

    /** 平台启动入口：直接调用 GameActivity，无容器查找；路径均已解析为绝对路径。 */
    private suspend fun execute(request: LaunchRequest) = withContext(Dispatchers.Main) {
        GameActivity.launch(
            context = RaLaunchApp.getAppContext(),
            gameExePath = request.executable.toString(),
            gameArgs = request.arguments.toTypedArray(),
            gameId = request.game.gameId,
            gameRendererOverride = request.game.rendererOverride,
            gameEnvVars = request.game.gameEnvVars,
            gameRuntimeVersionOverride = request.game.dotNetRuntimeVersionOverride
        )
    }

    private fun snapshot(game: GameItem): GameItem =
        game.copy(gameEnvVars = game.gameEnvVars.toMap())

    private fun loadGames(): List<GameItem> {
        indexConfig.load()
        return indexConfig.value.games.distinct().mapNotNull { id ->
            if (!infoFile(id).exists()) return@mapNotNull null
            directory(id)
            val config = GameItemConfig()
            config.load(infoFile(id))
            val game = config.value
            require(game.id == id) { "Game ID does not match its directory: $id" }
            snapshot(game)
        }
    }

    /** 在 mutex 内调用；只写变更的游戏信息，排序/移除不重写其他游戏配置。 */
    private fun saveLocked(game: GameItem, index: Int): GameItem {
        val saved = snapshot(game)
        validate(saved)
        val list = gameState.value.filterNot { it.id == saved.id }.toMutableList()
        list.add(index.coerceIn(0, list.size), saved)
        persist(list, saved)
        return snapshot(saved)
    }

    private fun persist(games: List<GameItem>, changedGame: GameItem? = null) {
        changedGame?.let { game ->
            val config = GameItemConfig()
            config.update { game }
            check(config.save(infoFile(game.id))) { "Failed to save game info: ${game.id}" }
        }
        // 文件写入成功后才发布状态；异常传给保存/删除操作的调用方。
        indexConfig.update { GameList(games.map { it.id }) }
        check(indexConfig.save()) { "Failed to save game list" }
        gameState.value = games
    }

    private fun infoFile(id: String): File =
        directory(id).resolve(AppConstants.Files.GAME_INFO).toFile()

    private fun storagePath(id: String): Path {
        val relative = Path(id)
        require(id.isNotBlank() && !relative.isAbsolute && relative.nameCount == 1 &&
            id != "." && id != "..") { "Invalid game directory ID: $id" }
        return gamesDirectory.resolve(relative)
    }

    private fun validate(game: GameItem) {
        require(game.displayedName.isNotBlank()) { "Game name must not be blank" }
        require(game.gameId.isNotBlank()) { "Game type must not be blank" }
        val root = directory(game.id)
        resolveFile(root, game.gameExePathRelative)
        // 图标是可选展示资源；缺失时仍允许保存游戏配置。
        game.iconPathRelative?.let {
            val relative = Path(it)
            require(!relative.isAbsolute && root.resolve(relative).normalize().startsWith(root)) {
                "Icon path escapes game directory: $it"
            }
        }
    }

    private fun resolveFile(root: Path, relativePath: String): Path {
        val relative = Path(relativePath)
        require(relativePath.isNotBlank() && !relative.isAbsolute) { "Expected a relative file path" }
        val path = root.resolve(relative).toRealPath()
        require(path.startsWith(root) && path.isRegularFile()) { "File escapes game directory: $relativePath" }
        return path
    }

    private val UNSAFE_DIRECTORY_CHARACTERS = Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]")
}
