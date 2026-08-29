package com.app.ralaunch.jsonconfig

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.take
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

@Serializable
data class TestConfigData(
    val name: String = "a",
    val port: Int = 8080,
)

class TestConfig(
    // 写入会自动落盘，默认路径指向临时目录避免污染工作目录
    override val configPath: String = File(System.getProperty("java.io.tmpdir"), "test_config.json").absolutePath,
) : FlowJsonConfigBase<TestConfigData>(
    initial = TestConfigData(),
    serializer = TestConfigData.serializer(),
) {
    val c = Instance()
    val s = AutoSaveInstance()

    inner class Instance {
        var name by configProperty(TestConfigData::name) { c, v -> c.copy(name = v) }
        var port by configProperty(TestConfigData::port) { c, v -> c.copy(port = v) }
    }

    inner class AutoSaveInstance {
        var name by autoSaveConfigProperty(TestConfigData::name) { c, v -> c.copy(name = v) }
        var port by autoSaveConfigProperty(TestConfigData::port) { c, v -> c.copy(port = v) }
    }
}

class FlowJsonConfigBaseTest {

    private fun newTempDir(): File = Files.createTempDirectory("ralaunch-flow-config").toFile()

    @Test
    fun delegateReadsAndWritesSharedState() {
        val config = TestConfig()

        assertEquals("a", config.c.name)
        assertEquals(8080, config.c.port)

        config.c.name = "hello"
        config.c.port = 9000

        assertEquals("hello", config.c.name)
        assertEquals(9000, config.c.port)
        assertEquals(TestConfigData(name = "hello", port = 9000), config.value)
    }

    @Test
    fun stateReflectsLatestSnapshot() {
        val config = TestConfig()

        config.c.port = 1234

        assertEquals(TestConfigData(port = 1234), config.state.value)
    }

    @Test
    fun flowOfEmitsInitialValueChangesAndSkipsDuplicates() = runBlocking {
        val config = TestConfig()
        val emissions = mutableListOf<String>()
        val job = launch {
            config.flowOf(TestConfigData::name).take(3).collect { emissions.add(it) }
        }
        try {
            // 每步都泵事件循环等待预期值到达再进行下一步：
            // 既保证订阅在变更前建立，也排除 StateFlow 对连续更新的合并竞态；
            // withTimeout 兜底，任何逻辑错误都会在 5 秒内失败而非挂起。
            withTimeout(5_000) { while (emissions.isEmpty()) yield() } // 初始值 "a"
            assertEquals("a", emissions.last())

            config.c.name = "b"
            withTimeout(5_000) { while (emissions.size < 2) yield() } // 变更 "b" 到达

            config.c.name = "c"
            withTimeout(5_000) { while (emissions.size < 3) yield() } // 变更 "c" 到达

            config.update { it.copy(name = "c") } // 相等值应被 distinctUntilChanged 过滤
            config.c.port = 1 // 其他字段变化不应触发 name 流
            withTimeout(5_000) { while (!job.isCompleted) yield() } // take(3) 已满足，job 应完成
            job.join()
        } finally {
            job.cancel()
        }
        assertEquals(listOf("a", "b", "c"), emissions)
    }

    @Test
    fun initializeWritesDefaultConfigWhenFileMissing() {
        val dir = newTempDir()
        try {
            val config = TestConfig(File(dir, "test_config.json").absolutePath)
            config.load()

            val file = File(dir, "test_config.json")
            assertTrue(file.exists())
            assertEquals(TestConfigData(), config.value)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun saveAndLoadRoundTripsThroughDisk() {
        val dir = newTempDir()
        try {
            val first = TestConfig(File(dir, "test_config.json").absolutePath)
            first.load()
            first.c.name = "persisted"
            first.c.port = 65535
            assertTrue(first.save())

            val second = TestConfig(File(dir, "test_config.json").absolutePath)
            second.load()

            assertEquals(TestConfigData(name = "persisted", port = 65535), second.value)
            assertEquals("persisted", second.c.name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun configPropertyWriteUpdatesStateOnlyWithoutPersisting() {
        val dir = newTempDir()
        try {
            val path = File(dir, "test_config.json").absolutePath
            val config = TestConfig(path)
            config.load()

            config.c.port = 7777

            // 内存状态已更新，但磁盘仍是旧值
            assertEquals(7777, config.value.port)
            val reloaded = TestConfig(path)
            reloaded.load()
            assertEquals(8080, reloaded.c.port)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun autoSaveConfigPropertyWritePersistsToDisk() {
        val dir = newTempDir()
        try {
            val path = File(dir, "test_config.json").absolutePath
            val config = TestConfig(path)
            config.load()

            config.s.port = 7777

            assertEquals(7777, config.value.port)
            val reloaded = TestConfig(path)
            reloaded.load()
            assertEquals(7777, reloaded.c.port)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun updateSavePersistsWhileUpdateDoesNot() {
        val dir = newTempDir()
        try {
            val path = File(dir, "test_config.json").absolutePath
            val config = TestConfig(path)
            config.load()

            config.update { it.copy(port = 1111) }
            val afterUpdate = TestConfig(path)
            afterUpdate.load()
            assertEquals(8080, afterUpdate.c.port)

            config.updateSave { it.copy(port = 2222) }
            val afterUpdateSave = TestConfig(path)
            afterUpdateSave.load()
            assertEquals(2222, afterUpdateSave.c.port)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun loadFallsBackToDefaultOnCorruptFile() {
        val dir = newTempDir()
        try {
            File(dir, "test_config.json").writeText("{ not valid json")

            val config = TestConfig(File(dir, "test_config.json").absolutePath)
            config.load()

            assertEquals(TestConfigData(), config.value)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun loadIgnoresUnknownKeysForBackwardCompatibility() {
        val dir = newTempDir()
        try {
            File(dir, "test_config.json").writeText("""{"name":"kept","futureField":123}""")

            val config = TestConfig(File(dir, "test_config.json").absolutePath)
            config.load()

            assertEquals(TestConfigData(name = "kept"), config.value)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun saveWorksWithoutPriorLoad() {
        val dir = newTempDir()
        try {
            // configPath 就绪即可保存，无需先 load
            val config = TestConfig(File(dir, "test_config.json").absolutePath)

            assertTrue(config.save())
            assertTrue(File(dir, "test_config.json").exists())
            assertEquals(TestConfigData(), config.value)
        } finally {
            dir.deleteRecursively()
        }
    }
}
