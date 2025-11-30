/**
 * Virtual Mouse SDL Injection
 * 
 * 将虚拟鼠标（右摇杆控制）直接注入 SDL 鼠标事件系统
 * 无需 C# 补丁，纯 SDL 原生实现
 */

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include "SDL/include/SDL.h"

#define TAG "VirtualMouseSDL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// 虚拟鼠标状态
static int g_vm_enabled = 0;
static float g_vm_x = 0.0f;
static float g_vm_y = 0.0f;
static int g_vm_screen_width = 1920;
static int g_vm_screen_height = 1080;

// 鼠标移动范围限制（屏幕百分比 0.0-1.0）
static float g_vm_range_left = 0.0f;
static float g_vm_range_top = 0.0f;
static float g_vm_range_right = 1.0f;
static float g_vm_range_bottom = 1.0f;

// 鼠标按钮状态
static int g_vm_left_pressed = 0;
static int g_vm_right_pressed = 0;

// 获取 SDL 窗口
static SDL_Window* get_sdl_window(void) {
    // SDL 通常只有一个窗口
    SDL_Window* window = SDL_GetGrabbedWindow();
    if (!window) {
        window = SDL_GetKeyboardFocus();
    }
    if (!window) {
        window = SDL_GetMouseFocus();
    }
    return window;
}

// ===== JNI 函数：Java 调用这些函数控制虚拟鼠标 =====

