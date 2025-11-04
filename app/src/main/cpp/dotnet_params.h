/**
 * @file dotnet_params.h
 * @brief .NET 运行时启动参数与全局状态管理
 * 
 * 此头文件定义了 .NET 应用程序启动所需的全局参数，包括应用路径、
 * .NET 运行时路径和框架版本。这些参数由 Java 层通过 JNI 传递，
 * 并在 CoreCLR 启动时使用。
 */
#pragma once

#include <jni.h>

/**
 * @brief .NET 应用程序的主程序集路径（如 /data/data/com.app/files/games/MyGame.dll）
 */
extern char* g_appPath;

/**
 * @brief .NET 运行时根目录路径（如 /data/data/com.app/files/dotnet）
 */
extern char* g_dotnetPath;

/**
 * @brief 指定的 .NET 框架版本（如 "8.0.1"），为空则自动选择最高版本
 */
extern char* g_frameworkVersion;

/**
 * @brief 是否启用详细日志模式（0 = 禁用，1 = 启用）
 * 
 * 启用后，CoreCLR 会输出详细的运行时日志，包括：
 * - 程序集加载信息
 * - JIT 编译信息
 * - 垃圾回收信息
 * - 异常堆栈跟踪
 */
extern int g_verboseLogging;

/**
 * @brief FNA 渲染器类型（字符串）
 * 
 * 支持的渲染器：
 * - "opengl_gl4es": OpenGL 通过 gl4es 翻译到 OpenGL ES（推荐）
 * - "opengl_native": 原生 OpenGL（可能不支持）
 * - "vulkan": Vulkan 渲染器（未来支持）
 */
extern char* g_renderer;

/**
 * @brief Bootstrap DLL 路径（用于反射启动）
 */
extern char* g_bootstrapDll;

/**
 * @brief 目标游戏程序集路径（传递给Bootstrap）
 */
extern char* g_targetGameAssembly;

/**
 * @brief 清理所有全局内存分配
 * 
 * 释放 g_appPath、g_dotnetPath 和 g_frameworkVersion 的内存，
 * 并将指针重置为 NULL。通常在程序退出或重新启动前调用。
 */
void CleanupGlobalMemory();

/**
 * @brief 设置 .NET 应用启动参数（基础版本）
 * 
 * @param appPath .NET 应用程序的主程序集路径
 * @param dotnetPath .NET 运行时根目录路径
 * 
 * 此函数会清理旧的全局参数，并复制新参数到全局变量。
 * 不指定框架版本时，将自动选择最高可用版本。
 */
void Params_SetLaunch(const char* appPath, const char* dotnetPath);

/**
 * @brief 设置 .NET 应用启动参数（包含运行时版本）
 * 
 * @param appPath .NET 应用程序的主程序集路径
 * @param dotnetPath .NET 运行时根目录路径
 * @param frameworkVersion 指定的框架版本（如 "8.0.1"），可为空表示自动选择
 * 
 * 此函数在基础版本之上增加了框架版本控制，允许应用使用特定
 * 版本的 .NET 运行时。如果 frameworkVersion 为空或仅包含空白字符，
 * 则行为等同于 Params_SetLaunch。
 */
void Params_SetLaunchWithRuntime(const char* appPath, const char* dotnetPath, const char* frameworkVersion);

/**
 * @brief 设置Bootstrap启动参数
 * 
 * @param bootstrapDll Bootstrap程序集路径
 * @param targetGameAssembly 目标游戏程序集路径
 * @param dotnetPath .NET 运行时根目录路径
 * 
 * 此函数用于Bootstrap反射启动模式，Bootstrap将通过反射加载并启动目标游戏程序集。
 */
void Params_SetBootstrapLaunch(const char* bootstrapDll, const char* targetGameAssembly, const char* dotnetPath);


