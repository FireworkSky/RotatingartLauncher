package com.app.ralaunch.core.extractor

import android.app.Application
import android.content.Context
import com.app.ralaunch.strings.StringsResource
import com.app.ralaunch.strings.generated.En
import com.app.ralaunch.strings.generated.LocaleStrings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.div

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GogShFileExtractorTest {
    private lateinit var tempDir: Path
    private lateinit var previousStrings: LocaleStrings

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("gog-sh-test")
        val context = mockk<Context>()
        every { context.externalCacheDir } returns tempDir.toFile()
        mockkStatic(KoinJavaComponent::class)
        every { KoinJavaComponent.get<Context>(Context::class.java) } returns context
        previousStrings = StringsResource.Strings
        StringsResource.Strings = En
    }

    @After
    fun tearDown() {
        StringsResource.Strings = previousStrings
        unmockkAll()
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun parsesTerrariaMakeselfSample() {
        val sample = Path.of("testcases/gogsh/terraria_v1_4_5_6_89299.sh").toFile()
        assumeTrue("Local GOG sample is not available", sample.isFile)

        val gameData = GogShFileExtractor.GameDataZipFile.parseFromGogShFile(sample.toPath())

        val parsed = requireNotNull(gameData)
        assertTrue(!parsed.id.isNullOrBlank())
        assertTrue(!parsed.version.isNullOrBlank())
        assertTrue(!parsed.build.isNullOrBlank())
        assertTrue(!parsed.locale.isNullOrBlank())
    }

    @Test
    fun builderExtractsGameDataAndReturnsTypedResult() {
        val gameData = tempDir / "game_data.zip"
        ZipOutputStream(Files.newOutputStream(gameData)).use { zip ->
            zip.putNextEntry(ZipEntry("data/noarch/gameinfo"))
            zip.write("test-game\n1.0\nbuild\nen".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("data/noarch/game/game.txt"))
            zip.write("game".toByteArray())
            zip.closeEntry()
        }
        val gogSh = tempDir / "game.sh"
        Files.newOutputStream(gogSh).use { output ->
            output.write("SKIP=2\nSIZE=0\n".toByteArray())
            output.write(Files.readAllBytes(gameData))
        }

        val events = mutableListOf<GogShFileExtractor.Event>()
        val result = GogShFileExtractor.builder()
            .id("gog-install")
            .sourcePath(gogSh)
            .destinationPath(tempDir / "output")
            .callback { events += it }
            .build()
            .extract()

        assertTrue(result is GogShFileExtractor.Result.Success)
        result as GogShFileExtractor.Result.Success
        assertEquals("test-game", result.gameDataZipFile.id)
        assertEquals("game", Files.readString(result.gamePath / "game.txt"))
        assertEquals(
            listOf(
                "Extracting installer script...",
                "Extracting MojoSetup archive...",
                "Extracting game data...",
                "Parsing game data...",
                "Decompressing game data...",
                "Extracting: data/noarch/game/game.txt",
                "Extraction complete",
                "Game data extraction complete"
            ),
            events.filterIsInstance<GogShFileExtractor.Event.Progress>().map { it.message }
        )
    }

    @Test
    fun emitsLocalizedFailureForInvalidScript() {
        val events = mutableListOf<GogShFileExtractor.Event>()

        val result = GogShFileExtractor.builder()
            .sourcePath(tempDir / "invalid.sh")
            .destinationPath(tempDir / "output")
            .callback { events += it }
            .build()
            .extract()

        assertEquals(
            "Failed to extract GOG .sh file",
            (result as GogShFileExtractor.Result.Failure).message
        )
        assertEquals(
            "Failed to extract GOG .sh file",
            (events.last() as GogShFileExtractor.Event.Error).message
        )
    }
}
