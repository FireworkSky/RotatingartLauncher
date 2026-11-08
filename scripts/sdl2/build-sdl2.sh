#!/data/data/com.termux/files/usr/bin/bash
# 为 Android arm64-v8a 构建 SDL2 2.32.10（官方 build-scripts/androidbuildlibs.sh / ndk-build）
#
# 用途：RAL 的 SDL2 路径。SDL2 使用自己的 Java 胶水层，本工程把该胶水层的包名整体改成
# org.libsdl.app2（避免与 SDL3 的 org.libsdl.app.* 冲突），因此本脚本只做两件事：
#   1) 给 SDL2 源码打补丁：JNI 前缀 org_libsdl_app -> org_libsdl_app2、恢复 Java 绑定注册、
#      EGL 里 Android 专用调用加运行期守卫；
#   2) 用官方 build-scripts/androidbuildlibs.sh（ndk-build）构建：
#      产物天然叫 libSDL2.so（无版本号，bionic 按文件名解析 DT_NEEDED 时无需 patchelf），
#      且只带 Android 平台驱动（Android 视频 + AAudio/OpenSL ES）。
#
# 产物：$WORK/ndklibs/arm64-v8a/libSDL2.so -> 复制到 app/src/main/jniLibs/arm64-v8a/。
set -euo pipefail

export NDK=${NDK:-/data/data/com.termux/files/usr/opt/android-ndk}
export API=${API:-28}
export WORK=${WORK:-/data/data/com.termux/files/home/sdl2android}
export SRC=${SRC:-/data/data/com.termux/files/home/sdl2src}

mkdir -p "$WORK/log" "$WORK/ndklibs"

