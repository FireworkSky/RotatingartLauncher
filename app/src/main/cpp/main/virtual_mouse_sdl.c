

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include "SDL.h"

#define TAG "VirtualMouseSDL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

/* External SDL functions for virtual mouse range limit */
extern DECLSPEC void SDLCALL SDL_SetVirtualMouseRangeEnabled(SDL_bool enabled);
extern DECLSPEC void SDLCALL SDL_SetVirtualMouseScreenSize(int width, int height);
extern DECLSPEC void SDLCALL SDL_SetVirtualMouseRange(float left, float top, float right, float bottom);
extern DECLSPEC void SDLCALL SDL_ApplyVirtualMouseRangeLimit(int *mouseX, int *mouseY);

// 虚拟鼠标状态（右摇杆使用，自动初始化）
static int g_vm_initialized = 0;
static float g_vm_x = 0.0f;
static float g_vm_y = 0.0f;
static int g_vm_screen_width = 1920;
static int g_vm_screen_height = 1080;
static int g_vm_is_active = 0;  // 右摇杆是否正在使用（0=静止，1=移动中）
static int g_vm_has_saved_position = 0;  // 是否有保存的位置（0=首次使用，1=已保存）
static float g_vm_saved_x = 0.0f;  // 右摇杆停止时保存的位置
static float g_vm_saved_y = 0.0f;

// 鼠标移动范围限制（从屏幕中心向四周扩展的距离，百分比 0.0-1.0）
// 0.0 = 不扩展（鼠标固定在中心）
// 1.0 = 扩展到屏幕边缘（全屏）
// 阈值 * 0.5 = 实际扩展距离（因为从中心到边缘是 50%）
static float g_vm_range_left = 1.0f;   // 默认100%（全屏）
static float g_vm_range_top = 1.0f;    // 默认100%（全屏）
static float g_vm_range_right = 1.0f;  // 默认100%（全屏）
static float g_vm_range_bottom = 1.0f; // 默认100%（全屏）


// 获取 SDL 窗口
static SDL_Window* get_sdl_window(void) {
    // 检查 SDL 是否已初始化
    if (!SDL_WasInit(SDL_INIT_VIDEO)) {
        LOGW("SDL video not initialized yet");
        return NULL;
    }
    
    // SDL 通常只有一个窗口
    SDL_Window* window = NULL;
    
    // 安全地尝试获取窗口
    // SDL_GetGrabbedWindow() 可能在窗口未准备好时崩溃，所以先检查
    if (SDL_WasInit(SDL_INIT_VIDEO)) {
        window = SDL_GetGrabbedWindow();
        if (!window) {
            window = SDL_GetKeyboardFocus();
        }
        if (!window) {
            window = SDL_GetMouseFocus();
        }
    }
    
    return window;
}

// ===== JNI 函数：Java 调用这些函数控制虚拟鼠标 =====

/**
 * 自动初始化虚拟鼠标（如果未初始化）
 * 不会清除已保存的位置
 */
static void ensure_virtual_mouse_initialized(int screenWidth, int screenHeight) {
    if (!g_vm_initialized) {
        g_vm_screen_width = screenWidth > 0 ? screenWidth : 1920;
        g_vm_screen_height = screenHeight > 0 ? screenHeight : 1080;
        g_vm_x = g_vm_screen_width / 2.0f;
        g_vm_y = g_vm_screen_height / 2.0f;
        g_vm_initialized = 1;
        // 不重置 g_vm_saved_x, g_vm_saved_y, g_vm_has_saved_position
        LOGI("Virtual mouse auto-initialized: screen=%dx%d, pos=(%.0f,%.0f)", 
            g_vm_screen_width, g_vm_screen_height, g_vm_x, g_vm_y);
    }
}

/**
 * 初始化虚拟鼠标（使用实际屏幕尺寸）
 * 注意：不会清除已保存的位置，保持右摇杆的位置记忆
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_bridges_SDLInputBridge_nativeInitVirtualMouseSDL(
    JNIEnv *env, jclass clazz, int screenWidth, int screenHeight) {
    
    g_vm_screen_width = screenWidth > 0 ? screenWidth : 1920;
    g_vm_screen_height = screenHeight > 0 ? screenHeight : 1080;
    
    // 只在真正首次初始化时设置位置
    if (!g_vm_initialized) {
        g_vm_x = g_vm_screen_width / 2.0f;
        g_vm_y = g_vm_screen_height / 2.0f;
        g_vm_initialized = 1;
    }
    // 不要重置 g_vm_saved_x, g_vm_saved_y, g_vm_has_saved_position
    // 保持右摇杆的位置记忆
    
    /* 设置SDL的虚拟鼠标屏幕尺寸 */
    SDL_SetVirtualMouseScreenSize(g_vm_screen_width, g_vm_screen_height);
    
    LOGI("Virtual mouse initialized with real screen: %dx%d, pos=(%.0f,%.0f)", 
        g_vm_screen_width, g_vm_screen_height, g_vm_x, g_vm_y);
}

