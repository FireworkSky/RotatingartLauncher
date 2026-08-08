package com.app.ralaunch

import android.annotation.SuppressLint
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.app.ralaunch.utils.ConfigManager
import kotlinx.serialization.Serializable
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import android.os.Build
import android.util.Log
import com.app.ralaunch.strings.StringsResource
import com.app.ralaunch.strings.StringsResource.Strings
import com.app.ralaunch.utils.AppLogger

/*******************************************************************************
 * RotatingArtLauncher - ConfigurationState
 * 
 * This file is part of the RotatingArtLauncher project.
 * 
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 * 
 * Created by: eternalfuture-e38299 (2026/7/4)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

object ConfigurationState {
    @Serializable
    data class AppConfig(
        var themeSeedColor: ULong = Color(0xFF2198F3).value,    // 主题色
        var dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S, // 动态取色
        var themeMode: ThemeMode = ThemeMode.SYSTEM,   // 主题模式
        var logFileMaxSize: Long = 10 * 1024 * 1024,        // 日志文件最大大小
        var logFileMaxCount: Int = 10,               // 日志文件最大数量
        var logLevel: AppLogger.LogLevel = AppLogger.LogLevel.INFO, // 日志等级
        var logFileEnabled: Boolean = true,
        var language : StringsResource.Language = StringsResource.Language.System, // 语言

        // 控件相关
        var multitouch: Boolean = true,
        var vibration: Boolean = false,
        var virtualController: Boolean = false,
        var vibrationLevel: Float = 0.5f,

        // 游戏相关配置
        var bigCore: Boolean = false,                // 大核亲和性
        var lowLatency: Boolean = false,             // 低延迟音频
        var qualityPreset: QualityPreset = QualityPreset.HIGH, // 画质预设
        var shaderPrecision: Boolean = false,        // 着色器低精度
        var fpsLimit: FpsLimit = FpsLimit.UNLIMITED, // 帧率限制
        var renderer: Renderer = Renderer.NATIVE,    // 渲染器
        var dotnetVersion: String = "10.0.4",              // .NET运行时版本
    ) {
        enum class ThemeMode {
            LIGHT,
            DARK,
            SYSTEM;

            // 获取显示名称的内部函数
            fun string(): String {
                return when (this) {
                    LIGHT -> Strings.settings.theme.light
                    DARK -> Strings.settings.theme.dark
                    SYSTEM -> Strings.settings.system
                }
            }
        }

        enum class Renderer(val id: String, val displayName: String) {
            // AUTO("auto"),
            NATIVE("native", "Native OpenGL ES 3"),
            GL4ES("gl4es", "GL4ES"),
            GL4ES_ANGLE("gl4es_angle", "GL4ES + ANGLE"),
            ANGLE("angle", "ANGLE (Vulkan)"),
            OPENGL("opengl", "OpenGL (gl4es+)"),
            OPENGLES3("opengles3", "OpenGL ES 3 (Native)"),
            VULKAN("vulkan", "Vulkan");

            fun getDescription(): String {
                return when (this) {
                    NATIVE -> Strings.settings.renderer.nativeDesc
                    GL4ES -> Strings.settings.renderer.gl4esDesc
                    GL4ES_ANGLE -> Strings.settings.renderer.gl4esAngleDesc
                    ANGLE -> Strings.settings.renderer.angleDesc
                    OPENGL -> Strings.settings.renderer.openglDesc
                    OPENGLES3 -> Strings.settings.renderer.opengles3Desc
                    VULKAN -> Strings.settings.renderer.vulkanDesc
                }
            }
        }

        enum class QualityPreset {
            HIGH,
            MEDIUM,
            LOW;

            fun getDisplayName() : String {
                return when(this) {
                    HIGH -> Strings.settings.game.qualityHigh
                    MEDIUM -> Strings.settings.game.qualityMedium
                    LOW -> Strings.settings.game.qualityLow
                }
            }
        }

        enum class FpsLimit(val value: Int) {
            UNLIMITED(0),
            FPS_30(30),
            FPS_45(45),
            FPS_60(60);
        }
    }

    class AutoConfigDelegate<T>(
        private val propertyRef: KMutableProperty1<AppConfig, T>,
        private val onConfigUpdate: (T) -> Unit = {}
    ) : ReadWriteProperty<Any?, T> {

        private var _state: MutableState<T>? = null

        @SuppressLint("LogNotTimber")
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            if (_state == null) {
                try {
                    val initialValue = propertyRef.get(ConfigManager.getInstance().getConfig())
                    _state = mutableStateOf(initialValue)
                } catch (e: Exception) {
                    Log.w("RAL", "AutoConfig load failed for ${propertyRef.name}, e=${e}")
                }
            }
            return _state!!.value
        }

        @SuppressLint("LogNotTimber")
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (_state == null) {
                _state = mutableStateOf(value)
            } else if (_state!!.value != value) {
                _state!!.value = value
                try {
                    ConfigManager.getInstance().updateConfig { config ->
                        propertyRef.set(config, value)
                    }
                    onConfigUpdate(value)
                } catch (e: Exception) {
                    Log.e("RAL", "AutoConfig save failed for ${propertyRef.name} = $value, e=${e}")
                }
            }
        }
    }
    private var _themeSeedColorULong by AutoConfigDelegate(
        AppConfig::themeSeedColor
    )

    var themeSeedColor: Color
        get() = Color(_themeSeedColorULong)
        set(value) {
            _themeSeedColorULong = value.value
        }

    var dynamicColor by AutoConfigDelegate(AppConfig::dynamicColor)
    var themeMode by AutoConfigDelegate(AppConfig::themeMode)
    var logFileMaxSize by AutoConfigDelegate(AppConfig::logFileMaxSize)
    var logFileMaxCount by AutoConfigDelegate(AppConfig::logFileMaxCount)
    var logLevel by AutoConfigDelegate(AppConfig::logLevel)
    var logFileEnabled by AutoConfigDelegate(AppConfig::logFileEnabled)
    var language by AutoConfigDelegate(AppConfig::language) {
        StringsResource.setLanguage(it)
    }

    var multitouch by AutoConfigDelegate(AppConfig::multitouch)
    var vibration by AutoConfigDelegate(AppConfig::vibration)
    var virtualController by AutoConfigDelegate(AppConfig::virtualController)
    var vibrationLevel by AutoConfigDelegate(AppConfig::vibrationLevel)

    var bigCore by AutoConfigDelegate(AppConfig::bigCore)
    var lowLatency by AutoConfigDelegate(AppConfig::lowLatency)
    var qualityPreset by AutoConfigDelegate(AppConfig::qualityPreset)
    var shaderPrecision by AutoConfigDelegate(AppConfig::shaderPrecision)
    var fpsLimit by AutoConfigDelegate(AppConfig::fpsLimit)
    var renderer by AutoConfigDelegate(AppConfig::renderer)
    var dotnetVersion by AutoConfigDelegate(AppConfig::dotnetVersion)
}