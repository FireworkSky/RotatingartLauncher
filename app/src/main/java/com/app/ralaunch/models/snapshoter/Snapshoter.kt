package com.app.ralaunch.models.snapshoter

/**
 * 标记一个 data class，由 KSP 处理器（ksp/snapshoter-processor）在
 * `com.app.ralaunch.models.snapshoter` 包下生成 `<Model>Snapshoter.kt`
 * （输出到 build/generated/ksp），内含顶层扩展函数 `snapshot()`：
 * import `com.app.ralaunch.models.snapshoter.snapshot` 后以 `model.snapshot()`
 * 调用，也支持非绑定函数引用 `Model::snapshot`（绑定引用 `model::snapshot` 不适用于扩展）。
 *
 * 深拷贝范围：Kotlin 内置集合（List/Set/Map 及其变体，含任意嵌套）与其他同样标注
 * `@Snapshoter` 的 data class 会递归深拷贝；其余引用类型属性与原对象共享引用
 * （浅拷贝），详见生成函数的 KDoc 限制说明。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Snapshoter