step_sdl2_patches() {
  echo "=== [5.5] 应用 SDL2 补丁 ==="
  # 路径必须由 shell 展开后传给 python（写在引号 heredoc 里不会展开）
  python3 - "$SRC" <<'EOPY'
import os, sys

root = os.path.join(sys.argv[1], "SDL2-2.32.10")
SRC_A = os.path.join(root, "src/core/android/SDL_android.c")
SRC_E = os.path.join(root, "src/video/SDL_egl.c")
SRC_GL = os.path.join(root, "src/video/android/SDL_androidgl.c")

def patch(path, old, new, tag):
    s = open(path, encoding="utf-8").read()
    if new in s:
        print("  %s: 已是补丁后状态，跳过" % tag); return s
    if old not in s:
        print("  !! %s: 锚点未找到（上游可能已改）" % tag); return s
    s = s.replace(old, new, 1)
    open(path, "w", encoding="utf-8").write(s)
    print("  %s: 已应用" % tag)
    return s

# ---- 补丁1/2：SDL_EGL_CreateSurface 的两处 Android 调用改为运行期判断驱动名 ----
# 否则非 Android 后端下 nw 可能不是 ANativeWindow*，被错误使用会导致崩溃
patch(SRC_E,
"""#ifdef SDL_VIDEO_DRIVER_ANDROID
    /* On Android, EGL_NATIVE_VISUAL_ID is an attribute of the EGLConfig that is
     * guaranteed to be accepted by ANativeWindow_setBuffersGeometry(). */
    _this->egl_data->eglGetConfigAttrib(_this->egl_data->egl_display,
                                        _this->egl_data->egl_config,
                                        EGL_NATIVE_VISUAL_ID, &format_wanted);

    /* Format based on selected egl config. */
    ANativeWindow_setBuffersGeometry(nw, 0, 0, format_wanted);
#endif""",
"""#ifdef SDL_VIDEO_DRIVER_ANDROID
    /* RAL: 编译期宏为真不等于运行的是 Android 视频驱动。非 Android 后端下 nw 可能不是
     * ANativeWindow*，交给 ANativeWindow_setBuffersGeometry 会崩。改运行期判断。 */
    if (SDL_strcmp(_this->name, "Android") == 0) {
        _this->egl_data->eglGetConfigAttrib(_this->egl_data->egl_display,
                                            _this->egl_data->egl_config,
                                            EGL_NATIVE_VISUAL_ID, &format_wanted);
        ANativeWindow_setBuffersGeometry(nw, 0, 0, format_wanted);
    }
#endif""", "补丁1 SDL_egl(setBuffersGeometry)")

patch(SRC_E,
"""#ifdef SDL_VIDEO_DRIVER_ANDROID
    format_got = ANativeWindow_getFormat(nw);
    Android_SetFormat(format_wanted, format_got);
#endif""",
"""#ifdef SDL_VIDEO_DRIVER_ANDROID
    /* RAL: 同上，非 Android 视频驱动时 nw 不是 ANativeWindow* */
    if (SDL_strcmp(_this->name, "Android") == 0) {
        format_got = ANativeWindow_getFormat(nw);
        Android_SetFormat(format_wanted, format_got);
    }
#endif""", "补丁2 SDL_egl(getFormat)")

# ---- 补丁3：Java 胶水层改名 org.libsdl.app -> org.libsdl.app2 ----
# APK 内 org.libsdl.app.* 属于 SDL3，两套胶水层不能共存（类名/签名/静态状态冲突）。
# 传统做法：把 SDL2 的胶水层整体挪到 org.libsdl.app2，native 侧的 JNI 前缀同步改名，
# 于是 SDL2 可以正常使用自己的 Java 桥接层（Android 视频后端 + 系统 EGL/GLES，走厂商 GPU）。
# 1) JNI 函数名前缀：Java_org_libsdl_app_SDLActivity_xxx -> Java_org_libsdl_app2_...
patch(SRC_A,
"""#define SDL_JAVA_PREFIX                               org_libsdl_app""",
"""#define SDL_JAVA_PREFIX                               org_libsdl_app2""", "补丁3a JNI 前缀")

# DT_NEEDED 依赖可能在 Java 层之前加载 SDL2，此时 JNI_OnLoad 尚未填充 mJavaVM。
# 从 nativeSetupJNI 收到的 JNIEnv 恢复 JavaVM，避免后续 Android_JNI_GetEnv 返回空指针。
patch(SRC_A,
"""    /* Save JNIEnv of SDLActivity */
    Android_JNI_SetEnv(env);

    if (!mJavaVM) {
        __android_log_print(ANDROID_LOG_ERROR, "SDL", "failed to found a JavaVM");
    }""",
"""    /* Save JNIEnv of SDLActivity */
    Android_JNI_SetEnv(env);

    if (!mJavaVM) {
        /* SDL can be loaded as a DT_NEEDED dependency before JNI_OnLoad is
         * invoked. Recover the VM from the JNIEnv supplied by Java. */
        if ((*env)->GetJavaVM(env, &mJavaVM) != JNI_OK || !mJavaVM) {
            __android_log_print(ANDROID_LOG_ERROR, "SDL", "failed to obtain a JavaVM");
        }
    }""", "补丁7 JNI JavaVM 恢复")

patch(SRC_A,
"""void Android_JNI_InitTouch(void)
{
    JNIEnv *env = Android_JNI_GetEnv();
    (*env)->CallStaticVoidMethod(env, mActivityClass, midInitTouch);
}""",
"""void Android_JNI_InitTouch(void)
{
    JNIEnv *env = Android_JNI_GetEnv();
    if (!env || !mActivityClass || !midInitTouch) {
        __android_log_print(ANDROID_LOG_ERROR, "SDL",
                            "Skipping touch initialization because JNI is not ready");
        return;
    }
    (*env)->CallStaticVoidMethod(env, mActivityClass, midInitTouch);
}""", "补丁8 触摸 JNI 空指针保护")

patch(os.path.join(root, "src/hidapi/android/hid.cpp"),
"""#define SDL_JAVA_PREFIX                                 org_libsdl_app""",
"""#define SDL_JAVA_PREFIX                                 org_libsdl_app2""", "补丁3b hid JNI 前缀")

# 2) JNI_OnLoad 注册的类名同步改成 app2（保留注册：SDL2 现在有自己的 Java 层）
patch(SRC_A,
"""    register_methods(env, "org/libsdl/app/SDLActivity", SDLActivity_tab, SDL_arraysize(SDLActivity_tab));
    register_methods(env, "org/libsdl/app/SDLInputConnection", SDLInputConnection_tab, SDL_arraysize(SDLInputConnection_tab));
    register_methods(env, "org/libsdl/app/SDLAudioManager", SDLAudioManager_tab, SDL_arraysize(SDLAudioManager_tab));
    register_methods(env, "org/libsdl/app/SDLControllerManager", SDLControllerManager_tab, SDL_arraysize(SDLControllerManager_tab));""",
"""    /* RAL: 注册到改名后的胶水层 org.libsdl.app2（APK 内 org.libsdl.app.* 归属 SDL3） */
    register_methods(env, "org/libsdl/app2/SDLActivity", SDLActivity_tab, SDL_arraysize(SDLActivity_tab));
    register_methods(env, "org/libsdl/app2/SDLInputConnection", SDLInputConnection_tab, SDL_arraysize(SDLInputConnection_tab));
    register_methods(env, "org/libsdl/app2/SDLAudioManager", SDLAudioManager_tab, SDL_arraysize(SDLAudioManager_tab));
    register_methods(env, "org/libsdl/app2/SDLControllerManager", SDLControllerManager_tab, SDL_arraysize(SDLControllerManager_tab));""", "补丁3c 注册类名")

# ---- 补丁4：依赖 Java 缓存 jclass/jmethodID 的函数加优雅降级守卫 ----
# 兜底：若 mActivityClass 尚未就绪，这些函数会以 NULL class 调 JNI -> abort。
# 注意：必须在“各自函数体内”定位锚点（全文件首个匹配会落到别的函数里），
# 且守卫要插在该函数所有声明之后，否则触发 -Werror=declaration-after-statement。
GUARD = ("    if (!mActivityClass) {\n"
         "        /* RAL: Java 桥接层尚未就绪，安全降级；\n"
         "         * 位置已确保在该函数所有声明之后，避免 -Wdeclaration-after-statement。 */\n"
         "        return%s;\n"
         "    }")

targets = [
    ("void Android_JNI_PollInputDevices(void)", "midPollInputDevices);", ""),
    ("void Android_JNI_PollHapticDevices(void)", "midPollHapticDevices);", ""),
    ("void Android_JNI_HapticRun(int device_id, float intensity, int length)", "midHapticRun,", ""),
    ("void Android_JNI_HapticStop(int device_id)", "midHapticStop,", ""),
    ("int Android_JNI_GetPowerInfo(int *plugged, int *charged, int *battery, int *seconds, int *percent)", "if (!LocalReferenceHolder_Init(&refs, env)) {", " 0"),
    ("int Android_JNI_SetClipboardText(const char *text)", "midClipboardSetText,", " -1"),
    ("char *Android_JNI_GetClipboardText(void)", "midClipboardGetText);", " NULL"),
    ("SDL_bool Android_JNI_HasClipboardText(void)", "return (retval == JNI_TRUE) ? SDL_TRUE : SDL_FALSE;", " SDL_FALSE"),
]

def body_range(text, sig):
    i = text.find(sig)
    if i < 0: return None
    b = text.find('{', i)
    if b < 0: return None
    depth = 0
    for j in range(b, len(text)):
        if text[j] == '{': depth += 1
        elif text[j] == '}':
            depth -= 1
            if depth == 0: return (b, j)
    return None

gs = open(SRC_A, encoding="utf-8").read()
if "Java 桥接层未注册" in gs:
    print("  补丁4: 已存在，跳过")
else:
    done = 0
    for sig, anchor, dflt in targets:
        rng = body_range(gs, sig)
        if not rng:
            print("  !! 补丁4 未找到函数:", sig[:45]); continue
        b, e = rng
        ai = gs.find(anchor, b, e)
        if ai < 0:
            print("  !! 补丁4 函数内未找到锚点:", sig[:40]); continue
        ls = gs.rfind('\n', b, ai) + 1
        gs = gs[:ls] + (GUARD % dflt) + '\n' + gs[ls:]
        done += 1
    open(SRC_A, "w", encoding="utf-8").write(gs)
    print("  补丁4: 已插入 %d/%d 处守卫" % (done, len(targets)))

# ---- 补丁5：虚拟控件触点消费（与 SDL3 fork 的 SDL_touch.c 同语义）----
# 被 RAL 虚拟按键占用的触点：触摸事件照常下发给游戏，但**不再合成鼠标事件**，
# 否则手机上的虚拟按键会在游戏里同时触发一次鼠标点击（点一下等于两次操作）。
# SDL3 那边由 fork 的 SDL_touch.c 实现；SDL2 这里以同样方式打补丁。
SRC_T = os.path.join(root, "src/events/SDL_touch.c")
SRC_TH = os.path.join(root, "src/events/SDL_touch_c.h")

T5_IMPL = '''
/* RAL: 被虚拟控件占用的触点（由 org.libsdl.app2.SDLActivity 的 native 方法设置） */
#define RAL_MAX_CONSUMED_FINGERS 10
static int ral_consumed_finger_count;
static SDL_FingerID ral_consumed_fingers[RAL_MAX_CONSUMED_FINGERS];

static SDL_bool SDL_IsFingerConsumed(SDL_FingerID fingerid)
{
    int i;
    for (i = 0; i < ral_consumed_finger_count; ++i) {
        if (ral_consumed_fingers[i] == fingerid) {
            return SDL_TRUE;
        }
    }
    return SDL_FALSE;
}

void SDL_ConsumeFingerTouch(int fingerId)
{
    SDL_FingerID fingerid = (SDL_FingerID)fingerId;
    if (ral_consumed_finger_count >= RAL_MAX_CONSUMED_FINGERS || SDL_IsFingerConsumed(fingerid)) {
        return;
    }
    ral_consumed_fingers[ral_consumed_finger_count++] = fingerid;
}

void SDL_ReleaseFingerTouch(int fingerId)
{
    int i;
    SDL_FingerID fingerid = (SDL_FingerID)fingerId;
    for (i = 0; i < ral_consumed_finger_count; ++i) {
        if (ral_consumed_fingers[i] == fingerid) {
            SDL_memmove(&ral_consumed_fingers[i], &ral_consumed_fingers[i + 1],
                        (ral_consumed_finger_count - i - 1) * sizeof(ral_consumed_fingers[i]));
            --ral_consumed_finger_count;
            return;
        }
    }
}

void SDL_ClearConsumedFingers(void)
{
    ral_consumed_finger_count = 0;
}
'''

patch(SRC_T,
"""/* for mapping touch events to mice */

#define SYNTHESIZE_TOUCH_TO_MOUSE 1""",
"""/* for mapping touch events to mice */

#define SYNTHESIZE_TOUCH_TO_MOUSE 1
""" + T5_IMPL, "补丁5a 触点消费实现")

patch(SRC_TH,
"""/* Shutdown the touch subsystem */
extern void SDL_TouchQuit(void);""",
"""/* Shutdown the touch subsystem */
extern void SDL_TouchQuit(void);

/* RAL: 虚拟控件触点消费（见 SDL_touch.c 的实现注释） */
extern void SDL_ConsumeFingerTouch(int fingerId);
extern void SDL_ReleaseFingerTouch(int fingerId);
extern void SDL_ClearConsumedFingers(void);""", "补丁5b 声明")

# SDL_SendTouch：按下/抬起时的鼠标合成
patch(SRC_T,
"""#else
        if (mouse->touch_mouse_events) {
#endif""",
"""#else
        /* RAL: 被虚拟控件占用的触点不合成鼠标事件（触摸事件本身照常下发） */
        if (mouse->touch_mouse_events && !SDL_IsFingerConsumed(fingerid)) {
#endif""", "补丁5c SDL_SendTouch")

# SDL_SendTouchMotion：移动时的鼠标合成
patch(SRC_T,
"""        if (mouse->touch_mouse_events) {
            if (id != SDL_MOUSE_TOUCHID) {
                if (window) {
                    if (finger_touching == SDL_TRUE && track_touchid == id && track_fingerid == fingerid) {""",
"""        /* RAL: 被虚拟控件占用的触点不合成鼠标移动 */
        if (mouse->touch_mouse_events && !SDL_IsFingerConsumed(fingerid)) {
            if (id != SDL_MOUSE_TOUCHID) {
                if (window) {
                    if (finger_touching == SDL_TRUE && track_touchid == id && track_fingerid == fingerid) {""", "补丁5d SDL_SendTouchMotion")

# ---- 补丁6：暴露 JNI 入口供 org.libsdl.app2.SDLActivity 调用 ----
patch(SRC_A,
"""    { "nativeAddTouch", "(ILjava/lang/String;)V", SDL_JAVA_INTERFACE(nativeAddTouch) },""",
"""    { "nativeAddTouch", "(ILjava/lang/String;)V", SDL_JAVA_INTERFACE(nativeAddTouch) },
    /* RAL: 虚拟控件触点消费 */
    { "nativeConsumeFingerTouch", "(I)V", SDL_JAVA_INTERFACE(nativeConsumeFingerTouch) },
    { "nativeReleaseFingerTouch", "(I)V", SDL_JAVA_INTERFACE(nativeReleaseFingerTouch) },
    { "nativeClearConsumedFingers", "()V", SDL_JAVA_INTERFACE(nativeClearConsumedFingers) },""", "补丁6a JNI 表")

patch(SRC_A,
"""JNIEXPORT void JNICALL SDL_JAVA_INTERFACE(onNativeKeyDown)(
    JNIEnv *env, jclass jcls,
    jint keycode);""",
"""/** RAL: 虚拟控件触点消费（实现见 events/SDL_touch.c） */
JNIEXPORT void JNICALL SDL_JAVA_INTERFACE(nativeConsumeFingerTouch)(
    JNIEnv *env, jclass jcls,
    jint fingerId);

JNIEXPORT void JNICALL SDL_JAVA_INTERFACE(nativeReleaseFingerTouch)(
    JNIEnv *env, jclass jcls,
    jint fingerId);

JNIEXPORT void JNICALL SDL_JAVA_INTERFACE(nativeClearConsumedFingers)(
    JNIEnv *env, jclass jcls);

JNIEXPORT void JNICALL SDL_JAVA_INTERFACE(onNativeKeyDown)(
    JNIEnv *env, jclass jcls,
    jint keycode);""", "补丁6b 前置声明")

patch(SRC_A,
"""JNIEXPORT void JNICALL SDL_JAVA_INTERFACE(onNativeKeyDown)(
    JNIEnv *env, jclass jcls,
    jint keycode)
{
    Android_OnKeyDown(keycode);
}""",
"""JNIEXPORT void JNICALL SDL_JAVA_INTERFACE(onNativeKeyDown)(
    JNIEnv *env, jclass jcls,
    jint keycode)
{
    Android_OnKeyDown(keycode);
}

JNIEXPORT void JNICALL SDL_JAVA_INTERFACE(nativeConsumeFingerTouch)(
    JNIEnv *env, jclass jcls,
    jint fingerId)
{
    (void)env; (void)jcls;
    SDL_ConsumeFingerTouch((int)fingerId);
}

JNIEXPORT void JNICALL SDL_JAVA_INTERFACE(nativeReleaseFingerTouch)(
    JNIEnv *env, jclass jcls,
    jint fingerId)
{
    (void)env; (void)jcls;
    SDL_ReleaseFingerTouch((int)fingerId);
}

JNIEXPORT void JNICALL SDL_JAVA_INTERFACE(nativeClearConsumedFingers)(
    JNIEnv *env, jclass jcls)
{
    (void)env; (void)jcls;
    SDL_ClearConsumedFingers();
}""", "补丁6c JNI 实现")

patch(SRC_A,
"""#include "SDL_android.h"
""",
"""#include "SDL_android.h"
#include "../../events/SDL_touch_c.h"
""", "补丁6d include")

# ---- 补丁9：沿用 SDL3 Android 后端的真实交换帧采样，供现有 FPSDisplayView 读取 ----
FPS_TRACKING = '''
/* RAL: Adapted from core/libs/SDL/src/video/android/SDL_androidgl.c (SDL3).
 * Use successful game buffer swaps, not the Android UI refresh rate. */
#include <time.h>

#define FPS_SAMPLE_COUNT 5
#define FPS_UPDATE_INTERVAL_NS 100000000ULL

static Uint64 frame_times[FPS_SAMPLE_COUNT];
static int frame_index;
static int frame_count;
static Uint64 last_update_ns;
static float fps_smoothed;

static void Android_UpdateFPS(void)
{
    struct timespec ts;
    Uint64 current_ns;
    Uint64 window_duration_ns;
    int oldest_index;
    int newest_index;
    int frame_intervals;
    float fps_current;
    float frame_time_ms;
    char fps_env_buffer[64];

    clock_gettime(CLOCK_MONOTONIC, &ts);
    current_ns = (Uint64)ts.tv_sec * 1000000000ULL + (Uint64)ts.tv_nsec;

    /* Discard pre-pause samples instead of counting time spent in background. */
    if (frame_count > 0 &&
        current_ns - frame_times[(frame_index + FPS_SAMPLE_COUNT - 1) % FPS_SAMPLE_COUNT] > 1000000000ULL) {
        frame_count = 0;
        frame_index = 0;
        fps_smoothed = 0.0f;
        last_update_ns = 0;
    }

    frame_times[frame_index] = current_ns;
    frame_index = (frame_index + 1) % FPS_SAMPLE_COUNT;
    if (frame_count < FPS_SAMPLE_COUNT) {
        ++frame_count;
    }
    if (frame_count < 2 || current_ns - last_update_ns < FPS_UPDATE_INTERVAL_NS) {
        return;
    }
    last_update_ns = current_ns;

    oldest_index = (frame_index - frame_count + FPS_SAMPLE_COUNT) % FPS_SAMPLE_COUNT;
    newest_index = (frame_index - 1 + FPS_SAMPLE_COUNT) % FPS_SAMPLE_COUNT;
    window_duration_ns = frame_times[newest_index] - frame_times[oldest_index];
    if (window_duration_ns == 0) {
        return;
    }
    frame_intervals = frame_count - 1;
    fps_current = (float)frame_intervals * 1000000000.0f / (float)window_duration_ns;
    frame_time_ms = (float)window_duration_ns / (float)frame_intervals / 1000000.0f;
    fps_smoothed = (fps_smoothed <= 0.0f) ? fps_current : (fps_smoothed * 0.5f + fps_current * 0.5f);

    SDL_snprintf(fps_env_buffer, sizeof(fps_env_buffer), "%.1f", fps_smoothed);
    SDL_setenv("RAL_FPS", fps_env_buffer, 1);
    SDL_snprintf(fps_env_buffer, sizeof(fps_env_buffer), "%.2f", frame_time_ms);
    SDL_setenv("RAL_FRAME_TIME", fps_env_buffer, 1);
}
'''

patch(SRC_GL,
"""#include <dlfcn.h>
""",
"""#include <dlfcn.h>
""" + FPS_TRACKING, "补丁9a FPS 采样")

patch(SRC_GL,
"""    retval = SDL_EGL_SwapBuffers(_this, ((SDL_WindowData *)window->driverdata)->egl_surface);
""",
"""    retval = SDL_EGL_SwapBuffers(_this, ((SDL_WindowData *)window->driverdata)->egl_surface);
    if (retval == 0) {
        Android_UpdateFPS();
    }
""", "补丁9b 记录成功交换帧")
EOPY
}

