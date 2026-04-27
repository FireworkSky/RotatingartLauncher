package com.app.ralaunch.core.logging

import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.logging.contract.Logger
import com.app.ralaunch.core.logging.service.LogExportHelper
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.feature.patch.data.PatchManager
import com.app.ralaunch.feature.patch.data.PatchManagerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class LogExportHelperTest {

    @Before
    fun setUp() {
        AppLog.install(NoOpLogger)
    }

    @After
    fun tearDown() {
        AppLog.reset()
    }

    @Test
    fun managedFilesListAppLogsThenLogcatOldestFirstWithinGroups() = withTempLogDir { dir ->
        val olderAppLog = dir.createLogFile("ralaunch_2026-04-25.log", "oldest", modified = 1_000L)
        val newerLogcat = dir.createLogFile("ralaunch_2026-04-26_logcat.log", "logcat", modified = 2_000L)
        val newestAppLog = dir.createLogFile("ralaunch_2026-04-27.log", "newest app", modified = 3_000L)
        dir.createLogFile("other.log", "ignored", modified = 4_000L)

        assertEquals(listOf(olderAppLog, newestAppLog, newerLogcat), helperFor(dir).getLogFiles())
    }

    @Test
    fun appLogFilesExcludeLogcatLogs() = withTempLogDir { dir ->
        val appLog = dir.createLogFile("ralaunch_2026-04-26.log", "app", modified = 2_000L)
        val newerLogcat = dir.createLogFile("ralaunch_2026-04-27_logcat.log", "logcat", modified = 3_000L)
        val olderAppLog = dir.createLogFile("ralaunch_2026-04-25.log", "old app", modified = 1_000L)

        val appLogFiles = helperFor(dir).getAppLogFiles()

        assertEquals(listOf(olderAppLog, appLog), appLogFiles)
        assertFalse(newerLogcat in appLogFiles)
    }

    @Test
    fun appLogFilesAreListedOldestFirst() = withTempLogDir { dir ->
        val oldestAppLog = dir.createLogFile("ralaunch_2026-04-25.log", "old app", modified = 1_000L)
        dir.createLogFile("ralaunch_2026-04-26.log", "app", modified = 2_000L)
        dir.createLogFile("ralaunch_2026-04-27_logcat.log", "logcat", modified = 3_000L)

        assertEquals(oldestAppLog, helperFor(dir).getAppLogFiles().firstOrNull())
    }

    @Test
    fun combinedExportContentIncludesDiagnosticsThenPrioritizedLogFiles() = withTempLogDir { dir ->
        dir.createLogFile("ralaunch_2026-04-26.log", "middle\n", modified = 2_000L)
        dir.createLogFile("ralaunch_2026-04-25.log", "oldest", modified = 1_000L)
        dir.createLogFile("ralaunch_2026-04-27_logcat.log", "newest", modified = 3_000L)

        val content = helperFor(dir).buildExportContent()

        val repositoryIndex = content.indexOf("=============== Game Repository Information ===============")
        val patchIndex = content.indexOf("=============== Patch Management Information ===============")
        val logcatIndex = content.indexOf("=============== ralaunch_2026-04-27_logcat.log ===============")
        val oldestIndex = content.indexOf("=============== ralaunch_2026-04-25.log ===============")
        val middleIndex = content.indexOf("=============== ralaunch_2026-04-26.log ===============")

        assertTrue(repositoryIndex >= 0)
        assertTrue(patchIndex > repositoryIndex)
        assertTrue(oldestIndex > patchIndex)
        assertTrue(middleIndex > oldestIndex)
        assertTrue(logcatIndex > middleIndex)
        assertTrue(content.contains("newest"))
        assertTrue(content.contains("oldest"))
        assertTrue(content.contains("middle\n"))
    }

    @Test
    fun gameRepositoryInfoIncludesCoreGameFieldsAndRedactsEnvValues() = withTempLogDir { dir ->
        val repository = FakeGameRepository(
            root = Files.createTempDirectory("ralaunch-games"),
            initialGames = listOf(
                GameItem(
                    id = "celeste_abcd1234",
                    displayedName = "Celeste",
                    displayedDescription = "Platformer",
                    gameId = "celeste",
                    gameExePathRelative = "Celeste.exe",
                    iconPathRelative = "icon.png",
                    modLoaderEnabled = false,
                    rendererOverride = "angle",
                    dotNetRuntimeVersionOverride = "8.0.0",
                    gameEnvVars = mapOf(
                        "VISIBLE_KEY" to "secret-value",
                        "UNSET_KEY" to null
                    )
                )
            )
        )

        val info = helperFor(dir, repository = repository).buildGameRepositoryInfo()

        assertTrue(info.contains("Installed Games: 1"))
        assertTrue(info.contains("Id: celeste_abcd1234"))
        assertTrue(info.contains("Game Id: celeste"))
        assertTrue(info.contains("Executable Relative: Celeste.exe"))
        assertTrue(info.contains("Renderer Override: angle"))
        assertTrue(info.contains(".NET Runtime Override: 8.0.0"))
        assertTrue(info.contains("VISIBLE_KEY: set"))
        assertTrue(info.contains("UNSET_KEY: unset"))
        assertFalse(info.contains("secret-value"))
    }

    @Test
    fun patchManagementInfoHandlesUnavailablePatchManager() = withTempLogDir { dir ->
        val info = helperFor(dir).buildPatchManagementInfo()

        assertTrue(info.contains("Patch Management Information"))
        assertTrue(info.contains("Status: Unavailable"))
    }

    @Test
    fun patchManagementInfoIncludesInstalledApplicableEnabledAndDisabledPatchSummaries() = withTempLogDir { dir ->
        val gamesRoot = Files.createTempDirectory("ralaunch-games")
        val game = GameItem(
            id = "celeste_abcd1234",
            displayedName = "Celeste",
            gameId = "celeste",
            gameExePathRelative = "Celeste.exe"
        )
        val repository = FakeGameRepository(gamesRoot, listOf(game))
        val patchStorage = Files.createTempDirectory("ralaunch-patches")
        patchStorage.createPatchDir(
            id = "enabled_patch",
            name = "Enabled Patch",
            targetGames = listOf("celeste"),
            priority = 20
        )
        patchStorage.createPatchDir(
            id = "disabled_patch",
            name = "Disabled Patch",
            targetGames = listOf("celeste"),
            priority = 10
        )
        patchStorage.createPatchDir(
            id = "other_patch",
            name = "Other Patch",
            targetGames = listOf("terraria"),
            priority = 5
        )

        val config = PatchManagerConfig().apply {
            disabledPatches[Paths.get(game.gameExePathFull!!).toAbsolutePath().normalize().toString()] =
                arrayListOf("disabled_patch")
        }
        val patchManager = patchManagerFor(patchStorage, config)

        val info = helperFor(dir, repository = repository, patchManager = patchManager).buildPatchManagementInfo()

        assertTrue(info.contains("Status: Available"))
        assertTrue(info.contains("Installed Patches: 3"))
        assertTrue(info.contains("Id: enabled_patch"))
        assertTrue(info.contains("Id: disabled_patch"))
        assertTrue(info.contains("Id: other_patch"))
        assertTrue(info.contains("Applicable Patches: 2"))
        assertTrue(info.contains("Enabled Applicable Patch Ids: enabled_patch"))
        assertTrue(info.contains("Disabled Applicable Patch Ids: disabled_patch"))
    }

    @Test
    fun emptyLogDirectoryStillReturnsDiagnosticContent() = withTempLogDir { dir ->
        val content = helperFor(dir).buildExportContent()

        assertTrue(content.contains("Game Repository Information"))
        assertTrue(content.contains("Patch Management Information"))
    }

    private fun withTempLogDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("ralaunch-log-export-helper").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun File.createLogFile(name: String, content: String, modified: Long): File {
        return File(this, name).apply {
            writeText(content)
            setLastModified(modified)
        }
    }

    private fun helperFor(
        logsDir: File,
        repository: IGameRepositoryServiceV3? = null,
        patchManager: PatchManager? = null
    ): LogExportHelper =
        LogExportHelper(
            logsDirPathProvider = { logsDir.absolutePath },
            gameRepositoryProvider = { repository },
            patchManagerProvider = { patchManager }
        )

    private class FakeGameRepository(
        private val root: Path,
        initialGames: List<GameItem>
    ) : IGameRepositoryServiceV3 {
        private val backingGames = MutableStateFlow(initialGames.onEach { it.gameRepositoryParent = this })

        override val games: StateFlow<List<GameItem>> = backingGames

        override suspend fun getById(id: String): GameItem? = backingGames.value.find { it.id == id }
        override suspend fun upsert(game: GameItem, index: Int) = Unit
        override suspend fun removeById(id: String) = Unit
        override suspend fun removeAt(index: Int) = Unit
        override suspend fun reorder(from: Int, to: Int) = Unit
        override suspend fun replaceAll(games: List<GameItem>) = Unit
        override suspend fun clear() = Unit
        override fun getGameGlobalStorageDirFull(): String = root.toString()
        override fun createGameStorageRoot(gameId: String): Pair<String, String> = root.resolve(gameId).toString() to gameId
        override fun deleteGameFiles(game: GameItem): Boolean = true
    }

    private fun Path.createPatchDir(
        id: String,
        name: String,
        targetGames: List<String>,
        priority: Int
    ) {
        val patchDir = resolve(id)
        Files.createDirectories(patchDir)
        Files.write(
            patchDir.resolve("patch.json"),
            """
            {
              "id": "$id",
              "name": "$name",
              "description": "Test patch",
              "version": "1.2.3",
              "author": "Tester",
              "targetGames": [${targetGames.joinToString(",") { "\"$it\"" }}],
              "dllFileName": "$id.dll",
              "entryPoint": {
                "typeName": "Test.Entry",
                "methodName": "Start"
              },
              "priority": $priority,
              "dependencies": {
                "libs": ["lib$id.so"]
              }
            }
            """.trimIndent().toByteArray(Charsets.UTF_8)
        )
    }

    private fun patchManagerFor(patchStoragePath: Path, config: PatchManagerConfig): PatchManager {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null)
        val manager = unsafeClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, PatchManager::class.java) as PatchManager

        setPrivateField(manager, "patchStoragePath", patchStoragePath)
        setPrivateField(manager, "configFilePath", patchStoragePath.resolve(PatchManagerConfig.CONFIG_FILE_NAME))
        setPrivateField(manager, "config", config)

        return manager
    }

    private fun setPrivateField(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private object NoOpLogger : Logger {
        override fun v(tag: String, message: String): Int = 0
        override fun v(tag: String, message: String, throwable: Throwable?): Int = 0
        override fun d(tag: String, message: String): Int = 0
        override fun d(tag: String, message: String, throwable: Throwable?): Int = 0
        override fun i(tag: String, message: String): Int = 0
        override fun i(tag: String, message: String, throwable: Throwable?): Int = 0
        override fun w(tag: String, message: String): Int = 0
        override fun w(tag: String, message: String, throwable: Throwable?): Int = 0
        override fun e(tag: String, message: String): Int = 0
        override fun e(tag: String, message: String, throwable: Throwable?): Int = 0
    }
}