/**
 * 获取虚拟鼠标当前位置
 * @return float数组，[0]=x, [1]=y
 */
JNIEXPORT jfloatArray JNICALL
Java_com_app_ralaunch_controls_bridges_SDLInputBridge_nativeGetVirtualMousePositionSDL(
    JNIEnv *env, jclass clazz) {
    
    // 如果未初始化，使用默认值（屏幕中心）
    if (!g_vm_initialized) {
        g_vm_x = g_vm_screen_width / 2.0f;
        g_vm_y = g_vm_screen_height / 2.0f;
    }
    
    // 创建返回数组
    jfloatArray result = (*env)->NewFloatArray(env, 2);
    if (result == NULL) {
        LOGW("Failed to create float array for mouse position");
        return NULL;
    }
    
    float pos[2] = {g_vm_x, g_vm_y};
    (*env)->SetFloatArrayRegion(env, result, 0, 2, pos);
    
    return result;
}

/**
 * 设置虚拟鼠标移动范围（从中心扩展模式）
 * @param left   向左扩展的阈值（0.0-1.0，1.0=扩展到左边缘）
 * @param top    向上扩展的阈值（0.0-1.0，1.0=扩展到上边缘）
 * @param right  向右扩展的阈值（0.0-1.0，1.0=扩展到右边缘）
 * @param bottom 向下扩展的阈值（0.0-1.0，1.0=扩展到下边缘）
 * 
 * 例如：(1.0, 1.0, 1.0, 1.0) = 全屏（100%）
 *      (0.6, 0.6, 0.6, 0.6) = 中间60%区域
 *      (0.5, 0.5, 0.5, 0.5) = 中间50%区域
 *      (0.0, 0.0, 0.0, 0.0) = 固定在中心点
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_bridges_SDLInputBridge_nativeSetVirtualMouseRangeSDL(
    JNIEnv *env, jclass clazz, float left, float top, float right, float bottom) {
    
    // 自动初始化虚拟鼠标（使用默认屏幕尺寸，后续会在updateVirtualMouseDelta中更新）
    ensure_virtual_mouse_initialized(1920, 1080);
    
    g_vm_range_left = left;
    g_vm_range_top = top;
    g_vm_range_right = right;
    g_vm_range_bottom = bottom;
    
    /* 启用SDL的虚拟鼠标范围限制 */
    SDL_SetVirtualMouseRangeEnabled(SDL_TRUE);
    SDL_SetVirtualMouseRange(left, top, right, bottom);
    
    LOGI("Virtual mouse range (center-based, max 100%%): left=%.0f%%, top=%.0f%%, right=%.0f%%, bottom=%.0f%%",
        left * 100, top * 100, right * 100, bottom * 100);
}

