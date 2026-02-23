package com.app.ralaunch.shared.core.data.service

import android.content.Context
import com.app.ralaunch.shared.core.contract.service.*
import com.app.ralaunch.shared.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/**
 * Android 平台补丁服务实现
 * 
 * 包装现有的 PatchManager 功能
 */
class AndroidPatchService(
    private val context: Context
) : PatchService {
    
    companion object {
        private const val TAG = "AndroidPatchService"
    }
    
    private val _installedPatches = MutableStateFlow<List<PatchInfo>>(emptyList())
    override val installedPatches: Flow<List<PatchInfo>> = _installedPatches.asStateFlow()

    /**
     * 刷新补丁列表
     */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val patches = getInstalledPatches()
        _installedPatches.value = patches
    }
    
    override suspend fun getInstalledPatches(): List<PatchInfo> = withContext(Dispatchers.IO) {
        // 桥接到 PatchManager.getPatches
        emptyList()
    }
    
    override suspend fun getPatchesForGame(gameId: String): List<PatchInfo> = withContext(Dispatchers.IO) {
        // 桥接到 PatchManager.getPatchesForGame
        val allPatches = getInstalledPatches()
        allPatches.filter { patch ->
            patch.targetGames.isEmpty() || patch.targetGames.contains(gameId)
        }
    }
    
    override suspend fun getPatchInfo(patchId: String): PatchInfo? = withContext(Dispatchers.IO) {
        // 桥接到 PatchManager.getPatch
        null
    }
    
    override suspend fun checkCompatibility(patchId: String, gameId: String): PatchCompatibility = withContext(Dispatchers.IO) {
        val patch = getPatchInfo(patchId)
        if (patch == null) {
            return@withContext PatchCompatibility(
                false,
                getString(Res.string.shared_patch_not_found)
            )
        }
        
        if (patch.targetGames.isEmpty()) {
            return@withContext PatchCompatibility(true)
        }
        
        if (gameId in patch.targetGames) {
            return@withContext PatchCompatibility(true)
        }
        
        PatchCompatibility(
            false,
            getString(Res.string.shared_patch_not_supported_for_game)
        )
    }
    
    override suspend fun enablePatch(patchId: String, gameId: String): Boolean = withContext(Dispatchers.IO) {
        // 桥接到 PatchManager.enablePatch
        false
    }
    
    override suspend fun disablePatch(patchId: String, gameId: String): Boolean = withContext(Dispatchers.IO) {
        // 桥接到 PatchManager.disablePatch
        false
    }
    
    override suspend fun getEnabledPatches(gameId: String): List<PatchInfo> = withContext(Dispatchers.IO) {
        // 桥接到 PatchManager.getEnabledPatches
        emptyList()
    }
    
    override suspend fun installPatch(patchPath: String): Result<PatchInfo> = withContext(Dispatchers.IO) {
        // 桥接到 PatchManager.installPatch
        Result.failure(
            NotImplementedError(
                getString(Res.string.shared_feature_not_implemented)
            )
        )
    }
    
    override suspend fun uninstallPatch(patchId: String): Boolean = withContext(Dispatchers.IO) {
        // 桥接到 PatchManager.uninstallPatch
        false
    }
    
    override suspend fun installBuiltInPatches() = withContext(Dispatchers.IO) {
        // 桥接到 PatchManager.installBuiltInPatches
    }
    
    override fun buildStartupHooksEnvVar(patches: List<PatchInfo>): String? {
        if (patches.isEmpty()) return null
        
        // 构建 DOTNET_STARTUP_HOOKS 环境变量
        // 格式: path1:path2:path3
        return patches
            .mapNotNull { it.installPath }
            .filter { it.isNotBlank() }
            .joinToString(":")
            .takeIf { it.isNotBlank() }
    }
}
