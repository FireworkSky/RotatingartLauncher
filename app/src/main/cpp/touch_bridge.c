/**
 * Touch Bridge - 触摸数据桥接
 * 在 Java 和 C# 之间共享触摸数据
 */

#include <jni.h>
#include <android/log.h>
#include <string.h>

#define TAG "TouchBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// 最大支持 10 个触摸点
#define MAX_TOUCHES 10

// 触摸数据结构
typedef struct {
    int count;
    float x[MAX_TOUCHES];
    float y[MAX_TOUCHES];
    int screen_width;
    int screen_height;
} TouchData;

// 全局触摸数据
static TouchData g_touch_data = {0};

// ===== 供 Java 调用的 JNI 函数 =====

// 通用的设置触摸数据函数
static void setTouchDataInternal(JNIEnv *env, int count, jfloatArray x_arr, jfloatArray y_arr, int screenWidth, int screenHeight) {
    g_touch_data.count = count > MAX_TOUCHES ? MAX_TOUCHES : count;
    g_touch_data.screen_width = screenWidth;
    g_touch_data.screen_height = screenHeight;
    
    if (count > 0 && x_arr != NULL && y_arr != NULL) {
        jfloat* x_data = (*env)->GetFloatArrayElements(env, x_arr, NULL);
        jfloat* y_data = (*env)->GetFloatArrayElements(env, y_arr, NULL);
        
        for (int i = 0; i < g_touch_data.count; i++) {
            g_touch_data.x[i] = x_data[i];
            g_touch_data.y[i] = y_data[i];
        }
        
        (*env)->ReleaseFloatArrayElements(env, x_arr, x_data, 0);
        (*env)->ReleaseFloatArrayElements(env, y_arr, y_data, 0);
        
        // Debug log for multi-touch
        if (count > 1) {
            LOGI("Multi-touch: count=%d", count);
        }
    }
}

// SDLSurface JNI 函数
JNIEXPORT void JNICALL
Java_org_libsdl_app_SDLSurface_nativeSetTouchData(JNIEnv *env, jclass clazz,
    int count, jfloatArray x_arr, jfloatArray y_arr, int screenWidth, int screenHeight) {
    setTouchDataInternal(env, count, x_arr, y_arr, screenWidth, screenHeight);
}

JNIEXPORT void JNICALL
Java_org_libsdl_app_SDLSurface_nativeClearTouchData(JNIEnv *env, jclass clazz) {
    g_touch_data.count = 0;
}

// GameActivity JNI 函数
JNIEXPORT void JNICALL
Java_com_app_ralaunch_activity_GameActivity_nativeSetTouchData(JNIEnv *env, jclass clazz,
    int count, jfloatArray x_arr, jfloatArray y_arr, int screenWidth, int screenHeight) {
    setTouchDataInternal(env, count, x_arr, y_arr, screenWidth, screenHeight);
}

JNIEXPORT void JNICALL
Java_com_app_ralaunch_activity_GameActivity_nativeClearTouchData(JNIEnv *env, jclass clazz) {
    g_touch_data.count = 0;
}

// ===== 供 C# P/Invoke 调用的导出函数 =====

__attribute__((visibility("default")))
int RAL_GetTouchCount() {
    return g_touch_data.count;
}

__attribute__((visibility("default")))
float RAL_GetTouchX(int index) {
    if (index >= 0 && index < g_touch_data.count) {
        // 返回像素坐标
        return g_touch_data.x[index] * g_touch_data.screen_width;
    }
    return 0.0f;
}

__attribute__((visibility("default")))
float RAL_GetTouchY(int index) {
    if (index >= 0 && index < g_touch_data.count) {
        // 返回像素坐标
        return g_touch_data.y[index] * g_touch_data.screen_height;
    }
    return 0.0f;
}

__attribute__((visibility("default")))
int RAL_GetScreenWidth() {
    return g_touch_data.screen_width;
}

__attribute__((visibility("default")))
int RAL_GetScreenHeight() {
    return g_touch_data.screen_height;
}

