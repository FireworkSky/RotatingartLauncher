/**
 * @file netcorehost_launcher.cpp
 * @brief 简化的 .NET 启动器实现（直接使用 run_app）
 * 
 * 此文件实现了简化的 .NET 应用启动流程，直接使用 hostfxr->run_app()
 */

#include "netcorehost_launcher.h"
#include "corehost_trace_redirect.h"
#include "thread_affinity_manager.h"
#include <netcorehost/nethost.hpp>
#include <netcorehost/hostfxr.hpp>
#include <netcorehost/context.hpp>
#include <netcorehost/error.hpp>
#include <netcorehost/bindings.hpp>
#include <netcorehost/delegate_loader.hpp>
#include <jni.h>

// 直接声明静态链接的 nethost 函数
extern "C" {
int32_t get_hostfxr_path(
        char* buffer,
        size_t* buffer_size,
        const netcorehost::bindings::get_hostfxr_parameters* parameters
);
JNIEnv* Bridge_GetJNIEnv();
JavaVM* Bridge_GetJavaVM();
}

#include <jni.h>
#include <android/log.h>
#include <cstdlib>
#include <cstring>
#include <format>
#include <unistd.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <string>
#include <cassert>
#include "app_logger.h"

#define LOG_TAG "NetCoreHost"

// 全局参数（简化版）
static char* g_app_path = nullptr;
static char* g_dotnet_path = nullptr;
static int g_framework_major = 0;
static char* g_startup_hooks_dll = nullptr;
static bool g_enable_corehost_trace = false;

// 错误消息缓冲区
static char g_last_error[1024] = {0};

/**
 * @brief 辅助函数：复制字符串
 */
static char* str_dup(const char* str) {
    if (!str) return nullptr;
    return strdup(str);
}

/**
 * @brief 辅助函数：释放字符串
 */
static void str_free(char*& str) {
    if (str) {
        free(str);
        str = nullptr;
    }
}

static std::string get_package_name() {
    const char *package_name_cstr = getenv("PACKAGE_NAME"); // RaLaunchApplication.java 中设置了
    assert(package_name_cstr != nullptr);
    return {package_name_cstr};
}

static bool is_set_thread_affinity_to_big_core() {
    const char *env_value = getenv("SET_THREAD_AFFINITY_TO_BIG_CORE");
    return (env_value != nullptr) && (strcmp(env_value, "1") == 0);
}

/**
 * @brief 设置启动参数
 */
