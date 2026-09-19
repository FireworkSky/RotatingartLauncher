package com.app.ralaunch.utils

import com.app.ralaunch.core.model.GameItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class GameManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `external import persists independent settings without moving source`() = runBlocking {
        val root = temporaryFolder.newFolder("games").toPath()
        val source = temporaryFolder.newFolder("source").toPath()
        source.resolve("game.exe").writeText("assembly")
        GameManager.initialize(root)

        val game = GameManager.add(source, "test", "game.exe", "Original")
        GameManager.configure(game.id) {
            displayedName = "Renamed"
            rendererOverride = "vulkan"
        }
        GameManager.initialize(root)
        assertEquals("Renamed", GameManager.get(game.id)?.displayedName)
        assertEquals("vulkan", GameManager.get(game.id)?.rendererOverride)
        assertEquals("assembly", GameManager.directory(game.id).resolve("game.exe").readText())
        assertEquals("assembly", source.resolve("game.exe").readText())

        GameManager.remove(game.id)
        assertFalse(root.resolve(game.id).exists())
        assertEquals("assembly", source.resolve("game.exe").readText())
        GameManager.initialize(root)
        assertEquals(null, GameManager.get(game.id))
    }

    @Test
    fun `launch snapshots follow config and mutable copies do not change saved settings`() = runBlocking {
        val root = temporaryFolder.newFolder("games").toPath()
        val source = root.resolve("test").createDirectories()
        source.resolve("game.exe").writeText("assembly")
        GameManager.initialize(root)
        val game = GameManager.add(source, "test", "game.exe")
        GameManager.configure(game.id) {
            rendererOverride = "opengl"
            dotNetRuntimeVersionOverride = "8.0"
            gameEnvVars = mapOf("SAVED" to "yes")
        }
        GameManager.get(game.id)!!.displayedName = "Accidental mutation"
        val launch = GameManager.launch(game.id)

        // Launch 快照默认取自配置
        assertEquals("opengl", launch.renderer)
        assertEquals("8.0", launch.runtimeVersion)
        assertEquals(mapOf("SAVED" to "yes"), launch.environment)

        // 本次覆盖只作用于快照本身
        launch.apply {
            renderer = null
            runtimeVersion = "9.0"
            environment = mapOf("SAVED" to null)
            arguments = listOf("--test")
        }

        GameManager.initialize(root)
        assertEquals("test", GameManager.get(game.id)?.displayedName)
        assertEquals("opengl", GameManager.get(game.id)?.rendererOverride)
        assertEquals("8.0", GameManager.get(game.id)?.dotNetRuntimeVersionOverride)
        assertEquals(mapOf("SAVED" to "yes"), GameManager.get(game.id)?.gameEnvVars)
    }

    @Test
    fun `existing JSON loads with full paths and preserves configuration and order after editing`() = runBlocking {
        val root = temporaryFolder.newFolder("games").toPath()
        val celeste = root.resolve("celeste_abcd1234").createDirectories()
        celeste.resolve("Celeste.exe").writeText("assembly")
        celeste.resolve("icon.png").writeText("icon")
        celeste.resolve("game_info.json").writeText(
            """
            {
              "id": "celeste_abcd1234",
              "displayedName": "Celeste",
              "displayedDescription": "Platformer",
              "gameId": "celeste",
              "gameExePathRelative": "Celeste.exe",
              "iconPathRelative": "icon.png",
              "modLoaderEnabled": false,
              "rendererOverride": "opengl",
              "dotNetRuntimeVersionOverride": "8.0",
              "gameEnvVars": {"SAVED": "yes", "UNSET": null}
            }
            """.trimIndent()
        )
        val terraria = root.resolve("terraria_1234abcd").createDirectories()
        terraria.resolve("Terraria.exe").writeText("other assembly")
        terraria.resolve("game_info.json").writeText(
            """
            {
              "id": "terraria_1234abcd",
              "displayedName": "Terraria",
              "gameId": "terraria",
              "gameExePathRelative": "Terraria.exe"
            }
            """.trimIndent()
        )
        root.resolve("game_list.json").writeText(
            """{"games":["celeste_abcd1234","terraria_1234abcd"]}"""
        )

        GameManager.initialize(root)
        assertEquals(listOf("celeste_abcd1234", "terraria_1234abcd"), GameManager.currentGames.map { it.id })
        val loaded = GameManager.get("celeste_abcd1234")!!
        assertEquals(celeste.toString(), loaded.storageRootPathFull)
        assertEquals(celeste.resolve("Celeste.exe").toString(), loaded.gameExePathFull)
        assertEquals(celeste.resolve("icon.png").toString(), loaded.iconPathFull)
        assertEquals("assembly", java.nio.file.Path.of(loaded.gameExePathFull!!).readText())

        GameManager.currentGames[1].displayedName = "Unsaved snapshot mutation"
        GameManager.games.first()[1].gameExePathRelative = "Unsaved.exe"
        loaded.displayedName = "Edited Celeste"
        loaded.rendererOverride = "vulkan"
        val saved = GameManager.save(loaded)
        loaded.displayedName = "Unsaved input mutation"
        saved.rendererOverride = "Unsaved returned snapshot mutation"
        GameManager.reorder(0, 1)

        val persistedOrder = Json.parseToJsonElement(root.resolve("game_list.json").readText())
            .jsonObject.getValue("games").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("terraria_1234abcd", "celeste_abcd1234"), persistedOrder)
        GameManager.initialize(root)
        assertEquals(listOf("terraria_1234abcd", "celeste_abcd1234"), GameManager.currentGames.map { it.id })
        assertEquals("Terraria", GameManager.get("terraria_1234abcd")?.displayedName)
        assertEquals("Terraria.exe", GameManager.get("terraria_1234abcd")?.gameExePathRelative)
        val edited = GameManager.get(loaded.id)!!
        assertEquals("Edited Celeste", edited.displayedName)
        assertEquals("Platformer", edited.displayedDescription)
        assertEquals("celeste", edited.gameId)
        assertFalse(edited.modLoaderEnabled)
        assertEquals("vulkan", edited.rendererOverride)
        assertEquals("8.0", edited.dotNetRuntimeVersionOverride)
        assertEquals(mapOf("SAVED" to "yes", "UNSET" to null), edited.gameEnvVars)
        assertEquals(celeste.toString(), edited.storageRootPathFull)
        assertEquals(celeste.resolve("Celeste.exe").toString(), edited.gameExePathFull)
        assertEquals(celeste.resolve("icon.png").toString(), edited.iconPathFull)
    }

    @Test
    fun `failed configuration write does not publish unsaved settings`() = runBlocking {
        val root = temporaryFolder.newFolder("games").toPath()
        val source = root.resolve("test").createDirectories()
        source.resolve("game.exe").writeText("assembly")
        GameManager.initialize(root)
        val game = GameManager.add(source, "test", "game.exe", "Saved")
        source.resolve("game_info.json").deleteExisting()
        source.resolve("game_info.json").createDirectories()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { GameManager.configure(game.id) { displayedName = "Unsaved" } }
        }
        assertEquals("Saved", GameManager.get(game.id)?.displayedName)
    }

    @Test
    fun `escaped executable is rejected and deletion preserves neighboring games`() = runBlocking {
        val root = temporaryFolder.newFolder("games").toPath()
        val source = root.resolve("test").createDirectories()
        source.resolve("game.exe").writeText("assembly")
        val sibling = root.resolve("neighbor").createDirectories()
        sibling.resolve("game.exe").writeText("keep")
        GameManager.initialize(root)
        val game = GameManager.add(source, "test", "game.exe")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { GameManager.configure(game.id) { gameExePathRelative = "../neighbor/game.exe" } }
        }
        assertThrows(IllegalArgumentException::class.java) { GameManager.directory("..") }
        assertEquals("game.exe", GameManager.get(game.id)?.gameExePathRelative)
        GameManager.remove(game.id)
        assertFalse(source.exists())
        assertEquals("keep", sibling.resolve("game.exe").readText())
    }

    @Test
    fun `deletion removes replaced directory symlink without touching its target`() = runBlocking {
        val root = temporaryFolder.newFolder("games").toPath()
        val source = root.resolve("test").createDirectories()
        source.resolve("game.exe").writeText("assembly")
        val outside = temporaryFolder.newFolder("outside").toPath()
        val protectedFile = outside.resolve("protected.txt")
        protectedFile.writeText("keep")
        GameManager.initialize(root)
        val game = GameManager.add(source, "test", "game.exe")
        source.resolve("game.exe").deleteExisting()
        source.resolve("game_info.json").deleteExisting()
        source.deleteExisting()
        try {
            Files.createSymbolicLink(source, outside)
        } catch (e: UnsupportedOperationException) {
            assumeNoException(e)
        } catch (e: SecurityException) {
            assumeNoException(e)
        } catch (e: IOException) {
            assumeNoException(e)
        }

        GameManager.remove(game.id)

        assertFalse(Files.exists(source, NOFOLLOW_LINKS))
        assertEquals("keep", protectedFile.readText())
        assertTrue(outside.exists())
        assertTrue(root.exists())
        GameManager.initialize(root)
        assertEquals(null, GameManager.get(game.id))
    }

    @Test
    fun `blank and escaping ids cannot access or delete unmanaged files`() = runBlocking {
        val root = temporaryFolder.newFolder("games").toPath()
        root.resolve("keep.txt").writeText("keep root")
        val outside = temporaryFolder.newFolder("outside").toPath()
        outside.resolve("keep.txt").writeText("keep outside")
        GameManager.initialize(root)

        for (id in listOf("", " ", "..", "../outside", outside.toString())) {
            assertThrows(IllegalArgumentException::class.java) { GameManager.directory(id) }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { GameManager.remove(id) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    GameManager.save(
                        GameItem(
                            id = id,
                            displayedName = "Invalid",
                            gameId = "test",
                            gameExePathRelative = "game.exe"
                        )
                    )
                }
            }
        }

        assertEquals("keep root", root.resolve("keep.txt").readText())
        assertEquals("keep outside", outside.resolve("keep.txt").readText())
        GameManager.initialize(root)
        assertTrue(GameManager.currentGames.isEmpty())
    }
}