/**
 * 更新虚拟鼠标位置（相对移动）- 用于右摇杆
 * 右摇杆移动时，鼠标在限制范围内累积移动，到达边界后保持在边界
 * 松开后再次使用时，从上次停止的位置继续
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_bridges_SDLInputBridge_nativeUpdateVirtualMouseDeltaSDL(
    JNIEnv *env, jclass clazz, float deltaX, float deltaY) {
    
    // 自动初始化虚拟鼠标（如果未初始化）
    ensure_virtual_mouse_initialized(1920, 1080);
    
    // 计算屏幕中心点
    float centerX = g_vm_screen_width * 0.5f;
    float centerY = g_vm_screen_height * 0.5f;
    
    // 检测右摇杆是否正在移动
    int is_moving = (deltaX != 0.0f || deltaY != 0.0f);
    
    if (!is_moving) {
        // 右摇杆停止，保存当前位置
        if (g_vm_is_active) {
            g_vm_saved_x = g_vm_x;
            g_vm_saved_y = g_vm_y;
            g_vm_is_active = 0;
            LOGD("Right joystick stopped, saved position: (%.0f, %.0f)", g_vm_saved_x, g_vm_saved_y);
        }
        return;  // 不移动
    }
    
    // 右摇杆正在移动
    if (!g_vm_is_active) {
        // 刚开始移动
        if (!g_vm_has_saved_position) {
            // 首次使用，初始化到中心点
            g_vm_x = centerX;
            g_vm_y = centerY;
            g_vm_saved_x = centerX;
            g_vm_saved_y = centerY;
            g_vm_has_saved_position = 1;  // 标记已有保存位置
            LOGI("First use: virtual mouse at center: (%.0f, %.0f)", g_vm_x, g_vm_y);
        } else {
            // 恢复到上次保存的位置
            g_vm_x = g_vm_saved_x;
            g_vm_y = g_vm_saved_y;
            LOGI("Resumed from saved position: (%.0f, %.0f)", g_vm_x, g_vm_y);
        }
        g_vm_is_active = 1;
    }
    
    // 累积移动量
    g_vm_x += deltaX;
    g_vm_y += deltaY;
    
    // 计算范围限制（从屏幕中心向四周扩展，百分比转像素）
    float minX = centerX - (g_vm_range_left * centerX);   // 向左扩展：阈值 * 50%宽度
    float maxX = centerX + (g_vm_range_right * centerX);  // 向右扩展：阈值 * 50%宽度
    float minY = centerY - (g_vm_range_top * centerY);    // 向上扩展：阈值 * 50%高度
    float maxY = centerY + (g_vm_range_bottom * centerY); // 向下扩展：阈值 * 50%高度
    
    // 限制在范围内，到达边界后保持在边界
    if (g_vm_x < minX) g_vm_x = minX;
    if (g_vm_x > maxX) g_vm_x = maxX;
    if (g_vm_y < minY) g_vm_y = minY;
    if (g_vm_y > maxY) g_vm_y = maxY;
}

/**
 * 设置虚拟鼠标绝对位置
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_bridges_SDLInputBridge_nativeSetVirtualMousePositionSDL(
    JNIEnv *env, jclass clazz, float x, float y) {
    
    // 自动初始化虚拟鼠标（如果未初始化）
    ensure_virtual_mouse_initialized(1920, 1080);
    
    g_vm_x = x;
    g_vm_y = y;
    
    // 计算范围（从屏幕中心向四周扩展，百分比转像素）
    // 阈值范围 0.0-1.0：0.0=中心点, 1.0=全屏
    // 实际扩展距离 = 阈值 * 50%（因为从中心到边缘是屏幕的50%）
    float centerX = g_vm_screen_width * 0.5f;
    float centerY = g_vm_screen_height * 0.5f;
    
    float minX = centerX - (g_vm_range_left * centerX);   // 向左扩展：阈值 * 50%宽度
    float maxX = centerX + (g_vm_range_right * centerX);  // 向右扩展：阈值 * 50%宽度
    float minY = centerY - (g_vm_range_top * centerY);    // 向上扩展：阈值 * 50%高度
    float maxY = centerY + (g_vm_range_bottom * centerY); // 向下扩展：阈值 * 50%高度
    
    // 限制在范围内
    if (g_vm_x < minX) g_vm_x = minX;
    if (g_vm_x > maxX) g_vm_x = maxX;
    if (g_vm_y < minY) g_vm_y = minY;
    if (g_vm_y > maxY) g_vm_y = maxY;
    
    // 不使用 SDL_WarpMouseInWindow，只更新内部位置追踪
    // 实际的鼠标移动通过 sendMouseMove 发送相对移动事件
}

/**
 * 获取虚拟鼠标 X 位置
 */
JNIEXPORT float JNICALL
Java_com_app_ralaunch_controls_bridges_SDLInputBridge_nativeGetVirtualMouseXSDL(
    JNIEnv *env, jclass clazz) {
    // 自动初始化虚拟鼠标（如果未初始化）
    ensure_virtual_mouse_initialized(1920, 1080);
    return g_vm_x;
}

/**
 * 获取虚拟鼠标 Y 位置
 */
JNIEXPORT float JNICALL
Java_com_app_ralaunch_controls_bridges_SDLInputBridge_nativeGetVirtualMouseYSDL(
    JNIEnv *env, jclass clazz) {
    // 自动初始化虚拟鼠标（如果未初始化）
    ensure_virtual_mouse_initialized(1920, 1080);
    return g_vm_y;
}

/**
 * 虚拟鼠标是否启用
 */
JNIEXPORT jboolean JNICALL
Java_com_app_ralaunch_controls_bridges_SDLInputBridge_nativeIsVirtualMouseActiveSDL(
    JNIEnv *env, jclass clazz) {
    return g_vm_initialized ? JNI_TRUE : JNI_FALSE;
}

/**
 * 发送鼠标滚轮事件
 * @param scrollY 滚轮滚动量（正数=向上，负数=向下）
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_bridges_SDLInputBridge_nativeSendMouseWheelSDL(
    JNIEnv *env, jclass clazz, jfloat scrollY) {
    
    // 创建 SDL 鼠标滚轮事件
    SDL_Event event;
    SDL_zero(event);
    event.type = SDL_MOUSEWHEEL;
    event.wheel.x = 0;               // 水平滚动（通常为0）
    event.wheel.y = (int)scrollY;    // 垂直滚动（正数=向上，负数=向下）
    event.wheel.direction = SDL_MOUSEWHEEL_NORMAL;  // 正常方向（非翻转）
    event.wheel.windowID = SDL_GetWindowID(get_sdl_window());
    
    // 推送事件到 SDL 事件队列
    SDL_PushEvent(&event);
    
    LOGD("Mouse wheel: scrollY=%d", (int)scrollY);
}

