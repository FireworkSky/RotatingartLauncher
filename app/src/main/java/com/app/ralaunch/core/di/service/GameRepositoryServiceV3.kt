package com.app.ralaunch.core.di.service

import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.core.model.GameList
import com.app.ralaunch.core.platform.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.random.Random

/**
 * 游戏仓库实现 V3
 *
 * 统一负责游戏列表读写、安装目录分配与游戏文件删除。
 */
@OptIn(ExperimentalPathApi::class)
class GameRepositoryServiceV3(
    private val gamesDirPathProvider: () -> java.nio.file.Path
) : IGameRepositoryServiceV3 {

    constructor(pathsProvider: StoragePathsProviderServiceV1) : this(
        gamesDirPathProvider = { Path(pathsProvider.gamesDirPathFull()) }
    )

    constructor(gamesDirPathFull: java.nio.file.Path) : this(
        gamesDirPathProvider = { gamesDirPathFull }
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutationMutex = Mutex()
    private val _gamesFlow = MutableStateFlow(loadGameList())
    override val games: StateFlow<List<GameItem>> = _gamesFlow.asStateFlow()

    override suspend fun getById(id: String): GameItem? = games.value.find { it.id == id }

    override suspend fun upsert(game: GameItem, index: Int) = mutateAndSave { list ->
        list.removeAll { it.id == game.id }
        val insertIndex = index.coerceIn(0, list.size)
        list.add(insertIndex, game)
    }

    override suspend fun removeById(id: String) = mutateAndSave { list ->
        list.removeAll { it.id == id }
    }

    override suspend fun removeAt(index: Int) = mutateAndSave { list ->
        if (index in list.indices) {
            list.removeAt(index)
        }
    }

    override suspend fun reorder(from: Int, to: Int) = mutateAndSave { list ->
        if (from !in list.indices || to !in list.indices || from == to) return@mutateAndSave
        val game = list.removeAt(from)
        list.add(to, game)
    }

    override suspend fun replaceAll(games: List<GameItem>) {
        mutationMutex.withLock {
            persist(games)
        }
    }

    override suspend fun clear() {
        mutationMutex.withLock {
            persist(emptyList())
        }
    }

    override fun getGameGlobalStorageDirFull(): String = gamesDirPathFull.toString()

    override fun createGameStorageRoot(gameId: String): Pair<String, String> {
        val baseName = gameId.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
        val storageRootPathRelative = "${baseName}_${randomHex(8)}"
        val storageRootPathFull = gamesDirPathFull.resolve(storageRootPathRelative)
        storageRootPathFull.createDirectories()
        return storageRootPathFull.toString() to storageRootPathRelative
    }

    override fun deleteGameFiles(game: GameItem): Boolean {
        val storageRootPathRelative = game.storageRootPathRelative
        if (storageRootPathRelative.isBlank()) return false

        return try {
            val gamesDir = gamesDirPathFull.toAbsolutePath().normalize()
            val gameDir = gamesDir.resolve(storageRootPathRelative).normalize()
            FileUtils.deleteDirectoryRecursivelyWithinRoot(gameDir, gamesDir)
        } catch (e: Exception) {
            AppLog.e(TAG, "删除游戏文件时发生错误: ${e.message}", e)
            false
        }
    }

    private fun loadGameList(): List<GameItem> {
        return try {
            if (!gameListPathFull.exists()) return emptyList()

            val gameList = json.decodeFromString<GameList>(gameListPathFull.readText())
            gameList.games.mapNotNull(::loadGameInfo).also(::attachRepository)
        } catch (e: Exception) {
            AppLog.e(TAG, "加载游戏列表失败: ${e.message}", e)
            emptyList()
        }
    }

    private fun loadGameInfo(storageRootPathRelative: String): GameItem? {
        return try {
            val gameInfoPathFull = gamesDirPathFull
                .resolve(storageRootPathRelative)
                .resolve(AppConstants.Files.GAME_INFO)
            if (!gameInfoPathFull.exists()) return null

            json.decodeFromString<GameItem>(gameInfoPathFull.readText()).also {
                it.gameRepositoryParent = this
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "加载游戏信息失败: ${e.message}", e)
            null
        }
    }

    private fun saveGameList(games: List<GameItem>) {
        try {
            gamesDirPathFull.createDirectories()

            val gameList = GameList(games = games.map { it.id })
            gameListPathFull.writeText(json.encodeToString(gameList))

            games.forEach(::saveGameInfo)
        } catch (e: Exception) {
            AppLog.e(TAG, "保存游戏列表失败: ${e.message}", e)
        }
    }

    private fun saveGameInfo(game: GameItem) {
        try {
            val storageRootPathFull = gamesDirPathFull.resolve(game.id)
            val gameInfoPathFull = storageRootPathFull.resolve(AppConstants.Files.GAME_INFO)

            storageRootPathFull.createDirectories()
            gameInfoPathFull.writeText(json.encodeToString(game))
        } catch (e: Exception) {
            AppLog.e(TAG, "保存游戏信息失败: ${e.message}", e)
        }
    }

    private suspend inline fun mutateAndSave(crossinline mutate: (MutableList<GameItem>) -> Unit) {
        mutationMutex.withLock {
            val list = _gamesFlow.value.toMutableList()
            mutate(list)
            persist(list)
        }
    }

    private fun persist(games: List<GameItem>) {
        val immutable = games.toList()
        attachRepository(immutable)
        _gamesFlow.value = immutable
        saveGameList(immutable)
    }

    private fun attachRepository(games: List<GameItem>) {
        games.forEach { it.gameRepositoryParent = this }
    }

    private fun randomHex(length: Int): String {
        val chars = "0123456789abcdef"
        return buildString(length) {
            repeat(length) {
                append(chars[Random.nextInt(chars.length)])
            }
        }
    }

    private val gamesDirPathFull
        get() = gamesDirPathProvider()

    private val gameListPathFull
        get() = gamesDirPathFull.resolve(AppConstants.Files.GAME_LIST)

    private companion object {
        const val TAG = "GameRepositoryServiceV3"
    }
}
