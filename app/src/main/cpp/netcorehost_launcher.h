/**
 * @file netcorehost_launcher.h
 * @brief 简化的 .NET 启动器（使用 netcorehost API）
 * 
 * 使用 netcorehost C++ API 替代直接的 CoreCLR API 调用，
 * 大幅简化代码并提供更好的错误处理。
 */
#pragma once

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief 设置 .NET 应用启动参数
 * 
 * @param app_dir 应用程序目录（包含 .dll 和 .runtimeconfig.json）
 * @param main_assembly 主程序集名称（如 "MyGame.dll"）
 * @param dotnet_root .NET 运行时根目录（可选，NULL 表示使用系统默认）
 * @param framework_major 首选框架主版本号（如 8 表示 .NET 8，0 表示最高版本）
 * @param hostfxr_override_path 可选，用于指定 hostfxr.so 的替代路径
 * @param argc 命令行参数数量（不包括程序集路径本身，0 表示无参数）
 * @param argv 命令行参数数组（不包括程序集路径本身，NULL 表示无参数）
 * 
 * @return 0 成功，负数表示失败
 */
int netcorehost_set_params(
    const char* app_dir,
    const char* main_assembly,
    const char* dotnet_root,
    int framework_major,
    const char* hostfxr_override_path,
    int argc,
    const char* const* argv);

/**
 * @brief 启动 .NET 应用程序
 * 
 * @return 应用程序退出码（0 表示成功）
 * 
 * 此函数使用 netcorehost API 启动 .NET 应用，执行流程：
 * 1. 加载 nethost 库
 * 2. 定位 hostfxr.so
 * 3. 初始化运行时
 * 4. 执行主程序集
 * 
 * 错误码：
 * - -1: 参数未设置
 * - -2: nethost/hostfxr 加载失败
 * - -3: 运行时初始化失败
 * - -4: 程序集执行失败
 */
int netcorehost_launch();

/**
 * @brief 清理资源
 * 
 * 释放所有分配的内存和资源
 */
void netcorehost_cleanup();

#ifdef __cplusplus
}
#endif

