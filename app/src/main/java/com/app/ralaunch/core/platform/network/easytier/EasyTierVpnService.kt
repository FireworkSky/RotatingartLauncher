package com.app.ralaunch.core.platform.network.easytier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.app.ralaunch.R
import com.easytier.jni.EasyTierJNI
import kotlinx.coroutines.*
import java.io.IOException

/**
 * EasyTier VPN 服务
 * 创建 TUN 接口供 EasyTier 使用
 */
class EasyTierVpnService : VpnService() {

    companion object {
        private const val TAG = "EasyTierVpnService"
        private const val CHANNEL_ID = "easytier_vpn_channel"
        private const val NOTIFICATION_ID = 2001
        
        // 服务动作
        const val ACTION_INIT = "com.app.ralaunch.core.platform.network.easytier.INIT"   // 仅初始化 TUN 接口
        const val ACTION_START = "com.app.ralaunch.core.platform.network.easytier.START"
        const val ACTION_STOP = "com.app.ralaunch.core.platform.network.easytier.STOP"
        
        // 广播动作（用于跨进程通信）
        const val BROADCAST_VPN_READY = "com.app.ralaunch.core.platform.network.easytier.VPN_READY"
        const val BROADCAST_VPN_ERROR = "com.app.ralaunch.core.platform.network.easytier.VPN_ERROR"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_TUN_FD = "tun_fd"
        
        // Intent 参数
        const val EXTRA_CONFIG = "config"
        const val EXTRA_INSTANCE_NAME = "instance_name"
        const val EXTRA_VIRTUAL_IP = "virtual_ip"
        
        @Volatile
        var isRunning = false
            private set
        
        @Volatile
        private var tunFd: Int = -1
        
        /**
         * 获取 TUN 文件描述符
         */
        fun getTunFd(): Int = tunFd
    }
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var instanceName: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INIT -> {
                // 仅初始化模式：创建 TUN 接口，不启动网络实例
                initTunInterface()
            }
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                instanceName = intent.getStringExtra(EXTRA_INSTANCE_NAME)
                val virtualIp = intent.getStringExtra(EXTRA_VIRTUAL_IP) ?: "10.126.126.1"
                
                if (config != null && instanceName != null) {
                    startVpn(config, virtualIp)
                } else {
                    Log.e(TAG, "Missing config or instance name")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    /**
     * 仅初始化 TUN 接口（不启动网络实例）
     */
    private fun initTunInterface() {
        try {
            // 启动前台服务
            startForeground(
                NOTIFICATION_ID,
                createNotification(getString(R.string.easytier_vpn_status_initializing))
            )
            
            // 创建 VPN 接口
            val builder = Builder()
                .setSession("EasyTier")
                .setMtu(1380)
                .addAddress("10.126.126.1", 24)
                .addRoute("10.126.126.0", 24)
            
            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                sendErrorBroadcast(getString(R.string.easytier_vpn_error_create_interface))
                stopSelf()
                return
            }
            
            tunFd = vpnInterface!!.fd
            isRunning = true
            Log.i(TAG, "TUN interface established, fd=$tunFd")
            
            updateNotification(getString(R.string.easytier_vpn_status_ready))
            
            // 发送广播通知已就绪（跨进程通信）
            sendReadyBroadcast(tunFd)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init TUN interface", e)
            sendErrorBroadcast(e.message ?: getString(R.string.common_unknown_error))
            stopSelf()
        }
    }
    
    /**
     * 发送 VPN 就绪广播
     */
    private fun sendReadyBroadcast(fd: Int) {
        val intent = Intent(BROADCAST_VPN_READY).apply {
            setPackage(packageName)
            putExtra(EXTRA_TUN_FD, fd)
        }
        sendBroadcast(intent)
        Log.d(TAG, "VPN ready broadcast sent, fd=$fd")
    }
    
    /**
     * 发送 VPN 错误广播
     */
    private fun sendErrorBroadcast(error: String) {
        val intent = Intent(BROADCAST_VPN_ERROR).apply {
            setPackage(packageName)
            putExtra(EXTRA_ERROR_MESSAGE, error)
        }
        sendBroadcast(intent)
        Log.d(TAG, "VPN error broadcast sent: $error")
    }
    
    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }
    
    private fun startVpn(config: String, virtualIp: String) {
        try {
            // 启动前台服务
            startForeground(
                NOTIFICATION_ID,
                createNotification(getString(R.string.easytier_vpn_status_connecting))
            )
            
            // 创建 VPN 接口
            val builder = Builder()
                .setSession("EasyTier")
                .setMtu(1380)
                .addAddress(virtualIp, 24)
                .addRoute("10.126.126.0", 24) // EasyTier 默认网段
            
            // 允许应用绕过 VPN（可选）
            // builder.addDisallowedApplication(packageName)
            
            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }
            
            val fd = vpnInterface!!.fd
            Log.i(TAG, "VPN interface established, fd=$fd")
            
            // 启动 EasyTier
            scope.launch {
                try {
                    // 先运行网络实例
                    val result = EasyTierJNI.runNetworkInstance(config)
                    if (result != 0) {
                        val error = EasyTierJNI.getLastError()
                        Log.e(TAG, "Failed to run network instance: $error")
                        withContext(Dispatchers.Main) {
                            updateNotification(
                                getString(R.string.easytier_vpn_status_connect_failed, error)
                            )
                        }
                        return@launch
                    }
                    
                    // 设置 TUN fd
                    val fdResult = EasyTierJNI.setTunFd(instanceName!!, fd)
                    if (fdResult != 0) {
                        val error = EasyTierJNI.getLastError()
                        Log.e(TAG, "Failed to set TUN fd: $error")
                        withContext(Dispatchers.Main) {
                            updateNotification(
                                getString(R.string.easytier_vpn_status_tun_set_failed, error)
                            )
                        }
                        return@launch
                    }
                    
                    isRunning = true
                    Log.i(TAG, "EasyTier VPN started successfully")
                    
                    withContext(Dispatchers.Main) {
                        updateNotification(
                            getString(R.string.easytier_vpn_status_connected, virtualIp)
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting EasyTier", e)
                    withContext(Dispatchers.Main) {
                        updateNotification(
                            getString(
                                R.string.easytier_vpn_status_error,
                                e.message ?: getString(R.string.common_unknown_error)
                            )
                        )
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopSelf()
        }
    }
    
    private fun stopVpn() {
        isRunning = false
        tunFd = -1
        
        // 停止 EasyTier 实例
        instanceName?.let {
            try {
                EasyTierJNI.stopAllInstances()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping EasyTier", e)
            }
        }
        
        // 关闭 VPN 接口
        vpnInterface?.let {
            try {
                it.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing VPN interface", e)
            }
        }
        vpnInterface = null
        instanceName = null
        
        Log.i(TAG, "VPN stopped")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.easytier_vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.easytier_vpn_channel_description)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(status: String): Notification {
        val stopIntent = Intent(this, EasyTierVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.easytier_vpn_notification_title))
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .addAction(0, getString(R.string.multiplayer_disconnect), stopPendingIntent)
            .build()
    }
    
    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(status))
    }
}
