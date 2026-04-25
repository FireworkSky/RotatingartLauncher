package com.app.ralaunch.core.di

import android.app.Application
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.logging.contract.Logger
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Koin 初始化器
 */
object KoinInitializer {

    /**
     * 初始化 Koin DI 框架
     */
    fun init(application: Application) {
        val koinApplication = startKoin {
            // 日志级别
            androidLogger(Level.ERROR)

            // Android Context
            androidContext(application)

            // 加载模块
            modules(
                getAppModules()
            )
        }
        AppLog.install(koinApplication.koin.get<Logger>())
    }
}
