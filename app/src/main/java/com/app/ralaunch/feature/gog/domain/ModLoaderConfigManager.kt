package com.app.ralaunch.feature.gog.domain

import android.content.Context
import com.app.ralaunch.R
import com.app.ralaunch.core.logging.AppLog
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * GOG ModLoader 配置管理器
 */
class ModLoaderConfigManager(context: Context) {

    companion object {
        private const val TAG = "ModLoaderConfigManager"
        private const val CONFIG_NAME = "gog_modloader_rules.json"
    }

    data class ModLoaderVersion(
        val version: String = "",
        val url: String = "",
        val fileName: String = "modloader.zip",
        val stable: Boolean = false
    ) {
        fun getDisplayString(context: Context): String =
            if (stable) "$version (${context.getString(R.string.runtime_version_stable)})" else version

        override fun toString(): String = version
    }

    data class ModLoaderRule(
        val gameId: Long = 0,
        val gameName: String = "",
        val name: String = "",
        val officialUrl: String = "",
        val versions: MutableList<ModLoaderVersion> = mutableListOf()
    )

    private val rules = mutableListOf<ModLoaderRule>()

    init {
        loadConfig(context)
    }

    fun getRule(gameId: Long): ModLoaderRule? = rules.find { it.gameId == gameId }

    fun getAllRules(): List<ModLoaderRule> = rules.toList()

    /**
     * 获取 ModLoader 版本列表
     * 优先从 GitHub API 动态获取，失败时使用配置文件中的静态版本
     * @param rule ModLoader 规则
     * @param forceRefresh 强制刷新（忽略缓存）
     */
    fun getVersions(rule: ModLoaderRule, forceRefresh: Boolean = false): List<ModLoaderVersion> {
        // 如果已有版本且不强制刷新，直接返回
        if (rule.versions.isNotEmpty() && !forceRefresh) {
            return rule.versions
        }

        // 从 GitHub 获取最新稳定版本
        val fetchedVersions = ModLoaderVersionFetcher.fetchVersions(
            modLoaderName = rule.name,
            maxCount = 5
        )

        if (fetchedVersions.isNotEmpty()) {
            rule.versions.clear()
            rule.versions.addAll(fetchedVersions)
            AppLog.i(TAG, "从 GitHub 获取到 ${rule.name} 的 ${fetchedVersions.size} 个版本")
        } else {
            AppLog.w(TAG, "无法从 GitHub 获取 ${rule.name} 版本，使用本地配置")
        }

        return rule.versions
    }

    private fun loadConfig(context: Context) {
        try {
            context.assets.open(CONFIG_NAME).use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                    val content = reader.readText()
                    val array = JSONArray(content)

                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val rule = ModLoaderRule(
                            gameId = obj.optLong("gameId"),
                            gameName = obj.optString("gameName", ""),
                            name = obj.optString("modLoaderName", obj.optString("name", "")),
                            officialUrl = obj.optString("officialUrl", "")
                        )

                        obj.optJSONArray("versions")?.let { versionsArray ->
                            for (j in 0 until versionsArray.length()) {
                                val versionObj = versionsArray.getJSONObject(j)
                                rule.versions.add(ModLoaderVersion(
                                    version = versionObj.optString("version", ""),
                                    url = versionObj.optString("url", ""),
                                    fileName = versionObj.optString("fileName", "modloader.zip"),
                                    stable = versionObj.optBoolean("stable", false)
                                ))
                            }
                        }

                        // 兼容旧格式
                        if (obj.has("modLoaderUrl")) {
                            rule.versions.add(ModLoaderVersion(
                                version = "default",
                                url = obj.optString("modLoaderUrl", ""),
                                fileName = obj.optString("fileName", "modloader.zip"),
                                stable = true
                            ))
                        }

                        rules.add(rule)
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "读取 ModLoader 配置失败: ${e.message}", e)
        }
    }
}
