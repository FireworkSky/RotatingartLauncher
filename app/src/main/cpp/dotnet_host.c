/**
 * @file dotnet_host.c
 * @brief .NET CoreCLR 宿主启动器实现
 */

#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <jni.h>
#include <android/log.h>
#include "dotnet_params.h"

#define LOG_TAG "GameLauncher"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/** 主程序集路径 */
char* h_appPath = NULL;

/** .NET 运行时路径（可选） */
char* h_dotnetPath = NULL;

/** 应用程序目录 */
char* h_appDir = NULL;

/** 受信程序集列表 */
char* g_trustedAssemblies = NULL;

/** 原生库搜索路径 */
char* g_nativeSearchPaths = NULL;

/** 启动器 DLL 路径 */
char* g_launcherDll = NULL;

/**
 * @brief JNI 函数：设置完整的启动参数
 * 
 * @param env JNI 环境指针
 * @param clazz Java 类引用
 * @param appPath 应用程序主程序集路径
 * @param dotnetPath .NET 运行时路径
 * @param appDir 应用程序目录
 * @param trustedAssemblies 受信程序集列表（: 分隔）
 * @param nativeSearchPaths 原生库搜索路径（: 分隔）
 * @param mainAssemblyPath 主程序集路径（启动器 DLL）
 * 
 * 此函数从 Java 层接收所有启动参数，并存储到全局变量中。
 * 旧的全局变量值会被自动释放。
 */
JNIEXPORT void JNICALL Java_com_app_ralaunch_game_GameLauncher_setLaunchParamsFull(
    JNIEnv* env, jclass clazz,
    jstring appPath, jstring dotnetPath, jstring appDir, jstring trustedAssemblies, jstring nativeSearchPaths, jstring mainAssemblyPath) {
    // 宏：释放旧值并赋予新值
    #define FREE_AND_ASSIGN(VAR, JS) do { if (VAR) free(VAR); if (JS) { const char* tmp = (*env)->GetStringUTFChars(env, JS, 0); VAR = strdup(tmp); (*env)->ReleaseStringUTFChars(env, JS, tmp); } else VAR = NULL; } while(0)
    FREE_AND_ASSIGN(h_appPath, appPath);
    FREE_AND_ASSIGN(h_dotnetPath, dotnetPath);
    FREE_AND_ASSIGN(h_appDir, appDir);
    FREE_AND_ASSIGN(g_trustedAssemblies, trustedAssemblies);
    FREE_AND_ASSIGN(g_nativeSearchPaths, nativeSearchPaths);
    FREE_AND_ASSIGN(g_launcherDll, mainAssemblyPath);
}

/**
 * @brief JNI 函数：兼容性包装（用于 NativeBridge 类）
 * 
 * 此函数提供了对新 Java 类名 NativeBridge 的兼容支持，
 * 内部直接调用 GameLauncher 的实现。
 */
JNIEXPORT void JNICALL Java_com_app_ralaunch_game_NativeBridge_setLaunchParamsFull(
    JNIEnv* env, jclass clazz,
    jstring appPath, jstring dotnetPath, jstring appDir, jstring trustedAssemblies, jstring nativeSearchPaths, jstring mainAssemblyPath) {
    Java_com_app_ralaunch_game_GameLauncher_setLaunchParamsFull(env, clazz, appPath, dotnetPath, appDir, trustedAssemblies, nativeSearchPaths, mainAssemblyPath);
}

/**
 * @brief 通过 CoreCLR 启动 .NET 应用程序
 * 
 * @return 应用程序退出码或错误码
 * 
 * 此函数是 .NET 应用程序启动的核心实现，执行以下步骤：
 * 1. 切换工作目录到应用程序目录
 * 2. 设置 LD_LIBRARY_PATH 环境变量
 * 3. 加载 CoreCLR 动态库（libcoreclr.so）
 * 4. 获取 CoreCLR API 函数指针
 * 5. 初始化 CoreCLR 运行时
 * 6. 执行主程序集
 * 7. 关闭运行时并清理资源
 */
