/**
 * @file dotnet_params.c
 * @brief .NET 运行时启动参数管理实现
 */

#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include "dotnet_params.h"

#define LOG_TAG "GameLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/** 全局应用路径 */
char* g_appPath = NULL;

/** 全局 .NET 路径 */
char* g_dotnetPath = NULL;

/** 全局框架版本 */
char* g_frameworkVersion = NULL;

/** 全局详细日志标志 */
int g_verboseLogging = 0;

/** 全局渲染器类型 */
char* g_renderer = NULL;

/** Bootstrap DLL 路径 */
char* g_bootstrapDll = NULL;

/** 目标游戏程序集路径 */
char* g_targetGameAssembly = NULL;

/**
 * @brief 清理所有全局内存分配
 * 
 * 此函数释放所有全局参数字符串的内存，并将指针重置为 NULL。
 * 在设置新参数前或程序退出时调用，防止内存泄漏。
 */
void CleanupGlobalMemory() {
    free(g_appPath);
    free(g_dotnetPath);
    free(g_frameworkVersion);
    free(g_renderer);
    free(g_bootstrapDll);
    free(g_targetGameAssembly);
    g_appPath = g_dotnetPath = g_frameworkVersion = g_renderer = NULL;
    g_bootstrapDll = g_targetGameAssembly = NULL;
}

/**
 * @brief 设置 .NET 应用启动参数（基础版本）
 * 
 * @param appPath .NET 应用程序的主程序集路径
 * @param dotnetPath .NET 运行时根目录路径
 * 
 * 清理旧参数后，使用 strdup 复制新参数到全局变量。
 * 如果参数为 NULL，对应的全局变量将保持为 NULL。
 */
void Params_SetLaunch(const char* appPath, const char* dotnetPath) {
    // ⚠️ 保存全局设置（这些不应该被清理）
    char* savedRenderer = g_renderer ? strdup(g_renderer) : NULL;
    int savedVerboseLogging = g_verboseLogging;
    
    CleanupGlobalMemory();
    
    // ⚠️ 恢复全局设置
    g_renderer = savedRenderer;
    g_verboseLogging = savedVerboseLogging;
    
    if (appPath) g_appPath = strdup(appPath);
    if (dotnetPath) g_dotnetPath = strdup(dotnetPath);
    LOGI("Launch params set: appPath=%s, dotnetPath=%s", g_appPath, g_dotnetPath);
}

/**
 * @brief 设置 .NET 应用启动参数（包含运行时版本）
 * 
 * @param appPath .NET 应用程序的主程序集路径
 * @param dotnetPath .NET 运行时根目录路径
 * @param frameworkVersion 指定的框架版本（如 "8.0.1"），可为空表示自动选择
 * 
 * 此函数扩展了基础版本，允许指定特定的框架版本。
 * 如果 frameworkVersion 为 NULL 或空字符串，g_frameworkVersion 将保持为 NULL，
 * 表示使用自动版本选择（通常选择最高可用版本）。
 */
void Params_SetLaunchWithRuntime(const char* appPath, const char* dotnetPath, const char* frameworkVersion) {
    // ⚠️ 保存全局设置（这些不应该被清理）
    char* savedRenderer = g_renderer ? strdup(g_renderer) : NULL;
    int savedVerboseLogging = g_verboseLogging;
    
    CleanupGlobalMemory();
    
    // ⚠️ 恢复全局设置
    g_renderer = savedRenderer;
    g_verboseLogging = savedVerboseLogging;
    
    if (appPath) g_appPath = strdup(appPath);
    if (dotnetPath) g_dotnetPath = strdup(dotnetPath);
    if (frameworkVersion && frameworkVersion[0]) g_frameworkVersion = strdup(frameworkVersion);
    LOGI("Launch params set: appPath=%s, dotnetPath=%s, frameworkVersion=%s", g_appPath, g_dotnetPath, g_frameworkVersion ? g_frameworkVersion : "<auto>");
}

/**
 * @brief 设置Bootstrap启动参数
 * 
 * @param bootstrapDll Bootstrap程序集路径
 * @param targetGameAssembly 目标游戏程序集路径
 * @param dotnetPath .NET 运行时根目录路径
 * 
 * 此函数用于Bootstrap反射启动模式，Bootstrap将通过反射加载并启动目标游戏程序集。
 */
void Params_SetBootstrapLaunch(const char* bootstrapDll, const char* targetGameAssembly, const char* dotnetPath) {
    // ⚠️ 保存全局设置（这些不应该被清理）
    char* savedRenderer = g_renderer ? strdup(g_renderer) : NULL;
    int savedVerboseLogging = g_verboseLogging;
    
    // 先清理旧的全局变量
    CleanupGlobalMemory();
    
    // ⚠️ 恢复全局设置
    g_renderer = savedRenderer;
    g_verboseLogging = savedVerboseLogging;
    
    // 设置Bootstrap特定的变量
    if (bootstrapDll) g_bootstrapDll = strdup(bootstrapDll);
    if (targetGameAssembly) g_targetGameAssembly = strdup(targetGameAssembly);
    if (dotnetPath) g_dotnetPath = strdup(dotnetPath);
    
    // 同时设置传统的启动变量，以便 launch_with_coreclr_passthrough 可以使用
    // 在Bootstrap模式下，主程序集是Bootstrap.dll
    if (bootstrapDll) g_appPath = strdup(bootstrapDll);
    
    LOGI("Bootstrap launch params set: bootstrapDll=%s, targetGameAssembly=%s, dotnetPath=%s", 
         g_bootstrapDll, g_targetGameAssembly, g_dotnetPath);
}


