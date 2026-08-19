package com.app.ralaunch.utils

import androidx.core.content.edit
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.platform.AppConstants
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import kotlin.io.path.name

/*******************************************************************************
 * RotatingArtLauncher - RuntimeManager
 * 
 * This file is part of the RotatingArtLauncher project.
 * 
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 * 
 * Created by: eternalfuture-e38299 (2026/7/31)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

object RuntimeManager {

    const val SELECTED_DOTNET_RUNTIME_VERSION = "selected_dotnet_runtime_version"
    const val SELECTED_BOX64_RUNTIME_VERSION = "selected_box64_runtime_version"

    // ====== Runtime Type Enum ======

    enum class RuntimeType(val dirName: String) {
        DOTNET("dotnet"),
        BOX64("box64");

        companion object {
            fun fromDirName(value: String): RuntimeType? = entries.find {
                it.dirName.equals(value, ignoreCase = true)
            }
        }
    }

    data class InstalledRuntime(
        val type: RuntimeType,
        val version: String,
        val rootPath: Path
    )

    // ====== Path Providers ======

    private var filesRootPath: Path? = null
    private var runtimesRootPath: Path? = null
    private var legacyDotnetRootPath: Path? = null

    // ====== Migration State ======

    @Volatile
    private var legacyDotnetMigrationChecked = false
    private val migrationLock = Any()

    // ====== Initialization ======

    fun initialize(filesDir: File) {
        filesRootPath = filesDir.toPath().toAbsolutePath().normalize()
        runtimesRootPath = filesRootPath?.resolve(AppConstants.Dirs.RUNTIMES)
        legacyDotnetRootPath = filesRootPath?.resolve("dotnet")
    }

    fun initialize(filesRootPath: Path, runtimesRootPath: Path, legacyDotnetRootPath: Path) {
        this.filesRootPath = filesRootPath.toAbsolutePath().normalize()
        this.runtimesRootPath = runtimesRootPath.toAbsolutePath().normalize()
        this.legacyDotnetRootPath = legacyDotnetRootPath.toAbsolutePath().normalize()
    }

    // ====== Path Methods ======

    fun getRuntimesRootPath(): Path {
        return runtimesRootPath ?: throw IllegalStateException("RuntimeManager not initialized")
    }

    fun getRuntimeTypeRootPath(type: RuntimeType): Path {
        return getRuntimesRootPath().resolve(type.dirName)
    }

    fun getRuntimeInstallPath(type: RuntimeType, version: String): Path {
        return getRuntimeTypeRootPath(type).resolve(version.trim())
    }

    private fun getRuntimeStorageRootPath(): Path {
        return filesRootPath ?: getRuntimesRootPath().parent ?: getRuntimesRootPath()
    }

    // ====== Installed Runtimes ======

    fun getInstalledRuntimes(type: RuntimeType): List<InstalledRuntime> {
        val typeRootPath = getRuntimeTypeRootPath(type)
        if (!typeRootPath.toFile().exists() || !typeRootPath.toFile().isDirectory) return emptyList()

        return typeRootPath.toFile().listFiles()
            ?.filter { it.isDirectory }
            ?.filter { isRuntimeLayoutValid(type, it.toPath()) }
            ?.map {
                InstalledRuntime(
                    type = type,
                    version = it.name,
                    rootPath = it.toPath()
                )
            }
            ?.sortedWith { left, right -> compareVersions(right.version, left.version) }
            ?: emptyList()
    }

    fun getInstalledVersions(type: RuntimeType): List<String> {
        return getInstalledRuntimes(type).map { it.version }
    }

    fun getSelectedRuntime(type: RuntimeType): InstalledRuntime? {
        val installed = getInstalledRuntimes(type)
        if (installed.isEmpty()) return null

        val selectedVersion = getSelectedRuntimeVersion(type)
        return installed.firstOrNull { it.version == selectedVersion } ?: installed.first()
    }

    fun getSelectedRuntimeVersion(type: RuntimeType): String? {
        val prefs = getSharedPreferences()
        val key = when (type) {
            RuntimeType.DOTNET -> SELECTED_DOTNET_RUNTIME_VERSION
            RuntimeType.BOX64 -> SELECTED_BOX64_RUNTIME_VERSION
        }
        return prefs.getString(key, null)
    }

    suspend fun setSelectedRuntimeVersion(type: RuntimeType, version: String) {
        val normalizedVersion = version.trim()
        val prefs = getSharedPreferences()
        val key = when (type) {
            RuntimeType.DOTNET -> SELECTED_DOTNET_RUNTIME_VERSION
            RuntimeType.BOX64 -> SELECTED_BOX64_RUNTIME_VERSION
        }
        prefs.edit {putString(key, normalizedVersion)}
    }

    // ====== Detection ======

    fun detectDotNetRuntimeVersion(runtimeRootPath: Path): String? {
        val hostVersions = listVersionDirectories(runtimeRootPath.resolve("host").resolve("fxr"))
        val sharedVersions = listVersionDirectories(
            runtimeRootPath.resolve("shared").resolve("Microsoft.NETCore.App")
        )

        val sharedVersionNames = sharedVersions.map { it.name }.toSet()
        val common = hostVersions.filter { it.name in sharedVersionNames }.map { it.name }

        return when {
            common.isNotEmpty() -> common.maxWithOrNull(::compareVersions)
            sharedVersions.isNotEmpty() -> sharedVersions.map { it.name }.maxWithOrNull(::compareVersions)
            hostVersions.isNotEmpty() -> hostVersions.map { it.name }.maxWithOrNull(::compareVersions)
            else -> null
        }
    }

    // ====== Validation ======

    private fun isRuntimeLayoutValid(type: RuntimeType, runtimeRootPath: Path): Boolean {
        return when (type) {
            RuntimeType.DOTNET -> isDotNetLayoutValid(runtimeRootPath)
            RuntimeType.BOX64 -> hasAnyChildren(runtimeRootPath)
        }
    }

    private fun isDotNetLayoutValid(runtimeRootPath: Path): Boolean {
        val version = detectDotNetRuntimeVersion(runtimeRootPath) ?: return false
        val requiredPaths = listOf(
            runtimeRootPath.resolve("host").resolve("fxr").resolve(version).resolve("libhostfxr.so"),
            runtimeRootPath.resolve("shared").resolve("Microsoft.NETCore.App").resolve(version).resolve("libcoreclr.so"),
            runtimeRootPath.resolve("shared").resolve("Microsoft.NETCore.App").resolve(version).resolve("libclrjit.so"),
            runtimeRootPath.resolve("shared").resolve("Microsoft.NETCore.App").resolve(version).resolve("libhostpolicy.so")
        )
        return requiredPaths.all { it.toFile().exists() }
    }

    private fun hasAnyChildren(runtimeRootPath: Path): Boolean {
        val file = runtimeRootPath.toFile()
        if (!file.exists() || !file.isDirectory) return false
        return file.listFiles()?.isNotEmpty() == true
    }

    // ====== Helper Methods ======

    private fun listVersionDirectories(path: Path): List<Path> {
        val file = path.toFile()
        if (!file.exists() || !file.isDirectory) return emptyList()
        return file.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.toPath() }
            ?: emptyList()
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

    private fun getSharedPreferences(): android.content.SharedPreferences {
        // 使用 Application 级上下文，避免依赖 MainActivity 静态字段
        // （该字段在 MainActivity 未创建的场景下为 null，如交互式预览）
        return com.app.ralaunch.RaLaunchApp.getAppContext()
            .getSharedPreferences(AppConstants.PREFS_NAME, 0)
    }

    // ====== Migration ======

    fun migrateLegacyInstallations() {
        if (legacyDotnetMigrationChecked) return

        synchronized(migrationLock) {
            if (legacyDotnetMigrationChecked) return
            migrateLegacyDotnetIfNeeded()
            legacyDotnetMigrationChecked = true
        }
    }

    private fun migrateLegacyDotnetIfNeeded() {
        val legacyRootPath = legacyDotnetRootPath ?: return
        val legacyFile = legacyRootPath.toFile()

        if (!legacyFile.exists() || !legacyFile.isDirectory) return

        if (!isRuntimeLayoutValid(RuntimeType.DOTNET, legacyRootPath)) {
            return
        }

        val version = detectDotNetRuntimeVersion(legacyRootPath)
        if (version.isNullOrBlank()) {
            return
        }

        val targetPath = getRuntimeInstallPath(RuntimeType.DOTNET, version)
        val targetFile = targetPath.toFile()
        targetFile.parentFile?.mkdirs()

        when {
            targetFile.exists() && isRuntimeLayoutValid(RuntimeType.DOTNET, targetPath) -> {
                FileUtils.deleteDirectoryRecursivelyWithinRoot(legacyFile, getRuntimeStorageRootPath().toFile())
            }
            targetFile.exists() -> {
                return
            }
            else -> {
                legacyFile.renameTo(targetFile)
            }
        }

        if (getSelectedRuntimeVersion(RuntimeType.DOTNET).isNullOrBlank()) {
            runBlocking {
                setSelectedRuntimeVersion(RuntimeType.DOTNET, version)
            }
        }
    }
}