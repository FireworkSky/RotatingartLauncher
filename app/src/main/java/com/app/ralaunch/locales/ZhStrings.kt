package com.app.ralaunch.locales

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

object ZhStrings : LocaleStrings {
    override val confirm: String = "确认"
    override val cancel: String = "取消"

    override val appName = "Rotating art Launcher"
    override val appDescription = "游戏启动器"
    override val appAuthors = "作者:"

    override val navrailGame: String = "游戏"
    override val navrailFile: String = "文件管理"
    override val navrailDownload: String = "下载"
    override val navrailSettings: String = "设置"
    override val navrailSettingsGeneral: String = "常规"
    override val navrailSettingsControl: String = "控制"
    override val navrailSettingsAdvanced: String = "高级"
    override val navrailSettingsAbout: String = "关于"

    override val settingsLanguage: String = "语言"
    override val settingsFollowSystem: String = "跟随系统"
    override val settingsTheme: String = "主题"
    override val settingsDark: String = "暗色"
    override val settingsLight: String = "亮色"
    override val settingsDynamicColor: String = "动态取色"
    override val settingsThemeColor: String = "主题颜色"
    override val settingsThemeColorDescription: String = "选择应用的主题颜色"
    override val settingsPalette: String = "调色板"
    override val settingsCustom: String = "自定义"
    override val settingsRenderer: String = "渲染器"
    override val settingsAuto: String = "自动"
    override val settingsVirtualJoystickOpacity: String = "虚拟手柄不透明度"
    override val settingsVibrationEnabled: String = "震动反馈"
    override val settingsServerGc: String = "服务器垃圾回收"
    override val settingsServerGcDescription: String = "多核优化，更高吞吐量，更高内存占用"
    override val settingsConcurrentGc: String = "并发垃圾回收"
    override val settingsConcurrentGcDescription: String = "减少暂停时间"
    override val settingsTieredCompilation: String = "分层编译"
    override val settingsTieredCompilationDescription: String = "加快启动速度"
    override val settingsCoreClrDebugLog: String = "CoreCLR调试日志"
    override val settingsThreadAffinity: String = "线程亲和"
    override val settingsThreadAffinityDescription: String = "将主线程绑定到大核心以提升性能"

