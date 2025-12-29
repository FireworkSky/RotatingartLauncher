/*
 * Copyright 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

#ifndef ANDROID_INCLUDE_HARDWARE_HWVULKAN_H
#define ANDROID_INCLUDE_HARDWARE_HWVULKAN_H

#include "hardware.h"

__BEGIN_DECLS

#define HWVULKAN_HARDWARE_MODULE_ID "vulkan"

#define HWVULKAN_MODULE_API_VERSION_0_1 HARDWARE_MAKE_API_VERSION(0, 1)
#define HWVULKAN_DEVICE_API_VERSION_0_1 HARDWARE_MAKE_API_VERSION(0, 1)

#define HWVULKAN_DEVICE_0 "vk0"

// Forward declarations for Vulkan types
typedef struct VkInstance_T* VkInstance;
typedef struct VkPhysicalDevice_T* VkPhysicalDevice;
typedef void (*PFN_vkVoidFunction)(void);
typedef uint32_t VkResult;

// Vulkan function pointer types
typedef PFN_vkVoidFunction (*PFN_vkGetInstanceProcAddr)(VkInstance instance, const char* pName);

// VkExtensionProperties - simplified
typedef struct VkExtensionProperties {
    char extensionName[256];
    uint32_t specVersion;
} VkExtensionProperties;

typedef struct hwvulkan_module_t {
    struct hw_module_t common;
} hwvulkan_module_t;

typedef struct hwvulkan_device_t {
    struct hw_device_t common;

    PFN_vkVoidFunction (*EnumerateInstanceExtensionProperties)(
        const char* pLayerName,
        uint32_t* pPropertyCount,
        VkExtensionProperties* pProperties);

    PFN_vkVoidFunction (*CreateInstance)(
        const void* pCreateInfo,
        const void* pAllocator,
        VkInstance* pInstance);

    PFN_vkGetInstanceProcAddr GetInstanceProcAddr;
} hwvulkan_device_t;

__END_DECLS

#endif  /* ANDROID_INCLUDE_HARDWARE_HWVULKAN_H */
