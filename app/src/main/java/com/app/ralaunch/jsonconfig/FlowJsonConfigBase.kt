package com.app.ralaunch.jsonconfig

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

/**
 * 基于 StateFlow 的 JSON 配置基类。
 *
 * 配置数据保持 immutable（[kotlinx.serialization.Serializable] 的 data class），
 * 所有写入通过 `copy()` 生成新实例后原子更新 [MutableStateFlow]，因此：
 * - 字段读写体验与普通对象一致（`AppConfig.c.abc = "x"`，仅更新内存状态）；
 * - 写后自动落盘用 [autoSaveConfigProperty]（生成类的 `AppConfig.s.abc = "x"`）；
 * - [state] 始终可被收集，字段级监听用 [flowOf]；
 * - 磁盘加载/写入语义：prettyPrint、忽略未知键、文件缺失时落盘默认配置、
 *   解析失败回退默认值。
 *
 * 用法（推荐：用 `@FlowJsonConfig` 注解让 KSP 处理器生成样板代码，
 * 见 [FlowJsonConfig]；生成类为 `<Model>FlowJsonConfigGenerated`，
 * 子类只需 override [configPath]）：
 * ```kotlin
 * @FlowJsonConfig
 * @Serializable
 * data class AppConfigData(
 *     val abc: String = "",
 *     val port: Int = 8080,
 * )
 *
 * object AppConfig : AppConfigDataFlowJsonConfigGenerated() {
 *     override val configPath = File(configDir, "app_config.json").absolutePath
 * }
 *
 * AppConfig.load()                                   // 加载（文件缺失时写入默认配置）
 * AppConfig.c.abc = "hello"                          // 像普通对象一样读写（不落盘）
 * AppConfig.s.abc = "hello"                          // 读写并在写入后自动落盘
 * AppConfig.flowOf(AppConfigData::abc)              // 观察单个字段
 * AppConfig.save()                                  // 写入磁盘
 * ```
 *
 * @param Data immutable 配置数据类型
 */
abstract class FlowJsonConfigBase<Data>(
    initial: Data,
    serializer: KSerializer<Data>,
) {
    private val default: Data = initial

    /** JSON 编解码配置：prettyPrint、忽略未知键 */
    private val json = Json {
        prettyPrint = true // 保存时格式化，便于阅读
        ignoreUnknownKeys = true // 忽略 JSON 中未知的键，提高兼容性
    }

    private val dataSerializer: KSerializer<Data> = serializer

    /** 配置文件路径，由子类 override 声明 */
    protected abstract val configPath: String

    protected val _state = MutableStateFlow(initial)

    /** 配置状态流，始终持有最新值 */
    val state: StateFlow<Data> = _state.asStateFlow()

    /** 当前配置快照 */
    val value: Data
        get() = _state.value

    /**
     * 声明配置属性代理：get 读取当前快照，set 通过 `copy()` 生成新数据后原子更新状态流
     */
    protected fun <T> configProperty(
        getter: KProperty1<Data, T>,
        setter: (Data, T) -> Data,
    ): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = getter.get(_state.value)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            _state.update { current -> setter(current, value) }
        }
    }

    /**
     * 与 [configProperty] 一致，但 set 更新状态流后自动落盘
     */
    protected fun <T> autoSaveConfigProperty(
        getter: KProperty1<Data, T>,
        setter: (Data, T) -> Data,
    ): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = getter.get(_state.value)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            _state.update { current -> setter(current, value) }
            save()
        }
    }

    /** 观察单个配置字段的变化（跳过相等值） */
    fun <T> flowOf(property: KProperty1<Data, T>): Flow<T> =
        state.map { property.get(it) }.distinctUntilChanged()

    /** 原子地更新整个配置（适合一次修改多个字段） */
    fun update(transform: (Data) -> Data) {
        _state.update(transform)
    }

    /** 与 [update] 一致，但更新状态流后自动落盘（适合批量修改） */
    fun updateSave(transform: (Data) -> Data) {
        _state.update(transform)
        save()
    }

    /**
     * 从磁盘加载配置：文件存在则解码（解析失败回退默认值），不存在则写入默认配置
     */
    fun load() {
        val file = File(configPath)
        if (file.exists()) {
            _state.value = runCatching {
                json.decodeFromString(dataSerializer, file.readText())
            }.getOrDefault(default)
        } else {
            _state.value = default
            save()
        }
    }

    /**
     * 将当前配置写入磁盘
     * @return 是否保存成功
     */
    fun save(): Boolean = runCatching {
            val file = File(configPath)
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            file.writeText(json.encodeToString(dataSerializer, _state.value))
        }.isSuccess
}
