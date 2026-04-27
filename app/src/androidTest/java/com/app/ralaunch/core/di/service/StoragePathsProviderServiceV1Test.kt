package com.app.ralaunch.core.di.service

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.platform.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

@OptIn(ExperimentalPathApi::class)
@RunWith(AndroidJUnit4::class)
class StoragePathsProviderServiceV1Test {

    @Test
    fun fullPathMethodsReturnAbsolutePathsWithoutCreatingGamesDirectory() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val filesDir = createTempDirectory("storage-paths-files-")
        val externalFilesDir = createTempDirectory("storage-paths-external-")

        try {
            val provider = StoragePathsProviderServiceV1(
                FakeContext(
                    base = appContext,
                    filesDir = filesDir,
                    externalFilesDir = externalFilesDir
                )
            )

            val gamesDir = externalFilesDir.resolve(AppConstants.Dirs.GAMES)
            val logsDir = externalFilesDir.resolve(AppConstants.Dirs.LOGS)

            assertEquals(gamesDir.absolutePathString(), provider.gamesDirPathFull())
            assertFalse(gamesDir.exists())
            assertEquals(logsDir.absolutePathString(), provider.logsDirPathFull())
            assertFalse(logsDir.exists())
            assertEquals(
                filesDir.resolve(AppConstants.Files.SETTINGS).absolutePathString(),
                provider.settingsFilePathFull()
            )
            assertEquals(filesDir.absolutePathString(), provider.filesDirPathFull())
            assertEquals(
                filesDir.resolve(AppConstants.Dirs.RUNTIMES).absolutePathString(),
                provider.runtimesDirPathFull()
            )
            assertEquals(
                filesDir.resolve(AppConstants.Dirs.LEGACY_DOTNET).absolutePathString(),
                provider.legacyDotnetDirPathFull()
            )
        } finally {
            FileUtils.deleteDirectoryRecursively(filesDir)
            FileUtils.deleteDirectoryRecursively(externalFilesDir)
        }
    }

    private class FakeContext(
        base: Context,
        private val filesDir: Path,
        private val externalFilesDir: Path
    ) : ContextWrapper(base) {
        override fun getFilesDir(): File = filesDir.toFile()

        override fun getExternalFilesDir(type: String?): File = externalFilesDir.toFile()
    }
}
