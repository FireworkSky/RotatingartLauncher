

#include <jni.h>
#include <android/log.h>
#include <string.h>

#define TAG "TouchBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// 最大支持 10 个触摸点
#define MAX_TOUCHES 10
// 最大虚拟触屏点（来自虚拟按钮/摇杆）
#define MAX_VIRTUAL_TOUCHES 5

// 触摸数据结构
typedef struct {
    int count;
    float x[MAX_TOUCHES];
    float y[MAX_TOUCHES];
    int screen_width;
    int screen_height;
} TouchData;

// 虚拟触屏点数据（来自虚拟按钮/摇杆）
typedef struct {
    int count;
    float x[MAX_VIRTUAL_TOUCHES];
    float y[MAX_VIRTUAL_TOUCHES];
    int active[MAX_VIRTUAL_TOUCHES];  // 是否激活
} VirtualTouchData;

// 全局触摸数据
static TouchData g_touch_data = {0};
// 全局虚拟触屏数据
static VirtualTouchData g_virtual_touch = {0};


// 通用的设置触摸数据函数
static void setTouchDataInternal(JNIEnv *env, int count, jfloatArray x_arr, jfloatArray y_arr, int screenWidth, int screenHeight) {
    g_touch_data.screen_width = screenWidth;
    g_touch_data.screen_height = screenHeight;
    
    if (count <= 0) {
        // 清除触摸数据
        g_touch_data.count = 0;
        return;
    }
    
    g_touch_data.count = count > MAX_TOUCHES ? MAX_TOUCHES : count;
    
    if (x_arr != NULL && y_arr != NULL) {
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
//            LOGI("Multi-touch: count=%d", count);
        }
    } else {
        // 如果数组为空，清除数据
        g_touch_data.count = 0;
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

JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeSetVirtualTouch(JNIEnv *env, jclass clazz,
    int index, float x, float y, int screenWidth, int screenHeight) {
    if (index >= 0 && index < MAX_VIRTUAL_TOUCHES) {
        g_virtual_touch.x[index] = x / screenWidth;  // 存储为归一化坐标
        g_virtual_touch.y[index] = y / screenHeight;
        g_virtual_touch.active[index] = 1;

        // 更新屏幕尺寸
        if (g_touch_data.screen_width == 0) {
            g_touch_data.screen_width = screenWidth;
            g_touch_data.screen_height = screenHeight;
        }

        LOGI("Virtual touch set: index=%d, x=%.1f, y=%.1f", index, x, y);
    }
}

// 清除虚拟触屏点（释放）
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeClearVirtualTouch(JNIEnv *env, jclass clazz,
    int index) {
    if (index >= 0 && index < MAX_VIRTUAL_TOUCHES) {
        g_virtual_touch.active[index] = 0;
        LOGI("Virtual touch cleared: index=%d", index);
    }
}


