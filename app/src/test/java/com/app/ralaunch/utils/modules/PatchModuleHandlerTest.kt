package com.app.ralaunch.utils.modules

import android.app.Application
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.utils.GameManager
import com.app.ralaunch.utils.ModuleManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

// install(zip) 经 ArchiveExtractor（android Os / 字符串资源），与 ModuleManagerTest 一致使用 Robolectric
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PatchModuleHandlerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var gamesRoot: Path
    private lateinit var modulesRoot: Path
    private val handler = PatchModuleHandler

    @Before
    fun setUp() {
        gamesRoot = temporaryFolder.newFolder("games").toPath()
        modulesRoot = temporaryFolder.newFolder("modules").toPath()
        GameManager.initialize(gamesRoot)
        ModuleManager.initialize(modulesRoot)
        ModuleManager.registerHandler(handler)
    }

    /** 在 modules 根写入一个补丁模块目录（meta.json 含补丁专属字段）。 */
    private fun writePatchModule(
        id: String,
        targetGames: List<String>,
        priority: Int = 0,
        dllFileName: String = "$id.dll",
        version: String = "1.0.0"
    ): Path = modulesRoot.resolve(id).createDirectories().also { dir ->
        dir.resolve(AppConstants.Files.MODULE_META).writeText(
            """
            {
              "type": "patch",
              "id": "$id",
              "name": "Patch $id",
              "version": "$version",
              "targetGames": [${targetGames.joinToString(",") { "\"$it\"" }}],
              "dllFileName": "$dllFileName",
              "priority": $priority
            }
            """.trimIndent()
        )
        Files.write(dir.resolve(dllFileName), ByteArray(16))
    }

    /** 建一个 celeste 游戏并返回登记项。 */
    private fun createGame(gameId: String = "celeste") = runBlocking {
        val source = temporaryFolder.newFolder("source_${System.nanoTime()}").toPath()
        Files.write(source.resolve("game.exe"), "assembly".toByteArray())
        GameManager.add(source, gameId, "game.exe")
    }

    @Test
    fun `getApplicablePatches matches wildcard and exact game and sorts by priority descending`() = runBlocking {
        writePatchModule("com.example.wildcard", targetGames = listOf("*"), priority = 10)
        writePatchModule("com.example.celeste", targetGames = listOf("celeste"), priority = 300)
        writePatchModule("com.example.terraria", targetGames = listOf("terraria"), priority = 20)
        writePatchModule("com.example.none", targetGames = emptyList())
        ModuleManager.initialize(modulesRoot)

        val patches = handler.getApplicablePatches("celeste")

        assertEquals(
            listOf("com.example.celeste", "com.example.wildcard"),
            patches.map { it.id }
        )
        // 空补丁（type 非 patch 的模块不会进入补丁列表）
        val theme = modulesRoot.resolve("com.example.theme").createDirectories()
        theme.resolve(AppConstants.Files.MODULE_META).writeText(
            """{"type":"theme","id":"com.example.theme"}"""
        )
        ModuleManager.initialize(modulesRoot)
        assertTrue(handler.patches().none { it.id == "com.example.theme" })
    }

    @Test
    fun `getApplicableAndEnabledPatches requires game match, enabledModules and module enabled`() = runBlocking {
        val game = createGame()
        writePatchModule("com.example.enabled", targetGames = listOf("celeste"), priority = 5)
        writePatchModule("com.example.off", targetGames = listOf("celeste"), priority = 4)
        ModuleManager.initialize(modulesRoot)
        val asmPath = Paths.get(game.gameExePathFull!!)

        // 默认 enabledModules 为空 → 无补丁生效
        assertTrue(handler.getApplicableAndEnabledPatches("celeste", asmPath).isEmpty())

        // 游戏启用该补丁 → 在列
        GameManager.configure(game.id) { enabledModules = listOf("com.example.enabled") }
        assertEquals(
            listOf("com.example.enabled"),
            handler.getApplicableAndEnabledPatches("celeste", asmPath).map { it.id }
        )

        // 模块总开关关闭 → 被排除
        ModuleManager.setEnabled("com.example.enabled", false)
        assertTrue(handler.getApplicableAndEnabledPatches("celeste", asmPath).isEmpty())
        ModuleManager.setEnabled("com.example.enabled", true)
        assertEquals(1, handler.getApplicableAndEnabledPatches("celeste", asmPath).size)

        // (gameId, asmPath) 匹配不到已登记游戏 → 空
        assertTrue(handler.getApplicableAndEnabledPatches("terraria", asmPath).isEmpty())
        assertTrue(handler.getApplicableAndEnabledPatches("celeste", Paths.get("/nowhere/game.exe")).isEmpty())
    }

    @Test
    fun `enabledModules persists across GameManager restart`() = runBlocking {
        val game = createGame()
        GameManager.configure(game.id) { enabledModules = listOf("com.example.enabled", "com.example.off") }

        GameManager.initialize(gamesRoot)

        assertEquals(
            listOf("com.example.enabled", "com.example.off"),
            GameManager.get(game.id)?.enabledModules
        )
    }

    @Test
    fun `stale enabledModules ids of removed modules are harmless`() = runBlocking {
        val game = createGame()
        writePatchModule("com.example.gone", targetGames = listOf("*"))
        ModuleManager.initialize(modulesRoot)
        GameManager.configure(game.id) { enabledModules = listOf("com.example.gone") }

        ModuleManager.remove("com.example.gone")

        val patches = handler.getApplicableAndEnabledPatches("celeste", Paths.get(game.gameExePathFull!!))
        assertTrue(patches.isEmpty())
        assertTrue(handler.patches().none { it.id == "com.example.gone" })
        assertTrue(handler.getApplicablePatches("celeste").none { it.id == "com.example.gone" })
    }

    @Test
    fun `ModuleManager installed meta zip patch is listed and old format is rejected`() = runBlocking {
        val zip = temporaryFolder.newFolder("zips").toPath().resolve("patch.zip")
        writeZip(
            zip,
            AppConstants.Files.MODULE_META to
                """{"type":"patch","id":"com.example.imported","version":"1.0.0","targetGames":["celeste"],"dllFileName":"P.dll","priority":1}""",
            "P.dll" to "binary"
        )

        val module = ModuleManager.install(zip, force = true)

        assertEquals("com.example.imported", module.id)
        assertTrue(module.enabled)
        assertEquals(
            listOf("com.example.imported"),
            handler.patches().map { it.id }
        )
        assertEquals(
            ModuleManager.directory("com.example.imported").resolve("P.dll").toRealPath().normalize(),
            handler.patches().single().getEntryAssemblyAbsolutePath().toRealPath().normalize()
        )

        // 旧格式 zip（根层 patch.json，无 meta.json）被拒绝
        val legacyZip = zip.parent.resolve("legacy.zip")
        writeZip(
            legacyZip,
            "patch.json" to """{"id":"com.example.legacy","targetGames":["celeste"]}""",
            "L.dll" to "binary"
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.install(legacyZip) }
        }
        assertTrue(handler.patches().none { it.id == "com.example.legacy" })
    }

    @Test
    fun `constructPatchesEnvVar deduplicates ids and joins distinct paths with colon`() = runBlocking {
        writePatchModule("com.example.first", targetGames = listOf("*"), dllFileName = "First.dll")
        writePatchModule("com.example.second", targetGames = listOf("*"), dllFileName = "Second.dll")
        ModuleManager.initialize(modulesRoot)
        val patches = handler.patches()
        val first = patches.single { it.id == "com.example.first" }
        val second = patches.single { it.id == "com.example.second" }

        val envVar = PatchModuleHandler.constructPatchesEnvVar(listOf(first, second, first))

        val expected = listOf("com.example.first" to "First.dll", "com.example.second" to "Second.dll")
            .joinToString(":") { (id, dll) -> ModuleManager.directory(id).resolve(dll).toString() }
        assertEquals(expected, envVar)
        assertEquals(2, envVar.split(":").count { it.isNotEmpty() })
    }

    @Test
    fun `deployBootstrapAssembly copies apk asset to filesDir bootstrap dir`() {
        val context = RuntimeEnvironment.getApplication()
        val expected = context.assets.open("bootstrap/RAL.Bootstrap.CoreCLR.dll").use { it.readBytes() }

        assertTrue(PatchModuleHandler.deployBootstrapAssembly(context))

        val target = PatchModuleHandler.bootstrapAssemblyFile(context)
        assertTrue(target.toFile().isFile)
        assertArrayEquals(expected, Files.readAllBytes(target))

        // 幂等：再次部署（覆盖写）仍成功
        assertTrue(PatchModuleHandler.deployBootstrapAssembly(context))
    }

    /** 生成 zip：条目直接位于压缩包根（同模块包布局契约）。 */
    private fun writeZip(zipPath: Path, vararg entries: Pair<String, String>) {
        ZipOutputStream(zipPath.outputStream().buffered()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
