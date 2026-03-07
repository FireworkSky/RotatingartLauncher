package com.app.ralaunch.shared.core.data.local

import com.app.ralaunch.shared.core.platform.AppConstants
import com.app.ralaunch.shared.core.data.repository.ControlLayoutStorage
import com.app.ralaunch.shared.core.model.domain.ControlLayout
import com.app.ralaunch.shared.core.model.domain.ControlPackManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File  // Thay kotlin.io.path.*

class CommonControlLayoutStorage(
    private val pathsProvider: StoragePathsProvider
) : ControlLayoutStorage {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override suspend fun loadAllLayouts(): List<ControlLayout> = withContext(Dispatchers.Default) {
        layoutsDirFull.mkdirs()
        layoutsDirFull.listFiles()
            ?.filter { it.isFile }
            ?.filter { it.name.endsWith(".json") }
            ?.filter { it.name != AppConstants.Files.CONTROL_LAYOUT_STATE }
            ?.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<ControlLayout>(file.readText())
                }.getOrNull()
            } ?: emptyList()
    }

    override suspend fun saveLayout(layout: ControlLayout) = withContext(Dispatchers.Default) {
        layoutsDirFull.mkdirs()
        layoutFileFull(layout.id).writeText(json.encodeToString(layout))
    }

    override suspend fun deleteLayout(id: String) = withContext(Dispatchers.Default) {
        val file = layoutFileFull(id)
        if (file.exists()) file.delete()
        Unit
    }

    override suspend fun loadCurrentLayoutId(): String? = withContext(Dispatchers.Default) {
        if (!layoutStateFull.exists()) return@withContext null
        runCatching {
            json.decodeFromString<ControlLayoutState>(layoutStateFull.readText()).currentLayoutId
        }.getOrNull()
    }

    override suspend fun saveCurrentLayoutId(id: String) = withContext(Dispatchers.Default) {
        layoutsDirFull.mkdirs()
        val state = ControlLayoutState(currentLayoutId = id)
        layoutStateFull.writeText(json.encodeToString(state))
    }

    override suspend fun importPack(packPath: String): Result<ControlLayout> = withContext(Dispatchers.Default) {
        runCatching {
            val packFile = File(packPath)
            if (!packFile.exists()) {
                throw IllegalArgumentException("File not found: $packPath")
            }
            val layout = json.decodeFromString<ControlLayout>(packFile.readText())
            saveLayout(layout)
            layout
        }
    }

    override suspend fun exportLayout(layout: ControlLayout, outputPath: String): Result<String> =
        withContext(Dispatchers.Default) {
            runCatching {
                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()
                outputFile.writeText(json.encodeToString(layout))
                outputPath
            }
        }

    override suspend fun fetchRemotePacks(): Result<List<ControlPackManifest>> {
        return Result.success(emptyList())
    }

    private val layoutsDirFull
        get() = File(pathsProvider.controlLayoutsDirPathFull())

    private fun layoutFileFull(layoutId: String) =
        File(layoutsDirFull, "$layoutId.json")

    private val layoutStateFull
        get() = File(layoutsDirFull, AppConstants.Files.CONTROL_LAYOUT_STATE)
}

@Serializable
private data class ControlLayoutState(
    val currentLayoutId: String? = null
)
