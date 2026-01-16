/*
 * Box64 Launcher for RotatingartLauncher
 * 
 * 基于 box64droid 的实现方式，直接调用 Box64 的 main 函数
 * 
 * Architecture:
 * - Box64 is compiled as Bionic SO and linked directly into the app
 * - Box64's wrapped libraries use glibc_bridge's dlopen_wrapper for lib redirection
 * - JNI calls from SDL work correctly because Box64 runs in SDL thread
 */

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sys/stat.h>
#include <vector>
#include <string>
#include <locale.h>
#include <locale>
#include <ios>

// Box64 的 main 函数入口点 (定义在 main.c 中)
extern "C" {
    int main(int argc, const char **argv, char **env);
    
    // Box64 的 glibc_bridge 钩子设置函数 (定义在 wrappedlibdl.c 中)
    typedef void* (*glibc_bridge_dlopen_fn)(const char* filename, int flags);
    typedef void* (*glibc_bridge_dlsym_fn)(void* handle, const char* symbol);
    void box64_set_glibc_bridge_hooks(glibc_bridge_dlopen_fn dlopen_hook, glibc_bridge_dlsym_fn dlsym_hook);
    
    // glibc_bridge 提供的 dlopen/dlsym 函数 (定义在 wrapper_libc.c 中)
    void* glibc_bridge_dlopen_for_box64(const char* filename, int flags);
    void* glibc_bridge_dlsym_for_box64(void* handle, const char* symbol);
    
    // glibc_bridge 的 rootfs 路径 (定义在 glibc_bridge_sharedlib.c 中)
    extern char g_glibc_root[512];
}

#define LOG_TAG "Box64Launcher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Set up Box64 environment variables
 */
static void setup_box64_environment(const char* rootfs_path, const char* game_dir) {
    // Box64 settings - 启用调试日志
    setenv("BOX64_LOG", "1", 1);
    setenv("BOX64_SHOWSEGV", "1", 1);
    setenv("BOX64_SHOWBT", "1", 1);
    setenv("BOX64_SHOWSIGNALS", "1", 1);
    setenv("BOX64_ALLOWMISSINGLIBS", "1", 1);
    setenv("BOX64_DYNAREC", "1", 1);
    setenv("BOX64_TRACE", "0", 1);
    
    // Library search path
    std::string rootfs_str(rootfs_path);
    std::string files_dir = rootfs_str.substr(0, rootfs_str.rfind('/'));
    
    // Set TMPDIR
    std::string tmp_dir = rootfs_str + "/tmp";
    mkdir(tmp_dir.c_str(), 0755);
    setenv("TMPDIR", tmp_dir.c_str(), 1);
    setenv("TMP", tmp_dir.c_str(), 1);
    setenv("TEMP", tmp_dir.c_str(), 1);
    
    std::string x64lib_path = files_dir + "/x64lib";
    std::string ld_library_path = std::string(rootfs_path) + "/usr/lib/x86_64-linux-gnu";
    ld_library_path += ":" + x64lib_path;
    if (game_dir) {
        ld_library_path += ":";
        ld_library_path += game_dir;
    }
    setenv("BOX64_LD_LIBRARY_PATH", ld_library_path.c_str(), 1);
    
    // SDL Audio - 强制使用 Android 音频驱动，禁用 ALSA
    setenv("SDL_AUDIODRIVER", "android", 1);
    setenv("SDL_AUDIO_ALSA", "0", 1);  // 禁用 ALSA
    
    // SDL 输入设置 - 确保触摸事件正确转换为鼠标事件
    setenv("SDL_MOUSE_TOUCH_EVENTS", "1", 1);
    setenv("SDL_TOUCH_MOUSE_EVENTS", "1", 1);
    setenv("SDL_VIDEO_X11_MOUSEACCEL", "0", 1);
    
    // FMOD 音频 - 禁用 ALSA，使用 SDL
    setenv("FMOD_FORCEFMOD_AUDIOAPI", "0", 1);
    
    // Locale - UTF-8 支持中文
    setlocale(LC_ALL, "C.UTF-8");
    setenv("LC_ALL", "C.UTF-8", 1);
    setenv("LANG", "C.UTF-8", 1);
    setenv("LC_CTYPE", "C.UTF-8", 1);
    
    LOGI("Box64 environment configured");
}

