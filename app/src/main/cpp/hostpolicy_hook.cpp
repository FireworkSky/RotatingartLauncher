#include "hostpolicy_hook.h"

#if defined(__aarch64__)

#include <cstdint>

#include "And64InlineHook/And64InlineHook.hpp"

#include <android/dlext.h>
#include <android/log.h>
#include <dlfcn.h>
#include <link.h>
#include <sys/mman.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <cerrno>
#include <cstring>
#include <limits>
#include <vector>

namespace {

constexpr const char* kLogTag = "HostpolicyHook";

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  kLogTag, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)

bool ProtectAndWrite(void* addr, const void* data, size_t len) {
    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) page_size = 4096;
    uintptr_t start = reinterpret_cast<uintptr_t>(addr) & ~static_cast<uintptr_t>(page_size - 1);
    uintptr_t end = (reinterpret_cast<uintptr_t>(addr) + len + page_size - 1) & ~static_cast<uintptr_t>(page_size - 1);
    size_t length = end - start;
    if (mprotect(reinterpret_cast<void*>(start), length, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        LOGE("mprotect 失败: %s", strerror(errno));
        return false;
    }
    memcpy(addr, data, len);
    __builtin___clear_cache(reinterpret_cast<char*>(addr), reinterpret_cast<char*>(addr) + len);
    return true;
}

} // namespace

// 辅助结构：存储可读段信息
struct ReadableSegment {
    uintptr_t start;
    uintptr_t end;
};

// 收集所有可读段的回调
int CollectReadableSegmentsCallback(struct dl_phdr_info* info, size_t, void* data) {
    auto* segments = static_cast<std::vector<ReadableSegment>*>(data);

    if (!info->dlpi_name) {
        return 0;
    }
    const char* name = strrchr(info->dlpi_name, '/');
    name = name ? name + 1 : info->dlpi_name;
    if (!name || strstr(name, "libhostpolicy.so") == nullptr) {
        return 0;
    }

    // 收集所有可读段
    for (int i = 0; i < info->dlpi_phnum; ++i) {
        const auto& ph = info->dlpi_phdr[i];
        // 只处理可读的 PT_LOAD 段 (PF_R = 0x4)
        if (ph.p_type != PT_LOAD || !(ph.p_flags & 0x4)) {
            continue;
        }
        ReadableSegment seg;
        seg.start = static_cast<uintptr_t>(info->dlpi_addr) + ph.p_vaddr;
        seg.end = seg.start + ph.p_memsz;
        segments->push_back(seg);
        LOGI("找到可读段: [%p - %p] size=%zu",
             reinterpret_cast<void*>(seg.start),
             reinterpret_cast<void*>(seg.end),
             ph.p_memsz);
    }

    return segments->empty() ? 0 : 1;
}

bool PatchHostpolicyStrings() {
    std::vector<ReadableSegment> segments;
    dl_iterate_phdr(CollectReadableSegmentsCallback, &segments);

    if (segments.empty()) {
        LOGW("未找到 libhostpolicy.so 的可读段");
        return false;
    }

    static constexpr char kFrom[] = "HOST_RUNTIME_CONTRACT";
    static constexpr char kTo[] = "HOST_RUNTIME_GUGUGAGA";
    static_assert(sizeof(kFrom) == sizeof(kTo), "replacement must keep length");

    const auto* needle = reinterpret_cast<const uint8_t*>(kFrom);
    const auto* replacement = reinterpret_cast<const uint8_t*>(kTo);
    const size_t len = sizeof(kFrom) - 1;

    bool any_patched = false;

    // 在每个可读段中搜索
    for (const auto& seg : segments) {
        auto* start = reinterpret_cast<uint8_t*>(seg.start);
        auto* end = reinterpret_cast<uint8_t*>(seg.end);

        auto cursor = start;
        while (cursor < end) {
            auto pos = std::search(cursor, end, needle, needle + len);
            if (pos == end) {
                break;
            }
            if (ProtectAndWrite(pos, replacement, len)) {
                LOGI("已替换 %s -> %s @ %p", kFrom, kTo, pos);
                any_patched = true;
            }
            cursor = pos + len;
        }
    }

    if (!any_patched) {
        LOGW("未在 libhostpolicy.so 中找到 %s", kFrom);
    }
    return any_patched;
}

namespace {

void* (*orig_dlopen)(const char*, int) = nullptr;
void* HookedDlopen(const char* filename, int flags) {
    void* handle = orig_dlopen ? orig_dlopen(filename, flags) : nullptr;
    if (!orig_dlopen) {
        auto real = reinterpret_cast<void* (*)(const char*, int)>(dlsym(RTLD_NEXT, "dlopen"));
        handle = real ? real(filename, flags) : nullptr;
    }
    if (handle && filename && strstr(filename, "libhostpolicy.so")) {
        bool result = PatchHostpolicyStrings();
        if (result) {
            LOGI("dlopen: 成功替换 libhostpolicy.so 字符串");
        } else {
            LOGW("dlopen: 未找到需要替换的字符串");
        }
    }
    return handle;
}

void* (*orig_android_dlopen_ext)(const char*, int, const android_dlextinfo*) = nullptr;
void* HookedAndroidDlopenExt(const char* filename, int flags, const android_dlextinfo* extinfo) {
    void* handle = orig_android_dlopen_ext ? orig_android_dlopen_ext(filename, flags, extinfo) : nullptr;
    if (!orig_android_dlopen_ext) {
        auto real = reinterpret_cast<void* (*)(const char*, int, const android_dlextinfo*)>(
            dlsym(RTLD_NEXT, "android_dlopen_ext"));
        handle = real ? real(filename, flags, extinfo) : nullptr;
    }
    if (handle && filename && strstr(filename, "libhostpolicy.so")) {
        bool result = PatchHostpolicyStrings();
        if (result) {
            LOGI("android_dlopen_ext: 成功替换 libhostpolicy.so 字符串");
        } else {
            LOGW("android_dlopen_ext: 未找到需要替换的字符串");
        }
    }
    return handle;
}

void InstallIfSymbolExists(void* libdl, const char* symbol_name, void** original, void* replacement) {
    if (!libdl) {
        return;
    }
    void* target = dlsym(libdl, symbol_name);
    if (!target) {
        LOGW("未找到符号 %s", symbol_name);
        return;
    }

    A64HookFunction(target, replacement, original);
    LOGI("已安装 %s hook", symbol_name);
}

} // namespace

extern "C" {

void InstallHostpolicyHook() {
    static bool installed = false;
    if (installed) {
        return;
    }

    void *libdl = dlopen("libdl.so", RTLD_NOW);
    if (!libdl) {
        LOGE("无法打开 libdl.so: %s", dlerror());
        return;
    }

    InstallIfSymbolExists(libdl, "dlopen",
                          reinterpret_cast<void **>(&orig_dlopen),
                          reinterpret_cast<void *>(HookedDlopen));
    InstallIfSymbolExists(libdl, "android_dlopen_ext",
                          reinterpret_cast<void **>(&orig_android_dlopen_ext),
                          reinterpret_cast<void *>(HookedAndroidDlopenExt));

    dlclose(libdl);
    installed = true;

    // 若 libhostpolicy 已加载，则立即尝试补丁一次
    PatchHostpolicyStrings();
}

} // extern "C"


#undef LOGI
#undef LOGW
#undef LOGE

#endif // __aarch64__

