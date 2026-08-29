package com.app.ralaunch.jsonconfig.processor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * KSP 处理器：扫描标注 `@FlowJsonConfig` 的 @Serializable data class，
 * 用 kotlinpoet 生成 `<Model>FlowJsonConfigGenerated` 抽象基类：
 * - 继承 `FlowJsonConfigBase<Model>`（initial = Model()，serializer = Model.serializer()）；
 * - 提供 `val c = Instance()` 与 inner class Instance，
 *   每个构造参数一个 `var x by configProperty(Model::x) { c, v -> c.copy(x = v) }` 委托属性；
 * - 提供 `val s = AutoSaveInstance()` 与 inner class AutoSaveInstance，
 *   每个构造参数一个 `var x by autoSaveConfigProperty(Model::x) { c, v -> c.copy(x = v) }` 委托属性（set 后自动落盘）。
 *
 * 子类只需 `override val configPath`。要求模型所有构造参数有默认值。
 */
class FlowJsonConfigProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {

    private val codeGenerator = environment.codeGenerator
    private val logger = environment.logger
    private var processed = false

    override fun process(resolver: com.google.devtools.ksp.processing.Resolver): List<KSFile> {
        if (processed) return emptyList() // KSP 多轮处理去重
        processed = true

        resolver.getSymbolsWithAnnotation(FLOW_JSON_CONFIG_FQN)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { declaration ->
                if (validate(declaration)) generate(declaration)
            }
        return emptyList()
    }

    private fun validate(declaration: KSClassDeclaration): Boolean {
        val fqName = declaration.qualifiedName?.asString()
            ?: declaration.simpleName.asString()

        if (Modifier.DATA !in declaration.modifiers) {
            logger.error("@FlowJsonConfig 只能标注在 data class 上: $fqName")
            return false
        }
        val isSerializable = declaration.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SERIALIZABLE_FQN
        }
        if (!isSerializable) {
            logger.error("$fqName 必须同时标注 @Serializable（生成代码调用其 serializer()）")
            return false
        }
        val params = declaration.primaryConstructor?.parameters.orEmpty()
        if (params.isEmpty()) {
            logger.error("$fqName 没有构造参数，无需生成 FlowJsonConfig 基类")
            return false
        }
        val missingDefaults = params.filter { !it.hasDefault }.mapNotNull { it.name?.asString() }
        if (missingDefaults.isNotEmpty()) {
            logger.error(
                "$fqName 构造参数 [${missingDefaults.joinToString(", ")}] 缺少默认值" +
                    " — 生成代码需要以 ${declaration.simpleName.asString()}() 构造 initial，请为所有参数提供默认值"
            )
            return false
        }
        return true
    }

    private fun generate(declaration: KSClassDeclaration) {
        val generatedName = "${declaration.simpleName.asString()}FlowJsonConfigGenerated"
        val modelClassName = declaration.toClassName()
        val baseClassName = ClassName(GENERATED_PACKAGE, "FlowJsonConfigBase")

        fun buildInstanceClass(className: String, delegateFun: String) = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.INNER)
            .addProperties(declaration.primaryConstructor!!.parameters.map { param ->
                val name = param.name!!.asString()
                PropertySpec.builder(name, param.type.toTypeName())
                    .mutable(true)
                    .delegate(
                        CodeBlock.of(
                            "$delegateFun(%T::$name) { c, v -> c.copy($name = v) }",
                            modelClassName,
                        )
                    )
                    .build()
            })
            .build()

        val instanceClass = buildInstanceClass("Instance", "configProperty")
        val autoSaveInstanceClass = buildInstanceClass("AutoSaveInstance", "autoSaveConfigProperty")

        val generatedClass = TypeSpec.classBuilder(generatedName)
            .addModifiers(KModifier.ABSTRACT)
            .superclass(baseClassName.parameterizedBy(modelClassName))
            .addSuperclassConstructorParameter("initial = %T()", modelClassName)
            .addSuperclassConstructorParameter("serializer = %T.serializer()", modelClassName)
            .addProperty(
                PropertySpec.builder("c", ClassName(GENERATED_PACKAGE, generatedName, "Instance"))
                    .addKdoc("c = config：配置字段读写入口，委托至 [FlowJsonConfigBase.configProperty]（StateFlow）")
                    .initializer("Instance()")
                    .build()
            )
            .addProperty(
                PropertySpec.builder("s", ClassName(GENERATED_PACKAGE, generatedName, "AutoSaveInstance"))
                    .addKdoc("s = save：配置字段读写入口，委托至 [FlowJsonConfigBase.autoSaveConfigProperty]（set 后自动落盘）")
                    .initializer("AutoSaveInstance()")
                    .build()
            )
            .addType(instanceClass)
            .addType(autoSaveInstanceClass)
            .build()

        val fileSpec = FileSpec.builder(GENERATED_PACKAGE, generatedName)
            .addFileComment("Generated by FlowJsonConfigProcessor — do not edit")
            .addType(generatedClass)
            .build()

        val dependencies = declaration.containingFile
            ?.let { Dependencies(false, it) }
            ?: Dependencies.ALL_FILES
        codeGenerator.createNewFile(dependencies, GENERATED_PACKAGE, generatedName)
            .use { out -> out.writer().use(fileSpec::writeTo) }
        logger.info("✅ Generated FlowJsonConfig base: $generatedName")
    }

    private companion object {
        const val FLOW_JSON_CONFIG_FQN = "com.app.ralaunch.jsonconfig.FlowJsonConfig"
        const val SERIALIZABLE_FQN = "kotlinx.serialization.Serializable"
        const val GENERATED_PACKAGE = "com.app.ralaunch.jsonconfig"
    }
}

class FlowJsonConfigProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        FlowJsonConfigProcessor(environment)
}
