#ifndef VULKAN_TURNIP_LOADER_H
#define VULKAN_TURNIP_LOADER_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief 加载 Turnip Vulkan 驱动（Adreno GPU）
 * @return true 成功，false 失败
 * 
 * 注意：此函数应在 Vulkan 库加载之前调用
 */
bool vulkan_turnip_loader_load(void);

#ifdef __cplusplus
}
#endif

#endif // VULKAN_TURNIP_LOADER_H



