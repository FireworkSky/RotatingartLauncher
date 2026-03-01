package com.app.ralaunch.shared.core.data.repository

import com.app.ralaunch.shared.core.data.local.StoragePathsProvider
import com.app.ralaunch.shared.core.model.domain.AppSettings
import com.app.ralaunch.shared.core.contract.repository.SettingsRepositoryV2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File  // ← Thay kotlin.io.path.* bằng java.io.File

class SettingsRepositoryImpl(
    private val storagePathsProvider: StoragePathsProvider
) : SettingsRepositoryV2 {

    private val writeMutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Dùng java.io.File thay vì kotlin.io.path.Path
    private val settingsFile = File(storagePathsProvider.settingsFilePathFull())

    @Volatile
    private var currentSettings: AppSettings = loadSettingsFromDisk()
    private val _settings = MutableStateFlow(currentSettings.copy())

    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override suspend fun getSettingsSnapshot(): AppSettings = currentSettings.copy()

    override suspend fun updateSettings(settings: AppSettings) {
        writeMutex.withLock {
            val persisted = persistSettings(settings.copy())
            currentSettings = persisted
            _settings.value = persisted.copy()
        }
    }

    override suspend fun update(block: AppSettings.() -> Unit) {
        writeMutex.withLock {
            val updated = currentSettings.copy().apply(block)
            val persisted = persistSettings(updated)
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
            if (!settingsFile.exists()) return@runCatching AppSettings.Default

            val raw = settingsFile.readText()
            json.decodeFromString<AppSettings>(raw)
        }.getOrElse {
            backupCorruptedFile()
            AppSettings.Default
        }
    }

    private fun persistSettings(settings: AppSettings): AppSettings {
        ensureParentDirectory()
        val serialized = json.encodeToString(settings)

        // Dùng File thay vì Path
        val tempFile = File(settingsFile.parent, "${settingsFile.name}.tmp")
        tempFile.writeText(serialized)

        // Atomic move
        if (!tempFile.renameTo(settingsFile)) {
            // Nếu renameTo thất bại (khác partition), dùng copy + delete
            settingsFile.writeText(serialized)
            tempFile.delete()
        }

        return settings
    }

    private fun ensureParentDirectory() {
        settingsFile.parentFile?.mkdirs()
    }

    private fun backupCorruptedFile() {
        runCatching {
            if (!settingsFile.exists()) return
            val backupFile = File(
                settingsFile.parent,
                "${settingsFile.name}.corrupt.${System.currentTimeMillis()}"
            )
            // Dùng renameTo thay vì moveTo
            settingsFile.renameTo(backupFile)
        }
    }
}
