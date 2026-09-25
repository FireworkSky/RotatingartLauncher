package com.app.ralaunch.core.di.contract

import java.nio.file.Path

/**
 * 运行时管理服务 V2
 *
 * 统一负责运行时目录发现、版本选择与兼容迁移。
 */
interface IRuntimeManagerServiceV2 {

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

    fun getRuntimesRootPath(): Path
    fun getRuntimeTypeRootPath(type: RuntimeType): Path
    fun getRuntimeInstallPath(type: RuntimeType, version: String): Path
    fun getInstalledRuntimes(type: RuntimeType): List<InstalledRuntime>
    fun getInstalledVersions(type: RuntimeType): List<String>
    fun getSelectedRuntime(type: RuntimeType): InstalledRuntime?
    fun getSelectedRuntimeVersion(type: RuntimeType): String?
    suspend fun setSelectedRuntimeVersion(type: RuntimeType, version: String)
    fun detectDotNetRuntimeVersion(runtimeRootPath: Path): String?
    fun migrateLegacyInstallations()
}
