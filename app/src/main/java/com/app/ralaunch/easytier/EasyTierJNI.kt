package com.app.ralaunch.easytier

import android.util.Log

/**
 * EasyTier JNI 包装类
 * 封装对原生 JNI 类的调用
 */
object EasyTierJNI {
    
    private const val TAG = "EasyTierJNI"
    
    private var isLoaded = false
    private var loadError: String? = null

    init {
        try {
            // 触发原生 JNI 类的加载
            com.easytier.jni.EasyTierJNI.getLastError()
            isLoaded = true
            Log.i(TAG, "EasyTier native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            isLoaded = false
            loadError = e.message
            Log.w(TAG, "EasyTier native library not found: ${e.message}")
        } catch (e: Exception) {
            isLoaded = false
            loadError = e.message
            Log.w(TAG, "EasyTier native library load error: ${e.message}")
        }
    }
    
    /**
     * 检查 JNI 库是否已加载
     */
    fun isAvailable(): Boolean = isLoaded
    
    /**
     * 获取加载错误信息
     */
    fun getLoadError(): String? = loadError

    /**
     * 设置 TUN 文件描述符
     */
    @JvmStatic
    fun setTunFd(instanceName: String, fd: Int): Int {
        return if (isLoaded) {
            com.easytier.jni.EasyTierJNI.setTunFd(instanceName, fd)
        } else -1
    }

    /**
     * 解析配置字符串
     */
    @JvmStatic
    fun parseConfig(config: String): Int {
        return if (isLoaded) {
            com.easytier.jni.EasyTierJNI.parseConfig(config)
        } else -1
    }

    /**
     * 运行网络实例
     */
    @JvmStatic
    fun runNetworkInstance(config: String): Int {
        return if (isLoaded) {
            com.easytier.jni.EasyTierJNI.runNetworkInstance(config)
        } else -1
    }

    /**
     * 保留指定的网络实例
     */
    @JvmStatic
    fun retainNetworkInstance(instanceNames: Array<String>?): Int {
        return if (isLoaded) {
            com.easytier.jni.EasyTierJNI.retainNetworkInstance(instanceNames)
        } else -1
    }

    /**
     * 收集网络信息
     * @param maxLength 最大返回长度，默认 4096
     */
    @JvmStatic
    fun collectNetworkInfos(maxLength: Int = 4096): String? {
        return if (isLoaded) {
            com.easytier.jni.EasyTierJNI.collectNetworkInfos(maxLength)
        } else null
    }

    /**
     * 获取最后的错误消息
     */
    @JvmStatic
    fun getLastError(): String? {
        return if (isLoaded) {
            com.easytier.jni.EasyTierJNI.getLastError()
        } else loadError
    }

    /**
     * 停止所有网络实例
     */
    @JvmStatic
    fun stopAllInstances(): Int {
        return if (isLoaded) {
            com.easytier.jni.EasyTierJNI.stopAllInstances()
        } else -1
    }

    /**
     * 保留单个实例
     */
    @JvmStatic
    fun retainSingleInstance(instanceName: String): Int {
        return if (isLoaded) {
            com.easytier.jni.EasyTierJNI.retainSingleInstance(instanceName)
        } else -1
    }
}
