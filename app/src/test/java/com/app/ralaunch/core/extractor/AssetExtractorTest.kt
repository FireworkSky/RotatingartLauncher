package com.app.ralaunch.core.extractor

import android.app.Application
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AssetExtractorTest {
    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("asset-extractor-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun copiesBundledAssetToFile() {
        val target = tempDir.resolve("runtime.data")
        val expected = RuntimeEnvironment.getApplication().assets
            .open("dotnet.tar.xz")
            .use { it.readBytes() }

        AssetExtractor.copyAssetToFile(
            RuntimeEnvironment.getApplication(),
            "dotnet.tar.xz",
            target
        )

        assertArrayEquals(expected, Files.readAllBytes(target))
    }
}
