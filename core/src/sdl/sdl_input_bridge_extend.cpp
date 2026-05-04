#include <jni.h>
#include <stdlib.h>
#include "SDL.h"
#include "logger.hpp"

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

/**
 * 发送鼠标滚轮事件
 * @param scrollY 滚轮滚动量（正数=向上，负数=向下）
 */
extern "C" JNIEXPORT void JNICALL
Java_com_app_ralaunch_feature_controls_bridges_SDLInputBridge_nativeSendMouseWheelSDL(
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

    LOGD("Mouse wheel: scrollY={}", (int) scrollY);
}

extern "C" JNIEXPORT void JNICALL
Java_com_app_ralaunch_feature_controls_bridges_SDLInputBridge_nativeStartTextInput(
        JNIEnv *env, jclass clazz) {
    SDL_StartTextInput();
}

extern "C" JNIEXPORT void JNICALL
Java_com_app_ralaunch_feature_controls_bridges_SDLInputBridge_nativeStopTextInput(
        JNIEnv *env, jclass clazz) {
    SDL_StopTextInput();
}