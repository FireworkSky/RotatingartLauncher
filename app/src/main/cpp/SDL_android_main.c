#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <pthread.h>

#define LOG_TAG "GameLauncher"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 声明 run_apphost 函数
int run_apphost(const char* appPath, const char* dotnetPath);

// 全局变量存储参数
static char* g_appPath = NULL;
static char* g_dotnetPath = NULL;

// 全局JavaVM指针
static JavaVM* g_jvm = NULL;
static int g_threadAttached = 0;

// JNI_OnLoad函数
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnLoad called");
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEnv* GetJNIEnv() {
    if (g_jvm == NULL) {
        LOGE("JavaVM is NULL in GetJNIEnv");
        return NULL;
    }

    JNIEnv* env = NULL;
    jint result = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);

    if (result == JNI_EDETACHED) {
        LOGI("Current thread not attached, attaching now...");
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGE("Failed to attach current thread to JVM");
            return NULL;
        }
        g_threadAttached = 1;
        LOGI("Thread attached successfully");
    } else if (result != JNI_OK) {
        LOGE("Failed to get JNIEnv, error code: %d", result);
        return NULL;
    }

    return env;
}

void SafeDetachJNIEnv() {
    if (g_jvm != NULL && g_threadAttached) {
        JNIEnv* env = NULL;
        jint result = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);

        if (result == JNI_OK) {
            (*g_jvm)->DetachCurrentThread(g_jvm);
            g_threadAttached = 0;
            LOGI("Thread safely detached from JVM");
        } else {
            LOGI("Thread already detached or not attached");
        }
    }
}

// 清理全局内存
void CleanupGlobalMemory() {
    free(g_appPath);
    free(g_dotnetPath);
    g_appPath = g_dotnetPath = NULL;
}

// 设置启动参数的JNI方法
JNIEXPORT void JNICALL
Java_com_app_ralaunch_game_GameLauncher_setLaunchParams(JNIEnv *env, jclass clazz,
                                                        jstring appPath, jstring dotnetPath) {
    // 释放之前的内存
    CleanupGlobalMemory();

    // 将Java字符串转换为C字符串并复制到全局变量
    const char *app_path = (*env)->GetStringUTFChars(env, appPath, 0);
    const char *dotnet_path = (*env)->GetStringUTFChars(env, dotnetPath, 0);

    g_appPath = strdup(app_path);
    g_dotnetPath = strdup(dotnet_path);

    LOGI("Launch params set: appPath=%s, dotnetPath=%s", g_appPath, g_dotnetPath);

    (*env)->ReleaseStringUTFChars(env, appPath, app_path);
    (*env)->ReleaseStringUTFChars(env, dotnetPath, dotnet_path);
}

// SDL_main 入口点
__attribute__ ((visibility("default"))) int SDL_main(int argc, char* argv[]) {
    LOGI("SDL_main started");

    // 设置环境变量
    setenv("COMPlus_LOGENABLE", "1", 1);
    setenv("COMPlus_LOGLEVEL", "5", 1);

    // 检查必要参数
    if (g_appPath == NULL || g_dotnetPath == NULL) {
        LOGE("Launch parameters not set. Call setLaunchParams first!");
        return -1;
    }

    LOGI("Starting with parameters:");
    LOGI("  appPath: %s", g_appPath);
    LOGI("  dotnetPath: %s", g_dotnetPath);

    // 在启动 .NET 之前初始化 JNI 环境
    // 这对于 .NET 加密库（libSystem.Security.Cryptography.Native.Android.so）是必需的
    LOGI("Initializing JNI environment before launching .NET...");
    JNIEnv* env = GetJNIEnv();
    if (env == NULL) {
        LOGE("Failed to initialize JNI environment");
        return -1;
    }
    LOGI("JNI environment initialized successfully");

    // 调用 Rust 函数
    int result = run_apphost(g_appPath, g_dotnetPath);

    LOGI("run_apphost finished with result: %d", result);

    // 重新获取JNIEnv（可能已经被detach）
    env = GetJNIEnv();
    if (env != NULL) {
        // 找到GameActivity类
        jclass clazz = (*env)->FindClass(env, "com/app/ralaunch/activity/GameActivity");
        if (clazz != NULL) {
            // 找到onGameExit静态方法
            jmethodID method = (*env)->GetStaticMethodID(env, clazz, "onGameExit", "(I)V");
            if (method != NULL) {
                // 调用静态方法
                (*env)->CallStaticVoidMethod(env, clazz, method, result);
            } else {
                LOGE("Failed to find method onGameExit");
            }
            // 删除局部引用
            (*env)->DeleteLocalRef(env, clazz);
        } else {
            LOGE("Failed to find class com/app/ralaunch/activity/GameActivity");
        }
    } else {
        LOGE("Failed to get JNIEnv in SDL_main");
    }

    // 清理资源
    CleanupGlobalMemory();
    SafeDetachJNIEnv();

    LOGI("SDL_main finished");
    return result;
}

// JNI_OnUnload函数
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnUnload called");
    // 清理资源
    CleanupGlobalMemory();
    g_jvm = NULL;
}