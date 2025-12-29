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
#include <dirent.h>
#include <dlfcn.h>
#include <vector>

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

// 命令行参数
static int g_argc = 0;
static char** g_argv = nullptr;

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
 * @brief 预加载 .NET 加密库并初始化 JNI
 * 
 * libSystem.Security.Cryptography.Native.Android.so 需要 JNI_OnLoad 来初始化 JavaVM 指针
 * 但 .NET 运行时通过 dlopen 加载它时不一定会触发 JNI_OnLoad
 * 所以我们需要手动加载并调用 JNI_OnLoad
 * 
 * 注意：需要加载所有版本的库，因为不同版本有各自的 g_jvm 静态变量
 */
static void preload_crypto_jni(JavaVM* jvm) {
    if (!jvm || !g_dotnet_path) {
        LOGW(LOG_TAG, "Cannot preload crypto JNI: jvm=%p, dotnet_path=%s", jvm, g_dotnet_path ? g_dotnet_path : "(null)");
        return;
    }
    
    LOGI(LOG_TAG, "Preloading crypto library JNI for all .NET versions...");
    
    // 查找 .NET 运行时目录
    std::string dotnet_root = g_dotnet_path;
    std::string shared_dir = dotnet_root + "/shared/Microsoft.NETCore.App";
    
    // 查找所有版本目录
    DIR* dir = opendir(shared_dir.c_str());
    if (!dir) {
        LOGW(LOG_TAG, "  Cannot open .NET shared directory: %s", shared_dir.c_str());
        return;
    }
    
    std::vector<std::string> versions;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type == DT_DIR && entry->d_name[0] != '.') {
            versions.push_back(entry->d_name);
        }
    }
    closedir(dir);
    
    if (versions.empty()) {
        LOGW(LOG_TAG, "  No .NET runtime versions found");
        return;
    }
    
    LOGI(LOG_TAG, "  Found %zu .NET runtime version(s)", versions.size());
    
    // 为每个版本加载加密库
    typedef jint (*JNI_OnLoad_t)(JavaVM* vm, void* reserved);
    
    for (const auto& version : versions) {
        std::string crypto_lib_path = shared_dir + "/" + version + "/libSystem.Security.Cryptography.Native.Android.so";
        LOGI(LOG_TAG, "  Loading crypto library for .NET %s: %s", version.c_str(), crypto_lib_path.c_str());
        
        // 使用 dlopen 加载库
        void* handle = dlopen(crypto_lib_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (!handle) {
            LOGW(LOG_TAG, "    Failed to dlopen: %s", dlerror());
            continue;
        }
        
        // 查找 JNI_OnLoad 函数
        JNI_OnLoad_t jni_onload = (JNI_OnLoad_t)dlsym(handle, "JNI_OnLoad");
        
        if (jni_onload) {
            LOGI(LOG_TAG, "    Calling JNI_OnLoad...");
            jint result = jni_onload(jvm, nullptr);
            LOGI(LOG_TAG, "    JNI_OnLoad returned: %d", result);
        } else {
            LOGW(LOG_TAG, "    JNI_OnLoad not found: %s", dlerror());
        }
        
        // 不要 dlclose，保持库加载状态
    }
    
    LOGI(LOG_TAG, "  Crypto library JNI preload complete");
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
    if (package_name_cstr != nullptr) {
        return {package_name_cstr};
    }
    
    // 备用方案：如果环境变量未设置，返回默认包名
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, 
        "PACKAGE_NAME not set, using default: com.app.ralaunch");
    return {"com.app.ralaunch"};
}

static std::string get_external_storage_directory() {
    const char *external_storage_directory_cstr = getenv("EXTERNAL_STORAGE_DIRECTORY"); // RaLaunchApplication.java 中设置了
    if (external_storage_directory_cstr != nullptr) {
        return {external_storage_directory_cstr};
    }
    
    // 备用方案：如果环境变量未设置，尝试使用默认路径
    // Android 10+ 使用 scoped storage，但我们可以尝试常见的路径
    const char* default_paths[] = {
        "/storage/emulated/0",
        "/sdcard",
        "/storage/sdcard0",
        nullptr
    };
    
    for (int i = 0; default_paths[i] != nullptr; i++) {
        struct stat st;
        if (stat(default_paths[i], &st) == 0 && S_ISDIR(st.st_mode)) {
            __android_log_print(ANDROID_LOG_WARN, LOG_TAG, 
                "EXTERNAL_STORAGE_DIRECTORY not set, using fallback: %s", default_paths[i]);
            return {default_paths[i]};
        }
    }
    
    // 如果所有备用方案都失败，返回空字符串并记录错误
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, 
        "EXTERNAL_STORAGE_DIRECTORY not set and no fallback path available");
    return {""};
}

