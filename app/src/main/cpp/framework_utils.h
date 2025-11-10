/**
 * @file framework_utils.h
 * @brief 框架工具（保留以兼容旧代码）
 */
#pragma once

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// 保留函数声明但不再使用
int framework_pick_version(
    const char* dotnet_root,
    int preferred_major_version,
    char* out_version,
    size_t out_size);

int framework_generate_runtimeconfig(
    const char* app_dir,
    const char* assembly_name,
    const char* framework_version);

#ifdef __cplusplus
}
#endif





