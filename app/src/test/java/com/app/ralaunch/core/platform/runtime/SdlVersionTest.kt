package com.app.ralaunch.core.platform.runtime

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SdlVersionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun game(): File = temporaryFolder.newFolder().resolve("Game.dll").apply {
        writeText("game")
    }

    private fun fna(file: File, sdl3: Boolean = false) {
        file.parentFile.mkdirs()
        file.writeBytes(("MZ\u0000SDL2_FNAPlatform\u0000" +
            if (sdl3) "SDL3_FNAPlatform\u0000" else "").toByteArray())
    }

    @Test
    fun sdl2OnlyFnaUsesSdl2EvenWhenSdl3IsSelected() {
        val game = game()
        fna(game.resolveSibling("Libraries/FNA/1.0.0/FNA.dll"))
        assertEquals(SdlVersion.SDL2, SdlVersion.resolveForGame(SdlVersion.SDL3, game))
    }

    @Test
    fun dualBackendFnaKeepsSelectedVersion() {
        val game = game()
        fna(game.resolveSibling("FNA.dll"), sdl3 = true)
        assertEquals(SdlVersion.SDL3, SdlVersion.resolveForGame(SdlVersion.SDL3, game))
        assertEquals(SdlVersion.SDL2, SdlVersion.resolveForGame(SdlVersion.SDL2, game))
    }

    @Test
    fun missingOrUnrecognizedFnaKeepsSelectedVersion() {
        val game = game()
        assertEquals(SdlVersion.SDL3, SdlVersion.resolveForGame(SdlVersion.SDL3, game))
        game.resolveSibling("FNA.dll").writeText("unrecognized assembly")
        assertEquals(SdlVersion.SDL3, SdlVersion.resolveForGame(SdlVersion.SDL3, game))
    }

    @Test
    fun adjacentFnaTakesPrecedenceOverLibraryCopies() {
        val game = game()
        fna(game.resolveSibling("FNA.dll"), sdl3 = true)
        fna(game.resolveSibling("Libraries/FNA/1.0.0/FNA.dll"))
        assertEquals(SdlVersion.SDL3, SdlVersion.resolveForGame(SdlVersion.SDL3, game))
    }

    @Test
    fun depsFileSelectsReferencedFnaVersion() {
        val game = game()
        fna(game.resolveSibling("Libraries/FNA/1.0.0/FNA.dll"))
        fna(game.resolveSibling("Libraries/FNA/2.0.0/FNA.dll"), sdl3 = true)
        game.resolveSibling("Game.deps.json").writeText("""{"libraries":{"FNA/2.0.0":{}}}""")
        assertEquals(SdlVersion.SDL3, SdlVersion.resolveForGame(SdlVersion.SDL3, game))
        game.resolveSibling("Game.deps.json").writeText("""{"libraries":{"FNA/1.0.0":{}}}""")
        assertEquals(SdlVersion.SDL2, SdlVersion.resolveForGame(SdlVersion.SDL3, game))
    }
}
