package com.app.ralaunch.shared.data.repository

import com.app.ralaunch.shared.data.local.StoragePathsProvider
import com.app.ralaunch.shared.domain.model.AppSettings
import com.app.ralaunch.shared.domain.model.FnaRenderer
import com.app.ralaunch.shared.domain.repository.SettingsRepositoryV2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
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
class SettingsRepositoryImpl(
    private val storagePathsProvider: StoragePathsProvider
) : SettingsRepositoryV2 {

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
            if (!settingsFilePathFull.exists()) return@runCatching AppSettings.Default

            val raw = settingsFilePathFull.readText()
            val decoded = json.decodeFromString<AppSettings>(raw)
            decoded.copy(fnaRenderer = normalizeRendererValue(decoded.fnaRenderer))
        }.getOrElse {
            backupCorruptedFile()
            AppSettings.Default
        }
    }

    private fun persistSettings(settings: AppSettings): AppSettings {
        ensureParentDirectory()
        val normalized = settings.copy(fnaRenderer = normalizeRendererValue(settings.fnaRenderer))
        val serialized = json.encodeToString(normalized)

        val tempPathFull = settingsFilePathFull.resolveSibling("${settingsFilePathFull.name}.tmp")
        tempPathFull.writeText(serialized)
        tempPathFull.moveTo(settingsFilePathFull, overwrite = true)
        return normalized
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

    private fun normalizeRendererValue(value: String?): String {
        if (value.isNullOrBlank()) return FnaRenderer.AUTO.value
        return when (value.lowercase()) {
            "自动", "自动选择", "auto" -> FnaRenderer.AUTO.value
            "native", "native opengl es 3", "opengl", "opengl es", "opengles3", "opengl_native" -> FnaRenderer.NATIVE.value
            "gl4es", "opengl_gl4es" -> FnaRenderer.GL4ES.value
            "gl4es+angle" -> FnaRenderer.GL4ES_ANGLE.value
            "mobileglues" -> FnaRenderer.MOBILEGLUES.value
            "angle" -> FnaRenderer.ANGLE.value
            "zink", "vulkan" -> FnaRenderer.ZINK.value
            else -> value
        }
    }
}
