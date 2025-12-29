#ifndef DXVK_LOADER_H
#define DXVK_LOADER_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief 初始化 DXVK 渲染器
 * 
 * 加载 DXVK 库并设置必要的环境变量
 * 
 * @return true 成功
 * @return false 失败
 */
bool dxvk_loader_init(void);

/**
 * @brief 加载指定的 DXVK 组件
 * 
 * @param component 组件名称: "d3d8", "d3d9", "d3d10", "d3d11", "dxgi"
 * @return void* 库句柄，失败返回 NULL
 */
void* dxvk_loader_load_component(const char* component);

/**
 * @brief 检查 DXVK 是否可用
 * 
 * @return true DXVK 库存在
 * @return false DXVK 库不存在
 */
bool dxvk_loader_is_available(void);

/**
 * @brief 获取 DXVK 版本信息
 * 
 * @return const char* 版本字符串
 */
const char* dxvk_loader_get_version(void);

/**
 * @brief 清理 DXVK 资源
 */
void dxvk_loader_cleanup(void);

#ifdef __cplusplus
}
#endif

#endif // DXVK_LOADER_H