# 官方 androidbuildlibs.sh（ndk-build）构建 SDL2 本体。
# 相比 autotools 的额外好处：产物天然叫 libSDL2.so（无 libSDL2.so.0 版本后缀，
# bionic 按文件名解析 DT_NEEDED 时不需 patchelf），且只带 Android 平台驱动。
step_sdl2ndk() {
  echo "=== [2] SDL2 2.32.10 官方 androidbuildlibs.sh (ndk-build) ==="
  cd "$SRC/SDL2-2.32.10"
  export ANDROID_NDK_HOME="$NDK"
  export PATH="$NDK:$PATH"
  rm -rf build/android
  mkdir -p "$WORK/ndklibs"
  bash build-scripts/androidbuildlibs.sh \
    NDK_LIBS_OUT="$WORK/ndklibs" \
    APP_ABI=arm64-v8a \
    APP_PLATFORM=android-$API
  echo "  产物: $WORK/ndklibs/arm64-v8a/libSDL2.so (SONAME=$(patchelf --print-soname "$WORK/ndklibs/arm64-v8a/libSDL2.so"))"
}

STEPS=${STEPS:-"sdl2_patches sdl2ndk"}
for s in $STEPS; do "step_$s"; done
echo "=== 全部完成 ==="
ls -la "$WORK/ndklibs/arm64-v8a/libSDL2.so"
