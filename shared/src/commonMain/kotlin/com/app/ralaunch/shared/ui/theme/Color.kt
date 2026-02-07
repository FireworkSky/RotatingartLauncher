package com.app.ralaunch.shared.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用颜色定义 - 跨平台共享
 *
 * 基于 Material 3 配色方案
 */
object AppColors {

    // ==================== 主色调 - 紫色系 ====================
    val Purple10 = Color(0xFF21005D)
    val Purple20 = Color(0xFF381E72)
    val Purple30 = Color(0xFF4F378B)
    val Purple40 = Color(0xFF6750A4)
    val Purple50 = Color(0xFF7F67BE)
    val Purple60 = Color(0xFF9A82DB)
    val Purple70 = Color(0xFFB69DF8)
    val Purple80 = Color(0xFFD0BCFF)
    val Purple90 = Color(0xFFEADDFF)
    val Purple95 = Color(0xFFF6EDFF)
    val Purple99 = Color(0xFFFFFBFE)

    // ==================== 次要色调 ====================
    val PurpleGrey10 = Color(0xFF1D1A22)
    val PurpleGrey20 = Color(0xFF332D41)
    val PurpleGrey30 = Color(0xFF4A4458)
    val PurpleGrey40 = Color(0xFF625B71)
    val PurpleGrey50 = Color(0xFF7A7289)
    val PurpleGrey60 = Color(0xFF958DA5)
    val PurpleGrey70 = Color(0xFFB0A7C0)
    val PurpleGrey80 = Color(0xFFCCC2DC)
    val PurpleGrey90 = Color(0xFFE8DEF8)

    // ==================== 强调色 ====================
    val Pink10 = Color(0xFF31111D)
    val Pink20 = Color(0xFF4A2532)
    val Pink30 = Color(0xFF633B48)
    val Pink40 = Color(0xFF7D5260)
    val Pink50 = Color(0xFF986977)
    val Pink60 = Color(0xFFB58392)
    val Pink70 = Color(0xFFD29DAC)
    val Pink80 = Color(0xFFEFB8C8)
    val Pink90 = Color(0xFFFFD8E4)

    // ==================== 中性色 ====================
    val Neutral10 = Color(0xFF1C1B1F)
    val Neutral20 = Color(0xFF313033)
    val Neutral30 = Color(0xFF484649)
    val Neutral40 = Color(0xFF605D62)
    val Neutral50 = Color(0xFF787579)
    val Neutral60 = Color(0xFF939094)
    val Neutral70 = Color(0xFFAEAAAE)
    val Neutral80 = Color(0xFFCAC5CA)
    val Neutral90 = Color(0xFFE6E1E5)
    val Neutral95 = Color(0xFFF4EFF4)
    val Neutral99 = Color(0xFFFFFBFE)

    // ==================== 功能色 ====================
    val Error10 = Color(0xFF410002)
    val Error20 = Color(0xFF690005)
    val Error30 = Color(0xFF93000A)
    val Error40 = Color(0xFFBA1A1A)
    val Error80 = Color(0xFFFFB4AB)
    val Error90 = Color(0xFFFFDAD6)

    val Success10 = Color(0xFF002106)
    val Success20 = Color(0xFF00390B)
    val Success30 = Color(0xFF005313)
    val Success40 = Color(0xFF006E1C)
    val Success80 = Color(0xFF7DDC7D)
    val Success90 = Color(0xFF98F898)

    val Warning10 = Color(0xFF2B1700)
    val Warning20 = Color(0xFF462A00)
    val Warning30 = Color(0xFF633F00)
    val Warning40 = Color(0xFF825500)
    val Warning80 = Color(0xFFFFB951)
    val Warning90 = Color(0xFFFFDDB0)

    // ==================== 游戏特定颜色 ====================
    val GameCardBackground = Color(0xFF2A2A3D)
    val GameCardBackgroundLight = Color(0xFFF5F5F8)
    val GameCardBorder = Color(0xFF3D3D54)
    val GameCardBorderLight = Color(0xFFE0E0E8)

    val ControlButtonDefault = Color(0x80FFFFFF)
    val ControlButtonPressed = Color(0xB0FFFFFF)

    // ==================== 发光 & 毛玻璃颜色 ====================

    /** 主色发光 (选中态) */
    val GlowPrimary = Color(0x806750A4)
    val GlowPrimaryIntense = Color(0xB36750A4)

    /** 次要发光 */
    val GlowSecondary = Color(0x6062CDCF)

    /** 成功发光 */
    val GlowSuccess = Color(0x604CAF50)

    /** 毛玻璃暗色叠层 */
    val GlassDark = Color(0x40000000)
    val GlassDarkMedium = Color(0x66000000)
    val GlassDarkHeavy = Color(0x99000000)

    /** 毛玻璃亮色叠层 */
    val GlassLight = Color(0x30FFFFFF)
    val GlassLightMedium = Color(0x50FFFFFF)
    val GlassLightHeavy = Color(0x80FFFFFF)

    /** 毛玻璃边框 */
    val GlassBorderDark = Color(0x30FFFFFF)
    val GlassBorderLight = Color(0x40000000)

    // ==================== 透明度工具 ====================
    fun Color.alpha(alpha: Float): Color = this.copy(alpha = alpha)
}

/**
 * 扩展函数：从 ARGB Int 创建 Color
 */
fun Int.toComposeColor(): Color = Color(this)
