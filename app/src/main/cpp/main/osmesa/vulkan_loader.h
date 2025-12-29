//
// Vulkan loader for zink
//
#ifndef VULKAN_LOADER_H
#define VULKAN_LOADER_H

#include <stdbool.h>

/**
 * @brief Load Vulkan library
 * @return true on success, false on failure
 * 
 * Loads libvulkan.so and stores the pointer in VULKAN_PTR environment variable.
 * This must be called before creating OSMesa context when using zink renderer.
 */
bool vulkan_loader_load(void);

/**
 * @brief Get Vulkan library handle
 * @return Vulkan library handle or NULL if not loaded
 */
void* vulkan_loader_get_handle(void);

/**
 * @brief Check if Vulkan is loaded
 * @return true if loaded, false otherwise
 */
bool vulkan_loader_is_loaded(void);

#endif // VULKAN_LOADER_H



