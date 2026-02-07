package com.app.ralaunch.easytier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
        
        // 多个公共服务器，提高连接成功率（参考 Terracotta）
        private val PUBLIC_SERVERS = listOf(
            "tcp://public.easytier.cn:11010",
            "tcp://public.easytier.top:11010",
            "tcp://public2.easytier.cn:54321",
            "tcp://ah.nkbpal.cn:11010",
            "tcp://turn.hb.629957.xyz:11010",
            "tcp://turn.js.629957.xyz:11012",
            "tcp://sh.993555.xyz:11010",
            "tcp://turn.bj.629957.xyz:11010",
            "tcp://et.sh.suhoan.cn:11010",
            "tcp://et-hk.clickor.click:11010"
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
    
    // VPN 初始化状态
    private val _vpnInitialized = MutableStateFlow(false)
    val vpnInitialized: StateFlow<Boolean> = _vpnInitialized.asStateFlow()
    
    // TUN fd（从广播中获取，跨进程）
    private var tunFd: Int = -1
    
    /**
     * 检查 EasyTier 是否可用
     */
    fun isAvailable(): Boolean = EasyTierJNI.isAvailable()
    
    /**
     * 获取不可用原因
     */
    fun getUnavailableReason(): String {
        return EasyTierJNI.getLoadError() ?: "EasyTier JNI 库未加载"
    }
    
    // VPN 广播接收器
    private var vpnReceiver: BroadcastReceiver? = null
    private var pendingVpnReadyCallback: (() -> Unit)? = null
    private var pendingVpnErrorCallback: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * 初始化 VPN 服务（创建 TUN 接口）
     * 必须在创建房间之前调用
     * @param context Android Context
     * @param onReady VPN 就绪回调
     * @param onError 错误回调
     */
    fun initVpnService(context: Context, onReady: () -> Unit, onError: (String) -> Unit) {
        if (_vpnInitialized.value) {
            Log.d(TAG, "VPN already initialized")
            onReady()
            return
        }
        
        // 保存回调
        pendingVpnReadyCallback = onReady
        pendingVpnErrorCallback = onError
        
        // 注册广播接收器（跨进程通信）
        registerVpnReceiver(context)
        
        // 启动 VPN 服务（仅初始化模式）
        val intent = Intent(context, EasyTierVpnService::class.java).apply {
            action = EasyTierVpnService.ACTION_INIT
        }
        context.startService(intent)
        Log.d(TAG, "Starting VPN service initialization...")
        
        // 设置超时（10秒）
        mainHandler.postDelayed({
            if (!_vpnInitialized.value) {
                Log.e(TAG, "VPN initialization timeout")
                unregisterVpnReceiver(context)
                pendingVpnErrorCallback?.invoke("VPN 初始化超时")
                pendingVpnReadyCallback = null
                pendingVpnErrorCallback = null
            }
        }, 10000)
    }
    
    /**
     * 注册 VPN 广播接收器
     */
    private fun registerVpnReceiver(context: Context) {
        if (vpnReceiver != null) {
            try {
                context.unregisterReceiver(vpnReceiver)
            } catch (_: Exception) {}
        }
        
        vpnReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    EasyTierVpnService.BROADCAST_VPN_READY -> {
                        val fd = intent.getIntExtra(EasyTierVpnService.EXTRA_TUN_FD, -1)
                        Log.i(TAG, "Received VPN ready broadcast, tunFd=$fd")
                        tunFd = fd  // 保存 tunFd（跨进程传递）
                        _vpnInitialized.value = true
                        mainHandler.removeCallbacksAndMessages(null) // 取消超时
                        mainHandler.post {
                            pendingVpnReadyCallback?.invoke()
                            pendingVpnReadyCallback = null
                            pendingVpnErrorCallback = null
                        }
                    }
                    EasyTierVpnService.BROADCAST_VPN_ERROR -> {
                        val error = intent.getStringExtra(EasyTierVpnService.EXTRA_ERROR_MESSAGE) ?: "未知错误"
                        Log.e(TAG, "Received VPN error broadcast: $error")
                        _vpnInitialized.value = false
                        mainHandler.removeCallbacksAndMessages(null) // 取消超时
                        mainHandler.post {
                            pendingVpnErrorCallback?.invoke(error)
                            pendingVpnReadyCallback = null
                            pendingVpnErrorCallback = null
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(EasyTierVpnService.BROADCAST_VPN_READY)
            addAction(EasyTierVpnService.BROADCAST_VPN_ERROR)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(vpnReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(vpnReceiver, filter)
        }
        Log.d(TAG, "VPN broadcast receiver registered")
    }
    
    /**
     * 注销 VPN 广播接收器
     */
    private fun unregisterVpnReceiver(context: Context) {
        vpnReceiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.d(TAG, "VPN broadcast receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister VPN receiver", e)
            }
        }
        vpnReceiver = null
    }
    
    /**
     * 检查 VPN 是否已初始化
     */
    fun isVpnInitialized(): Boolean = _vpnInitialized.value
    
    /**
     * 连接到 EasyTier 网络
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
        
        if (!EasyTierJNI.isAvailable()) {
            val error = "EasyTier JNI 库未加载: ${EasyTierJNI.getLoadError()}"
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
            // 加入者：初始连接时不配置端口转发，等发现房主后再重启配置
            val config = buildConfig(
                instanceName = instanceName,
                networkName = networkName,
                networkSecret = networkSecret,
                isHost = isHost,
                withPortForward = isHost  // 房主直接完整配置，加入者先不配置端口转发
            )
            
            Log.d(TAG, "Starting EasyTier with config:\n$config")
            
            // 解析配置
            val parseResult = EasyTierJNI.parseConfig(config)
            if (parseResult != 0) {
                val error = EasyTierJNI.getLastError() ?: "配置解析失败"
                throw Exception(error)
            }
            
            // 启动网络实例
            val runResult = EasyTierJNI.runNetworkInstance(config)
            if (runResult != 0) {
                val error = EasyTierJNI.getLastError() ?: "网络实例启动失败"
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
            
            // 如果 VPN 已初始化，设置 TUN fd
            // #region agent log - 假设P: 检查 VPN/TUN 状态
            Log.w("DEBUG_MULTIPLAYER", "VPN status: initialized=${_vpnInitialized.value}, tunFd=$tunFd, no_tun=true (NoTun mode)")
            // #endregion
            if (_vpnInitialized.value && tunFd >= 0) {
                Log.d(TAG, "Setting TUN fd: $tunFd")
                val fdResult = EasyTierJNI.setTunFd(instanceName, tunFd)
                if (fdResult != 0) {
                    Log.w(TAG, "Failed to set TUN fd: ${EasyTierJNI.getLastError()}")
                    Log.w("DEBUG_MULTIPLAYER", "setTunFd FAILED: ${EasyTierJNI.getLastError()}")
                } else {
                    Log.i(TAG, "TUN fd set successfully: $tunFd")
                    Log.w("DEBUG_MULTIPLAYER", "setTunFd SUCCESS: tunFd=$tunFd")
                }
            } else {
                Log.w(TAG, "VPN not initialized or tunFd invalid: initialized=${_vpnInitialized.value}, tunFd=$tunFd")
                Log.w("DEBUG_MULTIPLAYER", "VPN NOT READY - port forward may not work without TUN")
            }
            
            // 启动监控
            startMonitoring()
            
            if (isHost) {
                Log.i(TAG, "EasyTier host connected successfully to network: $networkName")
            } else {
                Log.i(TAG, "EasyTier guest started, finding host in network: $networkName")
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
     * 断开 EasyTier 网络并停止 VPN 服务
     */
    fun disconnect(context: Context) {
        scope.launch {
            disconnectInternal(context)
        }
    }
    
    /**
     * 断开 EasyTier 网络（挂起函数版本）
     */
    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        disconnectInternalNoVpn()
    }
    
    private suspend fun disconnectInternal(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopMonitoring()
            
            // 注销广播接收器
            withContext(Dispatchers.Main) {
                unregisterVpnReceiver(context)
                mainHandler.removeCallbacksAndMessages(null)
            }
            
            if (EasyTierJNI.isAvailable()) {
                EasyTierJNI.stopAllInstances()
            }
            
            // 停止 VPN 服务
            val intent = Intent(context, EasyTierVpnService::class.java).apply {
                action = EasyTierVpnService.ACTION_STOP
            }
            context.startService(intent)
            
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
            _vpnInitialized.value = false
            tunFd = -1
            pendingVpnReadyCallback = null
            pendingVpnErrorCallback = null
            
            Log.i(TAG, "EasyTier disconnected and VPN stopped")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect from EasyTier", e)
            _errorMessage.value = e.message
            Result.failure(e)
        }
    }
    
    private suspend fun disconnectInternalNoVpn(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopMonitoring()
            
            if (EasyTierJNI.isAvailable()) {
                EasyTierJNI.stopAllInstances()
            }
            
            currentInstanceName = null
            currentNetworkName = null
            currentConfig = null
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
        if (!EasyTierJNI.isAvailable()) {
            Log.w("DEBUG_MULTIPLAYER", "MonitorLoop: JNI not available")
            return
        }
        
        try {
            val infosJson = EasyTierJNI.collectNetworkInfos()
            if (infosJson.isNullOrEmpty() || infosJson.length < 50) {
                Log.w("DEBUG_MULTIPLAYER", "MonitorLoop: empty/short (${infosJson?.length})")
                return
            }
            
            Log.d(TAG, "Network info JSON: $infosJson")
            
            val networkInfo = parseNetworkInfo(infosJson)
            if (networkInfo.isNullOrEmpty()) {
                Log.d(TAG, "No instances found in network info")
                return
            }
            
            // EasyTier 返回的 map key 使用的是 network_name 而不是 instance_name
            // 优先使用 networkName 查找，其次尝试 instanceName
            var instanceInfo = networkInfo[currentNetworkName]
                ?: networkInfo[currentInstanceName]
                ?: networkInfo.values.firstOrNull { it.running }
                ?: networkInfo.values.firstOrNull()
            
            if (instanceInfo != null && instanceInfo.instanceName != currentInstanceName) {
                Log.d(TAG, "Using instance: ${instanceInfo.instanceName} (network: $currentNetworkName)")
            }
            
            if (instanceInfo == null) {
                Log.d(TAG, "No instance found in network info. Available: ${networkInfo.keys}")
                return
            }
            
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
            
            // #region agent log - 精简：只在状态变化时记录
            Log.w("DEBUG_MULTIPLAYER", "Status: ip=${instanceInfo.virtualIp}, peers=${instanceInfo.peers.size}, state=${_connectionState.value}")
            // #endregion
            
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
            Log.w("DEBUG_MULTIPLAYER", "HostFound: ${hostPeer.hostname}, ip=${hostPeer.virtualIp}")
            Log.w("DEBUG_MULTIPLAYER", "Game can now connect to: 127.0.0.1:$TERRARIA_PORT")
            hostFound = true
            
            // 切换到 CONNECTED 状态，端口转发已在启动时配置好
            _connectionState.value = EasyTierConnectionState.CONNECTED
        }
    }
    
    /**
     * 重启 EasyTier 带端口转发配置
     */
    private suspend fun restartWithPortForward() = withContext(Dispatchers.IO) {
        val networkName = currentNetworkName ?: return@withContext
        val networkSecret = currentNetworkSecret ?: return@withContext
        
        // #region agent log - 假设AK: 使用新的实例名称避免冲突
        val newInstanceName = "ral_mp_${System.currentTimeMillis() % 100000}"
        Log.w("DEBUG_MULTIPLAYER", "Restart: newInstance=$newInstanceName, portForward to $HOST_IP:$TERRARIA_PORT")
        // #endregion
        
        Log.i(TAG, "Restarting EasyTier with port forwarding...")
        
        try {
            // 停止当前实例（使用 retainNetworkInstance(emptyArray) 停止所有实例）
            val oldInstanceName = currentInstanceName
            Log.w("DEBUG_MULTIPLAYER", "Stopping old instance: $oldInstanceName")
            EasyTierJNI.stopAllInstances()
            delay(1000) // 等待 1 秒
            
            // 更新实例名称
            currentInstanceName = newInstanceName
            
            // 构建带端口转发的配置
            val config = buildConfig(
                instanceName = newInstanceName,
                networkName = networkName,
                networkSecret = networkSecret,
                isHost = false,
                withPortForward = true  // 现在启用端口转发
            )
            
            Log.d(TAG, "Restarting with config:\n$config")
            // #region agent log - 假设AA: 显示完整 TOML 配置（检查端口转发是否正确生成）
            Log.w("DEBUG_MULTIPLAYER", "=== FULL CONFIG ===\n${config.take(1500)}")
            // #endregion
            
            // #region agent log - 假设AG: 检查配置解析和实例启动
            Log.w("DEBUG_MULTIPLAYER", "=== RESTART: Parsing config...")
            // #endregion
            
            // 解析配置
            val parseResult = EasyTierJNI.parseConfig(config)
            // #region agent log
            Log.w("DEBUG_MULTIPLAYER", "parseConfig result=$parseResult, error=${EasyTierJNI.getLastError()}")
            // #endregion
            if (parseResult != 0) {
                val error = EasyTierJNI.getLastError() ?: "配置解析失败"
                Log.e(TAG, "Failed to parse config: $error")
                Log.w("DEBUG_MULTIPLAYER", "RESTART ABORT: parseConfig failed: $error")
                _errorMessage.value = error
                return@withContext
            }
            
            // #region agent log
            Log.w("DEBUG_MULTIPLAYER", "=== RESTART: Running network instance...")
            // #endregion
            
            // 启动网络实例
            val runResult = EasyTierJNI.runNetworkInstance(config)
            // #region agent log
            Log.w("DEBUG_MULTIPLAYER", "runNetworkInstance result=$runResult, error=${EasyTierJNI.getLastError()}")
            // #endregion
            if (runResult != 0) {
                val error = EasyTierJNI.getLastError() ?: "网络实例启动失败"
                Log.e(TAG, "Failed to run network instance: $error")
                Log.w("DEBUG_MULTIPLAYER", "RESTART ABORT: runNetworkInstance failed: $error")
                _errorMessage.value = error
                return@withContext
            }
            
            currentConfig = config
            
            // #region agent log - 假设AH: 检查实例是否真的在运行
            delay(500)
            val checkInfo = EasyTierJNI.collectNetworkInfos()
            Log.w("DEBUG_MULTIPLAYER", "Post-run check: networkInfo.length=${checkInfo?.length ?: 0}")
            if ((checkInfo?.length ?: 0) < 50) {
                Log.w("DEBUG_MULTIPLAYER", "WARNING: Instance may not be running! Info: $checkInfo")
            }
            // #endregion
            
            // 如果 VPN 已初始化，设置 TUN fd
            if (_vpnInitialized.value && tunFd >= 0) {
                val fdResult = EasyTierJNI.setTunFd(newInstanceName, tunFd)
                // #region agent log
                Log.w("DEBUG_MULTIPLAYER", "setTunFd($newInstanceName) result=$fdResult, error=${EasyTierJNI.getLastError()}")
                // #endregion
                if (fdResult != 0) {
                    Log.w(TAG, "Failed to set TUN fd: ${EasyTierJNI.getLastError()}")
                }
            }
            
            // 切换到 CONNECTED 状态
            _connectionState.value = EasyTierConnectionState.CONNECTED
            Log.i(TAG, "EasyTier restarted with port forwarding, now CONNECTED")
            
            // #region agent log - 假设AD: 等待网络重新连接
            Log.w("DEBUG_MULTIPLAYER", "=== RESTART COMPLETE ===")
            Log.w("DEBUG_MULTIPLAYER", "Waiting for network to reconnect...")
            
            // 等待网络重新连接（最多 10 秒）
            var networkReady = false
            for (i in 1..10) {
                delay(1000)
                val info = EasyTierJNI.collectNetworkInfos()
                val infoLen = info?.length ?: 0
                Log.w("DEBUG_MULTIPLAYER", "Wait[$i/10]: networkInfo.length=$infoLen")
                if (infoLen > 100) {  // 有效的网络信息通常大于 100 字节
                    networkReady = true
                    Log.w("DEBUG_MULTIPLAYER", "Network reconnected! Starting port forward...")
                    break
                }
            }
            
            if (!networkReady) {
                Log.w("DEBUG_MULTIPLAYER", "WARNING: Network not fully reconnected after 10s")
            }
            
            Log.w("DEBUG_MULTIPLAYER", "Game should connect to: 127.0.0.1:$TERRARIA_PORT")
            Log.w("DEBUG_MULTIPLAYER", "Port forward destination: $HOST_IP:$TERRARIA_PORT")
            
            // 重新启动监控
            startMonitoring()
            Log.w("DEBUG_MULTIPLAYER", "Monitoring restarted")
            // #endregion
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart with port forwarding", e)
            // #region agent log - 假设C: 记录重启失败
            Log.w("DEBUG_MULTIPLAYER", "=== RESTART FAILED: ${e.message} ===")
            // #endregion
            _errorMessage.value = e.message
        }
    }
    
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
