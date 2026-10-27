package com.app.ralaunch.utils

import com.app.ralaunch.core.config.AppConfig
import com.app.ralaunch.core.model.AppSettings
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class)
class RuntimeManagerTest {

    @Before
    fun setUp() {
        // AppConfig 为进程级单例，测试间重置内存态
        AppConfig.update { AppSettings() }
        // save 显式 mock 为无操作：避免依赖"无 Koin 时静默失败"的隐式行为
        // （configPath 解析依赖 Context/磁盘 IO）
        mockkObject(AppConfig)
        every { AppConfig.save() } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getSelectedRuntime falls back to newest installed version`() {
        val runtimesRoot = createTempDirectory("runtime-root-")

        try {
            createDotNetRuntimeLayout(runtimesRoot.resolve("dotnet").resolve("10.0.0"), "10.0.0")
            createDotNetRuntimeLayout(runtimesRoot.resolve("dotnet").resolve("10.0.4"), "10.0.4")

            AppConfig.c.selectedDotnetRuntimeVersion = "9.0.0"
            RuntimeManager.initialize(runtimesRoot)

            val selected = RuntimeManager.getSelectedRuntime(RuntimeManager.RuntimeType.DOTNET)

            assertNotNull(selected)
            assertEquals("10.0.4", selected?.version)
        } finally {
            runtimesRoot.deleteRecursively()
        }
    }

    @Test
    fun `getSelectedRuntime respects persisted version when installed`() {
        val runtimesRoot = createTempDirectory("runtime-root-")

        try {
            createDotNetRuntimeLayout(runtimesRoot.resolve("dotnet").resolve("10.0.0"), "10.0.0")
            createDotNetRuntimeLayout(runtimesRoot.resolve("dotnet").resolve("10.0.4"), "10.0.4")

            AppConfig.c.selectedDotnetRuntimeVersion = "10.0.0"
            RuntimeManager.initialize(runtimesRoot)

            val selected = RuntimeManager.getSelectedRuntime(RuntimeManager.RuntimeType.DOTNET)

            assertNotNull(selected)
            assertEquals("10.0.0", selected?.version)
        } finally {
            runtimesRoot.deleteRecursively()
        }
    }

    @Test
    fun `box64 discovery accepts versioned directories with contents`() {
        val runtimesRoot = createTempDirectory("runtime-root-")

        try {
            val box64RuntimeDir = runtimesRoot.resolve("box64").resolve("2.0.0").createDirectories()
            box64RuntimeDir.resolve("box64").createFile().writeText("binary")

            RuntimeManager.initialize(runtimesRoot)

            val installed = RuntimeManager.getInstalledRuntimes(RuntimeManager.RuntimeType.BOX64)

            assertEquals(listOf("2.0.0"), installed.map { it.version })
        } finally {
            runtimesRoot.deleteRecursively()
        }
    }

    private fun createDotNetRuntimeLayout(runtimeRoot: java.nio.file.Path, version: String) {
        runtimeRoot.resolve("host").resolve("fxr").resolve(version).createDirectories()
            .resolve("libhostfxr.so")
            .createFile()
            .writeText("hostfxr")

        val sharedDir = runtimeRoot.resolve("shared")
            .resolve("Microsoft.NETCore.App")
            .resolve(version)
            .createDirectories()

        listOf("libcoreclr.so", "libclrjit.so", "libhostpolicy.so").forEach { fileName ->
            sharedDir.resolve(fileName).createFile().writeText(fileName)
        }
    }
}
