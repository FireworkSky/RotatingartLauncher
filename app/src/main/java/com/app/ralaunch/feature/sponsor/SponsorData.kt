package com.app.ralaunch.feature.sponsor

import kotlinx.serialization.Serializable

/**
 * 赞助者信息
 */
@Serializable
data class Sponsor(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val bio: String = "",
    val tier: String,      // 赞助级别ID
    val joinDate: String = "",
    val website: String = ""
)

/**
 * 赞助级别定义
 */
@Serializable
data class SponsorTier(
    val id: String,
    val name: String,
    val nameEn: String,
    val color: String,           // 主题颜色 (hex)
    val particleType: String,    // 粒子效果类型: none, sparkle, stars, firework, galaxy
    val order: Int,              // 排序权重，越高越靠前
    val minAmount: Int = 0       // 最低赞助金额
)

/**
 * 赞助商仓库数据
 */
@Serializable
data class SponsorRepository(
    val version: Int,
    val name: String,
    val description: String,
    val lastUpdated: String,
    val tiers: List<SponsorTier>,
    val sponsors: List<Sponsor>
)

/**
 * 粒子效果类型枚举
 */
enum class ParticleType(val id: String) {
    NONE("none"),              // 无效果 - 爱心维护员
    SPARKLE("sparkle"),        // 闪光效果 - 星光先锋
    STARS("stars"),            // 星空效果 - 极致合伙人
    FIREWORK("firework"),      // 烟花效果 - 星空探索家
    GALAXY("galaxy");          // 银河效果 - 银河守护者

    companion object {
        fun fromString(id: String): ParticleType {
            return entries.find { it.id == id } ?: NONE
        }
    }
}

