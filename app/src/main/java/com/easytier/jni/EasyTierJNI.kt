package com.easytier.jni

/**
 * EasyTier JNI 接口类
 * 包名必须与原生库的 JNI 函数名匹配
 */
object EasyTierJNI {

    init {
        System.loadLibrary("easytier_android_jni")
    }

    @JvmStatic
    external fun setTunFd(instanceName: String, fd: Int): Int

    @JvmStatic
    external fun parseConfig(config: String): Int

    @JvmStatic
    external fun runNetworkInstance(config: String): Int

    @JvmStatic
    external fun retainNetworkInstance(instanceNames: Array<String>?): Int

    @JvmStatic
    external fun collectNetworkInfos(maxLength: Int): String?

    @JvmStatic
    external fun getLastError(): String?

    @JvmStatic
    fun stopAllInstances(): Int = retainNetworkInstance(null)

    @JvmStatic
    fun retainSingleInstance(instanceName: String): Int = retainNetworkInstance(arrayOf(instanceName))
}
