package com.app.ralaunch.core.di.service

import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.di.contract.ISettingsRepositoryServiceV2
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name

/**
 * 运行时管理服务 V2
 *
 * 使用 filesDir/runtimes/<runtime>/<version> 作为统一布局。
 */
@OptIn(ExperimentalPathApi::class)
class RuntimeManagerServiceV2(
    private val settingsRepository: ISettingsRepositoryServiceV2,
    private val runtimesRootPathProvider: () -> Path,
    private val legacyDotnetRootPathProvider: () -> Path,
    private val filesRootPathProvider: (() -> Path)? = null
) : IRuntimeManagerServiceV2 {

    constructor(
        settingsRepository: ISettingsRepositoryServiceV2,
        pathsProvider: StoragePathsProviderServiceV1
    ) : this(
        settingsRepository = settingsRepository,
        runtimesRootPathProvider = { Path(pathsProvider.runtimesDirPathFull()) },
        legacyDotnetRootPathProvider = { Path(pathsProvider.legacyDotnetDirPathFull()) },
        filesRootPathProvider = { Path(pathsProvider.filesDirPathFull()) }
    )

    constructor(
        settingsRepository: ISettingsRepositoryServiceV2,
        runtimesRootPath: Path,
        legacyDotnetRootPath: Path
    ) : this(
        settingsRepository = settingsRepository,
        runtimesRootPathProvider = { runtimesRootPath },
        legacyDotnetRootPathProvider = { legacyDotnetRootPath }
    )

    override fun getRuntimesRootPath(): Path = runtimesRootPathProvider().toAbsolutePath().normalize()

    override fun getRuntimeTypeRootPath(type: IRuntimeManagerServiceV2.RuntimeType): Path =
        getRuntimesRootPath().resolve(type.dirName)

    override fun getRuntimeInstallPath(type: IRuntimeManagerServiceV2.RuntimeType, version: String): Path =
        getRuntimeTypeRootPath(type).resolve(version.trim())

    override fun getInstalledRuntimes(type: IRuntimeManagerServiceV2.RuntimeType): List<IRuntimeManagerServiceV2.InstalledRuntime> {
        val typeRootPath = getRuntimeTypeRootPath(type)
        if (!typeRootPath.exists() || !typeRootPath.isDirectory()) return emptyList()

        return typeRootPath.listDirectoryEntries()
            .asSequence()
            .filter { it.isDirectory() }
            .filter { isRuntimeLayoutValid(type, it) }
            .map {
                IRuntimeManagerServiceV2.InstalledRuntime(
                    type = type,
                    version = it.name,
                    rootPath = it
                )
            }
            .sortedWith { left, right -> compareVersions(right.version, left.version) }
            .toList()
    }

    override fun getInstalledVersions(type: IRuntimeManagerServiceV2.RuntimeType): List<String> {
        return getInstalledRuntimes(type).map { it.version }
    }

    override fun getSelectedRuntime(type: IRuntimeManagerServiceV2.RuntimeType): IRuntimeManagerServiceV2.InstalledRuntime? {
        val installed = getInstalledRuntimes(type)
        if (installed.isEmpty()) return null

        val selectedVersion = getSelectedRuntimeVersion(type)
        return installed.firstOrNull { it.version == selectedVersion } ?: installed.first()
    }

    override fun getSelectedRuntimeVersion(type: IRuntimeManagerServiceV2.RuntimeType): String? {
        val selected = when (type) {
            IRuntimeManagerServiceV2.RuntimeType.DOTNET -> settingsRepository.Settings.selectedDotnetRuntimeVersion
            IRuntimeManagerServiceV2.RuntimeType.BOX64 -> settingsRepository.Settings.selectedBox64RuntimeVersion
        }.trim()

        return selected.ifBlank { null }
    }

    override suspend fun setSelectedRuntimeVersion(type: IRuntimeManagerServiceV2.RuntimeType, version: String) {
        val normalizedVersion = version.trim()
        settingsRepository.update {
            when (type) {
                IRuntimeManagerServiceV2.RuntimeType.DOTNET -> selectedDotnetRuntimeVersion = normalizedVersion
                IRuntimeManagerServiceV2.RuntimeType.BOX64 -> selectedBox64RuntimeVersion = normalizedVersion
            }
        }
    }

    override fun detectDotNetRuntimeVersion(runtimeRootPath: Path): String? {
        val hostVersions = runtimeRootPath.resolve("host").resolve("fxr")
            .listVersionDirectories()
        val sharedVersions = runtimeRootPath.resolve("shared")
            .resolve("Microsoft.NETCore.App")
            .listVersionDirectories()

        val sharedVersionNames = sharedVersions.map { it.name }.toSet()
        val common = hostVersions.filter { it.name in sharedVersionNames }.map { it.name }
        return when {
            common.isNotEmpty() -> common.maxWithOrNull(::compareVersions)
            sharedVersions.isNotEmpty() -> sharedVersions.map { it.name }.maxWithOrNull(::compareVersions)
            hostVersions.isNotEmpty() -> hostVersions.map { it.name }.maxWithOrNull(::compareVersions)
            else -> null
        }
    }

    private fun isRuntimeLayoutValid(type: IRuntimeManagerServiceV2.RuntimeType, runtimeRootPath: Path): Boolean {
        return when (type) {
            IRuntimeManagerServiceV2.RuntimeType.DOTNET -> isDotNetLayoutValid(runtimeRootPath)
            IRuntimeManagerServiceV2.RuntimeType.BOX64 -> hasAnyChildren(runtimeRootPath)
        }
    }

    private fun getRuntimeStorageRootPath(): Path {
        return filesRootPathProvider?.invoke()?.toAbsolutePath()?.normalize()
            ?: (getRuntimesRootPath().parent?.toAbsolutePath()?.normalize() ?: getRuntimesRootPath())
    }

    private fun isDotNetLayoutValid(runtimeRootPath: Path): Boolean {
        val version = detectDotNetRuntimeVersion(runtimeRootPath) ?: return false
        val requiredPaths = listOf(
            runtimeRootPath.resolve("host").resolve("fxr").resolve(version).resolve("libhostfxr.so"),
            runtimeRootPath.resolve("shared").resolve("Microsoft.NETCore.App").resolve(version).resolve("libcoreclr.so"),
            runtimeRootPath.resolve("shared").resolve("Microsoft.NETCore.App").resolve(version).resolve("libclrjit.so"),
            runtimeRootPath.resolve("shared").resolve("Microsoft.NETCore.App").resolve(version).resolve("libhostpolicy.so")
        )
        return requiredPaths.all { it.exists() }
    }

    private fun hasAnyChildren(runtimeRootPath: Path): Boolean {
        if (!runtimeRootPath.exists() || !runtimeRootPath.isDirectory()) return false
        return runtimeRootPath.listDirectoryEntries().isNotEmpty()
    }

    private fun Path.listVersionDirectories(): List<Path> {
        if (!exists() || !isDirectory()) return emptyList()
        return listDirectoryEntries().filter { it.isDirectory() }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(".").map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until maxLength) {
            val leftPart = leftParts.getOrElse(index) { 0 }
            val rightPart = rightParts.getOrElse(index) { 0 }
            if (leftPart != rightPart) {
                return leftPart.compareTo(rightPart)
            }
        }
        return 0
    }

    ///region Migration

    override fun migrateLegacyInstallations() {
        if (legacyDotnetMigrationChecked) return

        synchronized(migrationLock) {
            if (legacyDotnetMigrationChecked) return
            migrateLegacyDotnetIfNeeded()
            legacyDotnetMigrationChecked = true
        }
    }

    private fun migrateLegacyDotnetIfNeeded() {
        val legacyRootPath = legacyDotnetRootPathProvider().toAbsolutePath().normalize()
        if (!legacyRootPath.exists() || !legacyRootPath.isDirectory()) return
        if (!isRuntimeLayoutValid(IRuntimeManagerServiceV2.RuntimeType.DOTNET, legacyRootPath)) {
            return
        }

        val version = detectDotNetRuntimeVersion(legacyRootPath)
        if (version.isNullOrBlank()) {
            return
        }

        val targetPath = getRuntimeInstallPath(IRuntimeManagerServiceV2.RuntimeType.DOTNET, version)
        targetPath.parent?.createDirectories()

        when {
            targetPath.exists() && isRuntimeLayoutValid(IRuntimeManagerServiceV2.RuntimeType.DOTNET, targetPath) -> {
                FileUtils.deleteDirectoryRecursivelyWithinRoot(legacyRootPath, getRuntimeStorageRootPath())
            }

            targetPath.exists() -> {
                return
            }

            else -> {
                legacyRootPath.moveTo(targetPath)
            }
        }

        if (getSelectedRuntimeVersion(IRuntimeManagerServiceV2.RuntimeType.DOTNET).isNullOrBlank()) {
            runBlocking {
                setSelectedRuntimeVersion(IRuntimeManagerServiceV2.RuntimeType.DOTNET, version)
            }
        }
    }

    @Volatile
    private var legacyDotnetMigrationChecked = false

    private val migrationLock = Any()

    ///endregion
}
