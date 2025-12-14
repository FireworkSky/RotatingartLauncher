package com.app.ralaunch.locales

import androidx.compose.ui.text.AnnotatedString

// 字符串资源接口
interface LocaleStrings {
    // 通用
    val confirm: String
    val cancel: String

    // 应用基本信息
    val appName: String
    val appVersion: String
        get() = "0.1.0"
    val appRelease: String
        get() = "2025-12-13"
    val appDescription: String
    val appAuthors: String

    //导航
    val navrailGame: String
    val navrailFile: String
    val navrailDownload: String
    val navrailSettings: String
    val navrailSettingsGeneral: String
    val navrailSettingsControl: String
    val navrailSettingsAdvanced: String
    val navrailSettingsAbout: String

    // 设置页面
    val settingsLanguage: String
    val settingsFollowSystem: String
    val settingsTheme: String
    val settingsDark: String
    val settingsLight: String
    val settingsDynamicColor: String
    val settingsThemeColor: String
    val settingsThemeColorDescription: String
    val settingsPalette: String
    val settingsCustom: String
    val settingsRenderer: String
    val settingsAuto: String
    val settingsVirtualJoystickOpacity: String
    val settingsVibrationEnabled: String
    val settingsServerGc: String
    val settingsServerGcDescription: String
    val settingsConcurrentGc: String
    val settingsConcurrentGcDescription: String
    val settingsTieredCompilation: String
    val settingsTieredCompilationDescription: String
    val settingsCoreClrDebugLog: String
    val settingsThreadAffinity: String
    val settingsThreadAffinityDescription: String

    // 文件管理
    val fileManagerCreate: String
    val fileManagerCreateFile: String // "创建文件" 按钮文本
    val fileManagerCreateFolder: String // "创建文件夹" 按钮文本
    val fileManagerInputDialogTitle: String // "创建新项" 对话框标题
    val fileManagerInputFileNameLabel: String // 输入框标签 "名称"
    val fileManagerInputPlaceholder: String // 输入框占位符 "输入文件或文件夹名称"
    val fileManagerCurrentLocation: String // "位置:" 提示文本
    val fileManagerPathDialogLabel: String // "输入路径" 输入框标签
    val fileManagerNavigateUp: String // "返回上级" 图标内容描述
    val fileManagerOpenPath: String // "路径导航" 图标内容描述
    val fileManagerFolder: String // "文件夹" 图标内容描述 / 类型标识
    val fileManagerFile: String // "文件" 图标内容描述 / 类型标识
    val fileManagerMoreActions: String // "更多操作" 图标内容描述
    val fileManagerOpenAction: String // "打开" 操作菜单项
    val fileManagerOperationDialogTitle: String // "文件操作" 对话框标题
    val fileManagerOperationsTitle: String // "可用操作" 区域标题 (OperationGridSection)
    val fileManagerOpenButton: String // "打开" 操作按钮
    val fileManagerCopyButton: String // "复制" 操作按钮
    val fileManagerMoveButton: String // "移动" 操作按钮
    val fileManagerRenameButton: String // "重命名" 操作按钮
    val fileManagerDeleteButton: String // "删除" 操作按钮
    val fileManagerOperationButtonOpenDesc: String // "打开" 按钮内容描述
    val fileManagerOperationButtonCopyDesc: String // "复制" 按钮内容描述
    val fileManagerOperationButtonMoveDesc: String // "移动" 按钮内容描述
    val fileManagerOperationButtonRenameDesc: String // "重命名" 按钮内容描述
    val fileManagerOperationButtonDeleteDesc: String // "删除" 按钮内容描述
    val fileManagerDeleteConfirmTitle: String // "确认删除" 对话框标题
    val fileManagerDeleteConfirmMessage: String // "确认删除" 对话框消息模板 ("确定要删除 \"%s\" 吗？...")
    val fileManagerDeleteAction: String // "删除" 确认按钮文本
    val fileManagerRenameDialogTitle: String // "重命名" 对话框标题
    val fileManagerRenameInputLabel: String // "重命名" 输入框标签 "新名称"
    val fileManagerRenameAction: String // "确定" 重命名按钮文本
    val fileManagerOperationConfirmTitleTemplate: String // "确认%s" 对话框标题模板
    val fileManagerOperationConfirmMessageTemplate: String // "确认%s" 对话框消息模板 ("确定要%s \"%s\" 到 \"%s\" 吗？")
    val fileManagerOperationCopy: String // "复制" 动作名称 (用于模板)
    val fileManagerOperationMove: String // "移动" 动作名称 (用于模板)
    val fileManagerOperationCopyAction: String // "复制" 确认按钮文本
    val fileManagerOperationMoveAction: String // "移动" 确认按钮文本
    val fileManagerSnackbarCreatedFile: String // "文件创建成功" 提示
    val fileManagerSnackbarFileExistsOrFailed: String // "文件已存在或创建失败" 提示
    val fileManagerSnackbarCreatedFolder: String // "文件夹创建成功" 提示
    val fileManagerSnackbarFolderExistsOrFailed: String // "文件夹已存在或创建失败" 提示
    val fileManagerSnackbarCreateFailedTemplate: String // "创建失败: %s" 提示模板
    val fileManagerSnackbarCopiedTemplate: String // "已选择复制: %s" 提示模板
    val fileManagerSnackbarMovedTemplate: String // "已选择移动: %s" 提示模板
    val fileManagerSnackbarDeleted: String // "删除成功" 提示
    val fileManagerSnackbarDeleteFailed: String // "删除失败" 提示
    val fileManagerSnackbarRenamed: String // "重命名成功" 提示
    val fileManagerSnackbarRenameFailed: String // "重命名失败" 提示
    val fileManagerSnackbarOperationSuccess: String // "操作成功" 提示
    val fileManagerSnackbarOperationFailedTemplate: String // "操作失败: %s" 提示模板
    val fileManagerSnackbarOpeningTemplate: String // "正在打开: %s" 提示模板
    val fileManagerSnackbarNoAppToOpenTemplate: String // "没有应用可以打开此文件类型: %s" 提示模板
    val fileManagerSnackbarOpenFailedTemplate: String // "打开文件失败: %s" 提示模板
    val fileManagerSnackbarFileNotFoundTemplate: String // "文件不存在: %s" 提示模板

