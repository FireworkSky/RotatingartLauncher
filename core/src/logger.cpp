//
// Created by eternalfuture on 2026/5/4.
//

#include "logger.hpp"

#include <spdlog/sinks/android_sink.h>
#include <spdlog/sinks/sink.h>
#include <spdlog/sinks/basic_file_sink.h>
#include <spdlog/sinks/stdout_color_sinks.h>

void RALauncher::Logger::init() {
    try {
        if (auto existing_logger = spdlog::get("Rotating-art-Launcher")) {
            return;
        }

        std::vector<spdlog::sink_ptr> sinks;


        const auto android_sink = std::make_shared<spdlog::sinks::android_sink_mt>(
                "Rotating-art-Launcher");
        android_sink->set_pattern("%^[%Y-%m-%d %H:%M:%S.%e] [%l] [%n] %v%$");
        sinks.push_back(android_sink);


        auto logger = std::make_shared<spdlog::logger>("Rotating-art-Launcher", begin(sinks),
                                                       end(sinks));
#if  !defined(NDEBUG)
        logger->set_level(spdlog::level::trace);
#else
        logger->set_level(spdlog::level::info);
#endif

        if (!spdlog::get("Rotating-art-Launcher")) {
            spdlog::register_logger(logger);
            spdlog::set_default_logger(logger);
        } else {
            logger = spdlog::get("Rotating-art-Launcher");
        }

        logger->flush_on(spdlog::level::info);
        g_logger = logger;

        LOGI("New logger initialized successfully.");

    } catch (const spdlog::spdlog_ex &ex) {
        spdlog::error("Logger initialization failed: {}", ex.what());
        throw;
    }
}

void RALauncher::Logger::shutdown() {
    LOGI("Shutting down logger...");

    g_logger->flush();
    spdlog::drop("Rotating-art-Launcher");
    spdlog::shutdown();
}