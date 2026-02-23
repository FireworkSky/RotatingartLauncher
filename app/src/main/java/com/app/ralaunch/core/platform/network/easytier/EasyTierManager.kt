package com.app.ralaunch.core.platform.network.easytier

import android.content.Context
import android.util.Log
import com.app.ralaunch.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.java.KoinJavaComponent

/**
 * 网络节点信息
 */
data class NetworkPeerInfo(
    val id: String,
    val virtualIp: String,
    val hostname: String,
    val latency: Int?,
    val tunnelProto: String,
    val natType: String,
    val lossRate: Float = 0f
)

/**
 * 网络实例状态
 */
data class NetworkInstanceInfo(
    val instanceName: String,
    val running: Boolean,
    val virtualIp: String?,
    val peers: List<NetworkPeerInfo>,
    val errorMsg: String?
)

/**
 * EasyTier 连接状态
 */
enum class EasyTierConnectionState {
    DISCONNECTED,
    CONNECTING,
    FINDING_HOST,  // 加入者：正在寻找房主
    CONNECTED,
    ERROR
}

/**
 * EasyTier 管理类
 * 负责管理 EasyTier 实例的生命周期、监控网络状态
 */
class EasyTierManager {
    
    companion object {
        private const val TAG = "EasyTierManager"
        private const val MONITOR_INTERVAL = 3000L // 3秒监控间隔
        
        // 游戏端口配置
        const val TERRARIA_PORT = 7777
        const val STARDEW_VALLEY_PORT = 24642
        const val MINECRAFT_PORT = 25565
        
        // 房主固定 IP（参考 Terracotta 使用 10.144.144.1）
        const val HOST_IP = "10.126.126.1"
        const val HOST_IP_CIDR = "10.126.126.1/24"  // CIDR 格式，EasyTier 配置需要
        
        // 公共服务器列表
        // 注意：端口 11010 在部分 ISP 下被封，优先使用 54321 端口
        private val PUBLIC_SERVERS = listOf(
            "tcp://public2.easytier.cn:54321"
        )
        
        @Volatile
        private var instance: EasyTierManager? = null
        
        fun getInstance(): EasyTierManager {
            return instance ?: synchronized(this) {
                instance ?: EasyTierManager().also { instance = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    
    // 状态流
    private val _connectionState = MutableStateFlow(EasyTierConnectionState.DISCONNECTED)
    val connectionState: StateFlow<EasyTierConnectionState> = _connectionState.asStateFlow()
    
    private val _virtualIp = MutableStateFlow<String?>(null)
    val virtualIp: StateFlow<String?> = _virtualIp.asStateFlow()
    
    private val _peers = MutableStateFlow<List<NetworkPeerInfo>>(emptyList())
    val peers: StateFlow<List<NetworkPeerInfo>> = _peers.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private var currentInstanceName: String? = null
    private var currentNetworkName: String? = null
    private var currentNetworkSecret: String? = null
    private var currentConfig: String? = null
    private var isCurrentHost: Boolean = false
    private var hostFound: Boolean = false
    
    /**
     * 检查 EasyTier 是否可用
     */
    fun isAvailable(): Boolean = EasyTierJNI.isAvailable()
    
    /**
     * 获取不可用原因
     */
    fun getUnavailableReason(): String {
        val appContext: Context = KoinJavaComponent.get(Context::class.java)
        return EasyTierJNI.getLoadError()
            ?: appContext.getString(R.string.easytier_jni_not_loaded)
    }
    
    /**
     * 连接到 EasyTier 网络
     * no_tun 模式下不需要 VPN/TUN，直接通过端口转发连接
     *
     * @param networkName 网络名称（房间名）
     * @param networkSecret 网络密钥（房间密码）
     * @param isHost 是否是房主（创建房间）
     * @param instanceName 实例名称（可选，默认自动生成）
     */
    suspend fun connect(
        networkName: String,
        networkSecret: String,
        isHost: Boolean = false,
        instanceName: String = "ral_multiplayer"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val appContext: Context = KoinJavaComponent.get(Context::class.java)
        
        if (!EasyTierJNI.isAvailable()) {
            val error = appContext.getString(
                R.string.easytier_jni_not_loaded_with_reason,
                EasyTierJNI.getLoadError() ?: appContext.getString(R.string.common_unknown_error)
            )
            _errorMessage.value = error
            _connectionState.value = EasyTierConnectionState.ERROR
            return@withContext Result.failure(Exception(error))
        }
        
        try {
            _connectionState.value = EasyTierConnectionState.CONNECTING
            _errorMessage.value = null
            hostFound = false
            isCurrentHost = isHost
            
            // 构建 TOML 配置
            // no_tun 模式：房主和加入者都在首次启动时完整配置
            // 加入者直接配置端口转发，无需等待发现房主后再重启
            val config = buildConfig(
                instanceName = instanceName,
                networkName = networkName,
                networkSecret = networkSecret,
                isHost = isHost,
                withPortForward = true  // 加入者始终启用端口转发
            )
            
            Log.d(TAG, "Starting EasyTier with config:\n$config")
            
            // 解析配置
            val parseResult = EasyTierJNI.parseConfig(config)
            if (parseResult != 0) {
                val error = EasyTierJNI.getLastError()
                    ?: appContext.getString(R.string.easytier_error_config_parse_failed)
                throw Exception(error)
            }
            
            // 启动网络实例
            val runResult = EasyTierJNI.runNetworkInstance(config)
            if (runResult != 0) {
                val error = EasyTierJNI.getLastError()
                    ?: appContext.getString(R.string.easytier_error_instance_start_failed)
                throw Exception(error)
            }
            
            currentInstanceName = instanceName
            currentNetworkName = networkName
            currentNetworkSecret = networkSecret
            currentConfig = config
            
            // 房主直接进入 CONNECTED 状态
            // 加入者进入 FINDING_HOST 状态，等待发现房主
            _connectionState.value = if (isHost) {
                EasyTierConnectionState.CONNECTED
            } else {
                EasyTierConnectionState.FINDING_HOST
            }
            
            // no_tun 模式下不需要 VPN/TUN，跳过 setTunFd
            // 端口转发由 EasyTier 内部通过应用层 socket 实现
            Log.d(TAG, "Running in no_tun mode, skipping VPN/TUN setup")
            
            // 启动监控
            startMonitoring()
            
            if (isHost) {
                Log.i(TAG, "EasyTier host connected to network: $networkName")
            } else {
                Log.i(TAG, "EasyTier guest connected to network: $networkName, port forwarding active")
            }
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to EasyTier", e)
            _connectionState.value = EasyTierConnectionState.ERROR
            _errorMessage.value = e.message
            Result.failure(e)
        }
    }
    
    /**
     * 断开 EasyTier 网络
     * no_tun 模式下不需要停止 VPN 服务
     */
    fun disconnect(context: Context) {
        scope.launch {
            disconnectInternal()
        }
    }
    
    /**
     * 断开 EasyTier 网络（挂起函数版本）
     */
    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        disconnectInternal()
    }
    
    private suspend fun disconnectInternal(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopMonitoring()
            
            if (EasyTierJNI.isAvailable()) {
                EasyTierJNI.stopAllInstances()
            }
            
            currentInstanceName = null
            currentNetworkName = null
            currentNetworkSecret = null
            currentConfig = null
            isCurrentHost = false
            hostFound = false
            _connectionState.value = EasyTierConnectionState.DISCONNECTED
            _virtualIp.value = null
            _peers.value = emptyList()
            _errorMessage.value = null
            
            Log.i(TAG, "EasyTier disconnected")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect from EasyTier", e)
            _errorMessage.value = e.message
            Result.failure(e)
        }
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
        if (_connectionState.value == EasyTierConnectionState.ERROR) {
            _connectionState.value = EasyTierConnectionState.DISCONNECTED
        }
    }
    
    /**
     * 构建 TOML 配置（参考 Terracotta 实现）
     * 参考: D:\RotatingartLauncher\Terracotta-master\src\easytier\linkage_impl.rs
     * 
     * @param isHost 是否是房主
     * @param withPortForward 是否配置端口转发（仅对加入者有效）
     * @param gamePorts 需要暴露/转发的游戏端口列表（如 7777 for Terraria）
     */
    private fun buildConfig(
        instanceName: String,
        networkName: String,
        networkSecret: String,
        isHost: Boolean,
        withPortForward: Boolean = true,
        gamePorts: List<Int> = listOf(TERRARIA_PORT, STARDEW_VALLEY_PORT)
    ): String = EasyTierConfigBuilder.buildConfig(
        instanceName = instanceName,
        networkName = networkName,
        networkSecret = networkSecret,
        isHost = isHost,
        withPortForward = withPortForward,
        gamePorts = gamePorts,
        publicServers = PUBLIC_SERVERS
    )
    
    /**
     * 启动网络状态监控
     */
    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            val validStates = setOf(
                EasyTierConnectionState.CONNECTED,
                EasyTierConnectionState.FINDING_HOST
            )
            while (isActive && _connectionState.value in validStates) {
                try {
                    updateNetworkStatus()
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating network status", e)
                }
                delay(MONITOR_INTERVAL)
            }
        }
    }
    
