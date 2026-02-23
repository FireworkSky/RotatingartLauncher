package com.app.ralaunch.shared.core.data.service

import android.content.Context
import com.app.ralaunch.shared.core.model.domain.GameItem
import com.app.ralaunch.shared.core.contract.service.*
import com.app.ralaunch.shared.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.io.File

/**
 * Android 平台游戏启动服务实现
 * 
 * 包装现有的 GameLauncher 功能，提供统一的接口
 */
class AndroidGameLaunchService(
    private val context: Context
) : GameLaunchService {
    
    companion object {
        private const val TAG = "AndroidGameLaunchService"
    }
    
    private val _launchState = MutableStateFlow<LaunchState>(LaunchState.Idle)
    override val launchState: Flow<LaunchState> = _launchState.asStateFlow()
    
    private var lastError: String? = null
    
    override suspend fun canLaunch(game: GameItem): String? = withContext(Dispatchers.IO) {
        // 检查游戏路径是否存在
        val executablePath = game.gameExePathFull
        if (executablePath == null) {
            return@withContext getString(Res.string.shared_game_launch_executable_path_empty)
        }
        if (executablePath.isBlank()) {
            return@withContext getString(Res.string.shared_game_launch_executable_path_not_set)
        }
        
        val executableFile = File(executablePath)
        if (!executableFile.exists()) {
            return@withContext getString(
                Res.string.shared_game_launch_executable_not_found,
                executablePath
            )
        }
        
        null // 可以启动
    }
    
    override suspend fun launch(game: GameItem, config: LaunchConfig): LaunchResult = withContext(Dispatchers.IO) {
        try {
            _launchState.value = LaunchState.Preparing(
                getString(Res.string.shared_game_launch_preparing_environment)
            )
            
            // 检查是否可以启动
            val canLaunchError = canLaunch(game)
            if (canLaunchError != null) {
                lastError = canLaunchError
                _launchState.value = LaunchState.Finished(LaunchResult.Error(canLaunchError))
                return@withContext LaunchResult.Error(canLaunchError)
            }
            
            _launchState.value = LaunchState.Launching(game)
            
            // 启动游戏
            val result = launchGame(game, config)
            
            _launchState.value = LaunchState.Finished(result)
            result
        } catch (e: Exception) {
            lastError = e.message
            val errorResult = LaunchResult.Error(
                e.message ?: getString(Res.string.common_unknown_error),
                e
            )
            _launchState.value = LaunchState.Finished(errorResult)
            errorResult
        }
    }
    
    private suspend fun launchGame(game: GameItem, config: LaunchConfig): LaunchResult {
        return try {
            val executablePath = game.gameExePathFull
            if (executablePath == null) {
                return LaunchResult.Error(
                    getString(Res.string.shared_game_launch_executable_path_empty)
                )
            }
            if (executablePath.isBlank()) {
                return LaunchResult.Error(
                    getString(Res.string.shared_game_launch_executable_path_not_set)
                )
            }
            
            // 构建参数
            val args = config.customArgs.toTypedArray()
            
            // 调用现有的 GameLauncher
            // 注意：这里我们不直接调用 GameLauncher，而是通过桥接类
            // 实际集成时需要引入 app 模块的依赖或使用反射
            
            // 这里返回占位结果，实际实现需要调用 GameLauncher.launchDotNetAssembly
            _launchState.value = LaunchState.Running(game)
            
            // 模拟返回，实际需要调用真实启动器
            LaunchResult.Success(0)
        } catch (e: Exception) {
            LaunchResult.Error(
                e.message ?: getString(Res.string.shared_game_launch_failed),
                e
            )
        }
    }
    
    override suspend fun stop() {
        // TODO: 实现停止游戏逻辑
        _launchState.value = LaunchState.Finished(LaunchResult.Cancelled)
    }
    
    override fun getLastError(): String? = lastError
    
    override suspend fun isRuntimeInstalled(runtime: String): Boolean = withContext(Dispatchers.IO) {
        when (runtime) {
            RuntimeTypes.DOTNET -> true // 内置
            RuntimeTypes.BOX64 -> {
                // 检查 Box64 rootfs 是否存在
                val rootfsPath = File(context.filesDir, "rootfs")
                rootfsPath.exists() && rootfsPath.isDirectory
            }
            RuntimeTypes.NATIVE -> true // 始终可用
            else -> false
        }
    }
    
    override suspend fun initializeRuntime(runtime: String): Boolean = withContext(Dispatchers.IO) {
        when (runtime) {
            RuntimeTypes.BOX64 -> {
                // 初始化 Box64 环境
                // 实际需要调用 GameLauncher.initializeBox64
                true
            }
            else -> true
        }
    }
}
