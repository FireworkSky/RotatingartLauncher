#include <jni.h>

/* SDL2/SDL3 兼容层 */
#ifdef USE_SDL3
#include <SDL3/SDL.h>
#else
#include "SDL.h"
#endif

JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_bridges_SDLInputBridge_nativeStartTextInput(
        JNIEnv *env, jclass clazz) {
#ifdef USE_SDL3
    /* SDL3: SDL_StartTextInput 需要 window 参数，暂时使用 NULL（全局文本输入） */
    SDL_StartTextInput(NULL);
#else
    SDL_StartTextInput();
#endif
}

JNIEXPORT void JNICALL
Java_com_app_ralaunch_controls_bridges_SDLInputBridge_nativeStopTextInput(
        JNIEnv *env, jclass clazz) {
#ifdef USE_SDL3
    SDL_StopTextInput(NULL);
#else
    SDL_StopTextInput();
#endif
}