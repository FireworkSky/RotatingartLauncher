/**
 * @file netcorehost_launcher.cpp
 * @brief 简化的 .NET 启动器实现（直接使用 run_app）
 * 
 * 此文件实现了简化的 .NET 应用启动流程，直接使用 hostfxr->run_app()
 * 不再支持Bootstrap或补丁加载，所有程序集替换由MonoMod_Patch.zip在应用级别处理
 */

#include "netcorehost_launcher.h"
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
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <string>
#include "app_logger.h"

#define LOG_TAG "NetCoreHost"

// 全局参数（简化版）
static char* g_app_path = nullptr;           // 程序集完整路径
static char* g_dotnet_path = nullptr;        // .NET 运行时路径
static int g_framework_major = 0;            // 框架主版本号

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
    LOGI(LOG_TAG, "📝 启动参数已设置");
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  应用目录: %s", app_dir);
    LOGI(LOG_TAG, "  主程序集: %s", main_assembly);
    LOGI(LOG_TAG, "  完整路径: %s", g_app_path);
    LOGI(LOG_TAG, "  .NET路径: %s", g_dotnet_path ? g_dotnet_path : "(自动检测)");
    LOGI(LOG_TAG, "  框架版本: %d.x (仅供参考)", framework_major);
    LOGI(LOG_TAG, "========================================");

    // 3. 验证程序集存在
    if (access(g_app_path, F_OK) != 0) {
        LOGE(LOG_TAG, "程序集文件不存在: %s", g_app_path);
        return -1;
    }

    // 4. 设置 DOTNET_ROOT 环境变量（如果提供）
    if (g_dotnet_path) {
        setenv("DOTNET_ROOT", g_dotnet_path, 1);
        LOGI(LOG_TAG, "DOTNET_ROOT 环境变量已设置: %s", g_dotnet_path);
    }

    // 5. 根据用户选择的框架版本设置运行时策略
    LOGI(LOG_TAG, "📋 框架版本参数: framework_major=%d", framework_major);

    if (framework_major > 0) {
        // 策略：通过修改 DOTNET_ROOT 指向特定版本的运行时
        // 这样框架解析器只能看到我们指定的版本
        std::string versioned_dotnet_root = std::string(g_dotnet_path);

        // 注意：不修改 DOTNET_ROOT，而是依赖 hostfxr 的版本选择逻辑
        // 但我们强制使用 LatestMajor 来确保选择最高版本
        setenv("DOTNET_ROLL_FORWARD", "LatestMajor", 1);
        setenv("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "2", 1);
        setenv("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1", 1);

        LOGI(LOG_TAG, "已设置强制使用最新运行时模式: 将使用 net%d.x", framework_major);
        LOGI(LOG_TAG, "   （LatestMajor: 强制使用最高可用版本）");
    } else {
        // 自动模式，允许使用任何兼容版本
        setenv("DOTNET_ROLL_FORWARD", "LatestMajor", 1);
        setenv("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "2", 1);
        setenv("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1", 1);
        LOGI(LOG_TAG, "已设置自动版本模式（使用最新可用运行时，包括预发布版本）");
    }

    setenv("COMPlus_DebugWriteToStdErr", "1", 1);

    // 6. 启用详细日志（用于调试）
    setenv("COREHOST_TRACE", "1", 1);
    setenv("COREHOST_TRACEFILE", "/sdcard/Android/data/com.app.ralaunch/files/corehost_trace.log", 1);

    // 7. 设置保存目录
    setenv("XDG_DATA_HOME", std::string(app_dir).c_str(), 1);
    setenv("XDG_CONFIG_HOME", std::string(app_dir).c_str(), 1);
    setenv("HOME", std::string(app_dir).c_str(), 1);



    // 输入
    setenv("SDL_TOUCH_MOUSE_EVENTS", "1", 1);

    return 0;
}

/**
 * @brief 启动 .NET 应用（简化版 - 直接使用 run_app）
 */
int netcorehost_launch() {
    if (!g_app_path) {
        LOGE(LOG_TAG, "错误：未设置应用路径！请先调用 netcorehostSetParams()");
        return -1;
    }

    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "🚀 开始启动 .NET 应用");
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  程序集: %s", g_app_path);
    LOGI(LOG_TAG, "  .NET路径: %s", g_dotnet_path ? g_dotnet_path : "(环境变量)");

    // 设置工作目录为程序集所在目录，以便 .NET 能找到依赖的程序集
    std::string app_dir = g_app_path;
    size_t last_slash = app_dir.find_last_of("/\\");
    if (last_slash != std::string::npos) {
        app_dir = app_dir.substr(0, last_slash);
        if (chdir(app_dir.c_str()) == 0) {
            LOGI(LOG_TAG, "  工作目录: %s", app_dir.c_str());
        } else {
            LOGW(LOG_TAG, "无法设置工作目录: %s", app_dir.c_str());
        }
    }

    // 注意：libhostpolicy.so 已通过源码修改支持向下兼容

    LOGI(LOG_TAG, "==========================================");
    setenv("COREHOST_TRACEFILE", "/sdcard/Android/data/com.app.ralaunch/files/corehost_trace.log", 1);
    LOGI(LOG_TAG, "COREHOST_TRACE enabled, log file: /sdcard/Android/data/com.app.ralaunch/files/corehost_trace.log");
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
        // 加载 hostfxr（自动从 DOTNET_ROOT 环境变量读取）
        LOGI(LOG_TAG, "加载 hostfxr...");
        hostfxr = netcorehost::Nethost::load_hostfxr();

        if (!hostfxr) {
            LOGE(LOG_TAG, "hostfxr 加载失败：返回空指针");
            return -1;
        }

        LOGI(LOG_TAG, "hostfxr 加载成功");

        // 初始化 .NET 运行时
        LOGI(LOG_TAG, "初始化 .NET 运行时...");
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
            LOGE(LOG_TAG, ".NET 运行时初始化失败");
            return -1;
        }

        LOGI(LOG_TAG, ".NET 运行时初始化成功");

        // 直接运行应用程序
        LOGI(LOG_TAG, "========================================");
        LOGI(LOG_TAG, "运行应用程序...");
        LOGI(LOG_TAG, "========================================");

        auto app_result = context->run_app();
        int32_t exit_code = app_result.value();

        LOGI(LOG_TAG, "========================================");

        if (exit_code == 0) {
            LOGI(LOG_TAG, "应用程序正常退出");
            g_last_error[0] = '\0';  // 清空错误消息
        } else if (exit_code < 0) {
            auto hosting_result = app_result.as_hosting_result();
            std::string error_msg = hosting_result.get_error_message();
            LOGE(LOG_TAG, "托管错误 (code: %d)", exit_code);
            LOGE(LOG_TAG, "  %s", error_msg.c_str());
            // 保存错误消息
            snprintf(g_last_error, sizeof(g_last_error), "%s", error_msg.c_str());
        } else {
            LOGW(LOG_TAG, "应用退出码: %d", exit_code);
            g_last_error[0] = '\0';  // 清空错误消息
        }

        LOGI(LOG_TAG, "========================================");

        return exit_code;

    } catch (const netcorehost::HostingException& ex) {
        LOGE(LOG_TAG, "========================================");
        LOGE(LOG_TAG, "托管错误");
        LOGE(LOG_TAG, "========================================");
        LOGE(LOG_TAG, "  %s", ex.what());
        LOGE(LOG_TAG, "========================================");
        // 保存错误消息
        snprintf(g_last_error, sizeof(g_last_error), "托管错误: %s", ex.what());
        return -1;
    } catch (const std::exception& ex) {
        LOGE(LOG_TAG, "========================================");
        LOGE(LOG_TAG, "意外错误");
        LOGE(LOG_TAG, "========================================");
        LOGE(LOG_TAG, "  %s", ex.what());
        LOGE(LOG_TAG, "========================================");
        // 保存错误消息
        snprintf(g_last_error, sizeof(g_last_error), "意外错误: %s", ex.what());
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
    LOGI(LOG_TAG, "🔧 调用补丁方法");
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  应用目录: %s", app_dir_str);
    LOGI(LOG_TAG, "  程序集: %s", assembly_name_str);
    LOGI(LOG_TAG, "  类型: %s", type_name_str);
    LOGI(LOG_TAG, "  方法: %s", method_name_str);
    LOGI(LOG_TAG, "========================================");

    int result = -1;

    try {
        // 构建程序集完整路径
        std::string assembly_path = std::string(app_dir_str) + "/" + std::string(assembly_name_str);

        LOGI(LOG_TAG, "程序集路径: %s", assembly_path.c_str());

        // 验证程序集文件存在
        if (access(assembly_path.c_str(), F_OK) != 0) {
            LOGE(LOG_TAG, "程序集文件不存在: %s", assembly_path.c_str());
            result = -1;
            goto cleanup;
        }

        // 加载 hostfxr
        LOGI(LOG_TAG, "加载 hostfxr...");
        auto hostfxr = netcorehost::Nethost::load_hostfxr();

        if (!hostfxr) {
            LOGE(LOG_TAG, "hostfxr 加载失败");
            result = -2;
            goto cleanup;
        }

        LOGI(LOG_TAG, "hostfxr 加载成功");

        // 初始化运行时上下文
        LOGI(LOG_TAG, "初始化运行时上下文...");

        // 设置 Core Host 跟踪日志
        setenv("COREHOST_TRACEFILE", "/sdcard/Android/data/com.app.ralaunch/files/patches/corehost_trace.log", 1);
        LOGI(LOG_TAG, "已设置 COREHOST_TRACEFILE 环境变量");

        auto assembly_path_pdc = netcorehost::PdCString::from_str(assembly_path.c_str());

        std::unique_ptr<netcorehost::HostfxrContextForRuntimeConfig> context;

        context = hostfxr->initialize_for_runtime_config(assembly_path_pdc);

        if (!context) {
            LOGE(LOG_TAG, "运行时上下文初始化失败");
            result = -3;
            goto cleanup;
        }

        LOGI(LOG_TAG, "运行时上下文初始化成功");

        // 获取委托加载器
        LOGI(LOG_TAG, "获取委托加载器...");
        auto loader = context->get_delegate_loader();

        if (!loader) {
            LOGE(LOG_TAG, "委托加载器获取失败");
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

        LOGI(LOG_TAG, "完整类型名: %s", full_type_name.c_str());
        LOGI(LOG_TAG, "方法名: %s", method_name_str);

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
            LOGE(LOG_TAG, "获取方法指针失败: %s", ex.what());
            result = -5;
            goto cleanup;
        }

        if (!patch_method) {
            LOGE(LOG_TAG, "方法指针为空");
            result = -6;
            goto cleanup;
        }

        LOGI(LOG_TAG, "方法指针获取成功");

        // 调用补丁方法
        LOGI(LOG_TAG, "========================================");
        LOGI(LOG_TAG, "调用补丁方法: %s.%s()", type_name_str, method_name_str);
        LOGI(LOG_TAG, "========================================");

        int call_result = patch_method(nullptr, 0);

        LOGI(LOG_TAG, "========================================");
        LOGI(LOG_TAG, "补丁方法调用成功，返回值: %d", call_result);
        LOGI(LOG_TAG, "========================================");

        result = 0;

    } catch (const netcorehost::HostingException& ex) {
        LOGE(LOG_TAG, "托管错误: %s", ex.what());
        result = -100;
    } catch (const std::exception& ex) {
        LOGE(LOG_TAG, "意外错误: %s", ex.what());
        result = -101;
    }

cleanup:
    env->ReleaseStringUTFChars(appDir, app_dir_str);
    env->ReleaseStringUTFChars(assemblyName, assembly_name_str);
    env->ReleaseStringUTFChars(typeName, type_name_str);
    env->ReleaseStringUTFChars(methodName, method_name_str);

    return result;
}