    override val fileManagerCreate: String = "创建"
    override val fileManagerCreateFile: String = "创建文件"
    override val fileManagerCreateFolder: String = "创建文件夹"
    override val fileManagerInputDialogTitle: String = "创建新项"
    override val fileManagerInputFileNameLabel: String = "名称"
    override val fileManagerInputPlaceholder: String = "输入文件或文件夹名称"
    override val fileManagerCurrentLocation: String = "位置:"
    override val fileManagerPathDialogLabel: String = "输入路径"
    override val fileManagerNavigateUp: String = "返回上级"
    override val fileManagerOpenPath: String = "路径导航"
    override val fileManagerFolder: String = "文件夹"
    override val fileManagerFile: String = "文件"
    override val fileManagerMoreActions: String = "更多操作"
    override val fileManagerOpenAction: String = "打开"
    override val fileManagerOperationDialogTitle: String = "文件操作"
    override val fileManagerOperationsTitle: String = "可用操作"
    override val fileManagerOpenButton: String = "打开"
    override val fileManagerCopyButton: String = "复制"
    override val fileManagerMoveButton: String = "移动"
    override val fileManagerRenameButton: String = "重命名"
    override val fileManagerDeleteButton: String = "删除"
    override val fileManagerOperationButtonOpenDesc: String = "打开"
    override val fileManagerOperationButtonCopyDesc: String = "复制"
    override val fileManagerOperationButtonMoveDesc: String = "移动"
    override val fileManagerOperationButtonRenameDesc: String = "重命名"
    override val fileManagerOperationButtonDeleteDesc: String = "删除"
    override val fileManagerDeleteConfirmTitle: String = "确认删除"
    override val fileManagerDeleteConfirmMessage: String = "确定要删除 \"%s\" 吗？此操作无法撤销。"
    override val fileManagerDeleteAction: String = "删除"
    override val fileManagerRenameDialogTitle: String = "重命名"
    override val fileManagerRenameInputLabel: String = "新名称"
    override val fileManagerRenameAction: String = "确定"
    override val fileManagerOperationConfirmTitleTemplate: String = "确认%s"
    override val fileManagerOperationConfirmMessageTemplate: String =
        "确定要%s \"%s\" 到 \"%s\" 吗？"
    override val fileManagerOperationCopy: String = "复制"
    override val fileManagerOperationMove: String = "移动"
    override val fileManagerOperationCopyAction: String = "复制"
    override val fileManagerOperationMoveAction: String = "移动"
    override val fileManagerSnackbarCreatedFile: String = "文件创建成功"
    override val fileManagerSnackbarFileExistsOrFailed: String = "文件已存在或创建失败"
    override val fileManagerSnackbarCreatedFolder: String = "文件夹创建成功"
    override val fileManagerSnackbarFolderExistsOrFailed: String = "文件夹已存在或创建失败"
    override val fileManagerSnackbarCreateFailedTemplate: String = "创建失败: %s"
    override val fileManagerSnackbarCopiedTemplate: String = "已选择复制: %s"
    override val fileManagerSnackbarMovedTemplate: String = "已选择移动: %s"
    override val fileManagerSnackbarDeleted: String = "删除成功"
    override val fileManagerSnackbarDeleteFailed: String = "删除失败"
    override val fileManagerSnackbarRenamed: String = "重命名成功"
    override val fileManagerSnackbarRenameFailed: String = "重命名失败"
    override val fileManagerSnackbarOperationSuccess: String = "操作成功"
    override val fileManagerSnackbarOperationFailedTemplate: String = "操作失败: %s"
    override val fileManagerSnackbarOpeningTemplate: String = "正在打开: %s"
    override val fileManagerSnackbarNoAppToOpenTemplate: String = "没有应用可以打开此文件类型: %s"
    override val fileManagerSnackbarOpenFailedTemplate: String = "打开文件失败: %s"
    override val fileManagerSnackbarFileNotFoundTemplate: String = "文件不存在: %s"

    override val aboutIntroduction: String = "简介"
    override val aboutIntroductionText: String =
        "Rotating Art Launcher 是一个专为 Android 平台设计的游戏启动器，能够运行使用 FNA/XNA 框架开发的 .NET 游戏。本项目通过集成 .NET Core Runtime 和 SDL2，实现了在 Android 设备上原生运行 Windows PC 游戏的能力。"
    override val aboutFeature: String = "特性"
    override val aboutFeatureDotNetRuntime: String = ".NET 8 运行时"
    override val aboutFeatureFNAXNA: String = "FNA/XNA 兼容"
    override val aboutFeatureMaterialYou: String = "Material You 动态主题"
    override val aboutFeatureFullscreen: String = "全屏 & 刘海屏适配"
    override val aboutSupportedGamesTitle: String = "支持的游戏"
    override val aboutSupportedGameOtherFna: String = "其他 FNA 游戏"
    override val aboutSystemRequirementsTitle: String = "系统要求"
    override val aboutSystemRequirementStorage: String = "500MB+ 存储"
    override val aboutTechStackTitle: String = "技术栈"
    override val aboutKnownIssuesTitle: String = "已知问题"
    override val aboutKnownIssueContext: AnnotatedString = buildAnnotatedString {
        append("• 某些游戏可能需要额外的库文件\n")
        append("• 性能在低端设备上可能受限\n")
        append("• 部分游戏模组可能不兼容")
    }
    override val aboutContributeTitle: String = "贡献"
    override val aboutContributeContext: AnnotatedString = buildAnnotatedString {
        append("欢迎提交 Issue 和 Pull Request！\n")
        append("1. Fork 本仓库\n")
        append("2. 创建功能分支\n")
        append("3. 提交更改\n")
        append("4. 推送到分支\n")
        append("5. 开启 Pull Request")
    }
    override val aboutChangelogTitle: String = "更新日志 - %s (%s)"
    override val aboutChangelogContext: AnnotatedString = buildAnnotatedString {
        append("• ✨ 初始版本发布\n")
        append("• 🎮 支持 tModLoader 和 FNA 游戏\n")
        append("• 🖥️ 全屏和刘海屏支持\n")
        append("• 📦 自动资源解压\n")
        append("• 🌐 中英文双语支持")
    }
    override val aboutLicenseTitle: String = "许可证"
    override val aboutLicenseContext: AnnotatedString = buildAnnotatedString {
        append("本项目基于 GNU Lesser General Public License v3.0 (LGPLv3) 开源。\n")
        append("\n第三方库许可:\n")
        append("• SDL2 - Zlib License\n")
        append("• GL4ES - MIT License\n")
        append("• .NET Runtime - MIT License\n")
        append("• FNA - Ms-PL License")
    }
    override val aboutCreditsThanksTitle: String = "致谢"
    override val aboutCreditsThanksContext: AnnotatedString = buildAnnotatedString {
        append("感谢所有开源项目和贡献者：\n")
        append("• SDL Project\n")
        append("• GL4ES\n")
        append("• .NET Runtime\n")
        append("• FNA\n")
        append("• 以及所有贡献者和用户")
    }
    override val aboutAuthorsTitle: String = "作者"
    override val aboutContactTitle: String = "联系方式"
    override val aboutContactInstructions: AnnotatedString = buildAnnotatedString {
        appendLine("如有问题或建议，请：")
        appendLine("• 提交 Issue")
        appendLine("• 访问 Discussions")
    }
    override val aboutContactIssueButton: String = "提交 Issue"

