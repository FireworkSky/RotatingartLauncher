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
 * @param argc 命令行参数数量（0 表示无参数）
 * @param argv 命令行参数数组（NULL 表示无参数）
 * 
 * @return 0 成功，负数表示失败
 */
int netcorehost_set_params(
    const char* app_dir,
    const char* main_assembly,
    const char* dotnet_root,
    int framework_major,
    int argc,
    const char* const* argv);

/**
 * @brief 启动 .NET 应用程序
 * 
 * @return 应用程序退出码（0 表示成功）
 */
int netcorehost_launch();

/**
 * @brief 获取最后一次错误的详细消息
 */
const char* netcorehost_get_last_error();

/**
 * @brief 清理资源
 */
void netcorehost_cleanup();

/**
 * @brief 通用进程启动器 - 供 .NET P/Invoke 调用
 * 
 * 在独立进程中启动 .NET 程序集，所有参数由 C# 控制
 * 
 * @param assembly_path 程序集完整路径
 * @param args_json     命令行参数 JSON 数组（如 ["-server", "-world", "xxx"]）
 * @param startup_hooks DOTNET_STARTUP_HOOKS 值，可为 nullptr
 * @param title         通知标题
 * @return 0 成功，非0 失败
 */
int process_launcher_start(const char* assembly_path, const char* args_json, 
                           const char* startup_hooks, const char* title);

#ifdef __cplusplus
}
#endif
