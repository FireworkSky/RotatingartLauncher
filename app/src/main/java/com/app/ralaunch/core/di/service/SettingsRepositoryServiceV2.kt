package com.app.ralaunch.core.di.service

import com.app.ralaunch.core.di.contract.ISettingsRepositoryServiceV2
import com.app.ralaunch.core.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * 设置仓库实现（V2）
 *
 * 单一持久化来源：JSON 文件（settings.json）。
 */
class SettingsRepositoryServiceV2(
    private val storagePathsProvider: StoragePathsProviderServiceV1
) : ISettingsRepositoryServiceV2 {

    private val writeMutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val settingsFilePathFull = Path(storagePathsProvider.settingsFilePathFull())
    @Volatile
    private var currentSettings: AppSettings = loadSettingsFromDisk()
    private val _settings = MutableStateFlow(currentSettings.copy())

    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override suspend fun getSettingsSnapshot(): AppSettings = currentSettings.copy()

    override suspend fun updateSettings(settings: AppSettings) {
        writeMutex.withLock {
            val persisted = withContext(Dispatchers.IO) { persistSettings(settings.copy()) }
            currentSettings = persisted
            _settings.value = persisted.copy()
        }
    }

    override suspend fun update(block: AppSettings.() -> Unit) {
        writeMutex.withLock {
            val updated = currentSettings.copy().apply(block)
            val persisted = withContext(Dispatchers.IO) { persistSettings(updated) }
            currentSettings = persisted
            _settings.value = persisted.copy()
        }
    }

    override suspend fun resetToDefaults() {
        updateSettings(AppSettings.Default)
    }

    private fun loadSettingsFromDisk(): AppSettings {
        return runCatching {
            ensureParentDirectory()
            if (!settingsFilePathFull.exists()) return@runCatching AppSettings.Default

            val raw = settingsFilePathFull.readText()
            json.decodeFromString<AppSettings>(raw)
        }.getOrElse {
            backupCorruptedFile()
            AppSettings.Default
        }
    }

    private fun persistSettings(settings: AppSettings): AppSettings {
        ensureParentDirectory()
        val serialized = json.encodeToString(settings)

        val tempPathFull = settingsFilePathFull.resolveSibling("${settingsFilePathFull.name}.tmp")
        tempPathFull.writeText(serialized)
        tempPathFull.moveTo(settingsFilePathFull, overwrite = true)
        return settings
    }

    private fun ensureParentDirectory() {
        settingsFilePathFull.parent?.createDirectories()
    }

    private fun backupCorruptedFile() {
        runCatching {
            if (!settingsFilePathFull.exists()) return
            val backupPathFull = settingsFilePathFull.resolveSibling(
                "${settingsFilePathFull.name}.corrupt.${System.currentTimeMillis()}"
            )
            settingsFilePathFull.moveTo(backupPathFull, overwrite = true)
        }
    }
}
