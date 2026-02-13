package com.app.ralaunch.shared.data.repository

import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * 游戏仓库实现
 *
 * 使用 JSON 文件存储游戏列表
 */
class GameRepositoryImpl(
    private val gameListStorage: GameListStorage
) : GameRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _gamesFlow = MutableStateFlow<List<GameItem>>(emptyList())

    init {
        // 初始化时加载数据
        loadGamesInternal()
    }

    private fun loadGamesInternal() {
        val games = gameListStorage.loadGameList()
        // 确保所有游戏项都设置了 gameListStorageParent 引用（虽然 loadGameList 应该已经设置了）
        games.forEach { it.gameListStorageParent = gameListStorage }
        _gamesFlow.value = games
    }

    override fun getGames(): Flow<List<GameItem>> = _gamesFlow.asStateFlow()

    override suspend fun getGameList(): List<GameItem> = _gamesFlow.value

    override suspend fun getGameById(id: String): GameItem? {
        return _gamesFlow.value.find { it.id == id }
    }

    override suspend fun addGame(game: GameItem, position: Int) {
        val games = _gamesFlow.value.toMutableList()
        // 去重：如果已存在相同 storageBasePath 的游戏，先移除旧条目
        games.removeAll { it.id == game.id }
        val insertPosition = position.coerceIn(0, games.size)
        games.add(insertPosition, game)
        updateAndSave(games)
    }

    override suspend fun updateGame(game: GameItem) {
        val games = _gamesFlow.value.toMutableList()
        val index = games.indexOfFirst { it.id == game.id }
        if (index >= 0) {
            games[index] = game
            updateAndSave(games)
        }
    }

    override suspend fun deleteGame(id: String) {
        val games = _gamesFlow.value.toMutableList()
        games.removeAll { it.id == id }
        updateAndSave(games)
    }

    override suspend fun deleteGameAt(position: Int) {
        val games = _gamesFlow.value.toMutableList()
        if (position in games.indices) {
            games.removeAt(position)
            updateAndSave(games)
        }
    }

    override suspend fun moveGame(fromPosition: Int, toPosition: Int) {
        val games = _gamesFlow.value.toMutableList()
        if (fromPosition in games.indices) {
            val game = games.removeAt(fromPosition)
            // 支持将项目移动到列表末尾，同时对越界位置做保护
            val targetPosition = toPosition.coerceIn(0, games.size)
            games.add(targetPosition, game)
            updateAndSave(games)
        }
    }

    override suspend fun clearAll() {
        updateAndSave(emptyList())
    }

    override suspend fun saveGameList(games: List<GameItem>) {
        updateAndSave(games)
    }

    private fun updateAndSave(games: List<GameItem>) {
        // 确保所有游戏项都设置了 gameListStorageParent 引用
        games.forEach { it.gameListStorageParent = gameListStorage }
        _gamesFlow.value = games
        gameListStorage.saveGameList(games)
    }
}

/**
 * 游戏列表存储接口
 * 由各平台实现
 */
interface GameListStorage {
    fun loadGameList(): List<GameItem>
    fun saveGameList(games: List<GameItem>)
    fun getGameGlobalStorageDirFull(): String
    /**
     * 创建游戏安装目录
     * @param gameId 游戏名称（用于生成存储ID）
     * @return Pair(目录绝对路径, 存储ID)
     */
    fun createGameStorageRoot(gameId: String): Pair<String, String>
}