static bool is_set_thread_affinity_to_big_core() {
    const char *env_value = getenv("SET_THREAD_AFFINITY_TO_BIG_CORE");
    return (env_value != nullptr) && (strcmp(env_value, "1") == 0);
}

/**
 * @brief 释放命令行参数
 */
static void free_argv() {
    if (g_argv) {
        for (int i = 0; i < g_argc; i++) {
            if (g_argv[i]) free(g_argv[i]);
        }
        free(g_argv);
        g_argv = nullptr;
    }
    g_argc = 0;
}

/**
 * @brief 设置启动参数（支持命令行参数）
 */
int netcorehost_set_params(
        const char* app_dir,
        const char* main_assembly,
        const char* dotnet_root,
        int framework_major,
        int argc,
        const char* const* argv) {
    
    // 释放旧的命令行参数
    free_argv();
    

    
    // 复制新的命令行参数
    if (argc > 0 && argv != nullptr) {
        g_argc = argc;
        g_argv = (char**)malloc(sizeof(char*) * argc);
        for (int i = 0; i < argc; i++) {
            g_argv[i] = str_dup(argv[i]);
            LOGI(LOG_TAG, "  Arg[%d]: %s", i, g_argv[i]);
        }
    }

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
    
    // 设置游戏数据目录到 SD 卡的 RALauncher 文件夹
    std::string game_data_dir = get_external_storage_directory() + "/RALauncher";
    // 确保目录存在
    struct stat st;
    if (stat(game_data_dir.c_str(), &st) != 0) {
        // 目录不存在，尝试创建
        mode_t mode = S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH;
        if (mkdir(game_data_dir.c_str(), mode) != 0) {
            LOGW(LOG_TAG, "Failed to create game data directory: %s, using app_dir as fallback", game_data_dir.c_str());
            game_data_dir = app_dir;
        } else {
            LOGI(LOG_TAG, "Created game data directory: %s", game_data_dir.c_str());
        }
    } else {
        LOGI(LOG_TAG, "Using game data directory: %s", game_data_dir.c_str());
    }

    // 创建 .nomedia 文件以防止 Android 媒体扫描器索引游戏文件
    std::string nomedia_path = game_data_dir + "/.nomedia";
    if (access(nomedia_path.c_str(), F_OK) != 0) {
        // .nomedia 文件不存在，创建它
        FILE* nomedia_file = fopen(nomedia_path.c_str(), "w");
        if (nomedia_file) {
            fclose(nomedia_file);
            LOGI(LOG_TAG, "Created .nomedia file: %s", nomedia_path.c_str());
        } else {
            LOGW(LOG_TAG, "Failed to create .nomedia file: %s", nomedia_path.c_str());
        }
    } else {
        LOGI(LOG_TAG, ".nomedia file already exists: %s", nomedia_path.c_str());
    }

    // 设置存储路径环境变量
    setenv("XDG_DATA_HOME", game_data_dir.c_str(), 1);
    setenv("XDG_CONFIG_HOME", game_data_dir.c_str(), 1);
    setenv("HOME", game_data_dir.c_str(), 1);

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
            
            // 预初始化加密库的 JNI（解决 dlopen 不触发 JNI_OnLoad 的问题）
            preload_crypto_jni(jvm);
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

        // 根据是否有命令行参数和 dotnet_root 选择合适的初始化函数
        if (g_argc > 0 && g_argv != nullptr) {
            LOGI(LOG_TAG, "  Passing %d command line arguments to .NET:", g_argc);
            for (int i = 0; i < g_argc; i++) {
                LOGI(LOG_TAG, "    [%d] %s", i, g_argv[i]);
            }
            
            if (g_dotnet_path) {
                auto dotnet_root_str = netcorehost::PdCString::from_str(g_dotnet_path);
                context = hostfxr->initialize_for_dotnet_command_line_with_args_and_dotnet_root(
                        app_path_str, g_argc, (const char* const*)g_argv, dotnet_root_str);
            } else {
                context = hostfxr->initialize_for_dotnet_command_line_with_args(
                        app_path_str, g_argc, (const char* const*)g_argv);
            }
        } else {
            LOGI(LOG_TAG, "  No command line arguments");
            if (g_dotnet_path) {
                auto dotnet_root_str = netcorehost::PdCString::from_str(g_dotnet_path);
                context = hostfxr->initialize_for_dotnet_command_line_with_dotnet_root(
                        app_path_str, dotnet_root_str);
            } else {
                context = hostfxr->initialize_for_dotnet_command_line(app_path_str);
            }
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
        
        // 显式关闭 hostfxr context 以确保正确清理资源
        LOGI(LOG_TAG, "Closing hostfxr context...");
        try {
            context->close();
            LOGI(LOG_TAG, "Hostfxr context closed successfully");
        } catch (const std::exception& ex) {
            LOGW(LOG_TAG, "Error closing hostfxr context: %s", ex.what());
        }
        
        // 清理 context（虽然析构函数会自动调用 close，但我们已经显式关闭了）
        context.reset();
        LOGI(LOG_TAG, "Cleaning up hostfxr instance...");
        hostfxr.reset();
        LOGI(LOG_TAG, "Cleanup complete");
        
        return exit_code;

    } catch (const netcorehost::HostingException& ex) {
        LOGE(LOG_TAG, "Hosting error");
        LOGE(LOG_TAG, "  %s", ex.what());
        // 保存错误消息
        snprintf(g_last_error, sizeof(g_last_error), "Hosting error: %s", ex.what());
        
        // 确保清理资源
        if (hostfxr) {
            LOGI(LOG_TAG, "Cleaning up hostfxr instance after error...");
            hostfxr.reset();
        }
        
        return -1;
    } catch (const std::exception& ex) {
        LOGE(LOG_TAG, "Unexpected error");
        LOGE(LOG_TAG, "  %s", ex.what());
        snprintf(g_last_error, sizeof(g_last_error), "Unexpected error: %s", ex.what());
        
        // 确保清理资源
        if (hostfxr) {
            LOGI(LOG_TAG, "Cleaning up hostfxr instance after error...");
            hostfxr.reset();
        }
        
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
    // 清理路径字符串
    str_free(g_app_path);
    g_app_path = nullptr;
    
    str_free(g_dotnet_path);
    g_dotnet_path = nullptr;
    
    // 清理 StartupHooks 路径
    if (g_startup_hooks_dll) {
        free(g_startup_hooks_dll);
        g_startup_hooks_dll = nullptr;
    }
    
    // 清理命令行参数数组
    if (g_argv) {
        for (int i = 0; i < g_argc; i++) {
            if (g_argv[i]) {
                free(g_argv[i]);
            }
        }
        free(g_argv);
        g_argv = nullptr;
        g_argc = 0;
    }
    
    // 清空错误消息
    g_last_error[0] = '\0';
    
    LOGI(LOG_TAG, "Cleanup complete (freed: app_path, dotnet_path, startup_hooks, argv)");
}
/**
 * @brief JNI 函数：设置启动参数（无命令行参数）
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_GameLauncher_netcorehostSetParams(
        JNIEnv *env, jclass clazz,
        jstring appDir, jstring mainAssembly, jstring dotnetRoot, jint frameworkMajor) {

    const char *app_dir = env->GetStringUTFChars(appDir, nullptr);
    const char *main_assembly = env->GetStringUTFChars(mainAssembly, nullptr);
    const char *dotnet_root = dotnetRoot ? env->GetStringUTFChars(dotnetRoot, nullptr) : nullptr;

    int result = netcorehost_set_params(app_dir, main_assembly, dotnet_root, frameworkMajor, 0, nullptr);

    env->ReleaseStringUTFChars(appDir, app_dir);
    env->ReleaseStringUTFChars(mainAssembly, main_assembly);
    if (dotnet_root) env->ReleaseStringUTFChars(dotnetRoot, dotnet_root);

    return result;
}

/**
 * @brief JNI 函数：设置启动参数（带命令行参数）
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_app_ralaunch_core_GameLauncher_netcorehostSetParamsWithArgs(
        JNIEnv *env, jclass clazz,
        jstring appDir, jstring mainAssembly, jstring dotnetRoot, jint frameworkMajor,
        jobjectArray args) {

    const char *app_dir = env->GetStringUTFChars(appDir, nullptr);
    const char *main_assembly = env->GetStringUTFChars(mainAssembly, nullptr);
    const char *dotnet_root = dotnetRoot ? env->GetStringUTFChars(dotnetRoot, nullptr) : nullptr;

    // 转换 Java String[] 到 C char**
    int argc = 0;
    const char** argv = nullptr;
    
    if (args != nullptr) {
        argc = env->GetArrayLength(args);
        if (argc > 0) {
            argv = (const char**)malloc(sizeof(char*) * argc);
            for (int i = 0; i < argc; i++) {
                jstring jstr = (jstring)env->GetObjectArrayElement(args, i);
                argv[i] = env->GetStringUTFChars(jstr, nullptr);
            }
        }
    }

    int result = netcorehost_set_params(app_dir, main_assembly, dotnet_root, frameworkMajor, argc, argv);

    // 释放资源
    if (argv != nullptr) {
        for (int i = 0; i < argc; i++) {
            jstring jstr = (jstring)env->GetObjectArrayElement(args, i);
            env->ReleaseStringUTFChars(jstr, argv[i]);
        }
        free((void*)argv);
    }
    
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
 * @brief JNI 函数：获取最后一次错误的详细消息
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_app_ralaunch_core_GameLauncher_netcorehostGetLastError(JNIEnv *env, jclass clazz) {
    const char* error = netcorehost_get_last_error();
    if (error == nullptr || error[0] == '\0') {
        return nullptr;
    }
    return env->NewStringUTF(error);
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

/**
 * @brief 通用进程启动器 - 供 .NET P/Invoke 调用
 * 
 * @param assembly_path 程序集完整路径
 * @param args_json     命令行参数 JSON 数组（如 ["-server", "-world", "xxx"]）
 * @param startup_hooks DOTNET_STARTUP_HOOKS 值，可为 nullptr
 * @param title         通知标题
 * @return 0 成功，非0 失败
 */
extern "C" __attribute__((visibility("default"))) 
int process_launcher_start(const char* assembly_path, const char* args_json, 
                           const char* startup_hooks, const char* title) {
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "process_launcher_start called");
    LOGI(LOG_TAG, "========================================");
    LOGI(LOG_TAG, "  Assembly: %s", assembly_path ? assembly_path : "(null)");
    LOGI(LOG_TAG, "  Args JSON: %s", args_json ? args_json : "(null)");
    LOGI(LOG_TAG, "  StartupHooks: %s", startup_hooks ? "yes" : "no");
    LOGI(LOG_TAG, "  Title: %s", title ? title : "(null)");
    
    if (!assembly_path) {
        LOGE(LOG_TAG, "Assembly path is null");
        return -1;
    }
    
    JNIEnv* env = Bridge_GetJNIEnv();
    if (env == nullptr) {
        LOGE(LOG_TAG, "Failed to get JNIEnv");
        return -2;
    }
    
    // 解析 JSON 数组为 String[]
    // 简单解析：假设格式为 ["arg1","arg2",...]
    jobjectArray jArgs = nullptr;
    if (args_json && args_json[0] == '[') {
        // 简单的 JSON 数组解析
        std::vector<std::string> args;
        std::string json(args_json);
        size_t pos = 1; // 跳过 '['
        
        while (pos < json.length()) {
            // 跳过空白
            while (pos < json.length() && (json[pos] == ' ' || json[pos] == ',')) pos++;
            if (pos >= json.length() || json[pos] == ']') break;
            
            if (json[pos] == '"') {
                pos++; // 跳过开始引号
                std::string arg;
                while (pos < json.length() && json[pos] != '"') {
                    if (json[pos] == '\\' && pos + 1 < json.length()) {
                        pos++;
                        if (json[pos] == 'n') arg += '\n';
                        else if (json[pos] == 't') arg += '\t';
                        else arg += json[pos];
                    } else {
                        arg += json[pos];
                    }
                    pos++;
                }
                pos++; // 跳过结束引号
                args.push_back(arg);
            } else {
                pos++;
            }
        }
        
        if (!args.empty()) {
            jclass stringClass = env->FindClass("java/lang/String");
            jArgs = env->NewObjectArray(args.size(), stringClass, nullptr);
            for (size_t i = 0; i < args.size(); i++) {
                jstring jArg = env->NewStringUTF(args[i].c_str());
                env->SetObjectArrayElement(jArgs, i, jArg);
                env->DeleteLocalRef(jArg);
            }
            LOGI(LOG_TAG, "  Parsed %zu arguments", args.size());
        }
    }
    
    // 转换为 Java 字符串
    jstring jAssemblyPath = env->NewStringUTF(assembly_path);
    jstring jStartupHooks = startup_hooks ? env->NewStringUTF(startup_hooks) : nullptr;
    jstring jTitle = env->NewStringUTF(title ? title : "Process");
    
    // 获取 ProcessLauncherService 类
    jclass serviceClass = env->FindClass("com/app/ralaunch/service/ProcessLauncherService");
    if (serviceClass == nullptr) {
        LOGE(LOG_TAG, "Failed to find ProcessLauncherService class");
        // 清理已分配的资源
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
        env->DeleteLocalRef(jTitle);
        return -3;
    }
    
    // 获取 launch 静态方法
    jmethodID launchMethod = env->GetStaticMethodID(serviceClass, "launch",
        "(Landroid/content/Context;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    if (launchMethod == nullptr) {
        LOGE(LOG_TAG, "Failed to find launch method");
        // 清理已分配的资源
        env->DeleteLocalRef(serviceClass);
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
        env->DeleteLocalRef(jTitle);
        return -4;
    }
    
    // 获取 Context
    jclass sdlActivityClass = env->FindClass("org/libsdl/app/SDLActivity");
    if (sdlActivityClass == nullptr) {
        LOGE(LOG_TAG, "Failed to find SDLActivity class");
        // 清理已分配的资源
        env->DeleteLocalRef(serviceClass);
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
        env->DeleteLocalRef(jTitle);
        return -6;
    }
    
    jmethodID getContextMethod = env->GetStaticMethodID(sdlActivityClass, "getContext",
        "()Landroid/content/Context;");
    if (getContextMethod == nullptr) {
        LOGE(LOG_TAG, "Failed to find getContext method");
        // 清理已分配的资源
        env->DeleteLocalRef(sdlActivityClass);
        env->DeleteLocalRef(serviceClass);
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
        env->DeleteLocalRef(jTitle);
        return -7;
    }
    
    jobject context = env->CallStaticObjectMethod(sdlActivityClass, getContextMethod);
    if (context == nullptr) {
        LOGE(LOG_TAG, "Failed to get context");
        // 清理已分配的资源
        env->DeleteLocalRef(sdlActivityClass);
        env->DeleteLocalRef(serviceClass);
        env->DeleteLocalRef(jAssemblyPath);
        if (jArgs) env->DeleteLocalRef(jArgs);
        if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
        env->DeleteLocalRef(jTitle);
        return -5;
    }
    
    // 调用 launch
    LOGI(LOG_TAG, "Calling ProcessLauncherService.launch...");
    env->CallStaticVoidMethod(serviceClass, launchMethod,
        context, jAssemblyPath, jArgs, jStartupHooks, jTitle);
    
    // 清理所有 JNI 本地引用
    env->DeleteLocalRef(context);
    env->DeleteLocalRef(sdlActivityClass);
    env->DeleteLocalRef(serviceClass);
    env->DeleteLocalRef(jAssemblyPath);
    if (jArgs) env->DeleteLocalRef(jArgs);
    if (jStartupHooks) env->DeleteLocalRef(jStartupHooks);
    env->DeleteLocalRef(jTitle);
    
    LOGI(LOG_TAG, "Process launch requested!");
    LOGI(LOG_TAG, "========================================");
    return 0;
}