    /**
     * 停止监控
     */
    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }
    
    /**
     * 更新网络状态
     */
    private fun updateNetworkStatus() {
        if (!EasyTierJNI.isAvailable()) return
        
        try {
            val infosJson = EasyTierJNI.collectNetworkInfos()
            if (infosJson.isNullOrEmpty() || infosJson.length < 50) return
            
            val networkInfo = parseNetworkInfo(infosJson)
            if (networkInfo.isNullOrEmpty()) return
            
            // EasyTier 返回的 map key 使用的是 network_name 而不是 instance_name
            val instanceInfo = networkInfo[currentNetworkName]
                ?: networkInfo[currentInstanceName]
                ?: networkInfo.values.firstOrNull { it.running }
                ?: networkInfo.values.firstOrNull()
                ?: return
            
            if (!instanceInfo.running) {
                Log.w(TAG, "EasyTier instance not running: ${instanceInfo.errorMsg}")
                _connectionState.value = EasyTierConnectionState.ERROR
                _errorMessage.value = instanceInfo.errorMsg
                return
            }
            
            // 更新虚拟 IP
            instanceInfo.virtualIp?.let { ip ->
                if (_virtualIp.value != ip) {
                    _virtualIp.value = ip
                    Log.i(TAG, "Virtual IP updated: $ip")
                }
            }
            
            // 更新节点列表
            _peers.value = instanceInfo.peers
            
            Log.d(TAG, "Network status: IP=${instanceInfo.virtualIp}, peers=${instanceInfo.peers.size}")
            
            // 如果是加入者且在 FINDING_HOST 状态，检测房主
            if (!isCurrentHost && _connectionState.value == EasyTierConnectionState.FINDING_HOST) {
                checkAndConnectToHost(instanceInfo.peers)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse network info", e)
        }
    }
    
    /**
     * 检测房主并切换到 CONNECTED 状态
     * 端口转发已在首次启动时配置，不需要重启实例
     */
    private fun checkAndConnectToHost(peers: List<NetworkPeerInfo>) {
        // 检查是否有房主（hostname 包含 "host" 或 IP 是 HOST_IP）
        val hostPeer = peers.find { peer ->
            peer.hostname.lowercase().contains("host") || 
            peer.virtualIp == HOST_IP ||
            peer.virtualIp.startsWith(HOST_IP)
        }
        
        if (hostPeer != null && !hostFound) {
            Log.i(TAG, "Host found! hostname=${hostPeer.hostname}, ip=${hostPeer.virtualIp}")
            hostFound = true
            _connectionState.value = EasyTierConnectionState.CONNECTED
        }
    }
    
    // restartWithPortForward 已不再需要 —— no_tun 模式下加入者首次启动即配置端口转发
    
    private fun parseNetworkInfo(jsonString: String): Map<String, NetworkInstanceInfo>? =
        EasyTierJsonParser.parseNetworkInfo(jsonString)
    
    /**
     * 销毁管理器
     */
    fun destroy() {
        scope.launch {
            disconnect()
        }
        scope.cancel()
        instance = null
    }
}
