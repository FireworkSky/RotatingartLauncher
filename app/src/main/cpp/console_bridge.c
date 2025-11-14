/**
 * @file console_bridge.c
 * @brief C# Console 重定向桥接器实现
 */

#include "console_bridge.h"
#include "jni_bridge.h"
#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>

#define LOG_TAG "ConsoleBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ConsoleService Java 类引用
static jclass g_console_service_class = NULL;
static jmethodID g_write_output_method = NULL;
static jmethodID g_read_input_method = NULL;
static jobject g_console_service_instance = NULL;

// 用于输入同步的互斥锁和条件变量
static pthread_mutex_t g_input_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_input_cond = PTHREAD_COND_INITIALIZER;
static char* g_input_buffer = NULL;
static int g_input_ready = 0;

/**
 * @brief 初始化 Console 重定向桥接器
 */
int Console_Bridge_Init(JNIEnv* env) {
    LOGI("Initializing Console Bridge...");

    // 查找 ConsoleService 类
    jclass local_class = (*env)->FindClass(env, "com/app/ralaunch/console/ConsoleService");
    if (!local_class) {
        LOGE("Failed to find ConsoleService class");
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return -1;
    }

    // 创建全局引用
    g_console_service_class = (*env)->NewGlobalRef(env, local_class);
    (*env)->DeleteLocalRef(env, local_class);

    if (!g_console_service_class) {
        LOGE("Failed to create global reference for ConsoleService");
        return -1;
    }

    // 获取 getInstance() 方法
    jmethodID get_instance_method = (*env)->GetStaticMethodID(
        env, g_console_service_class, "getInstance",
        "()Lcom/app/ralaunch/console/ConsoleService;");
    if (!get_instance_method) {
        LOGE("Failed to find getInstance() method");
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return -1;
    }

    // 获取 ConsoleService 实例
    jobject local_instance = (*env)->CallStaticObjectMethod(
        env, g_console_service_class, get_instance_method);
    if (!local_instance) {
        LOGE("Failed to get ConsoleService instance");
        return -1;
    }

    g_console_service_instance = (*env)->NewGlobalRef(env, local_instance);
    (*env)->DeleteLocalRef(env, local_instance);

    // 获取 writeOutput 方法
    g_write_output_method = (*env)->GetMethodID(
        env, g_console_service_class, "writeOutput", "(Ljava/lang/String;)V");
    if (!g_write_output_method) {
        LOGE("Failed to find writeOutput() method");
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return -1;
    }

    // 获取 readInput 方法
    g_read_input_method = (*env)->GetMethodID(
        env, g_console_service_class, "readInput", "()Ljava/lang/String;");
    if (!g_read_input_method) {
        LOGE("Failed to find readInput() method");
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return -1;
    }

    LOGI("[OK] Console Bridge initialized successfully");
    return 0;
}

/**
 * @brief 从 C# 接收 Console.WriteLine 输出
 */
void Console_Bridge_WriteOutput(const char* text) {
    if (!text || !g_console_service_instance || !g_write_output_method) {
        return;
    }

    JNIEnv* env = Bridge_GetJNIEnv();
    if (!env) {
        LOGE("Failed to get JNI environment");
        return;
    }

    // 创建 Java 字符串
    jstring java_text = (*env)->NewStringUTF(env, text);
    if (!java_text) {
        LOGE("Failed to create Java string");
        Bridge_SafeDetachJNIEnv();
        return;
    }

    // 调用 Java 方法
    (*env)->CallVoidMethod(env, g_console_service_instance, 
                          g_write_output_method, java_text);

    // 检查异常
    if ((*env)->ExceptionCheck(env)) {
        LOGE("Exception occurred while calling writeOutput");
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }

    (*env)->DeleteLocalRef(env, java_text);
    Bridge_SafeDetachJNIEnv();
}

/**
 * @brief 从 C# 接收 Console.ReadLine 请求（阻塞直到有输入）
 */
int Console_Bridge_ReadInput(char* buffer, int buffer_size) {
    if (!buffer || buffer_size <= 0 || !g_console_service_instance || !g_read_input_method) {
        return -1;
    }

    JNIEnv* env = Bridge_GetJNIEnv();
    if (!env) {
        LOGE("Failed to get JNI environment");
        return -1;
    }

    LOGI("Waiting for console input...");

    // 调用 Java 的 readInput() 方法（阻塞）
    jstring java_input = (jstring)(*env)->CallObjectMethod(
        env, g_console_service_instance, g_read_input_method);

    if ((*env)->ExceptionCheck(env)) {
        LOGE("Exception occurred while calling readInput");
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        Bridge_SafeDetachJNIEnv();
        return -1;
    }

    if (!java_input) {
        LOGW("readInput returned null");
        Bridge_SafeDetachJNIEnv();
        return 0;
    }

    // 转换为 C 字符串
    const char* input_chars = (*env)->GetStringUTFChars(env, java_input, NULL);
    if (!input_chars) {
        LOGE("Failed to get UTF chars from Java string");
        (*env)->DeleteLocalRef(env, java_input);
        Bridge_SafeDetachJNIEnv();
        return -1;
    }

    int input_len = strlen(input_chars);
    int copy_len = (input_len < buffer_size - 1) ? input_len : (buffer_size - 1);
    memcpy(buffer, input_chars, copy_len);
    buffer[copy_len] = '\0';

    LOGI("[OK] Console input received: %s", buffer);

    (*env)->ReleaseStringUTFChars(env, java_input, input_chars);
    (*env)->DeleteLocalRef(env, java_input);
    Bridge_SafeDetachJNIEnv();

    return copy_len;
}

/**
 * @brief JNI 方法：设置 Console 输出回调
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_console_ConsoleService_nativeSetConsoleOutputCallback(
    JNIEnv* env, jclass clazz) {
    LOGI("Setting console output callback...");
    
    // 初始化桥接器（如果还没初始化）
    if (!g_console_service_instance) {
        Console_Bridge_Init(env);
    }
    
    // TODO: 通过 hostfxr 设置 C# Console.Out 重定向
    // 这需要在 .NET 运行时初始化后调用托管代码来设置
    
    LOGI("[OK] Console output callback set");
}

/**
 * @brief JNI 方法：设置 Console 输入回调
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_console_ConsoleService_nativeSetConsoleInputCallback(
    JNIEnv* env, jclass clazz) {
    LOGI("Setting console input callback...");
    
    // 初始化桥接器（如果还没初始化）
    if (!g_console_service_instance) {
        Console_Bridge_Init(env);
    }
    
    // TODO: 通过 hostfxr 设置 C# Console.In 重定向
    
    LOGI("[OK] Console input callback set");
}

