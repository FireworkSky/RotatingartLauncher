package com.app.ralaunch.core.di

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import com.app.ralaunch.core.common.GameLaunchManager
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.di.contract.ISettingsRepositoryServiceV2
import com.app.ralaunch.core.di.contract.IThemeManagerServiceV1
import com.app.ralaunch.core.di.service.GameRepositoryServiceV3
import com.app.ralaunch.core.di.service.PermissionManagerServiceV1
import com.app.ralaunch.core.di.service.RuntimeManagerServiceV2
import com.app.ralaunch.core.di.service.SettingsRepositoryServiceV2
import com.app.ralaunch.core.di.service.StoragePathsProviderServiceV1
import com.app.ralaunch.core.di.service.ThemeManagerServiceV1
import com.app.ralaunch.core.di.service.VibrationManagerServiceV1
import com.app.ralaunch.core.logging.LogFilePolicy
import com.app.ralaunch.core.logging.LogLevel
import com.app.ralaunch.core.logging.contract.Logger
import com.app.ralaunch.core.logging.service.AndroidFileLogger
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.feature.announcement.AnnouncementRepositoryService
import com.app.ralaunch.feature.announcement.vm.AnnouncementViewModel
import com.app.ralaunch.feature.controls.editors.vm.ControlEditorViewModel
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import com.app.ralaunch.feature.controls.packs.ControlPackRepositoryService
import com.app.ralaunch.feature.controls.packs.vm.ControlPackViewModel
import com.app.ralaunch.feature.controls.vm.ControlLayoutViewModel
import com.app.ralaunch.feature.filebrowser.vm.FileBrowserViewModel
import com.app.ralaunch.feature.gog.data.GogDownloader
import com.app.ralaunch.feature.gog.data.api.GogAuthClient
import com.app.ralaunch.feature.gog.data.api.GogWebsiteApi
import com.app.ralaunch.feature.gog.domain.ModLoaderConfigManager
import com.app.ralaunch.feature.gog.vm.GogViewModel
import com.app.ralaunch.feature.init.vm.InitializationViewModel
import com.app.ralaunch.feature.installer.vm.InstallerViewModel
import com.app.ralaunch.feature.main.update.LauncherUpdateChecker
import com.app.ralaunch.feature.main.vm.MainViewModel
import com.app.ralaunch.feature.main.vm.GameInfoEditViewModel
import com.app.ralaunch.feature.patch.data.PatchManager
import com.app.ralaunch.feature.patch.vm.PatchManagementViewModel
import com.app.ralaunch.feature.script.JavaScriptExecutor
import com.app.ralaunch.feature.settings.vm.AppInfo
import com.app.ralaunch.feature.settings.vm.SettingsViewModel
import com.app.ralaunch.feature.sponsor.SponsorRepositoryService
import com.app.ralaunch.feature.sponsor.vm.SponsorsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

private const val LOGCAT_FILE_LOGGER = "logcatFileLogger"

/**
 * App 模块 Koin 依赖配置
 *
 * 包含共享、Android 平台与应用层依赖
 */
val appModule = module {

    // ==================== 数据存储 ====================

    single(named(LOGCAT_FILE_LOGGER)) {
        AndroidFileLogger(
            fileNameProvider = { LogFilePolicy.logcatLogFileName() },
            emitToAndroidLog = false
        )
    }

    single {
        val settingsRepository = get<ISettingsRepositoryServiceV2>()
        AndroidFileLogger(
            isFileLoggingEnabled = { settingsRepository.Settings.logSystemEnabled },
            logLevel = {
                if (settingsRepository.Settings.verboseLogging) LogLevel.VERBOSE else LogLevel.INFO
            },
            logcatFileLogger = get(named(LOGCAT_FILE_LOGGER))
        )
    }

    single<Logger> {
        get<AndroidFileLogger>()
    }

    single<StoragePathsProviderServiceV1> {
        StoragePathsProviderServiceV1(androidContext())
    }

    // ==================== Repositories ====================

    single<IGameRepositoryServiceV3> {
        GameRepositoryServiceV3(pathsProvider = get())
    }

    single<ISettingsRepositoryServiceV2> {
        SettingsRepositoryServiceV2(storagePathsProvider = get())
    }

    single<IRuntimeManagerServiceV2> {
        RuntimeManagerServiceV2(
            settingsRepository = get(),
            pathsProvider = get()
        )
    }

    single<SharedPreferences> {
        androidContext().getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ==================== App Info ====================

    single {
        try {
            val context = androidContext()
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = packageInfo.longVersionCode
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
        VibrationManagerServiceV1(androidContext())
    }

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
        ControlPackRepositoryService(androidContext())
    }

    single {
        GameLaunchManager(androidContext())
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

    single {
        GogAuthClient(androidContext())
    }

    single {
        GogWebsiteApi(get())
    }

    single {
        GogDownloader(get())
    }

    single {
        ModLoaderConfigManager(androidContext())
    }

    single {
        SponsorRepositoryService(androidContext())
    }

    // ==================== ViewModels ====================

    viewModel {
        SettingsViewModel(
            settingsRepository = get<ISettingsRepositoryServiceV2>(),
            runtimeManager = get(),
            fileLogger = get(),
            appInfo = getOrNull<AppInfo>() ?: AppInfo()
        )
    }

    viewModel {
        MainViewModel(
            appContext = androidContext(),
            gameRepository = get(),
            gameLaunchManager = get(),
            settingsRepository = get(),
            announcementRepositoryService = get(),
            launcherUpdateChecker = get()
        )
    }

    viewModel {
        GameInfoEditViewModel(
            runtimeManager = get()
        )
    }

    viewModel {
        InstallerViewModel(
            appContext = androidContext(),
            gameRepository = get()
        )
    }

    viewModel {
        AnnouncementViewModel(
            appContext = androidContext(),
            repositoryService = get()
        )
    }

    viewModel {
        ControlPackViewModel(
            packManager = get(),
            repoService = get(),
            context = androidContext()
        )
    }

    viewModel {
        ControlEditorViewModel(
            appContext = androidContext(),
            packManager = get()
        )
    }

    viewModel {
        FileBrowserViewModel()
    }

    viewModel {
        GogViewModel(
            appContext = androidContext(),
            authClient = get(),
            websiteApi = get(),
            downloader = get(),
            modLoaderConfigManager = get()
        )
    }

    viewModel {
        InitializationViewModel(
            appContext = androidContext(),
            prefs = get(),
            runtimeManager = get()
        )
    }

    viewModel {
        ControlLayoutViewModel(
            appContext = androidContext(),
            packManager = get()
        )
    }

    viewModel {
        PatchManagementViewModel(
            appContext = androidContext(),
            gameRepository = get(),
            patchManager = getOrNull()
        )
    }

    viewModel {
        SponsorsViewModel(
            appContext = androidContext(),
            sponsorService = get()
        )
    }

    // ==================== UI Managers (参数化工厂) ====================

    factory<ThemeManagerServiceV1> { (activity: AppCompatActivity) ->
        ThemeManagerServiceV1(activity)
    }

    factory<IThemeManagerServiceV1> { (activity: AppCompatActivity) ->
        ThemeManagerServiceV1(activity)
    }

    factory<PermissionManagerServiceV1> { (activity: ComponentActivity) ->
        PermissionManagerServiceV1(activity)
    }
}

/**
 * 获取所有 App 模块
 */
fun getAppModules() = listOf(appModule)
