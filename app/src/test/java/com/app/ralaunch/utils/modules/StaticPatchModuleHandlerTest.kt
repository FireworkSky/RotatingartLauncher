package com.app.ralaunch.utils.modules

import android.app.Application
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.feature.installer.BaseInstallPlugin
import com.app.ralaunch.feature.installer.GameDefinition
import com.app.ralaunch.feature.installer.GameDetectResult
import com.app.ralaunch.feature.installer.GameFile
import com.app.ralaunch.feature.installer.GameInstallPlugin
import com.app.ralaunch.feature.installer.ModLoaderDetectResult
import com.app.ralaunch.utils.GameManager
import com.app.ralaunch.utils.ModuleManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

// install(zip) 与安装收尾接线经 ArchiveExtractor / Strings 资源，与 PatchModuleHandlerTest 一致使用 Robolectric
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class StaticPatchModuleHandlerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var gamesRoot: Path
    private lateinit var modulesRoot: Path
    private val handler = StaticPatchModuleHandler

    @Before
    fun setUp() {
        gamesRoot = temporaryFolder.newFolder("games").toPath()
        modulesRoot = temporaryFolder.newFolder("modules").toPath()
        GameManager.initialize(gamesRoot)
        ModuleManager.initialize(modulesRoot)
        ModuleManager.registerHandler(handler)
    }

    /** 在 modules 根写入一个静态补丁模块目录（meta.json 含专属字段）及其全部 source 文件。 */
    private fun writeStaticModule(
        id: String,
        targetGames: List<String>,
        priority: Int = 0,
        files: List<Pair<String, String>>
    ): Path = writeStaticModuleMeta(id, targetGames, priority, files).also { dir ->
        files.forEach { (source, _) -> writeSource(dir, source) }
    }

    /** 只写 meta.json（source 文件由调用方决定是否写入）。 */
    private fun writeStaticModuleMeta(
        id: String,
        targetGames: List<String>,
        priority: Int = 0,
        files: List<Pair<String, String>>
    ): Path = modulesRoot.resolve(id).createDirectories().also { dir ->
        fun jsonEscape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
        dir.resolve(AppConstants.Files.MODULE_META).writeText(
            """
            {
              "type": "static_patch",
              "id": "$id",
              "name": "Static $id",
              "version": "1.0.0",
              "targetGames": [${targetGames.joinToString(",") { "\"$it\"" }}],
              "priority": $priority,
              "files": [${files.joinToString(",") { (s, t) -> """{"source":"${jsonEscape(s)}","target":"${jsonEscape(t)}"}""" }}]
            }
            """.trimIndent()
        )
    }

    /** 在 modules 根写入一个补丁模块目录（meta.json 声明 type "patch"）。 */
    private fun writePatchModuleMeta(id: String, targetGames: List<String>): Path =
        modulesRoot.resolve(id).createDirectories().also { dir ->
            dir.resolve(AppConstants.Files.MODULE_META).writeText(
                """
                {
                  "type": "patch",
                  "id": "$id",
                  "name": "Patch $id",
                  "version": "1.0.0",
                  "targetGames": [${targetGames.joinToString(",") { "\"$it\"" }}],
                  "dllFileName": "$id.dll"
                }
                """.trimIndent()
            )
        }

    /** source 文件内容的确定值（覆盖后读回断言用）。 */
    private fun sourceContent(moduleId: String, source: String) = "content of $moduleId/$source"

    private fun writeSource(dir: Path, source: String) {
        val sourcePath = dir.resolve(source)
        sourcePath.parent?.createDirectories()
        sourcePath.writeText(sourceContent(dir.fileName.toString(), source))
    }

    /** 建一个空游戏目录（applyToGameDirectory 的目标；每次调用分配独立目录）。 */
    private fun newGameDir(): Path =
        temporaryFolder.newFolder("gameDir_${System.nanoTime()}").toPath()

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

    @Test
    fun `getApplicableStaticPatches matches wildcard and exact game and sorts by priority descending`() = runBlocking {
        writeStaticModule("com.example.wildcard", listOf("*"), priority = 10, files = listOf("a.dll" to "a.dll"))
        writeStaticModule("com.example.celeste", listOf("celeste"), priority = 300, files = listOf("b.dll" to "b.dll"))
        writeStaticModule("com.example.terraria", listOf("terraria"), priority = 20, files = listOf("c.dll" to "c.dll"))
        writeStaticModule("com.example.none", emptyList(), files = listOf("d.dll" to "d.dll"))
        ModuleManager.initialize(modulesRoot)

        assertEquals(
            listOf("com.example.celeste", "com.example.wildcard"),
            handler.getApplicableStaticPatches("celeste").map { it.id }
        )

        // type 非 static_patch 的模块不会进入静态补丁列表
        val theme = modulesRoot.resolve("com.example.theme").createDirectories()
        theme.resolve(AppConstants.Files.MODULE_META).writeText(
            """{"type":"theme","id":"com.example.theme"}"""
        )
        ModuleManager.initialize(modulesRoot)
        assertTrue(handler.staticPatches().none { it.id == "com.example.theme" })
    }

    @Test
    fun `getApplicableAndEnabledStaticPatches requires targetGames match and module enabled`() = runBlocking {
        writeStaticModule("com.example.on", listOf("celeste"), files = listOf("a.dll" to "a.dll"))
        writeStaticModule("com.example.off", listOf("celeste"), files = listOf("b.dll" to "b.dll"))
        ModuleManager.initialize(modulesRoot)

        ModuleManager.setEnabled("com.example.off", false)

        assertEquals(
            listOf("com.example.on"),
            handler.getApplicableAndEnabledStaticPatches("celeste").map { it.id }
        )
        assertTrue(handler.getApplicableAndEnabledStaticPatches("terraria").isEmpty())
    }

    @Test
    fun `applyToGameDirectory fixed entry overwrites existing file and creates missing target with parents`() = runBlocking {
        val gameDir = newGameDir()
        gameDir.resolve("MonoGame.Framework.dll").writeText("old framework")
        writeStaticModule(
            "com.example.fix", listOf("celeste"),
            files = listOf(
                "fw.dll" to "MonoGame.Framework.dll",
                "new.cfg" to "Content/Deep/new.cfg"
            )
        )
        ModuleManager.initialize(modulesRoot)

        val count = handler.applyToGameDirectory(handler.getApplicableStaticPatches("celeste"), gameDir)

        assertEquals(2, count)
        // 覆盖已存在文件：读回与 source 一致
        assertEquals(
            sourceContent("com.example.fix", "fw.dll"),
            gameDir.resolve("MonoGame.Framework.dll").readText()
        )
        // 创建不存在的 target（含多级父目录）
        assertEquals(
            sourceContent("com.example.fix", "new.cfg"),
            gameDir.resolve("Content/Deep/new.cfg").readText()
        )
    }

    @Test
    fun `glob doublestar name replaces same-name files at any depth only`() = runBlocking {
        val gameDir = newGameDir()
        gameDir.resolve("lib.dll").writeText("old root")
        gameDir.resolve("Sub").createDirectories()
        gameDir.resolve("Sub/lib.dll").writeText("old sub")
        gameDir.resolve("Deep/Nest").createDirectories()
        gameDir.resolve("Deep/Nest/lib.dll").writeText("old deep")
        gameDir.resolve("other.dll").writeText("keep me")
        writeStaticModule("com.example.lib", listOf("celeste"), files = listOf("lib.dll" to "**/lib.dll"))
        ModuleManager.initialize(modulesRoot)

        val count = handler.applyToGameDirectory(handler.getApplicableStaticPatches("celeste"), gameDir)

        assertEquals(3, count)
        val expected = sourceContent("com.example.lib", "lib.dll")
        assertEquals(expected, gameDir.resolve("lib.dll").readText())
        assertEquals(expected, gameDir.resolve("Sub/lib.dll").readText())
        assertEquals(expected, gameDir.resolve("Deep/Nest/lib.dll").readText())
        assertEquals("keep me", gameDir.resolve("other.dll").readText())

        // 无匹配文件：不改动、不抛异常、不创建文件
        val emptyDir = newGameDir()
        assertEquals(0, handler.applyToGameDirectory(handler.getApplicableStaticPatches("celeste"), emptyDir))
        val names = Files.list(emptyDir).use { stream -> stream.map { it.fileName.toString() }.toList() }
        assertTrue(names.isEmpty())
    }

    @Test
    fun `segment wildcard and directory glob match root, nested and directory-only files`() = runBlocking {
        val gameDir = newGameDir()
        gameDir.resolve("a.pdb").writeText("old a")
        gameDir.resolve("Sub").createDirectories()
        gameDir.resolve("Sub/b.pdb").writeText("old b")
        gameDir.resolve("Deep/Nest").createDirectories()
        gameDir.resolve("Deep/Nest/c.pdb").writeText("old c")
        gameDir.resolve("Content/Sub").createDirectories()
        gameDir.resolve("Content/x.cfg").writeText("old x")
        gameDir.resolve("Content/Sub/y.cfg").writeText("old y")
        gameDir.resolve("root.cfg").writeText("keep root")
        writeStaticModule(
            "com.example.mix", listOf("celeste"),
            files = listOf(
                "pdb/root.pdb" to "**/*.pdb",
                "c.cfg" to "Content/**"
            )
        )
        ModuleManager.initialize(modulesRoot)

        val count = handler.applyToGameDirectory(handler.getApplicableStaticPatches("celeste"), gameDir)

        assertEquals(5, count)
        val pdbContent = sourceContent("com.example.mix", "pdb/root.pdb")
        assertEquals(pdbContent, gameDir.resolve("a.pdb").readText())
        assertEquals(pdbContent, gameDir.resolve("Sub/b.pdb").readText())
        assertEquals(pdbContent, gameDir.resolve("Deep/Nest/c.pdb").readText())
        val cfgContent = sourceContent("com.example.mix", "c.cfg")
        assertEquals(cfgContent, gameDir.resolve("Content/x.cfg").readText())
        assertEquals(cfgContent, gameDir.resolve("Content/Sub/y.cfg").readText())
        assertEquals("keep root", gameDir.resolve("root.cfg").readText())
    }

    @Test
    fun `directory glob does not touch a plain file named like the directory`() = runBlocking {
        val gameDir = newGameDir()
        gameDir.resolve("Content").writeText("i am a file named Content")
        writeStaticModule("com.example.dir", listOf("celeste"), files = listOf("x.cfg" to "Content/**"))
        ModuleManager.initialize(modulesRoot)

        val count = handler.applyToGameDirectory(handler.getApplicableStaticPatches("celeste"), gameDir)

        assertEquals(0, count)
        assertEquals("i am a file named Content", gameDir.resolve("Content").readText())
    }

    @Test
    fun `invalid targets are skipped while other entries apply`() = runBlocking {
        val gameDir = newGameDir()
        gameDir.resolve("ok.dll").writeText("old")
        writeStaticModule(
            "com.example.invalid", listOf("celeste"),
            files = listOf(
                "a.dll" to "   ",
                "b.dll" to "/abs/a.dll",
                "c.dll" to "Content\\x.cfg",
                "d.dll" to "ok.dll"
            )
        )
        ModuleManager.initialize(modulesRoot)

        val count = handler.applyToGameDirectory(handler.getApplicableStaticPatches("celeste"), gameDir)

        assertEquals(1, count)
        assertEquals(sourceContent("com.example.invalid", "d.dll"), gameDir.resolve("ok.dll").readText())
        val names = Files.list(gameDir).use { stream -> stream.map { it.fileName.toString() }.toList() }
        assertEquals(listOf("ok.dll"), names)
    }

    @Test
    fun `missing source is skipped, others apply, glob counts per replaced file`() = runBlocking {
        val gameDir = newGameDir()
        gameDir.resolve("ok.dll").writeText("old")
        gameDir.resolve("lib.dll").writeText("old root lib")
        gameDir.resolve("Sub").createDirectories()
        gameDir.resolve("Sub/lib.dll").writeText("old sub lib")
        writeStaticModuleMeta(
            "com.example.missing", listOf("celeste"),
            files = listOf(
                "missing.dll" to "gone.dll",
                "good.dll" to "ok.dll",
                "lib.dll" to "**/lib.dll"
            )
        ).let { dir ->
            writeSource(dir, "good.dll")
            writeSource(dir, "lib.dll")
        }
        ModuleManager.initialize(modulesRoot)

        val count = handler.applyToGameDirectory(handler.getApplicableStaticPatches("celeste"), gameDir)

        // 定点 1 + glob 命中 2 个文件
        assertEquals(3, count)
        assertEquals(sourceContent("com.example.missing", "good.dll"), gameDir.resolve("ok.dll").readText())
        assertEquals(sourceContent("com.example.missing", "lib.dll"), gameDir.resolve("lib.dll").readText())
        assertEquals(sourceContent("com.example.missing", "lib.dll"), gameDir.resolve("Sub/lib.dll").readText())
        assertFalse(Files.exists(gameDir.resolve("gone.dll")))
    }

    @Test
    fun `escaping target and escaping source are skipped`() = runBlocking {
        val gameDir = newGameDir()
        gameDir.resolve("ok2.dll").writeText("old")
        writeStaticModuleMeta(
            "com.example.escape", listOf("celeste"),
            files = listOf(
                "a.dll" to "../outside.txt",
                "../secret.txt" to "ok2.dll",
                "b.dll" to "ok2.dll"
            )
        ).let { dir ->
            writeSource(dir, "a.dll")
            writeSource(dir, "b.dll")
            // ../secret.txt 故意不写：source 校验先于存在性检查，且不得越出模块目录
        }
        ModuleManager.initialize(modulesRoot)

        val count = handler.applyToGameDirectory(handler.getApplicableStaticPatches("celeste"), gameDir)

        assertEquals(1, count)
        assertEquals(sourceContent("com.example.escape", "b.dll"), gameDir.resolve("ok2.dll").readText())
        assertFalse(Files.exists(gameDir.parent.resolve("outside.txt")))
        assertFalse(Files.exists(modulesRoot.parent.resolve("secret.txt")))
    }

    @Test
    fun `conflicting patches apply ascending priority and higher priority wins`() = runBlocking {
        val gameDir = newGameDir()
        gameDir.resolve("MonoGame.Framework.dll").writeText("vanilla")
        writeStaticModule("com.example.low", listOf("celeste"), priority = 10, files = listOf("fw.dll" to "MonoGame.Framework.dll"))
        writeStaticModule("com.example.high", listOf("celeste"), priority = 100, files = listOf("fw.dll" to "MonoGame.Framework.dll"))
        ModuleManager.initialize(modulesRoot)

        val count = handler.applyToGameDirectory(handler.getApplicableStaticPatches("celeste"), gameDir)

        assertEquals(2, count)
        assertEquals(
            sourceContent("com.example.high", "fw.dll"),
            gameDir.resolve("MonoGame.Framework.dll").readText()
        )
    }

    @Test
    fun `ModuleManager installed meta zip static patch is listed with decoded manifest`() = runBlocking {
        val zip = temporaryFolder.newFolder("zips").toPath().resolve("static.zip")
        writeZip(
            zip,
            AppConstants.Files.MODULE_META to
                """{"type":"static_patch","id":"com.example.imported","version":"1.0.0","targetGames":["celeste"],"priority":7,"files":[{"source":"F.dll","target":"**/F.dll"}]}""",
            "F.dll" to "binary"
        )

        val module = ModuleManager.install(zip, force = true)

        assertEquals("com.example.imported", module.id)
        assertTrue(module.enabled)
        val patch = handler.staticPatches().single { it.id == "com.example.imported" }
        assertEquals(listOf("celeste"), patch.manifest.targetGames)
        assertEquals(7, patch.manifest.priority)
        assertEquals("F.dll", patch.manifest.files.single().source)
        assertEquals("**/F.dll", patch.manifest.files.single().target)
    }

    /** 最小安装插件：落一个 exe 并走 finishInstall，用于验证安装收尾接线。 */
    private class FakeInstallPlugin : BaseInstallPlugin() {
        override val pluginId = "fake"
        override val displayName = "Fake"
        override val supportedGames: List<GameDefinition> = emptyList()
        override fun detectGame(gameFile: GameFile): GameDetectResult? = null
        override fun detectModLoader(modLoaderFile: GameFile): ModLoaderDetectResult? = null

        var gameDir: Path? = null
            private set

        override suspend fun performInstall(
            gameFile: GameFile?,
            modLoaderFile: GameFile?,
            callback: ((GameInstallPlugin.Event) -> Unit)?
        ): GameInstallPlugin.Result {
            val root = GameManager.createDirectory("celeste")
            gameDir = root
            Files.write(root.resolve("Celeste.exe"), "original".toByteArray())
            return finishInstall(
                GameItem(
                    id = root.fileName.toString(),
                    displayedName = "Celeste",
                    gameId = "celeste",
                    gameExePathRelative = "Celeste.exe"
                ),
                callback
            )
        }
    }

    @Test
    fun `install finish applies applicable static patches to game directory`() = runBlocking {
        val moduleId = "com.example.static.celeste"
        writeStaticModule(moduleId, listOf("celeste"), files = listOf("new.exe" to "Celeste.exe"))
        ModuleManager.initialize(modulesRoot)

        val plugin = FakeInstallPlugin()
        val result = plugin.install(null, null)

        assertTrue(result is GameInstallPlugin.Result.Success)
        assertEquals(
            sourceContent(moduleId, "new.exe"),
            plugin.gameDir!!.resolve("Celeste.exe").readText()
        )

        // 模块总开关关闭后重装：不应用静态补丁，游戏保持原始文件
        ModuleManager.setEnabled(moduleId, false)
        val reinstallPlugin = FakeInstallPlugin()
        val reinstalled = reinstallPlugin.install(null, null)

        assertTrue(reinstalled is GameInstallPlugin.Result.Success)
        assertEquals("original", reinstallPlugin.gameDir!!.resolve("Celeste.exe").readText())
    }

    @Test
    fun `install finish auto-registers all applicable patch modules as enabled`() = runBlocking {
        writePatchModuleMeta("com.example.patch.celeste", listOf("celeste"))
        writePatchModuleMeta("com.example.patch.any", listOf("*"))
        writePatchModuleMeta("com.example.patch.terraria", listOf("terraria"))
        writePatchModuleMeta("com.example.patch.disabled", listOf("celeste"))
        writeStaticModule("com.example.static.celeste", listOf("celeste"), files = listOf("s.dll" to "s.dll"))
        ModuleManager.initialize(modulesRoot)
        ModuleManager.setEnabled("com.example.patch.disabled", false)

        val plugin = FakeInstallPlugin()
        val result = plugin.install(null, null)

        assertTrue(result is GameInstallPlugin.Result.Success)
        val saved = GameManager.get(plugin.gameDir!!.fileName.toString())!!
        // 适用（含 "*"）即注册为启用；总开关关闭的仍登记（启动链路按总开关过滤），
        // 不适用的与其余类型（static_patch）不进入按游戏启用列表
        assertEquals(
            setOf("com.example.patch.celeste", "com.example.patch.any", "com.example.patch.disabled"),
            saved.enabledModules.toSet()
        )
    }
}
