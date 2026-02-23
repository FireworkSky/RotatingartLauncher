package com.app.ralaunch.shared.core.di

import com.app.ralaunch.shared.core.data.repository.GameRepositoryImpl
import com.app.ralaunch.shared.core.data.repository.SettingsRepositoryImpl
import com.app.ralaunch.shared.core.contract.repository.ControlLayoutRepositoryV2
import com.app.ralaunch.shared.core.contract.repository.GameRepositoryV2
import com.app.ralaunch.shared.core.contract.repository.SettingsRepositoryV2
import com.app.ralaunch.shared.feature.controls.ControlLayoutViewModel
import com.app.ralaunch.shared.feature.settings.AppInfo
import com.app.ralaunch.shared.feature.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Shared 模块 Koin 依赖配置
 *
 * 包含跨平台共享的依赖
 */
val sharedModule = module {

    // ==================== Repositories ====================

    single<GameRepositoryV2> {
        GameRepositoryImpl(gameListStorage = get())
    }

    single<SettingsRepositoryV2> {
        SettingsRepositoryImpl(storagePathsProvider = get())
    }

    // ==================== ViewModels ====================

    viewModel {
        SettingsViewModel(
            settingsRepository = get<SettingsRepositoryV2>(),
            appInfo = getOrNull<AppInfo>() ?: AppInfo()
        )
    }

    viewModel { (controlLayoutRepository: ControlLayoutRepositoryV2) ->
        ControlLayoutViewModel(
            repository = controlLayoutRepository
        )
    }
}

/**
 * 获取所有共享模块
 */
fun getSharedModules() = listOf(sharedModule)