int launch_with_coreclr_passthrough() {
    LOGI("launch_with_coreclr_passthrough: app=%s dir=%s launcher=%s", h_appPath, h_appDir, g_launcherDll);
    
    // 1. 切换到应用程序目录
    chdir(h_appDir);
    
    // 2. 设置原生库搜索路径环境变量
    if (g_nativeSearchPaths && *g_nativeSearchPaths)
        setenv("LD_LIBRARY_PATH", g_nativeSearchPaths, 1);

    // 3. 从原生搜索路径的第一个目录推导 libcoreclr.so 路径
    char firstPath[1024] = {0};
    if (g_nativeSearchPaths) {
        const char* sep = strchr(g_nativeSearchPaths, ':');
        size_t len = sep ? (size_t)(sep - g_nativeSearchPaths) : strlen(g_nativeSearchPaths);
        if (len >= sizeof(firstPath)) len = sizeof(firstPath) - 1;
        memcpy(firstPath, g_nativeSearchPaths, len);
        firstPath[len] = '\0';
    }
    
    char coreclrPath[1536];
    if (firstPath[0] != '\0') 
        snprintf(coreclrPath, sizeof(coreclrPath), "%s/libcoreclr.so", firstPath);
    else 
        snprintf(coreclrPath, sizeof(coreclrPath), "libcoreclr.so");
    
    // 4. 加载 CoreCLR 动态库
    void* coreclrLib = dlopen(coreclrPath, RTLD_LAZY | RTLD_LOCAL);
    if (!coreclrLib) { 
        LOGE("dlopen coreclr.so fail: %s", dlerror()); 
        return -11; 
    }
    
    // 5. 定义 CoreCLR API 函数指针类型
    typedef int (*coreclr_initialize_ptr)(const char*,const char*,int,const char**,const char**,void**,unsigned int*);
    typedef int (*coreclr_execute_assembly_ptr)(void*,unsigned int,int,const char**,const char*,unsigned int*);
    typedef int (*coreclr_shutdown_ptr)(void*,unsigned int);
    
    // 6. 获取 CoreCLR API 函数指针
    dlerror(); // 清除之前的错误
    coreclr_initialize_ptr coreclr_initialize = (coreclr_initialize_ptr)dlsym(coreclrLib, "coreclr_initialize");
    const char* err1 = dlerror();
    if (err1) LOGE("dlsym coreclr_initialize fail: %s", err1);
    
    coreclr_execute_assembly_ptr coreclr_execute_assembly = (coreclr_execute_assembly_ptr)dlsym(coreclrLib, "coreclr_execute_assembly");
    const char* err2 = dlerror();
    if (err2) LOGE("dlsym coreclr_execute_assembly fail: %s", err2);
    
    coreclr_shutdown_ptr coreclr_shutdown = (coreclr_shutdown_ptr)dlsym(coreclrLib, "coreclr_shutdown");
    const char* err3 = dlerror();
    if (err3) {
        LOGW("dlsym coreclr_shutdown fail: %s (可能在 .NET 7+ 中已移除，将跳过)", err3);
    }
    
    // 注意: coreclr_shutdown 在 .NET 7+ 中可能不存在，这是正常的
    if (!coreclr_initialize || !coreclr_execute_assembly) { 
        dlclose(coreclrLib); 
        LOGE("coreclr dlsym fail: init=%p, exec=%p, shutdown=%p", 
             coreclr_initialize, coreclr_execute_assembly, coreclr_shutdown); 
        return -12; 
    }
    
    if (coreclr_shutdown) {
        LOGI("CoreCLR shutdown function available");
    } else {
        LOGW("CoreCLR shutdown function not available (expected in .NET 7+)");
    }
    
    // 7. 准备 CoreCLR 初始化参数
    const char* keys[] = { 
        "TRUSTED_PLATFORM_ASSEMBLIES",      // 受信程序集列表
        "APP_PATHS",                        // 应用程序路径
        "APP_CONTEXT_BASE_DIRECTORY",       // 应用程序基础目录
        "NATIVE_DLL_SEARCH_DIRECTORIES"     // 原生 DLL 搜索目录
    };
    const char* vals[] = { 
        g_trustedAssemblies, 
        h_appDir, 
        h_appDir, 
        g_nativeSearchPaths 
    };
    
    // 8. 初始化 CoreCLR 运行时
    void* hostHandle; 
    unsigned int domainId;
    int rc = coreclr_initialize(g_launcherDll, "AppDomain", 4, keys, vals, &hostHandle, &domainId);
    if (rc != 0) { 
        dlclose(coreclrLib); 
        LOGE("coreclr_initialize fail: %d", rc); 
        return -13; 
    }
    
    // 9. 执行主程序集
    unsigned int exitCode = 0;
    const char* argv[] = { h_appPath };
    rc = coreclr_execute_assembly(hostHandle, domainId, 1, argv, g_launcherDll, &exitCode);
    
    // 10. 关闭 CoreCLR 运行时（如果函数可用）
    if (coreclr_shutdown) {
        LOGI("Calling coreclr_shutdown");
        coreclr_shutdown(hostHandle, domainId);
    } else {
        LOGW("Skipping coreclr_shutdown (not available in this .NET version)");
    }
    
    // 11. 卸载 CoreCLR 动态库
    dlclose(coreclrLib);
    
    // 12. 返回退出码
    LOGI("CoreCLR execution finished with result: %d", rc == 0 ? (int)exitCode : -20);
    return rc == 0 ? (int)exitCode : -20;
}


