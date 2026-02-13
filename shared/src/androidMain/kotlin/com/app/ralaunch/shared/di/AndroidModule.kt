package com.app.ralaunch.shared.di

import com.app.ralaunch.shared.data.local.AndroidControlLayoutStorage
import com.app.ralaunch.shared.data.local.AndroidGameListStorage
import com.app.ralaunch.shared.data.local.StoragePathsProvider
import com.app.ralaunch.shared.data.repository.ControlLayoutRepositoryImpl
import com.app.ralaunch.shared.data.repository.ControlLayoutStorage
import com.app.ralaunch.shared.data.repository.GameListStorage
import com.app.ralaunch.shared.data.service.AndroidControlLayoutService
import com.app.ralaunch.shared.data.service.AndroidGameLaunchService
import com.app.ralaunch.shared.data.service.AndroidPatchService
import com.app.ralaunch.shared.domain.repository.ControlLayoutRepositoryV2
import com.app.ralaunch.shared.domain.service.ControlLayoutService
import com.app.ralaunch.shared.domain.service.GameLaunchService
import com.app.ralaunch.shared.domain.service.PatchService
import com.app.ralaunch.shared.ui.screens.settings.AppInfo
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android 平台特定 Koin 模块
 */
val androidModule = module {

    // ==================== 数据存储 ====================

    // StoragePathsProvider
    single<StoragePathsProvider> {
        StoragePathsProvider(androidContext())
    }

    // GameListStorage
    single<GameListStorage> {
        AndroidGameListStorage(get())
    }

    // ControlLayoutStorage
    single<ControlLayoutStorage> {
        AndroidControlLayoutStorage(get())
    }

    // ==================== Repositories ====================

    single<ControlLayoutRepositoryV2> {
        ControlLayoutRepositoryImpl(storage = get())
    }

    // ==================== 服务 ====================
    
    // 游戏启动服务
    single<GameLaunchService> {
        AndroidGameLaunchService(androidContext())
    }
    
    // 控制布局服务
    single<ControlLayoutService> {
        AndroidControlLayoutService(androidContext())
    }
    
    // 补丁服务
    single<PatchService> {
        AndroidPatchService(androidContext())
    }

    // ==================== App Info ====================

    single {
        try {
            val context = androidContext()
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            AppInfo(
                versionName = packageInfo.versionName ?: "Unknown",
                versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            )
        } catch (e: Exception) {
            AppInfo()
        }
    }
}

/**
 * 获取所有 Android 模块（包含共享模块）
 */
fun getAndroidModules() = getSharedModules() + androidModule
