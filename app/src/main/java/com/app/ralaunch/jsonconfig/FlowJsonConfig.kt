package com.app.ralaunch.jsonconfig

/**
 * 标记一个 @Serializable data class，由 KSP 处理器（ksp/jsonconfig-processor）生成
 * `<Model>FlowJsonConfigGenerated` 抽象基类（输出到 build/generated/ksp）。
 *
 * 要求：类必须同时标注 @Serializable；所有构造参数必须有默认值
 * （生成代码以 `initial = <Model>()` 构造默认配置）。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class FlowJsonConfig
