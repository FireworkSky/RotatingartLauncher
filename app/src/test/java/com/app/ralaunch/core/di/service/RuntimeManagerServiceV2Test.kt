package com.app.ralaunch.core.di.service

import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.di.contract.ISettingsRepositoryServiceV2
import com.app.ralaunch.core.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class)
class RuntimeManagerServiceV2Test {

    @Test
    fun `getSelectedRuntime falls back to newest installed version`() {
        val runtimesRoot = createTempDirectory("runtime-root-")
        val legacyDotnetRoot = createTempDirectory("legacy-dotnet-")

        try {
            createDotNetRuntimeLayout(runtimesRoot.resolve("dotnet").resolve("10.0.0"), "10.0.0")
            createDotNetRuntimeLayout(runtimesRoot.resolve("dotnet").resolve("10.0.4"), "10.0.4")

            val repository = FakeSettingsRepository(
                AppSettings(selectedDotnetRuntimeVersion = "9.0.0")
            )
            val service = RuntimeManagerServiceV2(repository, runtimesRoot, legacyDotnetRoot)

            val selected = service.getSelectedRuntime(IRuntimeManagerServiceV2.RuntimeType.DOTNET)

            assertNotNull(selected)
            assertEquals("10.0.4", selected?.version)
        } finally {
            runtimesRoot.deleteRecursively()
            legacyDotnetRoot.deleteRecursively()
        }
    }

    @Test
    fun `getSelectedRuntime respects persisted version when installed`() {
        val runtimesRoot = createTempDirectory("runtime-root-")
        val legacyDotnetRoot = createTempDirectory("legacy-dotnet-")

        try {
            createDotNetRuntimeLayout(runtimesRoot.resolve("dotnet").resolve("10.0.0"), "10.0.0")
            createDotNetRuntimeLayout(runtimesRoot.resolve("dotnet").resolve("10.0.4"), "10.0.4")

            val repository = FakeSettingsRepository(
                AppSettings(selectedDotnetRuntimeVersion = "10.0.0")
            )
            val service = RuntimeManagerServiceV2(repository, runtimesRoot, legacyDotnetRoot)

            val selected = service.getSelectedRuntime(IRuntimeManagerServiceV2.RuntimeType.DOTNET)

            assertNotNull(selected)
            assertEquals("10.0.0", selected?.version)
        } finally {
            runtimesRoot.deleteRecursively()
            legacyDotnetRoot.deleteRecursively()
        }
    }

    @Test
    fun `migrateLegacyInstallations moves legacy dotnet into versioned runtimes layout`() {
        val runtimesRoot = createTempDirectory("runtime-root-")
        val legacyParent = createTempDirectory("legacy-parent-")
        val legacyDotnetRoot = legacyParent.resolve("dotnet")

        try {
            createDotNetRuntimeLayout(legacyDotnetRoot, "10.0.4")

            val repository = FakeSettingsRepository()
            val service = RuntimeManagerServiceV2(repository, runtimesRoot, legacyDotnetRoot)

            service.migrateLegacyInstallations()

            val migratedRoot = runtimesRoot.resolve("dotnet").resolve("10.0.4")
            assertTrue(migratedRoot.exists())
            assertTrue(legacyDotnetRoot.notExists())
            assertEquals(
                "10.0.4",
                repository.Settings.selectedDotnetRuntimeVersion
            )
        } finally {
            runtimesRoot.deleteRecursively()
            legacyParent.deleteRecursively()
        }
    }

    @Test
    fun `getInstalledRuntimes does not migrate legacy dotnet automatically`() {
        val runtimesRoot = createTempDirectory("runtime-root-")
        val legacyParent = createTempDirectory("legacy-parent-")
        val legacyDotnetRoot = legacyParent.resolve("dotnet")

        try {
            createDotNetRuntimeLayout(legacyDotnetRoot, "10.0.4")

            val service = RuntimeManagerServiceV2(FakeSettingsRepository(), runtimesRoot, legacyDotnetRoot)

            val installed = service.getInstalledRuntimes(IRuntimeManagerServiceV2.RuntimeType.DOTNET)

            assertTrue(installed.isEmpty())
            assertTrue(legacyDotnetRoot.exists())
            assertFalse(runtimesRoot.resolve("dotnet").resolve("10.0.4").exists())
        } finally {
            runtimesRoot.deleteRecursively()
            legacyParent.deleteRecursively()
        }
    }

    @Test
    fun `box64 discovery accepts versioned directories with contents`() {
        val runtimesRoot = createTempDirectory("runtime-root-")
        val legacyDotnetRoot = createTempDirectory("legacy-dotnet-")

        try {
            val box64RuntimeDir = runtimesRoot.resolve("box64").resolve("2.0.0").createDirectories()
            box64RuntimeDir.resolve("box64").createFile().writeText("binary")

            val service = RuntimeManagerServiceV2(FakeSettingsRepository(), runtimesRoot, legacyDotnetRoot)

            val installed = service.getInstalledRuntimes(IRuntimeManagerServiceV2.RuntimeType.BOX64)

            assertEquals(listOf("2.0.0"), installed.map { it.version })
        } finally {
            runtimesRoot.deleteRecursively()
            legacyDotnetRoot.deleteRecursively()
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

    private class FakeSettingsRepository(
        initialSettings: AppSettings = AppSettings()
    ) : ISettingsRepositoryServiceV2 {
        private val backingFlow = MutableStateFlow(initialSettings)

        override val settings: StateFlow<AppSettings> = backingFlow

        override suspend fun getSettingsSnapshot(): AppSettings = backingFlow.value.copy()

        override suspend fun updateSettings(settings: AppSettings) {
            backingFlow.value = settings.copy()
        }

        override suspend fun update(block: AppSettings.() -> Unit) {
            backingFlow.value = backingFlow.value.copy().apply(block)
        }

        override suspend fun resetToDefaults() {
            backingFlow.value = AppSettings.Default
        }
    }
}
