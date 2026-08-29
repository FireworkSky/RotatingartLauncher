package com.app.ralaunch.jsonconfig

import com.app.ralaunch.core.model.AppSettings
import com.app.ralaunch.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GeneratedAppSettingsJsonConfig(
    override val configPath: String,
) : AppSettingsFlowJsonConfigGenerated()

// object 单例形态：configPath 用 by lazy 延迟到首次 load()/save() 才解析
// （对应 Android 侧路径依赖 Context.filesDir、需等 Koin 就绪的场景）
object ObjectAppSettings : AppSettingsFlowJsonConfigGenerated() {
    override val configPath: String by lazy {
        File(System.getProperty("java.io.tmpdir", "."), "object_app_settings.json").absolutePath
    }
}

class FlowJsonConfigGeneratedTest {

    private fun newTempDir(): File = Files.createTempDirectory("ralaunch-jsonconfig-gen").toFile()

    @Test
    fun generatedDefaultsMatchModelDefaults() {
        val config = GeneratedAppSettingsJsonConfig(
            File(System.getProperty("java.io.tmpdir"), "unused.json").absolutePath
        )
        assertEquals(AppSettings(), config.value)
        assertEquals(ThemeMode.LIGHT, config.c.themeMode)
        assertTrue(config.c.dynamicColor)
        assertEquals(0xFF6750A4.toInt(), config.c.themeColor)
        assertEquals(1.0f, config.c.videoPlaybackSpeed, 1e-6f)
    }

    @Test
    fun generatedDelegatesWriteThroughState() {
        val config = GeneratedAppSettingsJsonConfig(
            File(System.getProperty("java.io.tmpdir"), "unused.json").absolutePath
        )
        config.c.dynamicColor = false
        config.c.videoPlaybackSpeed = 1.5f
        config.c.targetFps = 120
        config.c.language = "zh-CN"
        assertEquals(
            AppSettings(dynamicColor = false, videoPlaybackSpeed = 1.5f, targetFps = 120, language = "zh-CN"),
            config.value
        )
    }

    @Test
    fun generatedAutoSaveDelegatesWriteThroughStateAndPersist() {
        val path = File(newTempDir(), "app_settings.json").absolutePath
        val config = GeneratedAppSettingsJsonConfig(path)
        config.load()

        config.s.videoPlaybackSpeed = 0.75f

        // 状态更新且自动落盘
        assertEquals(0.75f, config.value.videoPlaybackSpeed, 1e-6f)
        val reloaded = GeneratedAppSettingsJsonConfig(path)
        reloaded.load()
        assertEquals(0.75f, reloaded.c.videoPlaybackSpeed, 1e-6f)
    }

    @Test
    fun generatedSaveLoadRoundTripsThroughDisk() {
        val path = File(newTempDir(), "app_settings.json").absolutePath
        val config = GeneratedAppSettingsJsonConfig(path)
        config.c.language = "en-US"
        config.save()

        val reloaded = GeneratedAppSettingsJsonConfig(path)
        reloaded.load()
        assertEquals("en-US", reloaded.c.language)
    }

    @Test
    fun generatedLoadWritesDefaultWhenFileMissing() {
        val path = File(newTempDir(), "app_settings.json").absolutePath
        val config = GeneratedAppSettingsJsonConfig(path)
        config.load()
        assertTrue(File(path).exists())
        assertEquals(AppSettings(), config.value)
    }

    @Test
    fun objectSingletonHolderSupportsInstanceAccessAndRoundTrip() {
        val path = File(System.getProperty("java.io.tmpdir", "."), "object_app_settings.json").absolutePath
        File(path).delete()

        ObjectAppSettings.load() // 文件缺失时写入默认配置
        ObjectAppSettings.c.dynamicColor = false
        ObjectAppSettings.c.videoPlaybackSpeed = 0.25f
        assertTrue(ObjectAppSettings.save())

        // 用独立 class 实例读同一文件，验证落盘内容
        val independent = GeneratedAppSettingsJsonConfig(path)
        independent.load()
        assertFalse(independent.c.dynamicColor)
        assertEquals(0.25f, independent.c.videoPlaybackSpeed, 1e-6f)
    }
}
