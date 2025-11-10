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

// 直接声明静态链接的 nethost 函数
extern "C" {
    int32_t get_hostfxr_path(
        char* buffer,
        size_t* buffer_size,
        const netcorehost::bindings::get_hostfxr_parameters* parameters
    );
}

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <string>

#define LOG_TAG "NetCoreHost"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 全局参数（简化版）
static char* g_app_path = nullptr;           // 程序集完整路径
static char* g_dotnet_path = nullptr;        // .NET 运行时路径
static int g_framework_major = 0;            // 框架主版本号

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
    
    LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGI("📝 启动参数已设置");
    LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGI("  应用目录: %s", app_dir);
    LOGI("  主程序集: %s", main_assembly);
    LOGI("  完整路径: %s", g_app_path);
    LOGI("  .NET路径: %s", g_dotnet_path ? g_dotnet_path : "(自动检测)");
    LOGI("  框架版本: %d.x (仅供参考)", framework_major);
    LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    
    // 3. 验证程序集存在
    if (access(g_app_path, F_OK) != 0) {
        LOGE("❌ 程序集文件不存在: %s", g_app_path);
        return -1;
    }
    
    // 4. 设置 DOTNET_ROOT 环境变量（如果提供）
    if (g_dotnet_path) {
        setenv("DOTNET_ROOT", g_dotnet_path, 1);
        LOGI("✅ DOTNET_ROOT 环境变量已设置: %s", g_dotnet_path);
    }
    
    // 5. 根据用户选择的框架版本设置运行时策略
    LOGI("📋 框架版本参数: framework_major=%d", framework_major);
    
    if (framework_major > 0) {
        // 用户指定了版本，完全禁用版本滚动
        setenv("DOTNET_ROLL_FORWARD", "Disable", 1);
        setenv("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "0", 1);
        // 允许使用预发布版本（RC、Preview等）
        setenv("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1", 1);
        LOGI("✅ 已设置精确版本模式: net%d.x", framework_major);
        LOGI("   （完全禁用版本滚动，允许使用 RC/Preview 版本）");
    } else {
        // 自动模式，允许使用任何兼容版本
        setenv("DOTNET_ROLL_FORWARD", "LatestMajor", 1);
        setenv("DOTNET_ROLL_FORWARD_ON_NO_CANDIDATE_FX", "2", 1);
        setenv("DOTNET_ROLL_FORWARD_TO_PRERELEASE", "1", 1);
        LOGI("✅ 已设置自动版本模式（使用最新可用运行时，包括预发布版本）");
    }
    
    setenv("COMPlus_DebugWriteToStdErr", "1", 1);
    
    // 6. 启用详细日志（用于调试）
    setenv("COREHOST_TRACE", "1", 1);
    setenv("COREHOST_TRACEFILE", "/sdcard/Android/data/com.app.ralaunch/files/corehost_trace.log", 1);
    
    return 0;
}

/**
 * @brief 启动 .NET 应用（简化版 - 直接使用 run_app）
 */
int netcorehost_launch() {
    if (!g_app_path) {
        LOGE("❌ 错误：未设置应用路径！请先调用 netcorehostSetParams()");
        return -1;
    }
    
    LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGI("🚀 开始启动 .NET 应用");
    LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    LOGI("  程序集: %s", g_app_path);
    LOGI("  .NET路径: %s", g_dotnet_path ? g_dotnet_path : "(环境变量)");
    
    // 设置工作目录为程序集所在目录，以便 .NET 能找到依赖的程序集
    std::string app_dir = g_app_path;
    size_t last_slash = app_dir.find_last_of("/\\");
    if (last_slash != std::string::npos) {
        app_dir = app_dir.substr(0, last_slash);
        if (chdir(app_dir.c_str()) == 0) {
            LOGI("  工作目录: %s", app_dir.c_str());
        } else {
            LOGW("⚠️  无法设置工作目录: %s", app_dir.c_str());
        }
    }
    
    LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    setenv("COREHOST_TRACEFILE", "/sdcard/Android/data/com.app.ralaunch/files/corehost_trace.log", 1);
    LOGI("✓ 已启用 COREHOST_TRACE，日志将写入 /sdcard/Android/data/com.app.ralaunch/files/corehost_trace.log");
    std::shared_ptr<netcorehost::Hostfxr> hostfxr;
    
    try {
        // 加载 hostfxr（自动从 DOTNET_ROOT 环境变量读取）
        LOGI("⏳ 加载 hostfxr...");
        hostfxr = netcorehost::Nethost::load_hostfxr();
        
        if (!hostfxr) {
            LOGE("❌ hostfxr 加载失败：返回空指针");
            return -1;
        }
        
        LOGI("✅ hostfxr 加载成功");
        
        // 初始化 .NET 运行时
        LOGI("⏳ 初始化 .NET 运行时...");
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
            LOGE("❌ .NET 运行时初始化失败");
            return -1;
        }
        
        LOGI("✅ .NET 运行时初始化成功");
     
        // 直接运行应用程序
        LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGI("▶️  运行应用程序...");
        LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        auto app_result = context->run_app();
        int32_t exit_code = app_result.value();
        
        LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        if (exit_code == 0) {
            LOGI("✅ 应用程序正常退出");
        } else if (exit_code < 0) {
            auto hosting_result = app_result.as_hosting_result();
            LOGE("❌ 托管错误 (code: %d)", exit_code);
            LOGE("  %s", hosting_result.get_error_message().c_str());
        } else {
            LOGW("⚠️  应用退出码: %d", exit_code);
        }
        
        LOGI("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return exit_code;
        
    } catch (const netcorehost::HostingException& ex) {
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGE("❌ 托管错误");
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGE("  %s", ex.what());
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return -1;
    } catch (const std::exception& ex) {
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGE("❌ 意外错误");
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGE("  %s", ex.what());
        LOGE("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return -2;
    }
}

/**
 * @brief 清理资源
 */
void netcorehost_cleanup() {
    str_free(g_app_path);
    str_free(g_dotnet_path);
    LOGI("Cleanup complete");
}

/**
 * @brief JNI 函数：设置启动参数（简化版 - 4个参数）
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_app_ralaunch_game_GameLauncher_netcorehostSetParams(
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
Java_com_app_ralaunch_game_GameLauncher_netcorehostLaunch(JNIEnv *env, jclass clazz) {
    return netcorehost_launch();
}

/**
 * @brief JNI 函数：清理资源
 */
extern "C" JNIEXPORT void JNICALL
Java_com_app_ralaunch_game_GameLauncher_netcorehostCleanup(JNIEnv *env, jclass clazz) {
    netcorehost_cleanup();
}
