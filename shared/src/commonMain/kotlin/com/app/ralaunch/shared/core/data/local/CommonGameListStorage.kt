package com.app.ralaunch.shared.core.data.local

import com.app.ralaunch.shared.core.platform.AppConstants
import com.app.ralaunch.shared.core.data.repository.GameListStorage
import com.app.ralaunch.shared.core.model.domain.GameItem
import com.app.ralaunch.shared.core.model.domain.GameList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File  // Thay kotlin.io.path.*
import kotlin.random.Random

class CommonGameListStorage(
    private val pathsProvider: StoragePathsProvider
) : GameListStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun loadGameList(): List<GameItem> {
        return try {
            if (!gameListFile.exists()) return emptyList()

            val gameList = json.decodeFromString<GameList>(gameListFile.readText())
            gameList.games.mapNotNull { storageRootPathRelative ->
                loadGameInfo(storageRootPathRelative)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun saveGameList(games: List<GameItem>) {
        try {
            gamesDirFile.mkdirs()

            val gameList = GameList(games = games.map { it.id })
            gameListFile.writeText(json.encodeToString(gameList))

            games.forEach { saveGameInfo(it) }
            cleanupDeletedGameStorageRoots(games.map { it.id })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getGameGlobalStorageDirFull(): String = gamesDirFile.absolutePath

    override fun createGameStorageRoot(gameId: String): Pair<String, String> {
        val baseName = gameId.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
        val storageRootPathRelative = "${baseName}_${randomHex(8)}"
        val storageRootFile = File(gamesDirFile, storageRootPathRelative)
        storageRootFile.mkdirs()
        return Pair(storageRootFile.absolutePath, storageRootPathRelative)
    }

    private fun loadGameInfo(storageRootPathRelative: String): GameItem? {
        return try {
            val gameInfoFile = File(
                File(gamesDirFile, storageRootPathRelative),
                AppConstants.Files.GAME_INFO
            )
            if (!gameInfoFile.exists()) return null

            json.decodeFromString<GameItem>(gameInfoFile.readText()).also {
                it.gameListStorageParent = this
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveGameInfo(game: GameItem) {
        try {
            val storageRootFile = File(gamesDirFile, game.id)
            val gameInfoFile = File(storageRootFile, AppConstants.Files.GAME_INFO)

            storageRootFile.mkdirs()
            gameInfoFile.writeText(json.encodeToString(game))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanupDeletedGameStorageRoots(keepRoots: List<String>) {
        if (!gamesDirFile.exists()) return

        val keepSet = keepRoots.toSet()
        gamesDirFile.listFiles()
            ?.filter { it.isDirectory }
            ?.filter { it.name !in keepSet }
            ?.forEach { dir ->
                runCatching { dir.deleteRecursively() }
            }
    }

    private fun randomHex(length: Int): String {
        val chars = "0123456789abcdef"
        return buildString(length) {
            repeat(length) {
                append(chars[Random.nextInt(chars.length)])
            }
        }
    }

    private val gamesDirFile
        get() = File(pathsProvider.gamesDirPathFull())

    private val gameListFile
        get() = File(gamesDirFile, AppConstants.Files.GAME_LIST)
}
