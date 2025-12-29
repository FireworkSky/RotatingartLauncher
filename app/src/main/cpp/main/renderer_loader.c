/**
 * renderer_loader.c
 *
 * 渲染器动态加载 JNI 实现
 *
 * 提供 dlopen/dlclose/dlsym 和环境变量操作的 JNI 接口
 */

#include <jni.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "RendererLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ==================== dlopen/dlclose/dlerror ====================

/**
 * Java: nativeDlopen(String path)
 * 使用 dlopen 打开动态库
 */
JNIEXPORT jlong JNICALL
Java_com_app_ralaunch_renderer_RendererLoader_nativeDlopen(JNIEnv *env, jclass clazz, jstring path) {
    if (path == NULL) {
        LOGE("dlopen: path is null");
        return 0;
    }

    const char *path_str = (*env)->GetStringUTFChars(env, path, NULL);
    if (path_str == NULL) {
        LOGE("dlopen: failed to get path string");
        return 0;
    }

    LOGI("dlopen: %s", path_str);

    // 使用 RTLD_NOW | RTLD_GLOBAL
    // RTLD_NOW: 立即解析所有符号
    // RTLD_GLOBAL: 让其他库可以看到这个库的符号（关键！让 SDL 能找到 EGL 符号）
    void *handle = dlopen(path_str, RTLD_NOW | RTLD_GLOBAL);

    if (handle == NULL) {
        const char *error = dlerror();
        LOGE("dlopen failed: %s", error ? error : "unknown error");
        (*env)->ReleaseStringUTFChars(env, path, path_str);
        return 0;
    }

    LOGI("dlopen success: handle = %p", handle);
    (*env)->ReleaseStringUTFChars(env, path, path_str);

    return (jlong)(uintptr_t)handle;
}

/**
 * Java: nativeDlclose(long handle)
 * 使用 dlclose 关闭动态库
 */
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_renderer_RendererLoader_nativeDlclose(JNIEnv *env, jclass clazz, jlong handle) {
    if (handle == 0) {
        LOGE("dlclose: invalid handle");
        return -1;
    }

    void *lib_handle = (void *)(uintptr_t)handle;
    LOGI("dlclose: handle = %p", lib_handle);

    int result = dlclose(lib_handle);
    if (result != 0) {
        const char *error = dlerror();
        LOGE("dlclose failed: %s", error ? error : "unknown error");
    } else {
        LOGI("dlclose success");
    }

    return result;
}

/**
 * Java: nativeDlerror()
 * 获取 dlopen/dlsym 的错误信息
 */
JNIEXPORT jstring JNICALL
Java_com_app_ralaunch_renderer_RendererLoader_nativeDlerror(JNIEnv *env, jclass clazz) {
    const char *error = dlerror();
    if (error == NULL) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, error);
}

// ==================== 环境变量操作 ====================

/**
 * Java: nativeSetEnv(String key, String value)
 * 设置环境变量
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_renderer_RendererLoader_nativeSetEnv(JNIEnv *env, jclass clazz,
                                                            jstring key, jstring value) {
    if (key == NULL || value == NULL) {
        LOGE("setenv: key or value is null");
        return;
    }

    const char *key_str = (*env)->GetStringUTFChars(env, key, NULL);
    const char *value_str = (*env)->GetStringUTFChars(env, value, NULL);

    if (key_str == NULL || value_str == NULL) {
        LOGE("setenv: failed to get strings");
        if (key_str) (*env)->ReleaseStringUTFChars(env, key, key_str);
        if (value_str) (*env)->ReleaseStringUTFChars(env, value, value_str);
        return;
    }

    LOGI("setenv: %s = %s", key_str, value_str);

    // 使用 setenv (覆盖已存在的值)
    int result = setenv(key_str, value_str, 1);
    if (result != 0) {
        LOGE("setenv failed: %s = %s", key_str, value_str);
    }

    (*env)->ReleaseStringUTFChars(env, key, key_str);
    (*env)->ReleaseStringUTFChars(env, value, value_str);
}

/**
 * Java: nativeUnsetEnv(String key)
 * 取消环境变量
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_renderer_RendererLoader_nativeUnsetEnv(JNIEnv *env, jclass clazz, jstring key) {
    if (key == NULL) {
        LOGE("unsetenv: key is null");
        return;
    }

    const char *key_str = (*env)->GetStringUTFChars(env, key, NULL);
    if (key_str == NULL) {
        LOGE("unsetenv: failed to get key string");
        return;
    }

    LOGI("unsetenv: %s", key_str);

    int result = unsetenv(key_str);
    if (result != 0) {
        LOGE("unsetenv failed: %s", key_str);
    }

    (*env)->ReleaseStringUTFChars(env, key, key_str);
}

/**
 * Java: nativeGetEnv(String key)
 * 获取环境变量
 */
JNIEXPORT jstring JNICALL
Java_com_app_ralaunch_renderer_RendererLoader_nativeGetEnv(JNIEnv *env, jclass clazz, jstring key) {
    if (key == NULL) {
        LOGE("getenv: key is null");
        return NULL;
    }

    const char *key_str = (*env)->GetStringUTFChars(env, key, NULL);
    if (key_str == NULL) {
        LOGE("getenv: failed to get key string");
        return NULL;
    }

    const char *value = getenv(key_str);
    (*env)->ReleaseStringUTFChars(env, key, key_str);

    if (value == NULL) {
        return NULL;
    }

    return (*env)->NewStringUTF(env, value);
}
