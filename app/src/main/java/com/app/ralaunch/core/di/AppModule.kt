package com.app.ralaunch.core.di

import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import com.app.ralaunch.core.di.contract.GameListStorage
import com.app.ralaunch.core.di.contract.GameRepositoryV2
import com.app.ralaunch.core.di.contract.IThemeManager
import com.app.ralaunch.core.di.contract.IVibrationManager
import com.app.ralaunch.core.di.contract.SettingsRepositoryV2
import com.app.ralaunch.core.di.service.AndroidGameListStorage
import com.app.ralaunch.core.di.service.GameDeletionManager
import com.app.ralaunch.core.di.service.GameRepositoryImpl
import com.app.ralaunch.core.di.service.PermissionManager
import com.app.ralaunch.core.di.service.SettingsRepositoryImpl
import com.app.ralaunch.core.di.service.StoragePathsProvider
import com.app.ralaunch.core.di.service.ThemeManager
import com.app.ralaunch.core.di.service.VibrationManager
import com.app.ralaunch.feature.announcement.AnnouncementRepositoryService
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import com.app.ralaunch.feature.main.update.LauncherUpdateChecker
import com.app.ralaunch.feature.patch.data.PatchManager
import com.app.ralaunch.feature.script.JavaScriptExecutor
import com.app.ralaunch.feature.settings.AppInfo
import com.app.ralaunch.feature.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * App 模块 Koin 依赖配置
 *
 * 包含共享、Android 平台与应用层依赖
 */
val appModule = module {

    // ==================== 数据存储 ====================

    single<StoragePathsProvider> {
        StoragePathsProvider(androidContext())
    }

    single<GameListStorage> {
        AndroidGameListStorage(get())
    }

    // ==================== Repositories ====================

    single<GameRepositoryV2> {
        GameRepositoryImpl(gameListStorage = get())
    }

    single<SettingsRepositoryV2> {
        SettingsRepositoryImpl(storagePathsProvider = get())
    }

    // ==================== App Info ====================

    single {
        try {
            val context = androidContext()
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            AppInfo(
                versionName = packageInfo.versionName ?: versionCode.toString(),
                versionCode = versionCode
            )
        } catch (e: Exception) {
            AppInfo()
        }
    }

    // ==================== Managers ====================

    single {
        VibrationManager(androidContext())
    } bind IVibrationManager::class

    single {
        ControlPackManager(androidContext())
    }

    single {
        AnnouncementRepositoryService(androidContext())
    }

    single {
        LauncherUpdateChecker(androidContext())
    }

    single {
        JavaScriptExecutor()
    }

    single<PatchManager?> {
        try {
            PatchManager(null, false)
        } catch (e: Exception) {
            null
        }
    }

    // ==================== ViewModels ====================

    viewModel {
        SettingsViewModel(
            settingsRepository = get<SettingsRepositoryV2>(),
            appInfo = getOrNull<AppInfo>() ?: AppInfo()
        )
    }

    // ==================== UI Managers (参数化工厂) ====================

    factory<ThemeManager> { (activity: AppCompatActivity) ->
        ThemeManager(activity)
    }

    factory<IThemeManager> { (activity: AppCompatActivity) ->
        ThemeManager(activity)
    }

    factory<PermissionManager> { (activity: ComponentActivity) ->
        PermissionManager(activity)
    }

    factory<GameDeletionManager> { (activity: AppCompatActivity) ->
        GameDeletionManager(activity)
    }
}

/**
 * 获取所有 App 模块
 */
fun getAppModules() = listOf(appModule)
