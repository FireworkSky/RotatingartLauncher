/**
 * @file jni_bridge.c
 * @brief JNI 桥接器实现
 */

#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include "jni_bridge.h"

#define LOG_TAG "GameLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/** 全局 JavaVM 指针 */
static JavaVM* g_jvm = NULL;

/** 标记当前线程是否由此模块附加到 JVM */
static int g_threadAttached = 0;


/**
 * @brief JNI_OnLoad 生命周期回调实现
 * 
 * @param vm JavaVM 指针
 * @return JNI_VERSION_1_6
 * 
 * 保存 JavaVM 指针供后续使用。
 */
jint Bridge_JNI_OnLoad(JavaVM* vm) {
    LOGI("JNI_OnLoad called");
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

/**
 * @brief JNI_OnUnload 生命周期回调实现
 * 
 * @param vm JavaVM 指针
 * 
 * 清理所有全局资源和内存。
 */
void Bridge_JNI_OnUnload(JavaVM* vm) {
    (void)vm;
    LOGI("JNI_OnUnload called");
    // CleanupGlobalMemory() 已移除（旧的 dotnet_host 代码）
    g_jvm = NULL;
}

/**
 * @brief 获取当前线程的 JNI 环境
 * 
 * @return JNIEnv 指针，失败返回 NULL
 * 
 * 如果当前线程未附加到 JVM（JNI_EDETACHED），会自动附加。
 * 自动附加的线程需要调用 Bridge_SafeDetachJNIEnv() 分离。
 */
JNIEnv* Bridge_GetJNIEnv() {
    if (g_jvm == NULL) { 
        LOGE("JavaVM is NULL in GetJNIEnv"); 
        return NULL; 
    }
    
    JNIEnv* env = NULL;
    jint result = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
    
    if (result == JNI_EDETACHED) {
        // 线程未附加，执行附加操作
        LOGI("Current thread not attached, attaching now...");
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) { 
            LOGE("Failed to attach current thread to JVM"); 
            return NULL; 
        }
        g_threadAttached = 1;
    } else if (result != JNI_OK) { 
        LOGE("Failed to get JNIEnv, error code: %d", result); 
        return NULL; 
    }
    
    return env;
}

/**
 * @brief 安全地从 JVM 分离当前线程
 * 
 * 仅当线程是通过 Bridge_GetJNIEnv() 附加时才执行分离操作。
 * 这避免了对 Java 创建的线程进行错误的分离操作。
 */
void Bridge_SafeDetachJNIEnv() {
    if (g_jvm != NULL && g_threadAttached) {
        JNIEnv* env = NULL;
        jint result = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
        if (result == JNI_OK) {
            (*g_jvm)->DetachCurrentThread(g_jvm);
            g_threadAttached = 0;
            LOGI("Thread safely detached from JVM");
        }
    }
}

/**
 * @brief 获取全局 JavaVM 指针
 * 
 * @return JavaVM 指针，如果未初始化则返回 NULL
 * 
 * 用于其他模块（如 .NET 加密库）获取 JavaVM 以初始化 JNI 环境。
 */
JavaVM* Bridge_GetJavaVM() {
    return g_jvm;
}

/**
 * @brief 通知 Java 层游戏已退出
 * 
 * @param exitCode 游戏退出码
 * 
 * 通过 JNI 调用 GameActivity.onGameExit(int) 静态方法。
 * 如果方法不存在或调用失败，会静默失败（不抛出异常）。
 */
void Bridge_NotifyGameExit(int exitCode) {
    JNIEnv* env = Bridge_GetJNIEnv();
    if (!env) return;
    
    // 查找 GameActivity 类
    jclass clazz = (*env)->FindClass(env, "com/app/ralaunch/activity/GameActivity");
    if (clazz) {
        // 查找 onGameExit 静态方法
        jmethodID method = (*env)->GetStaticMethodID(env, clazz, "onGameExit", "(I)V");
        if (method) {
            // 调用静态方法
            (*env)->CallStaticVoidMethod(env, clazz, method, exitCode);
        }
        (*env)->DeleteLocalRef(env, clazz);
    }
}

// ======================================================================
// 已移除的旧 JNI 函数（已在 Java 端和 C++ 端删除）：
// - setLaunchParams
// - setLaunchParamsWithRuntime  
// - setVerboseLogging
// - setRenderer
// - setBootstrapLaunchParams
// ======================================================================

// 架构检测功能已移除 - 本应用仅支持 ARM64 架构


// ============================================================================
// 图标提取器JNI函数
// ============================================================================


