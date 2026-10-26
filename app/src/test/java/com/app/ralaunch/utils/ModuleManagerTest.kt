package com.app.ralaunch.utils

import android.app.Application
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.utils.modules.ModuleHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

// install(zip) 经 ArchiveExtractor（android Os / 字符串资源），与 ArchiveExtractorTest 一致使用 Robolectric
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class ModuleManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun writeModule(root: java.nio.file.Path, id: String, metaJson: String): java.nio.file.Path =
        root.resolve(id).createDirectories().also { dir ->
            dir.resolve(AppConstants.Files.MODULE_META).writeText(metaJson)
        }

    private fun stateFile(root: java.nio.file.Path): java.nio.file.Path =
        root.resolve(AppConstants.Files.MODULE_MANAGER)

    private fun savedIds(root: java.nio.file.Path): List<String> =
        Json.parseToJsonElement(stateFile(root).readText())
            .jsonObject["modules"]?.jsonArray.orEmpty()
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }

    /** 生成 zip：条目直接位于压缩包根（无顶层文件夹），同模块包布局契约。 */
    private fun writeZip(zipPath: java.nio.file.Path, vararg entries: Pair<String, String>) {
        ZipOutputStream(zipPath.outputStream().buffered()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `empty directory initializes with empty list and default state file`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        ModuleManager.initialize(root)

        assertTrue(ModuleManager.currentModules.isEmpty())
        // 首次启动生成状态文件；默认空列表被省略（encodeDefaults=false），语义为空且可再加载
        assertTrue(stateFile(root).exists())
        assertTrue(savedIds(root).isEmpty())
        ModuleManager.initialize(root)
        assertTrue(ModuleManager.currentModules.isEmpty())
    }

    @Test
    fun `registerHandler stores handler by type and unregistered types return null`() {
        ModuleManager.initialize(temporaryFolder.newFolder("modules").toPath())
        val handler = object : ModuleHandler { override val type = "registry.probe" }

        ModuleManager.registerHandler(handler)

        assertTrue(ModuleManager.handler("registry.probe") === handler)
        assertNull(ModuleManager.handler("registry.unknown"))
    }

    @Test
    fun `registerHandler replaces existing handler for same type`() {
        ModuleManager.initialize(temporaryFolder.newFolder("modules").toPath())
        val first = object : ModuleHandler { override val type = "registry.replace" }
        val second = object : ModuleHandler { override val type = "registry.replace" }

        ModuleManager.registerHandler(first)
        ModuleManager.registerHandler(second)

        assertTrue(ModuleManager.handler("registry.replace") === second)
    }

    @Test
    fun `invalid module directories are skipped and meta is passed through`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        writeModule(
            root, "com.example.theme",
            """{"id":"com.example.theme","type":"theme","name":"White Theme","description":"Bright theme","version":"1.2.0","author":"alice","colors":{"primary":"#ffffff"}}"""
        )
        // 目录名与 meta id 不符
        writeModule(root, "com.example.mismatch", """{"id":"com.example.theme","type":"theme"}""")
        // meta 非法 JSON
        root.resolve("com.example.broken").createDirectories()
            .resolve(AppConstants.Files.MODULE_META).writeText("{ not json")
        // 缺 meta.json
        root.resolve("com.example.missing").createDirectories()
        // id 非包名格式（单段）
        writeModule(root, "single", """{"id":"single","type":"theme"}""")

        ModuleManager.initialize(root)

        val modules = ModuleManager.currentModules
        assertEquals(listOf("com.example.theme"), modules.map { it.id })
        val module = modules.single()
        assertEquals("theme", module.type)
        assertEquals("White Theme", module.name)
        assertEquals("Bright theme", module.description)
        assertEquals("1.2.0", module.version)
        assertEquals("alice", module.author)
        assertTrue(module.enabled)
        // meta 完整透传，colors 原始结构保留
        assertEquals(
            Json.parseToJsonElement(
                """{"id":"com.example.theme","type":"theme","name":"White Theme","description":"Bright theme","version":"1.2.0","author":"alice","colors":{"primary":"#ffffff"}}"""
            ),
            module.meta
        )
        assertEquals(
            Json.parseToJsonElement("""{"primary":"#ffffff"}"""),
            module.meta["colors"]
        )
        assertEquals("com.example.theme", module.meta["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `setEnabled persists to state file and survives restart`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        writeModule(root, "com.example.theme", """{"id":"com.example.theme","type":"theme"}""")
        ModuleManager.initialize(root)

        ModuleManager.setEnabled("com.example.theme", false)

        assertTrue(stateFile(root).readText().contains("\"enabled\": false"))
        // 模拟重启：重新 initialize 后状态仍为 disabled
        ModuleManager.initialize(root)
        assertEquals(false, ModuleManager.get("com.example.theme")?.enabled)

        ModuleManager.setEnabled("com.example.theme", true)
        ModuleManager.initialize(root)
        assertEquals(true, ModuleManager.get("com.example.theme")?.enabled)
    }

    @Test
    fun `refresh picks up new directories in id order and drops removed ones`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        writeModule(root, "com.a.alpha", """{"id":"com.a.alpha","type":"theme"}""")
        ModuleManager.initialize(root)
        assertEquals(listOf("com.a.alpha"), ModuleManager.currentModules.map { it.id })

        writeModule(root, "com.z.gamma", """{"id":"com.z.gamma","type":"plugin"}""")
        writeModule(root, "com.a.beta", """{"id":"com.a.beta","type":"theme"}""")
        ModuleManager.refresh()

        assertEquals(
            listOf("com.a.alpha", "com.a.beta", "com.z.gamma"),
            ModuleManager.currentModules.map { it.id }
        )
        assertTrue(ModuleManager.get("com.a.beta")!!.enabled)
        // 通用元数据字段缺失时回退空字符串
        assertTrue(ModuleManager.get("com.a.beta")!!.name.isEmpty())
        assertTrue(ModuleManager.get("com.a.beta")!!.version.isEmpty())

        root.resolve("com.a.beta").deleteRecursively()
        ModuleManager.refresh()
        assertEquals(
            listOf("com.a.alpha", "com.z.gamma"),
            ModuleManager.currentModules.map { it.id }
        )
    }

    @Test
    fun `install copies external directory, registers and re-install skips`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        ModuleManager.initialize(root)
        val source = temporaryFolder.newFolder("com.example.theme").toPath()
        source.resolve(AppConstants.Files.MODULE_META).writeText(
            """{"id":"com.example.theme","type":"theme","name":"White Theme","description":"Bright theme","version":"1.2.0","author":"alice","colors":{"primary":"#ffffff"}}"""
        )
        source.resolve("assets").createDirectories().resolve("theme.css").writeText("body{}")

        val module = ModuleManager.install(source)

        assertEquals("com.example.theme", module.id)
        assertEquals("theme", module.type)
        assertEquals("White Theme", module.name)
        assertEquals("1.2.0", module.version)
        assertEquals("alice", module.author)
        assertTrue(module.enabled)
        assertEquals(
            Json.parseToJsonElement(
                """{"id":"com.example.theme","type":"theme","name":"White Theme","description":"Bright theme","version":"1.2.0","author":"alice","colors":{"primary":"#ffffff"}}"""
            ),
            module.meta
        )
        val target = root.resolve("com.example.theme")
        assertEquals(target, ModuleManager.directory("com.example.theme"))
        assertEquals("body{}", target.resolve("assets/theme.css").readText())
        assertEquals("body{}", source.resolve("assets/theme.css").readText())
        assertEquals(listOf("com.example.theme"), ModuleManager.currentModules.map { it.id })

        // 同版本重复安装：跳过并返回当前条目，状态不被破坏
        val skipped = ModuleManager.install(source)
        assertEquals("1.2.0", skipped.version)
        // 直属子目录重复登记：幂等返回当前条目
        val idempotent = ModuleManager.install(target)
        assertEquals("1.2.0", idempotent.version)
        assertEquals(listOf("com.example.theme"), ModuleManager.currentModules.map { it.id })
        assertTrue(target.resolve("assets/theme.css").exists())
    }

    @Test
    fun `install registers direct child without copying and is idempotent`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        ModuleManager.initialize(root)
        // initialize 之后才出现的直属子目录，经 install 登记
        val child = writeModule(
            root, "com.example.theme",
            """{"id":"com.example.theme","type":"theme","version":"1.0.0"}"""
        )
        child.resolve("asset.txt").writeText("kept")

        val module = ModuleManager.install(child)

        assertEquals("com.example.theme", module.id)
        assertEquals("1.0.0", module.version)
        assertTrue(module.enabled)
        assertEquals(listOf("com.example.theme"), ModuleManager.currentModules.map { it.id })
        assertEquals("kept", child.resolve("asset.txt").readText())

        // 再次登记幂等：不产生重复条目，目录原样保留
        val again = ModuleManager.install(child)
        assertEquals("1.0.0", again.version)
        assertEquals(listOf("com.example.theme"), ModuleManager.currentModules.map { it.id })
        assertEquals("kept", child.resolve("asset.txt").readText())
        assertEquals(listOf("com.example.theme"), savedIds(root))
    }

    @Test
    fun `install rejects modules directory, its ancestor, nested sources and invalid meta`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        ModuleManager.initialize(root)

        // 管理目录自身与其祖先
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.install(ModuleManager.modulesDirectory) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.install(root.parent) }
        }
        // 管理目录内的非直属子目录
        val nested = writeModule(root, "com.example.new", """{"id":"com.example.new","type":"theme"}""")
            .resolve("inner").createDirectories()
        nested.resolve(AppConstants.Files.MODULE_META).writeText(
            """{"id":"com.example.new","type":"theme"}"""
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.install(nested) }
        }
        // 外部目录缺 meta.json
        val noMeta = temporaryFolder.newFolder("no-meta").toPath()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.install(noMeta) }
        }
        // 不存在的路径
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.install(root.resolve("missing")) }
        }
        // 拒绝后状态与磁盘均未被污染
        assertTrue(ModuleManager.currentModules.isEmpty())
        assertFalse(root.resolve("com.example.theme").exists())
    }

    @Test
    fun `remove deletes directory, unregisters module and preserves neighbors`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        writeModule(root, "com.a.first", """{"id":"com.a.first","type":"theme"}""")
        writeModule(root, "com.b.second", """{"id":"com.b.second","type":"plugin"}""")
        ModuleManager.initialize(root)

        ModuleManager.remove("com.a.first")

        assertFalse(root.resolve("com.a.first").exists())
        assertEquals(listOf("com.b.second"), savedIds(root))
        val neighbor = ModuleManager.get("com.b.second")
        assertNotNull(neighbor)
        assertEquals("plugin", neighbor!!.type)
        assertTrue(root.resolve("com.b.second").exists())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.remove("../x") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.remove("") }
        }
        // 越界拒绝后邻居与状态文件不受影响
        assertEquals(listOf("com.b.second"), savedIds(root))
    }

    @Test
    fun `reorder updates flow, state file and survives restart`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        writeModule(root, "com.a.alpha", """{"id":"com.a.alpha","type":"theme"}""")
        writeModule(root, "com.a.beta", """{"id":"com.a.beta","type":"theme"}""")
        writeModule(root, "com.a.gamma", """{"id":"com.a.gamma","type":"theme"}""")
        ModuleManager.initialize(root)
        assertEquals(
            listOf("com.a.alpha", "com.a.beta", "com.a.gamma"),
            ModuleManager.currentModules.map { it.id }
        )

        ModuleManager.reorder(0, 2)

        val expected = listOf("com.a.beta", "com.a.gamma", "com.a.alpha")
        assertEquals(expected, ModuleManager.currentModules.map { it.id })
        assertEquals(expected, ModuleManager.modules.first().map { it.id })
        assertEquals(expected, savedIds(root))

        ModuleManager.initialize(root)
        assertEquals(expected, ModuleManager.currentModules.map { it.id })
    }

    @Test
    fun `install updates from external directory when version is newer preserving enabled and order`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        writeModule(root, "com.a.alpha", """{"id":"com.a.alpha","type":"theme"}""")
        writeModule(
            root, "com.example.theme",
            """{"id":"com.example.theme","type":"theme","name":"Theme","version":"1.0.0"}"""
        )
        ModuleManager.initialize(root)
        ModuleManager.setEnabled("com.example.theme", false)
        ModuleManager.directory("com.example.theme").resolve("old_asset.txt").writeText("old")

        val source = temporaryFolder.newFolder("source").toPath()
            .resolve("com.example.theme").createDirectories()
        source.resolve(AppConstants.Files.MODULE_META).writeText(
            """{"id":"com.example.theme","type":"theme","name":"Theme","version":"1.1.0","colors":{"primary":"#000000"}}"""
        )
        source.resolve("new_asset.txt").writeText("new")

        val updated = ModuleManager.install(source)

        assertEquals("1.1.0", updated.version)
        assertEquals(false, updated.enabled)
        assertEquals("com.a.alpha", ModuleManager.currentModules.first().id)
        val target = ModuleManager.directory("com.example.theme")
        assertEquals("new", target.resolve("new_asset.txt").readText())
        assertFalse(target.resolve("old_asset.txt").exists())
        assertEquals("new", source.resolve("new_asset.txt").readText())
        // 重启后版本与 enabled 状态均保留
        ModuleManager.initialize(root)
        assertEquals("1.1.0", ModuleManager.get("com.example.theme")?.version)
        assertEquals(false, ModuleManager.get("com.example.theme")?.enabled)
    }

    @Test
    fun `install skips equal or older directory versions unless forced`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        writeModule(
            root, "com.example.theme",
            """{"id":"com.example.theme","type":"theme","version":"2.0.0"}"""
        )
        ModuleManager.initialize(root)
        val target = ModuleManager.directory("com.example.theme")
        target.resolve("old_asset.txt").writeText("old")

        val source = temporaryFolder.newFolder("source").toPath()
            .resolve("com.example.theme").createDirectories()
        source.resolve(AppConstants.Files.MODULE_META).writeText(
            """{"id":"com.example.theme","type":"theme","version":"2.0.0"}"""
        )
        source.resolve("new_asset.txt").writeText("new")

        // 相同版本：跳过
        val skipped = ModuleManager.install(source)
        assertEquals("2.0.0", skipped.version)
        assertTrue(target.resolve("old_asset.txt").exists())
        assertFalse(target.resolve("new_asset.txt").exists())

        // 更旧版本：跳过
        source.resolve(AppConstants.Files.MODULE_META).writeText(
            """{"id":"com.example.theme","type":"theme","version":"1.9.0"}"""
        )
        val skippedOlder = ModuleManager.install(source)
        assertEquals("2.0.0", skippedOlder.version)
        assertTrue(target.resolve("old_asset.txt").exists())

        // force：无视版本强制重装
        val forced = ModuleManager.install(source, force = true)
        assertEquals("1.9.0", forced.version)
        assertEquals("new", target.resolve("new_asset.txt").readText())
        assertFalse(target.resolve("old_asset.txt").exists())
    }

    @Test
    fun `install zip installs new module with root-level meta and content`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        ModuleManager.initialize(root)
        val zip = temporaryFolder.newFolder("zips").toPath().resolve("theme.zip")
        writeZip(
            zip,
            AppConstants.Files.MODULE_META to
                """{"id":"com.example.theme","type":"theme","name":"White Theme","description":"Bright theme","version":"1.0.0","author":"alice","colors":{"primary":"#ffffff"}}""",
            "assets/theme.css" to "body{}"
        )

        val module = ModuleManager.install(zip)

        assertEquals("com.example.theme", module.id)
        assertEquals("theme", module.type)
        assertEquals("White Theme", module.name)
        assertEquals("Bright theme", module.description)
        assertEquals("1.0.0", module.version)
        assertEquals("alice", module.author)
        assertTrue(module.enabled)
        // meta 从 zip 原始透传
        assertEquals(
            "#ffffff",
            module.meta["colors"]!!.jsonObject["primary"]!!.jsonPrimitive.content
        )
        assertEquals(listOf("com.example.theme"), ModuleManager.currentModules.map { it.id })
        val target = ModuleManager.directory("com.example.theme")
        assertEquals("body{}", target.resolve("assets/theme.css").readText())
        assertEquals(root.resolve("com.example.theme"), target)
    }

    @Test
    fun `install zip updates newer version preserving enabled and order`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        writeModule(root, "com.a.alpha", """{"id":"com.a.alpha","type":"theme"}""")
        writeModule(
            root, "com.example.theme",
            """{"id":"com.example.theme","type":"theme","version":"1.0.0"}"""
        )
        ModuleManager.initialize(root)
        ModuleManager.setEnabled("com.example.theme", false)
        ModuleManager.directory("com.example.theme").resolve("old_asset.txt").writeText("old")

        val zip = temporaryFolder.newFolder("zips").toPath().resolve("theme.zip")
        writeZip(
            zip,
            AppConstants.Files.MODULE_META to
                """{"id":"com.example.theme","type":"theme","version":"1.1.0"}""",
            "new_asset.txt" to "new"
        )

        val updated = ModuleManager.install(zip)

        assertEquals("1.1.0", updated.version)
        assertEquals(false, updated.enabled)
        assertEquals("com.a.alpha", ModuleManager.currentModules.first().id)
        val target = ModuleManager.directory("com.example.theme")
        assertEquals("new", target.resolve("new_asset.txt").readText())
        assertFalse(target.resolve("old_asset.txt").exists())
        // 重启后版本与 enabled 状态均保留
        ModuleManager.initialize(root)
        assertEquals("1.1.0", ModuleManager.get("com.example.theme")?.version)
        assertEquals(false, ModuleManager.get("com.example.theme")?.enabled)
    }

    @Test
    fun `install zip skips equal or older version unless forced`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        writeModule(
            root, "com.example.theme",
            """{"id":"com.example.theme","type":"theme","version":"2.0.0"}"""
        )
        ModuleManager.initialize(root)
        val target = ModuleManager.directory("com.example.theme")
        target.resolve("old_asset.txt").writeText("old")
        val zip = temporaryFolder.newFolder("zips").toPath().resolve("theme.zip")

        // 相同版本：跳过
        writeZip(
            zip,
            AppConstants.Files.MODULE_META to
                """{"id":"com.example.theme","type":"theme","version":"2.0.0"}""",
            "new_asset.txt" to "new"
        )
        val skipped = ModuleManager.install(zip)
        assertEquals("2.0.0", skipped.version)
        assertTrue(target.resolve("old_asset.txt").exists())
        assertFalse(target.resolve("new_asset.txt").exists())

        // 更旧版本：跳过
        writeZip(
            zip,
            AppConstants.Files.MODULE_META to
                """{"id":"com.example.theme","type":"theme","version":"1.9.0"}""",
            "new_asset.txt" to "new"
        )
        val skippedOlder = ModuleManager.install(zip)
        assertEquals("2.0.0", skippedOlder.version)
        assertTrue(target.resolve("old_asset.txt").exists())

        // force：无视版本强制重装
        val forced = ModuleManager.install(zip, force = true)
        assertEquals("1.9.0", forced.version)
        assertEquals("new", target.resolve("new_asset.txt").readText())
        assertFalse(target.resolve("old_asset.txt").exists())
    }

    @Test
    fun `install zip rejects archives without valid root-level meta`() = runBlocking {
        val root = temporaryFolder.newFolder("modules").toPath()
        ModuleManager.initialize(root)
        val zips = temporaryFolder.newFolder("zips").toPath()

        // 顶层文件夹包裹 meta（违反无顶层文件夹布局契约）
        val nested = zips.resolve("nested.zip")
        writeZip(
            nested,
            "com.example.theme/${AppConstants.Files.MODULE_META}" to
                """{"id":"com.example.theme","type":"theme"}"""
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.install(nested) }
        }
        // 无 meta.json
        val noMeta = zips.resolve("no-meta.zip")
        writeZip(noMeta, "readme.txt" to "hi")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.install(noMeta) }
        }
        // 损坏压缩包
        val broken = zips.resolve("broken.zip")
        broken.writeText("not a zip")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.install(broken) }
        }
        // 不存在的路径
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModuleManager.install(zips.resolve("missing.zip")) }
        }
        // 状态与磁盘均未被污染
        assertTrue(ModuleManager.currentModules.isEmpty())
        assertFalse(root.resolve("com.example.theme").exists())
    }
}