int netcorehost_set_params(
        const char* app_dir,
        const char* main_assembly,
        const char* dotnet_root,
        int framework_major) {

    // 1. 保存 .NET 路径
    str_free(g_dotnet_path);
    g_dotnet_path = str_dup(dotnet_root);
    g_framework_major = framework_major;

    // 2. 构建完整程序集路径
    std::string app_path_str = std::string(app_dir) + "/" + std::string(main_assembly);
    str_free(g_app_path);
    g_app_path = str_dup(app_path_str.c_str());

    LOGI(LOG_TAG, "  App directory: %s", app_dir);
    LOGI(LOG_TAG, "  Main assembly: %s", main_assembly);
    LOGI(LOG_TAG, "  Full path: %s", g_app_path);
    LOGI(LOG_TAG, "  .NET path: %s", g_dotnet_path ? g_dotnet_path : "(auto-detect)");
    LOGI(LOG_TAG, "  Framework version: %d.x (reference only)", framework_major);
    LOGI(LOG_TAG, "========================================");
    if (access(g_app_path, F_OK) != 0) {
        LOGE(LOG_TAG, "Assembly file does not exist: %s", g_app_path);
        return -1;
    }
    if (g_dotnet_path) {
        setenv("DOTNET_ROOT", g_dotnet_path, 1);
        LOGI(LOG_TAG, "DOTNET_ROOT environment variable set: %s", g_dotnet_path);
    }
    LOGI(LOG_TAG, "Framework version parameter: framework_major=%d", framework_major);

    if (framework_major > 0) {
        std::string versioned_dotnet_root = std::string(g_dotnet_path);
        setenv("DOTNET_ROLL_FORWARD", "LatestMajor", 1);
        setenv("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "2", 1);
        setenv("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1", 1);

        LOGI(LOG_TAG, "Set forced latest runtime mode: will use net%d.x", framework_major);
        LOGI(LOG_TAG, "   (LatestMajor: force use highest available version)");
    } else {
        // 自动模式，允许使用任何兼容版本
        setenv("DOTNET_ROLL_FORWARD", "LatestMajor", 1);
        setenv("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "2", 1);
        setenv("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1", 1);
        LOGI(LOG_TAG, "Set automatic version mode (use latest available runtime, including prerelease)");
    }
    setenv("COMPlus_DebugWriteToStdErr", "1", 1);
    if (g_enable_corehost_trace) {
        setenv("COREHOST_TRACE", "1", 1);
        setenv("COREHOST_TRACEFILE", std::format("/sdcard/Android/data/{}/files/corehost_trace.log", get_package_name()).c_str(), 1);
    }
    setenv("XDG_DATA_HOME", std::string(app_dir).c_str(), 1);
    setenv("XDG_CONFIG_HOME", std::string(app_dir).c_str(), 1);
    setenv("HOME", std::string(app_dir).c_str(), 1);


    return 0;
}
/**
 * @brief 启动 .NET 应用
 */
int netcorehost_launch() {
    if (!g_app_path) {
        LOGE(LOG_TAG, "Error: Application path not set! Please call netcorehostSetParams() first");
        return -1;
    }
    if (is_set_thread_affinity_to_big_core()) {
        LOGI(LOG_TAG, "Setting thread affinity to big cores");
        setThreadAffinityToBigCores();
    }
    LOGI(LOG_TAG, " Starting .NET application");
    LOGI(LOG_TAG, "  Assembly: %s", g_app_path);
    LOGI(LOG_TAG, "  .NET path: %s", g_dotnet_path ? g_dotnet_path : "(environment variable)");

    // 设置工作目录为程序集所在目录，以便 .NET 能找到依赖的程序集
    std::string app_dir = g_app_path;
    size_t last_slash = app_dir.find_last_of("/\\");
    if (last_slash != std::string::npos) {
        app_dir = app_dir.substr(0, last_slash);
        if (chdir(app_dir.c_str()) == 0) {
            LOGI(LOG_TAG, "  Working directory: %s", app_dir.c_str());
        } else {
            LOGW(LOG_TAG, "Cannot set working directory: %s", app_dir.c_str());
        }
    }
    LOGI(LOG_TAG, "Initializing JNI Bridge...");
    JavaVM* jvm = Bridge_GetJavaVM();
    JNIEnv* env = nullptr;
    if (jvm) {
        env = Bridge_GetJNIEnv();
        if (env) {
            LOGI(LOG_TAG, "JNI Bridge initialized, JavaVM: %p, JNIEnv: %p", jvm, env);
        } else {
            LOGW(LOG_TAG, "JNI Bridge initialized but cannot get JNIEnv");
        }
    } else {
        LOGW(LOG_TAG, "JavaVM not initialized, some .NET features may not work");
    }
    std::shared_ptr<netcorehost::Hostfxr> hostfxr;
    try {
        // 根据设置决定是否初始化 COREHOST_TRACE 重定向
        if (g_enable_corehost_trace) {
            init_corehost_trace_redirect();
            LOGI(LOG_TAG, "COREHOST_TRACE redirect initialized");
            // 启用 COREHOST_TRACE 以便捕获所有 .NET runtime 的 trace 输出
            setenv("COREHOST_TRACEFILE", std::format("/sdcard/Android/data/{}/files/corehost_trace.log", get_package_name()).c_str(), 1);
            LOGI(LOG_TAG, std::format("COREHOST_TRACE enabled, log file: /sdcard/Android/data/{}/files/corehost_trace.log", get_package_name()).c_str());
            setenv("COREHOST_TRACE", "1", 1);
            LOGI(LOG_TAG, "COREHOST_TRACE enabled");
        } else {
            unsetenv("COREHOST_TRACE");
            LOGI(LOG_TAG, "COREHOST_TRACE disabled (verbose logging off)");
        }
        if (g_startup_hooks_dll != nullptr && strlen(g_startup_hooks_dll) > 0) {
            setenv("DOTNET_STARTUP_HOOKS", g_startup_hooks_dll, 1);
            LOGI(LOG_TAG, "Set DOTNET_STARTUP_HOOKS=%s", g_startup_hooks_dll);
            LOGI(LOG_TAG, "StartupHook patch will execute automatically before app Main()");
        } else {
            LOGI(LOG_TAG, "DOTNET_STARTUP_HOOKS not set, skipping patch loading");
        }
        LOGI(LOG_TAG, "Loading hostfxr...");
        hostfxr = netcorehost::Nethost::load_hostfxr();

        if (!hostfxr) {
            LOGE(LOG_TAG, "hostfxr loading failed: returned null pointer");
            return -1;
        }
        LOGI(LOG_TAG, "hostfxr loaded successfully");
        // 初始化 .NET 运行时
        LOGI(LOG_TAG, "Initializing .NET runtime...");
        auto app_path_str = netcorehost::PdCString::from_str(g_app_path);

        std::unique_ptr<netcorehost::HostfxrContextForCommandLine> context;

        if (g_dotnet_path) {
            auto dotnet_root_str = netcorehost::PdCString::from_str(g_dotnet_path);
            context = hostfxr->initialize_for_dotnet_command_line_with_dotnet_root(
                    app_path_str, dotnet_root_str);
        } else {
            context = hostfxr->initialize_for_dotnet_command_line(app_path_str);
        }
        if (!context) {
            LOGE(LOG_TAG, ".NET runtime initialization failed");
            return -1;
        }
        LOGI(LOG_TAG, ".NET runtime initialized successfully");
        // 获取委托加载器（用于加载游戏）
        LOGI(LOG_TAG, "Getting delegate loader...");
        auto loader = context->get_delegate_loader();
        LOGI(LOG_TAG, "Running application...");
        auto app_result = context->run_app();
        int32_t exit_code = app_result.value();
        if (exit_code == 0) {
            LOGI(LOG_TAG, "Application exited normally");
            g_last_error[0] = '\0';  // 清空错误消息
        } else if (exit_code < 0) {
            auto hosting_result = app_result.as_hosting_result();
            std::string error_msg = hosting_result.get_error_message();
            LOGE(LOG_TAG, "Hosting error (code: %d)", exit_code);
            LOGE(LOG_TAG, "  %s", error_msg.c_str());
            // 保存错误消息
            snprintf(g_last_error, sizeof(g_last_error), "%s", error_msg.c_str());
        } else {
            LOGW(LOG_TAG, "Application exit code: %d", exit_code);
            g_last_error[0] = '\0';  // 清空错误消息
        }
        return exit_code;

    } catch (const netcorehost::HostingException& ex) {
        LOGE(LOG_TAG, "Hosting error");
        LOGE(LOG_TAG, "  %s", ex.what());
        // 保存错误消息
        snprintf(g_last_error, sizeof(g_last_error), "Hosting error: %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        LOGE(LOG_TAG, "Unexpected error");
        LOGE(LOG_TAG, "  %s", ex.what());
        snprintf(g_last_error, sizeof(g_last_error), "Unexpected error: %s", ex.what());
        return -2;
    }
}

/**
 * @brief 获取最后一次错误的详细消息
 */
const char* netcorehost_get_last_error() {
    if (g_last_error[0] == '\0') {
        return nullptr;
    }
    return g_last_error;
}

/**
 * @brief 清理资源
 */
void netcorehost_cleanup() {
    str_free(g_app_path);
    str_free(g_dotnet_path);
    g_last_error[0] = '\0';  // 清空错误消息
    LOGI(LOG_TAG, "Cleanup complete");
}
/**
 * @brief JNI 函数：设置启动参数
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_GameLauncher_netcorehostSetParams(
        JNIEnv *env, jclass clazz,
        jstring appDir, jstring mainAssembly, jstring dotnetRoot, jint frameworkMajor) {

    const char *app_dir = env->GetStringUTFChars(appDir, nullptr);
    const char *main_assembly = env->GetStringUTFChars(mainAssembly, nullptr);
    const char *dotnet_root = dotnetRoot ? env->GetStringUTFChars(dotnetRoot, nullptr) : nullptr;

    int result = netcorehost_set_params(app_dir, main_assembly, dotnet_root, frameworkMajor);

    env->ReleaseStringUTFChars(appDir, app_dir);
    env->ReleaseStringUTFChars(mainAssembly, main_assembly);
    if (dotnet_root) env->ReleaseStringUTFChars(dotnetRoot, dotnet_root);

    return result;
}
/**
 * @brief JNI 函数：设置 DOTNET_STARTUP_HOOKS 补丁路径
 */
extern "C" JNIEXPORT void JNICALL
Java_com_app_ralaunch_core_GameLauncher_netcorehostSetStartupHooks(
        JNIEnv *env, jclass clazz, jstring startupHooksDll) {

    // 释放旧的路径
    if (g_startup_hooks_dll) {
        free(g_startup_hooks_dll);
        g_startup_hooks_dll = nullptr;
    }

    // 设置新的路径
    if (startupHooksDll != nullptr) {
        const char *dll_path = env->GetStringUTFChars(startupHooksDll, nullptr);
        g_startup_hooks_dll = str_dup(dll_path);
        env->ReleaseStringUTFChars(startupHooksDll, dll_path);

        LOGI(LOG_TAG, "Set StartupHooks DLL: %s", g_startup_hooks_dll);
    } else {
        LOGI(LOG_TAG, "Clear StartupHooks DLL");
    }
}
/**
 * @brief JNI 函数：设置是否启用 COREHOST_TRACE
 */
extern "C" JNIEXPORT void JNICALL
Java_com_app_ralaunch_core_GameLauncher_netcorehostSetCorehostTrace(
        JNIEnv *env, jclass clazz, jboolean enabled) {
    g_enable_corehost_trace = (enabled == JNI_TRUE);
    LOGI(LOG_TAG, "COREHOST_TRACE setting: %s", g_enable_corehost_trace ? "enabled" : "disabled");
}
/**
 * @brief JNI 函数：启动应用
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_GameLauncher_netcorehostLaunch(JNIEnv *env, jclass clazz) {
    return netcorehost_launch();
}
/**
 * @brief JNI 函数：清理资源
 */
extern "C" JNIEXPORT void JNICALL
Java_com_app_ralaunch_core_GameLauncher_netcorehostCleanup(JNIEnv *env, jclass clazz) {
    netcorehost_cleanup();
}
/**
 * @brief JNI 函数：设置环境变量（用于 CoreCLR 配置）
 */
extern "C" JNIEXPORT void JNICALL
Java_com_app_ralaunch_utils_CoreCLRConfig_nativeSetEnv(
        JNIEnv *env, jclass clazz, jstring key, jstring value) {

    const char *key_str = env->GetStringUTFChars(key, nullptr);
    const char *value_str = env->GetStringUTFChars(value, nullptr);
    setenv(key_str, value_str, 1);
    LOGI(LOG_TAG, "  %s = %s", key_str, value_str);

    env->ReleaseStringUTFChars(key, key_str);
    env->ReleaseStringUTFChars(value, value_str);
}