/**
 * 启用虚拟鼠标
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeEnableVirtualMouseSDL(
    JNIEnv *env, jclass clazz, int screenWidth, int screenHeight) {
    
    g_vm_enabled = 1;
    g_vm_screen_width = screenWidth > 0 ? screenWidth : 1920;
    g_vm_screen_height = screenHeight > 0 ? screenHeight : 1080;
    g_vm_x = g_vm_screen_width / 2.0f;
    g_vm_y = g_vm_screen_height / 2.0f;
    
    LOGI("Virtual mouse SDL enabled: screen=%dx%d, pos=(%.0f,%.0f)", 
        g_vm_screen_width, g_vm_screen_height, g_vm_x, g_vm_y);
    
    // 发送初始鼠标位置到 SDL
    SDL_Window* window = get_sdl_window();
    if (window) {
        SDL_WarpMouseInWindow(window, (int)g_vm_x, (int)g_vm_y);
    }
}

/**
 * 禁用虚拟鼠标
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeDisableVirtualMouseSDL(
    JNIEnv *env, jclass clazz) {
    
    g_vm_enabled = 0;
    LOGI("Virtual mouse SDL disabled");
}

/**
 * 设置虚拟鼠标移动范围
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeSetVirtualMouseRangeSDL(
    JNIEnv *env, jclass clazz, float left, float top, float right, float bottom) {
    
    g_vm_range_left = left;
    g_vm_range_top = top;
    g_vm_range_right = right;
    g_vm_range_bottom = bottom;
    
    LOGI("Virtual mouse range: left=%.2f, top=%.2f, right=%.2f, bottom=%.2f",
        left, top, right, bottom);
}

/**
 * 更新虚拟鼠标位置（相对移动）- 用于右摇杆
 * 直接注入 SDL 鼠标事件
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeUpdateVirtualMouseDeltaSDL(
    JNIEnv *env, jclass clazz, float deltaX, float deltaY) {
    
    if (!g_vm_enabled) return;
    
    // 更新位置
    g_vm_x += deltaX;
    g_vm_y += deltaY;
    
    // 计算范围（百分比转像素）
    float minX = g_vm_range_left * g_vm_screen_width;
    float maxX = g_vm_range_right * g_vm_screen_width;
    float minY = g_vm_range_top * g_vm_screen_height;
    float maxY = g_vm_range_bottom * g_vm_screen_height;
    
    // 限制在范围内
    if (g_vm_x < minX) g_vm_x = minX;
    if (g_vm_x > maxX) g_vm_x = maxX;
    if (g_vm_y < minY) g_vm_y = minY;
    if (g_vm_y > maxY) g_vm_y = maxY;
    
    // 直接移动 SDL 鼠标
    SDL_Window* window = get_sdl_window();
    if (window) {
        SDL_WarpMouseInWindow(window, (int)g_vm_x, (int)g_vm_y);
    }
}

/**
 * 设置虚拟鼠标绝对位置
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeSetVirtualMousePositionSDL(
    JNIEnv *env, jclass clazz, float x, float y) {
    
    if (!g_vm_enabled) return;
    
    g_vm_x = x;
    g_vm_y = y;
    
    // 限制在屏幕范围内
    if (g_vm_x < 0) g_vm_x = 0;
    if (g_vm_x > g_vm_screen_width) g_vm_x = g_vm_screen_width;
    if (g_vm_y < 0) g_vm_y = 0;
    if (g_vm_y > g_vm_screen_height) g_vm_y = g_vm_screen_height;
    
    // 直接移动 SDL 鼠标
    SDL_Window* window = get_sdl_window();
    if (window) {
        SDL_WarpMouseInWindow(window, (int)g_vm_x, (int)g_vm_y);
    }
}

/**
 * 发送虚拟鼠标按钮事件
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeSendVirtualMouseButtonSDL(
    JNIEnv *env, jclass clazz, int button, jboolean pressed) {
    
    SDL_Window* window = get_sdl_window();
    if (!window) {
        LOGW("No SDL window for virtual mouse button");
        return;
    }
    
    Uint8 sdl_button;
    switch (button) {
        case 1: // 左键
            sdl_button = SDL_BUTTON_LEFT;
            g_vm_left_pressed = pressed ? 1 : 0;
            break;
        case 2: // 右键
            sdl_button = SDL_BUTTON_RIGHT;
            g_vm_right_pressed = pressed ? 1 : 0;
            break;
        case 3: // 中键
            sdl_button = SDL_BUTTON_MIDDLE;
            break;
        default:
            return;
    }
    
    // 创建鼠标按钮事件
    SDL_Event event;
    event.type = pressed ? SDL_MOUSEBUTTONDOWN : SDL_MOUSEBUTTONUP;
    event.button.windowID = SDL_GetWindowID(window);
    event.button.which = 0;  // 虚拟鼠标 ID
    event.button.button = sdl_button;
    event.button.state = pressed ? SDL_PRESSED : SDL_RELEASED;
    event.button.clicks = 1;
    event.button.x = (int)g_vm_x;
    event.button.y = (int)g_vm_y;
    
    SDL_PushEvent(&event);
    
    LOGD("Virtual mouse button: button=%d, pressed=%d, pos=(%.0f,%.0f)", 
        button, pressed, g_vm_x, g_vm_y);
}

/**
 * 获取虚拟鼠标 X 位置
 */
JNIEXPORT float JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeGetVirtualMouseXSDL(
    JNIEnv *env, jclass clazz) {
    return g_vm_x;
}

/**
 * 获取虚拟鼠标 Y 位置
 */
JNIEXPORT float JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeGetVirtualMouseYSDL(
    JNIEnv *env, jclass clazz) {
    return g_vm_y;
}

/**
 * 虚拟鼠标是否启用
 */
JNIEXPORT jboolean JNICALL
Java_com_app_ralaunch_controls_SDLInputBridge_nativeIsVirtualMouseActiveSDL(
    JNIEnv *env, jclass clazz) {
    return g_vm_enabled ? JNI_TRUE : JNI_FALSE;
}

// ===== 导出给其他 C 模块使用 =====

__attribute__((visibility("default")))
int VirtualMouse_IsEnabled(void) {
    return g_vm_enabled;
}

__attribute__((visibility("default")))
void VirtualMouse_GetPosition(float* x, float* y) {
    if (x) *x = g_vm_x;
    if (y) *y = g_vm_y;
}

