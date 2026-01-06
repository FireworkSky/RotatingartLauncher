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
