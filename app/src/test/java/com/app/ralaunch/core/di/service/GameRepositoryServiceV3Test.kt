package com.app.ralaunch.core.di.service

import com.app.ralaunch.core.model.GameItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class)
class GameRepositoryServiceV3Test {

    @Test
    fun `deleteGameFiles deletes only the target game directory`() {
        val gamesDir = createTempDirectory("games-root-")
        val targetDir = gamesDir.resolve("game_a").createDirectories()
        val siblingDir = gamesDir.resolve("game_b").createDirectories()

        try {
            targetDir.resolve("content.txt").createFile().writeText("delete me")
            siblingDir.resolve("keep.txt").createFile().writeText("keep me")

            val repository = GameRepositoryServiceV3(gamesDir)

            assertTrue(repository.deleteGameFiles(game("game_a")))
            assertTrue(gamesDir.exists())
            assertTrue(targetDir.notExists())
            assertTrue(siblingDir.exists())
            assertTrue(siblingDir.resolve("keep.txt").exists())
        } finally {
            gamesDir.deleteRecursively()
        }
    }

    @Test
    fun `deleteGameFiles removes symlink without touching linked directory`() {
        val gamesDir = createTempDirectory("games-root-")
        val outsideDir = createTempDirectory("games-outside-")

        try {
            val protectedFile = outsideDir.resolve("protected.txt").createFile().apply {
                writeText("keep")
            }
            val symlink = gamesDir.resolve("game_link")
            createDirectorySymlinkOrSkip(symlink, outsideDir)

            val repository = GameRepositoryServiceV3(gamesDir)

            assertTrue(repository.deleteGameFiles(game("game_link")))
            assertTrue(protectedFile.exists())
            assertTrue(outsideDir.exists())
            assertTrue(symlink.notExists())
        } finally {
            gamesDir.deleteRecursively()
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun `deleteGameFiles refuses blank ids`() {
        val gamesDir = createTempDirectory("games-root-")

        try {
            val repository = GameRepositoryServiceV3(gamesDir)

            assertFalse(repository.deleteGameFiles(game("")))
            assertTrue(gamesDir.exists())
        } finally {
            gamesDir.deleteRecursively()
        }
    }

    private fun game(id: String) = GameItem(
        id = id,
        displayedName = "Test",
        gameId = "test",
        gameExePathRelative = "game.exe"
    )

    private fun createDirectorySymlinkOrSkip(link: Path, target: Path) {
        try {
            Files.createSymbolicLink(link, target)
        } catch (e: UnsupportedOperationException) {
            assumeNoException(e)
        } catch (e: SecurityException) {
            assumeNoException(e)
        } catch (e: Exception) {
            assumeNoException(e)
        }
    }
}
