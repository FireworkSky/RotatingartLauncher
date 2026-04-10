// SPDX-License-Identifier: BSD-2-Clause
// Copyright © 2021 Billy Laws

#include <array>
#include <cstdio>
#include <cstdlib>
#include <errno.h>
#include <dlfcn.h>
#include <unistd.h>
#include <fcntl.h>
#include <android/dlext.h>
#include <android/log.h>
#include <android/api-level.h>
#include <sys/mman.h>
#include "elf_soname_patcher.h"
#include "android_linker_ns.h"

#ifdef __cplusplus
extern "C" {
#endif

#define LOG_TAG "linkernsbypass"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

using loader_android_create_namespace_t = android_namespace_t *(*)(const char *, const char *,
                                                                   const char *, uint64_t,
                                                                   const char *,
                                                                   android_namespace_t *,
                                                                   const void *);
static loader_android_create_namespace_t loader_android_create_namespace;

static bool lib_loaded;
static int device_api_level = 0; // Cache API level


/* Public API */
bool linkernsbypass_load_status() {
    return lib_loaded;
}

struct android_namespace_t *android_create_namespace(const char *name,
                                                     const char *ld_library_path,
                                                     const char *default_library_path,
                                                     uint64_t type,
                                                     const char *permitted_when_isolated_path,
                                                     android_namespace_t *parent_namespace) {
    // === FIX: Android < 28 không có namespace, trả về NULL ===
    if (!lib_loaded) {
        LOGW("android_create_namespace: not available on API %d, returning NULL", device_api_level);
        return nullptr;
    }

    auto caller{__builtin_return_address(0)};
    return loader_android_create_namespace(name, ld_library_path, default_library_path, type,
                                           permitted_when_isolated_path, parent_namespace, caller);
}

struct android_namespace_t *android_create_namespace_escape(const char *name,
                                                            const char *ld_library_path,
                                                            const char *default_library_path,
                                                            uint64_t type,
                                                            const char *permitted_when_isolated_path,
                                                            android_namespace_t *parent_namespace) {
    // === FIX: Android < 28 không có namespace, trả về NULL ===
    if (!lib_loaded) {
        LOGW("android_create_namespace_escape: not available on API %d, returning NULL", device_api_level);
        return nullptr;
    }

    auto caller{reinterpret_cast<void *>(&dlopen)};
    return loader_android_create_namespace(name, ld_library_path, default_library_path, type,
                                           permitted_when_isolated_path, parent_namespace, caller);
}

android_get_exported_namespace_t android_get_exported_namespace;

android_link_namespaces_all_libs_t android_link_namespaces_all_libs;

android_link_namespaces_t android_link_namespaces;

bool linkernsbypass_link_namespace_to_default_all_libs(android_namespace_t *to) {
    // === FIX: Android < 28 không có namespace restriction, trả về true (thành công) ===
    if (!lib_loaded) {
        LOGI("linkernsbypass_link_namespace_to_default_all_libs: "
             "API %d has no namespace restrictions, returning success", device_api_level);
        return true;
    }

    static auto defaultNs{android_create_namespace_escape("default_copy", nullptr, nullptr,
                                                          ANDROID_NAMESPACE_TYPE_SHARED, nullptr,
                                                          nullptr)};
    return android_link_namespaces_all_libs(to, defaultNs);
}

void *linkernsbypass_namespace_dlopen(const char *filename, int flags, android_namespace_t *ns) {
    // === FIX: Android < 28 không có namespace, dùng dlopen thường ===
    if (!lib_loaded || ns == nullptr) {
        LOGI("linkernsbypass_namespace_dlopen: falling back to dlopen() for '%s' (API %d)",
             filename ? filename : "null", device_api_level);
        return dlopen(filename, flags);
    }

    android_dlextinfo extInfo{
            .flags = ANDROID_DLEXT_USE_NAMESPACE,
            .library_namespace = ns
    };

    return android_dlopen_ext(filename, flags, &extInfo);
}

#ifndef __NR_memfd_create
#if defined(__aarch64__)
#define __NR_memfd_create 279
#else
#error Unsupported target architecture!
#endif
#endif

void *
linkernsbypass_namespace_dlopen_unique(const char *libPath, const char *libTargetDir, int flags,
                                       android_namespace_t *ns) {
    // === FIX: Android < 28 không có namespace, dùng dlopen thường ===
    if (!lib_loaded || ns == nullptr) {
        LOGI("linkernsbypass_namespace_dlopen_unique: falling back to dlopen() for '%s' (API %d)",
             libPath ? libPath : "null", device_api_level);
        return dlopen(libPath, flags);
    }

    static std::array<char, PATH_MAX> PathBuf{};

    // Used as a unique ID for overwriting soname and creating target lib files
    static uint16_t TargetId{};

    int libTargetFd{[&]() {
        if (libTargetDir) {
            snprintf(PathBuf.data(), PathBuf.size(), "%s/%d_patched.so", libTargetDir, TargetId);
            return open(PathBuf.data(), O_CREAT | O_RDWR, S_IRUSR | S_IWUSR);
        } else {
            // If memfd isn't supported errno will contain ENOSYS after calling
            errno = 0;
            int fd{static_cast<int>(syscall(__NR_memfd_create, libPath, 0))};
            if (errno == ENOSYS || fd < 0)
                return -1;
            else
                return fd;
        }
    }()};
    if (libTargetFd == -1)
        return nullptr;

    // Partially overwrite soname with 3 digits (replacing lib...) with to make sure a cached so isn't loaded
    std::array<char, 3> sonameOverwrite{};
    snprintf(sonameOverwrite.data(), sonameOverwrite.size(), "%03u", TargetId++);

    if (!elf_soname_patch(libPath, libTargetFd, sonameOverwrite.data()))
        return nullptr;

    // Load our patched library into the hook namespace
    android_dlextinfo hookExtInfo{
            .flags = ANDROID_DLEXT_USE_NAMESPACE | ANDROID_DLEXT_USE_LIBRARY_FD,
            .library_fd = libTargetFd,
            .library_namespace = ns
    };

    // Make a path that looks about right
    snprintf(PathBuf.data(), PathBuf.size(), "/proc/self/fd/%d", libTargetFd);

    return android_dlopen_ext(PathBuf.data(), flags, &hookExtInfo);
}

static void *align_ptr(void *ptr) {
    return reinterpret_cast<void *>(reinterpret_cast<uintptr_t>(ptr) & ~(getpagesize() - 1));
}

/* Private */
__attribute__((constructor)) static void resolve_linker_symbols() {
    using loader_dlopen_t = void *(*)(const char *, int, const void *);

    // === FIX: Cache API level để dùng trong log ===
    device_api_level = android_get_device_api_level();

    if (device_api_level < 28) {
        LOGI("API level %d < 28: linker namespace bypass disabled, "
             "using standard dlopen() fallback (no namespace restrictions on this Android version)",
             device_api_level);
        return;
    }

    // ARM64 specific function walking to locate the internal dlopen handler
    auto loader_dlopen{[]() {
        union BranchLinked {
            uint32_t raw;

            struct {
                int32_t offset: 26; //!< 26-bit branch offset
                uint8_t sig: 6;  //!< 6-bit signature
            };

            bool Verify() {
                return sig == 0x25;
            }
        };
        static_assert(sizeof(BranchLinked) == 4, "BranchLinked is wrong size");

        // Some devices ship with --X mapping for executables so work around that
        mprotect(align_ptr(reinterpret_cast<void *>(&dlopen)), getpagesize(),
                 PROT_WRITE | PROT_READ | PROT_EXEC);

        // dlopen is just a wrapper for __loader_dlopen that passes the return address as the third arg
        auto blInstr{reinterpret_cast<BranchLinked *>(&dlopen)};
        while (!blInstr->Verify())
            blInstr++;

        return reinterpret_cast<loader_dlopen_t>(blInstr + blInstr->offset);
    }()};

    mprotect(align_ptr(reinterpret_cast<void *>(&loader_dlopen)), getpagesize(),
             PROT_WRITE | PROT_READ | PROT_EXEC);

    auto ldHandle{loader_dlopen("ld-android.so", RTLD_LAZY, reinterpret_cast<void *>(&dlopen))};
    if (!ldHandle)
        return;

    android_link_namespaces_all_libs = reinterpret_cast<android_link_namespaces_all_libs_t>(dlsym(
            ldHandle, "__loader_android_link_namespaces_all_libs"));
    if (!android_link_namespaces_all_libs)
        return;

    android_link_namespaces = reinterpret_cast<android_link_namespaces_t>(dlsym(ldHandle,
                                                                                "__loader_android_link_namespaces"));
    if (!android_link_namespaces)
        return;

    auto libdlAndroidHandle{
            loader_dlopen("libdl_android.so", RTLD_LAZY, reinterpret_cast<void *>(&dlopen))};
    if (!libdlAndroidHandle)
        return;

    loader_android_create_namespace = reinterpret_cast<loader_android_create_namespace_t>(dlsym(
            libdlAndroidHandle, "__loader_android_create_namespace"));
    if (!loader_android_create_namespace)
        return;

    android_get_exported_namespace = reinterpret_cast<android_get_exported_namespace_t>(dlsym(
            libdlAndroidHandle, "__loader_android_get_exported_namespace"));
    if (!android_get_exported_namespace)
        return;

    // Lib is now safe to use
    lib_loaded = true;
    LOGI("Linker namespace bypass loaded successfully on API %d", device_api_level);
}

#ifdef __cplusplus
}
#endif
