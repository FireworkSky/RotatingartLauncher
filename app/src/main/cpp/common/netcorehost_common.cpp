/**
 * @file common/netcorehost_common.cpp
 * @brief .NET Core Host 公共功能实现
 */

#include "common/netcorehost_common.h"
#include "corehost_trace_redirect.h"
#include "app_logger.h"
#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <cassert>
#include <format>
#include <string>
#include <sys/stat.h>

// 直接声明 JNI Bridge 函数
extern "C" {
JNIEnv* Bridge_GetJNIEnv();
JavaVM* Bridge_GetJavaVM();
}

#define LOG_TAG "NetCoreCommon"

/**
 * @brief 获取包名（从环境变量）
 */
const char* netcorehost_common_get_package_name(void) {
    const char* package_name_cstr = getenv("PACKAGE_NAME");
    if (!package_name_cstr) {
        LOGE(LOG_TAG, "PACKAGE_NAME environment variable not set");
        return nullptr;
    }
    return package_name_cstr;
}

/**
 * @brief 初始化 .NET 运行时环境变量
 */
int netcorehost_common_setup_env(const netcorehost_env_config_t* config) {
    if (!config) {
        LOGE(LOG_TAG, "config is null");
        return -1;
    }

    // 1. 设置 DOTNET_ROOT
    if (config->dotnet_root) {
        setenv("DOTNET_ROOT", config->dotnet_root, 1);
        LOGI(LOG_TAG, "DOTNET_ROOT=%s", config->dotnet_root);
    }

    // 2. 设置运行时策略
    if (config->framework_major > 0) {
        setenv("DOTNET_ROLL_FORWARD", "LatestMajor", 1);
        setenv("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "2", 1);
        setenv("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1", 1);
        LOGI(LOG_TAG, "Roll forward policy: LatestMajor (net%d.x)", config->framework_major);
    } else {
        setenv("DOTNET_ROLL_FORWARD", "LatestMajor", 1);
        setenv("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "2", 1);
        setenv("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1", 1);
        LOGI(LOG_TAG, "Roll forward policy: automatic (latest version)");
    }

    // 3. 设置调试输出
    setenv("COMPlus_DebugWriteToStdErr", "1", 1);

    // 4. 设置 COREHOST_TRACE
    if (config->enable_corehost_trace) {
        init_corehost_trace_redirect();
        LOGI(LOG_TAG, "COREHOST_TRACE redirect initialized");

        const char* package_name = netcorehost_common_get_package_name();
        if (package_name) {
            std::string trace_file = std::format("/sdcard/Android/data/{}/files/corehost_trace.log", package_name);
            setenv("COREHOST_TRACEFILE", trace_file.c_str(), 1);
            setenv("COREHOST_TRACE", "1", 1);
            LOGI(LOG_TAG, "COREHOST_TRACE enabled, log file: %s", trace_file.c_str());
        } else {
            setenv("COREHOST_TRACE", "1", 1);
            LOGI(LOG_TAG, "COREHOST_TRACE enabled (no trace file)");
        }
    } else {
        unsetenv("COREHOST_TRACE");
        LOGI(LOG_TAG, "COREHOST_TRACE disabled");
    }

    // 5. 设置保存目录（游戏数据目录，不是游戏安装目录）
    const char* game_data_dir = "/storage/emulated/0/RALauncher";
    // 确保目录存在
    struct stat st;
    if (stat(game_data_dir, &st) != 0) {
        // 目录不存在，尝试创建
        mode_t mode = S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH;
        if (mkdir(game_data_dir, mode) != 0) {
            LOGW(LOG_TAG, "Failed to create game data directory: %s, using app_dir as fallback", game_data_dir);
            game_data_dir = config->app_dir;
        } else {
            LOGI(LOG_TAG, "Created game data directory: %s", game_data_dir);
        }
    } else {
        LOGI(LOG_TAG, "Using game data directory: %s", game_data_dir);
    }
    
    setenv("XDG_DATA_HOME", game_data_dir, 1);
    setenv("XDG_CONFIG_HOME", game_data_dir, 1);
    setenv("HOME", game_data_dir, 1);
    LOGI(LOG_TAG, "Game data directories set to: %s", game_data_dir);
    if (config->app_dir) {
        LOGI(LOG_TAG, "App directory (for assemblies): %s", config->app_dir);
    }

    // 6. 设置输入相关
    setenv("SDL_TOUCH_MOUSE_EVENTS", "1", 1);

    // 7. 设置 DOTNET_STARTUP_HOOKS（如果提供）
    if (config->startup_hooks_dll && strlen(config->startup_hooks_dll) > 0) {
        setenv("DOTNET_STARTUP_HOOKS", config->startup_hooks_dll, 1);
        LOGI(LOG_TAG, "DOTNET_STARTUP_HOOKS=%s", config->startup_hooks_dll);
    }

    // 8. 设置 Android Context 环境变量（用于加密库）
    // 加密库需要访问 Android Context，我们通过环境变量传递包名
    const char* package_name = netcorehost_common_get_package_name();
    if (package_name) {
        setenv("ANDROID_PACKAGE_NAME", package_name, 1);
        LOGI(LOG_TAG, "ANDROID_PACKAGE_NAME=%s", package_name);
    }

    return 0;
}

/**
 * @brief 初始化 JNI Bridge
 */
int netcorehost_common_init_jni_bridge(const char* log_tag) {
    if (!log_tag) {
        log_tag = LOG_TAG;
    }

    LOGI(log_tag, "Initializing JNI Bridge...");
    JavaVM* jvm = Bridge_GetJavaVM();
    JNIEnv* env = nullptr;

    if (jvm) {
        env = Bridge_GetJNIEnv();
        if (env) {
            LOGI(log_tag, "JNI Bridge initialized, JavaVM: %p, JNIEnv: %p", jvm, env);
            
            // 重要：确保当前线程已附加到 JVM，并且 JNI 环境已正确设置
            // 这对于 .NET 加密库（System.Security.Cryptography.Native.Android）至关重要
            // 加密库需要 JNI 环境来调用 Android KeyStore API
            // 在后台线程中运行时，必须确保线程已附加到 JVM
            // 注意：在 C++ 中，JavaVM 是结构体，需要使用 jvm->GetEnv 而不是 (*jvm)->GetEnv
            JNIEnv* verify_env = nullptr;
            jint result = jvm->GetEnv((void**)&verify_env, JNI_VERSION_1_6);
            if (result == JNI_EDETACHED) {
                LOGW(log_tag, "Thread not attached, this should not happen after Bridge_GetJNIEnv()");
            } else if (result == JNI_OK) {
                LOGI(log_tag, "JNI environment verified, thread is attached to JVM");
            } else {
                LOGW(log_tag, "JNI environment check returned: %d", result);
            }
            
            return 0;
        } else {
            LOGW(log_tag, "JNI Bridge initialized but cannot get JNIEnv");
            return -1;
        }
    } else {
        LOGW(log_tag, "JavaVM not initialized, some .NET features may not work");
        return -1;
    }
}

/**
 * @brief 设置 COREHOST_TRACE
 */
int netcorehost_common_set_corehost_trace(bool enabled, const char* log_tag) {
    if (!log_tag) {
        log_tag = LOG_TAG;
    }

    if (enabled) {
        init_corehost_trace_redirect();
        LOGI(log_tag, "COREHOST_TRACE redirect initialized");

        const char* package_name = netcorehost_common_get_package_name();
        if (package_name) {
            std::string trace_file = std::format("/sdcard/Android/data/{}/files/corehost_trace.log", package_name);
            setenv("COREHOST_TRACEFILE", trace_file.c_str(), 1);
            setenv("COREHOST_TRACE", "1", 1);
            LOGI(log_tag, "COREHOST_TRACE enabled, log file: %s", trace_file.c_str());
        } else {
            setenv("COREHOST_TRACE", "1", 1);
            LOGI(log_tag, "COREHOST_TRACE enabled (no trace file)");
        }
    } else {
        unsetenv("COREHOST_TRACE");
        LOGI(log_tag, "COREHOST_TRACE disabled");
    }

    return 0;
}