    // 关于页面
    val aboutIntroduction : String
    val aboutIntroductionText : String
    val aboutFeature : String
    val aboutFeatureDotNetRuntime: String
    val aboutFeatureFNAXNA: String
    val aboutFeatureMaterialYou: String
    val aboutFeatureFullscreen: String
    val aboutSupportedGamesTitle: String
    val aboutSupportedGameOtherFna: String
    val aboutSystemRequirementsTitle: String
    val aboutSystemRequirementStorage: String
    val aboutTechStackTitle: String
    val aboutKnownIssuesTitle: String
    val aboutKnownIssueContext: AnnotatedString
    val aboutContributeTitle: String
    val aboutContributeContext: AnnotatedString
    val aboutChangelogTitle: String // 带版本号占位符 "更新日志 - %s (%s)"
    val aboutChangelogContext: AnnotatedString
    val aboutLicenseTitle: String
    val aboutLicenseContext: AnnotatedString
    val aboutCreditsThanksTitle: String
    val aboutCreditsThanksContext: AnnotatedString
    val aboutAuthorsTitle: String
    val aboutContactTitle: String
    val aboutContactInstructions: AnnotatedString
    val aboutContactIssueButton: String

    // 初始化页面
    val setupOneStepTitle: String
    val setupOneStepDescription: AnnotatedString
    val setupOptimizedForLandscape: String
    val setupLegalTitle: String
    val setupLegalContent: AnnotatedString
    val setupComponentsListTitle: String
    val setupInstallButtonStart: String
    val setupInstallButtonInstalling: String
    val setupInstallButtonReinstall: String
    val setupExitApp: String
    val setupAcceptAndContinue: String
    val setupExtractionTitle: String
    val setupExtractionDescription: String
    val setupOverallProgressPreparing: String
    val setupOverallProgressInstalling: String
    val setupOverallProgressVerifying: String
    val setupOverallProgressCompleted: String
    val setupCheckAssets: String
    val setupCreateTargetDir: String
    val setupStartExtracting: String // "开始解压 ${component.name}..."
    val setupExtractingStructure: String
    val setupExtracting: String
    val setupExtractionFailedPrefix: String // "解压失败: "
    val setupInvalidFilePathPrefix: String // "无效的文件路径: "
    val setupAssetFileMissingPrefix: String // "资源文件 ${component.fileName} 不存在"
    val setupExtractingSuccessful : String
}