/**
 * JNI function: Run Box64 directly in the current process
 * 基于 box64droid 的实现方式
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_app_ralaunch_box64_Box64Helper_runBox64InProcess(
    JNIEnv* env,
    jclass clazz,
    jobjectArray jargs,
    jstring jworkDir
) {
    // 设置 C++ locale
    try {
        try {
            std::locale::global(std::locale("C.UTF-8"));
        } catch (...) {
            std::locale::global(std::locale::classic());
        }
        std::ios::sync_with_stdio(false);
    } catch (...) {}
    
    int argc = env->GetArrayLength(jargs);
    if (argc == 0) {
        LOGE("No arguments provided");
        return -1;
    }
    
    // Get work directory
    const char* workDirCStr = jworkDir ? env->GetStringUTFChars(jworkDir, nullptr) : nullptr;
    std::string workDir = workDirCStr ? std::string(workDirCStr) : "";
    if (workDirCStr) {
        env->ReleaseStringUTFChars(jworkDir, workDirCStr);
    }
    
    LOGI("Box64 Launcher starting");
    
    // 切换工作目录
    if (!workDir.empty()) {
        if (chdir(workDir.c_str()) != 0) {
            LOGE("Failed to chdir: %s", workDir.c_str());
            return -1;
        }
    }
    
    // Get rootfs path
    const char* rootfs_path = getenv("BOX64_ROOTFS");
    if (!rootfs_path) {
        rootfs_path = "/data/data/com.app.ralaunch/files/rootfs";
    }
    
    // Set up environment
    setup_box64_environment(rootfs_path, workDir.empty() ? nullptr : workDir.c_str());
    
    // ===== 关键：Box64 期望 argv 在连续内存块中 =====
    // 首先计算所有字符串的总长度
    size_t total_len = strlen("box64") + 1;  // argv[0] = "box64"
    
    // 获取所有游戏参数并计算长度
    std::vector<std::string> game_args;
    for (int i = 0; i < argc; i++) {
        jstring jstr = (jstring)env->GetObjectArrayElement(jargs, i);
        const char* str = env->GetStringUTFChars(jstr, nullptr);
        game_args.push_back(str);
        total_len += game_args[i].length() + 1;
        env->ReleaseStringUTFChars(jstr, str);
        env->DeleteLocalRef(jstr);
    }
    
    // 在连续内存块中分配所有字符串
    char* string_block = new char[total_len];
    char* current_pos = string_block;
    
    // 构建 argv 指针数组
    const char** argv = new const char*[argc + 3];  // +1 for "box64", +1 for nullptr, +1 spare
    
    // argv[0] = "box64"
    strcpy(current_pos, "box64");
    argv[0] = current_pos;
    current_pos += strlen("box64") + 1;
    
    // 复制游戏参数
    for (int i = 0; i < argc; i++) {
        strcpy(current_pos, game_args[i].c_str());
        argv[i + 1] = current_pos;
        current_pos += game_args[i].length() + 1;
    }
    argv[argc + 1] = nullptr;
    
    // 获取环境变量并过滤 NULL
    extern char** environ;
    std::vector<char*> filtered_env;
    if (environ) {
        for (char** env_ptr = environ; *env_ptr != nullptr; env_ptr++) {
            if (*env_ptr && strlen(*env_ptr) > 0) {
                filtered_env.push_back(*env_ptr);
            }
        }
    }
    filtered_env.push_back(nullptr);
    
    
    // 设置 glibc_bridge rootfs 路径
    strncpy(g_glibc_root, rootfs_path, sizeof(g_glibc_root) - 1);
    g_glibc_root[sizeof(g_glibc_root) - 1] = '\0';
    
    // 设置 glibc_bridge 钩子
    box64_set_glibc_bridge_hooks(glibc_bridge_dlopen_for_box64, glibc_bridge_dlsym_for_box64);
    
    // 调用 Box64 main
    int result = main(argc + 1, argv, filtered_env.data());
    
    // 清理内存
    delete[] string_block;
    delete[] argv;
    
    return result;
}

