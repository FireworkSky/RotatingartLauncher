//
// Created by eternalfuture on 2026/5/4.
//

#pragma once

#include <spdlog/spdlog.h>

#if !defined(NDEBUG)
#define LOGT(...)    spdlog::trace("[{}:{}:{}] {}", __FILE__, __LINE__, __FUNCTION__, fmt::format(__VA_ARGS__))
#define LOGD(...)    spdlog::debug("[{}:{}:{}] {}", __FILE__, __LINE__, __FUNCTION__, fmt::format(__VA_ARGS__))
#define LOGI(...)     spdlog::info("[{}:{}:{}] {}", __FILE__, __LINE__, __FUNCTION__, fmt::format(__VA_ARGS__))
#define LOGW(...)     spdlog::warn("[{}:{}:{}] {}", __FILE__, __LINE__, __FUNCTION__, fmt::format(__VA_ARGS__))
#define LOGE(...)    spdlog::error("[{}:{}:{}] {}", __FILE__, __LINE__, __FUNCTION__, fmt::format(__VA_ARGS__))
#define LOGC(...) spdlog::critical("[{}:{}:{}] {}", __FILE__, __LINE__, __FUNCTION__, fmt::format(__VA_ARGS__))
#else
#define LOGT(...)    spdlog::trace("{}", fmt::format(__VA_ARGS__))
#define LOGD(...)    spdlog::debug("{}", fmt::format(__VA_ARGS__))
#define LOGI(...)     spdlog::info("{}", fmt::format(__VA_ARGS__))
#define LOGW(...)     spdlog::warn("{}", fmt::format(__VA_ARGS__))
#define LOGE(...)    spdlog::error("{}", fmt::format(__VA_ARGS__))
#define LOGC(...) spdlog::critical("{}", fmt::format(__VA_ARGS__))
#endif


namespace RALauncher::Logger {
    inline std::shared_ptr<spdlog::logger> g_logger{};  // 全局日志实例


    /**
     * 初始化日志系统
     */
    void init();

    /**
     * 关闭日志系统
     */
    void shutdown();
}