    override val setupOneStepTitle: String = "一步完成首次配置"
    override val setupOneStepDescription: AnnotatedString = buildAnnotatedString {
        appendLine("• 同意法律条款后即可开始安装")
        appendLine("• 内置 .NET 运行时，免手动下载")
        appendLine("• 实时显示安装进度和状态")
    }
    override val setupOptimizedForLandscape: String = "针对横屏设备优化的初始化体验"
    override val setupLegalTitle: String = "法律声明"
    override val setupLegalContent: AnnotatedString = buildAnnotatedString {
        appendLine("本启动器是第三方工具，需要原始游戏文件才能运行。您必须拥有合法授权的游戏副本才能使用本软件。\n")
        appendLine("本启动器开发者与原游戏创作者无关联，对因使用本软件而产生的任何法律后果不承担责任。\n")
        appendLine("使用本软件即表示您已阅读、理解并同意遵守上述条款。")
    }
    override val setupComponentsListTitle: String = "组件列表"
    override val setupInstallButtonStart: String = "开始安装"
    override val setupInstallButtonInstalling: String = "安装中..."
    override val setupInstallButtonReinstall: String = "重新安装"
    override val setupExitApp: String = "退出应用"
    override val setupAcceptAndContinue: String = "接受并继续"
    override val setupExtractionTitle: String = "安装必要组件"
    override val setupExtractionDescription: String = "启动器需要安装这些组件才能确保游戏正常运行。这些组件已包含在应用中，需要解压后才能使用。"
    override val setupOverallProgressPreparing: String = "准备安装..."
    override val setupOverallProgressInstalling: String = "安装中..."
    override val setupOverallProgressVerifying: String = "验证文件..."
    override val setupOverallProgressCompleted: String = "安装完成"
    override val setupCheckAssets: String = "准备资源文件..."
    override val setupCreateTargetDir: String = "创建目标目录..."
    override val setupStartExtracting: String = "开始解压 %s..."
    override val setupExtractingStructure: String = "解压文件结构..."
    override val setupExtracting: String = "解压中"
    override val setupExtractionFailedPrefix: String = "解压失败：%s"
    override val setupInvalidFilePathPrefix: String = "无效的文件路径: %s"
    override val setupAssetFileMissingPrefix: String = "资源文件 %s 不存在"
    override val setupExtractingSuccessful: String = "解压成功"
}