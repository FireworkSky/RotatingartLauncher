#include "shared_envvars.hpp"

#define LOG_TAG "SharedEnvVars"

#include <android/log.h>
#include <cstdlib>
#include <string>
#include <filesystem>

namespace fs = std::filesystem;

namespace ral::shared_envvars {

    static std::string get_package_name() {
        const char *package_name_cstr = getenv("PACKAGE_NAME"); // RaLaunchApplication.java 中设置了
        if (package_name_cstr != nullptr) {
            return {package_name_cstr};
        }

        // 备用方案：如果环境变量未设置，返回默认包名
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                            "PACKAGE_NAME not set, using default: com.app.ralaunch");
        return {"com.app.ralaunch"};
    }

    static std::string get_external_storage_directory() {
        const char *external_storage_directory_cstr = getenv("EXTERNAL_STORAGE_DIRECTORY"); // RaLaunchApplication.java 中设置了
        if (external_storage_directory_cstr != nullptr) {
            return {external_storage_directory_cstr};
        }

        // 备用方案：如果环境变量未设置，尝试使用默认路径
        // Android 10+ 使用 scoped storage，但我们可以尝试常见的路径
        const char* default_paths[] = {
                "/storage/emulated/0",
                "/sdcard",
                "/storage/sdcard0",
                nullptr
        };

        for (int i = 0; default_paths[i] != nullptr; i++) {
            std::error_code ec;
            if (fs::exists(default_paths[i], ec) && fs::is_directory(default_paths[i], ec)) {
                __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                                    "EXTERNAL_STORAGE_DIRECTORY not set, using fallback: %s", default_paths[i]);
                return {default_paths[i]};
            }
        }

        // 如果所有备用方案都失败，返回空字符串并记录错误
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "EXTERNAL_STORAGE_DIRECTORY not set and no fallback path available");
        return {""};
    }

    static bool is_set_thread_affinity_to_big_core() {
        const char *env_value = getenv("SET_THREAD_AFFINITY_TO_BIG_CORE");
        return (env_value != nullptr) && (strcmp(env_value, "1") == 0);
    }
} // ral