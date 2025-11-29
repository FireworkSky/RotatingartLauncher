/**
 * @file netcorehost_launcher.cpp
 * @brief 简化的 .NET 启动器实现（直接使用 run_app）
 * 
 * 此文件实现了简化的 .NET 应用启动流程，直接使用 hostfxr->run_app()
 * 不再支持Bootstrap或补丁加载，所有程序集替换由MonoMod_Patch.zip在应用级别处理
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
static char* g_app_path = nullptr;           // 程序集完整路径
static char* g_dotnet_path = nullptr;        // .NET 运行时路径
static int g_framework_major = 0;            // 框架主版本号
static char* g_startup_hooks_dll = nullptr;  // DOTNET_STARTUP_HOOKS 补丁DLL路径
static bool g_enable_corehost_trace = false; // 是否启用 COREHOST_TRACE 重定向

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
 * @brief 设置启动参数（简化版 - 不再支持补丁）
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

    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "📝 Launch parameters set");
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  App directory: %s", app_dir);
    LOGI(LOG_TAG, "  Main assembly: %s", main_assembly);
    LOGI(LOG_TAG, "  Full path: %s", g_app_path);
    LOGI(LOG_TAG, "  .NET path: %s", g_dotnet_path ? g_dotnet_path : "(auto-detect)");
    LOGI(LOG_TAG, "  Framework version: %d.x (reference only)", framework_major);
    LOGI(LOG_TAG, "========================================");

    // 3. 验证程序集存在
    if (access(g_app_path, F_OK) != 0) {
        LOGE(LOG_TAG, "Assembly file does not exist: %s", g_app_path);
        return -1;
    }

    // 4. 设置 DOTNET_ROOT 环境变量（如果提供）
    if (g_dotnet_path) {
        setenv("DOTNET_ROOT", g_dotnet_path, 1);
        LOGI(LOG_TAG, "DOTNET_ROOT environment variable set: %s", g_dotnet_path);
    }

    // 5. 根据用户选择的框架版本设置运行时策略
    LOGI(LOG_TAG, "📋 Framework version parameter: framework_major=%d", framework_major);

    if (framework_major > 0) {
        // 策略：通过修改 DOTNET_ROOT 指向特定版本的运行时
        // 这样框架解析器只能看到我们指定的版本
        std::string versioned_dotnet_root = std::string(g_dotnet_path);

        // 注意：不修改 DOTNET_ROOT，而是依赖 hostfxr 的版本选择逻辑
        // 但我们强制使用 LatestMajor 来确保选择最高版本
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

    // 6. 启用详细日志（用于调试）
    if (g_enable_corehost_trace) {
        setenv("COREHOST_TRACE", "1", 1);
        setenv("COREHOST_TRACEFILE", std::format("/sdcard/Android/data/{}/files/corehost_trace.log", get_package_name()).c_str(), 1);
    }

    // 7. 设置保存目录
    setenv("XDG_DATA_HOME", std::string(app_dir).c_str(), 1);
    setenv("XDG_CONFIG_HOME", std::string(app_dir).c_str(), 1);
    setenv("HOME", std::string(app_dir).c_str(), 1);



    // 输入
    setenv("SDL_TOUCH_MOUSE_EVENTS", "0 ", 1);

    // 隐藏鼠标光标（如果Java层已设置，这里会覆盖；如果未设置，这里保持默认）
    // 注意：实际值由Java层在loadLibraries时设置
    const char* hideCursor = getenv("RALCORE_HIDE_CURSOR");
    if (hideCursor == nullptr) {
        // 如果未设置，默认不隐藏
        unsetenv("RALCORE_HIDE_CURSOR");
    }

    return 0;
}

/**
 * @brief 启动 .NET 应用（简化版 - 直接使用 run_app）
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

    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "🚀 Starting .NET application");
    LOGI(LOG_TAG, "========================================");
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

    // 注意：libhostpolicy.so 已通过源码修改支持向下兼容

    LOGI(LOG_TAG, "==========================================");
    // 初始化 JNI Bridge（在运行 .NET 程序集前）
    // 重要：.NET 加密库需要 JNI 环境来调用 Android KeyStore API
    LOGI(LOG_TAG, "Initializing JNI Bridge...");
    JavaVM* jvm = Bridge_GetJavaVM();
    JNIEnv* env = nullptr;
    if (jvm) {
        // 验证 JavaVM 已正确初始化
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

        // ======== 重要：在 hostfxr 初始化之前设置 DOTNET_STARTUP_HOOKS ========
        // 如果 Java 层传递了 startup hooks DLL 路径，设置环境变量
        if (g_startup_hooks_dll != nullptr && strlen(g_startup_hooks_dll) > 0) {
            setenv("DOTNET_STARTUP_HOOKS", g_startup_hooks_dll, 1);
            LOGI(LOG_TAG, "Set DOTNET_STARTUP_HOOKS=%s", g_startup_hooks_dll);
            LOGI(LOG_TAG, "StartupHook patch will execute automatically before app Main()");
        } else {
            LOGI(LOG_TAG, "DOTNET_STARTUP_HOOKS not set, skipping patch loading");
        }
        // ======== DOTNET_STARTUP_HOOKS 设置完成 ========

        // 加载 hostfxr（自动从 DOTNET_ROOT 环境变量读取）
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

        // 运行应用程序
        LOGI(LOG_TAG, "========================================");
        LOGI(LOG_TAG, "Running application...");
        LOGI(LOG_TAG, "========================================");

        auto app_result = context->run_app();
        int32_t exit_code = app_result.value();

        LOGI(LOG_TAG, "========================================");

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

        LOGI(LOG_TAG, "========================================");

        return exit_code;

    } catch (const netcorehost::HostingException& ex) {
        LOGE(LOG_TAG, "========================================");
        LOGE(LOG_TAG, "Hosting error");
        LOGE(LOG_TAG, "========================================");
        LOGE(LOG_TAG, "  %s", ex.what());
        LOGE(LOG_TAG, "========================================");
        // 保存错误消息
        snprintf(g_last_error, sizeof(g_last_error), "Hosting error: %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        LOGE(LOG_TAG, "========================================");
        LOGE(LOG_TAG, "Unexpected error");
        LOGE(LOG_TAG, "========================================");
        LOGE(LOG_TAG, "  %s", ex.what());
        LOGE(LOG_TAG, "========================================");
        // 保存错误消息
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
 * @brief JNI 函数：设置启动参数（简化版 - 4个参数）
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
 * @brief JNI 函数：调用补丁程序集方法
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_GameLauncher_netcorehostCallMethod(
        JNIEnv *env, jclass clazz,
        jstring appDir, jstring assemblyName, jstring typeName, jstring methodName, jint frameworkMajor) {

    const char *app_dir_str = env->GetStringUTFChars(appDir, nullptr);
    const char *assembly_name_str = env->GetStringUTFChars(assemblyName, nullptr);
    const char *type_name_str = env->GetStringUTFChars(typeName, nullptr);
    const char *method_name_str = env->GetStringUTFChars(methodName, nullptr);

    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "🔧 Calling patch method");
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  App directory: %s", app_dir_str);
    LOGI(LOG_TAG, "  Assembly: %s", assembly_name_str);
    LOGI(LOG_TAG, "  Type: %s", type_name_str);
    LOGI(LOG_TAG, "  Method: %s", method_name_str);
    LOGI(LOG_TAG, "========================================");

    int result = -1;

    try {
        // 构建程序集完整路径
        std::string assembly_path = std::string(app_dir_str) + "/" + std::string(assembly_name_str);

        LOGI(LOG_TAG, "Assembly path: %s", assembly_path.c_str());

        // 验证程序集文件存在
        if (access(assembly_path.c_str(), F_OK) != 0) {
            LOGE(LOG_TAG, "Assembly file does not exist: %s", assembly_path.c_str());
            result = -1;
            goto cleanup;
        }

        // 加载 hostfxr
        LOGI(LOG_TAG, "Loading hostfxr...");
        auto hostfxr = netcorehost::Nethost::load_hostfxr();

        if (!hostfxr) {
            LOGE(LOG_TAG, "hostfxr loading failed");
            result = -2;
            goto cleanup;
        }

        LOGI(LOG_TAG, "hostfxr loaded successfully");

        // 初始化运行时上下文
        LOGI(LOG_TAG, "Initializing runtime context...");

        // 设置 Core Host 跟踪日志
        if (g_enable_corehost_trace) {
            setenv("COREHOST_TRACEFILE", std::format("/sdcard/Android/data/{}/files/corehost_trace.log", get_package_name()).c_str(), 1);
            LOGI(LOG_TAG, "Set COREHOST_TRACEFILE environment variable");
        }

        auto assembly_path_pdc = netcorehost::PdCString::from_str(assembly_path.c_str());

        std::unique_ptr<netcorehost::HostfxrContextForRuntimeConfig> context;

        context = hostfxr->initialize_for_runtime_config(assembly_path_pdc);

        if (!context) {
            LOGE(LOG_TAG, "Runtime context initialization failed");
            result = -3;
            goto cleanup;
        }

        LOGI(LOG_TAG, "Runtime context initialized successfully");

        // 获取委托加载器
        LOGI(LOG_TAG, "Getting delegate loader...");
        auto loader = context->get_delegate_loader();

        if (!loader) {
            LOGE(LOG_TAG, "Failed to get delegate loader");
            result = -4;
            goto cleanup;
        }

        // 构造完整的类型名（包含程序集名称）
        std::string assembly_name_without_ext = std::string(assembly_name_str);
        size_t dot_pos = assembly_name_without_ext.find_last_of('.');
        if (dot_pos != std::string::npos) {
            assembly_name_without_ext = assembly_name_without_ext.substr(0, dot_pos);
        }

        std::string full_type_name = std::string(type_name_str) + ", " + assembly_name_without_ext;

        LOGI(LOG_TAG, "Full type name: %s", full_type_name.c_str());
        LOGI(LOG_TAG, "Method name: %s", method_name_str);

        auto type_name_pdc = netcorehost::PdCString::from_str(full_type_name.c_str());
        auto method_name_pdc = netcorehost::PdCString::from_str(method_name_str);

        // 获取方法指针（使用默认签名：int (void*, int)）
        typedef int (*component_entry_point_fn)(void* arg, int arg_size_in_bytes);
        component_entry_point_fn patch_method = nullptr;

        try {
            patch_method = loader->get_function_with_default_signature(
                    assembly_path_pdc,
                    type_name_pdc,
                    method_name_pdc
            );
        } catch (const netcorehost::HostingException& ex) {
            LOGE(LOG_TAG, "Failed to get method pointer: %s", ex.what());
            result = -5;
            goto cleanup;
        }

        if (!patch_method) {
            LOGE(LOG_TAG, "Method pointer is null");
            result = -6;
            goto cleanup;
        }

        LOGI(LOG_TAG, "Method pointer obtained successfully");

        // 调用补丁方法
        LOGI(LOG_TAG, "========================================");
        LOGI(LOG_TAG, "Calling patch method: %s.%s()", type_name_str, method_name_str);
        LOGI(LOG_TAG, "========================================");

        int call_result = patch_method(nullptr, 0);

        LOGI(LOG_TAG, "========================================");
        LOGI(LOG_TAG, "Patch method called successfully, return value: %d", call_result);
        LOGI(LOG_TAG, "========================================");

        result = 0;

        // 显式关闭上下文，确保 hostfxr 状态被清理
        LOGI(LOG_TAG, "Closing runtime context...");
        context->close();
        context.reset();  // 显式销毁 context
        LOGI(LOG_TAG, "Runtime context closed");

    } catch (const netcorehost::HostingException& ex) {
        LOGE(LOG_TAG, "Hosting error: %s", ex.what());
        result = -100;
    } catch (const std::exception& ex) {
        LOGE(LOG_TAG, "Unexpected error: %s", ex.what());
        result = -101;
    }

cleanup:
    env->ReleaseStringUTFChars(appDir, app_dir_str);
    env->ReleaseStringUTFChars(assemblyName, assembly_name_str);
    env->ReleaseStringUTFChars(typeName, type_name_str);
    env->ReleaseStringUTFChars(methodName, method_name_str);

    return result;
}

/**
 * @brief JNI 函数：设置环境变量（用于 CoreCLR 配置）
 */
extern "C" JNIEXPORT void JNICALL
Java_com_app_ralaunch_utils_CoreCLRConfig_nativeSetEnv(
        JNIEnv *env, jclass clazz, jstring key, jstring value) {

    const char *key_str = env->GetStringUTFChars(key, nullptr);
    const char *value_str = env->GetStringUTFChars(value, nullptr);

    // 设置环境变量
    setenv(key_str, value_str, 1);

    LOGI(LOG_TAG, "  %s = %s", key_str, value_str);

    env->ReleaseStringUTFChars(key, key_str);
    env->ReleaseStringUTFChars(value, value_str);
}