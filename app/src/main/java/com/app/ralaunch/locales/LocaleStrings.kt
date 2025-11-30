package com.app.ralaunch.locales

// 字符串资源接口
interface LocaleStrings {
    // 通用
    val confirm : String
    val cancel : String

    // 应用基本信息
    val appName: String
    val appVersion: String
    val appDescription: String
    val appAuthors: String

    //导航栏
    val navrailGame : String
    val navrailFile : String
    val navrailDownload : String
    val navrailSettings : String

    // 设置页面
    val settingsLanguage : String
    val settingsFollowSystem : String
    val settingsTheme : String
    val settingsDark : String
    val settingsLight : String
    val settingsDynamicColor : String
    val settingsThemeColor : String
    val settingsThemeColorDescription : String
    val settingsPalette : String
    val settingsCustom : String
    val settingsRenderer : String
}