/*******************************************************************************
 * RotatingArtLauncher - StringsGenerator
 * 
 * This file is part of the RotatingArtLauncher project.
 * 
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 * 
 * Created by: eternalfuture-e38299 (2026/7/5)
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

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.json.JSONObject
import java.io.File
import java.util.Locale

object StringsGenerator {
    /**
    ◦ 为指定模块生成字符串代码（遍历所有JSON文件，生成接口和实现）
     */
    fun generateForModule(moduleDir: File, packageName: String = "com.app.ralaunch") {
        val resourcesDir = File(moduleDir, "src/main/localization")
        val outputDir = File(moduleDir, "build/generated/strings")

        outputDir.deleteRecursively()
        outputDir.mkdirs()

        if (!resourcesDir.exists()) {
            println("⚠️ Resources directory not found: ${resourcesDir.absolutePath}")
            return
        }

        // 遍历所有JSON文件
        val jsonFiles = resourcesDir.listFiles { file ->
            file.isFile && file.extension.lowercase(Locale.ROOT) == "json"
        } ?: emptyArray()

        if (jsonFiles.isEmpty()) {
            println("⚠️ No JSON files found in: ${resourcesDir.absolutePath}")
            return
        }

        // 从第一个文件生成基础接口
        val interfaceName = "LocaleStrings"
        val firstFileData = JSONObject(jsonFiles.first().readText()).toMap()
        generateBaseInterface(firstFileData, outputDir, packageName, interfaceName)

        // 为每个JSON文件生成object实现
        jsonFiles.forEach { jsonFile ->
            try {
                val jsonData = JSONObject(jsonFile.readText()).toMap()
                val className = jsonFile.nameWithoutExtension.capitalize(Locale.ROOT)

                generateObjectImplementation(jsonData, outputDir, packageName, className, interfaceName)
                println("✅ Generated strings object: $className from ${jsonFile.name}")
            } catch (e: Exception) {
                println("❌ Error processing file ${jsonFile.name}: ${e.message}")
                e.printStackTrace()
            }
        }

        println("✅ Generated ${jsonFiles.size} strings objects and interface for module: ${moduleDir.name}")
    }

    private fun generateBaseInterface(
        data: Map<String, Any>,
        outputDir: File,
        packageName: String,
        interfaceName: String
    ) {
        val interfaceBuilder = TypeSpec.interfaceBuilder(interfaceName)

        processDataForInterface(data, interfaceBuilder, interfaceName)

        FileSpec.builder(packageName, interfaceName)
            .addType(interfaceBuilder.build())
            .build()
            .writeTo(outputDir)

        println("✅ Generated interface: $interfaceName")
    }

    private fun processDataForInterface(
        data: Map<String, Any>,
        builder: TypeSpec.Builder,
        parentInterface: String
    ) {
        // 分离普通键名和带点键名
        val dottedKeys = data.filterKeys { it.contains('.') }
        val normalKeys = data.filterKeys { !it.contains('.') }

        // 处理普通键名
        normalKeys.forEach { (key, value) ->
            processSingleKeyForInterface(key, value, builder, parentInterface)
        }

        // 处理带点键名 - 按第一级分组
        val groupedByFirstLevel = dottedKeys.keys.groupBy { it.split('.').first() }

        groupedByFirstLevel.forEach { (firstLevel, keys) ->
            val nestedInterfaceName = firstLevel.capitalize(Locale.ROOT)

            // 创建嵌套接口
            val nestedBuilder = TypeSpec.interfaceBuilder(nestedInterfaceName)

            // 收集嵌套数据
            val nestedData = mutableMapOf<String, Any>()

            keys.forEach { key ->
                val remainingKey = key.split('.').drop(1).joinToString(".")
                val value = dottedKeys[key]!!
                nestedData[remainingKey] = value
            }

            // 递归处理嵌套数据
            processDataForInterface(nestedData, nestedBuilder, "$parentInterface.$nestedInterfaceName")

            // 添加嵌套接口和访问属性
            builder.addType(nestedBuilder.build())
            builder.addProperty(
                PropertySpec.builder(firstLevel, ClassName("", nestedInterfaceName))
                    .build()
            )
        }
    }

    private fun processSingleKeyForInterface(
        key: String,
        value: Any,
        builder: TypeSpec.Builder,
        parentInterface: String
    ) {
        when (value) {
            is String -> {
                if (hasPlaceholders(value)) {
                    val params = extractPlaceholders(value)
                    val functionBuilder = FunSpec.builder(key)
                        .returns(String::class)

                    params.forEach { paramName ->
                        functionBuilder.addParameter(ParameterSpec.builder(paramName, Any::class).build())
                    }

                    // 为函数返回变量名称作为默认值
                    functionBuilder.addStatement("return \"$key\"")

                    builder.addFunction(functionBuilder.build())
                } else {
                    builder.addProperty(
                        PropertySpec.builder(key, String::class)
                            .build()
                    )
                }
            }
            is Map<*, *> -> {
                val nestedName = key.capitalize(Locale.ROOT)
                val nestedBuilder = TypeSpec.interfaceBuilder(nestedName)
                processDataForInterface(value as Map<String, Any>, nestedBuilder, "$parentInterface.$nestedName")
                builder.addType(nestedBuilder.build())
                builder.addProperty(
                    PropertySpec.builder(key, ClassName("", nestedName))
                        .build()
                )
            }
            is List<*> -> {
                builder.addProperty(
                    PropertySpec.builder(key, List::class.asClassName().parameterizedBy(String::class.asTypeName()))
                        .build()
                )
            }
        }
    }

    private fun generateObjectImplementation(
        data: Map<String, Any>,
        outputDir: File,
        packageName: String,
        className: String,
        interfaceName: String
    ) {
        val objectBuilder = TypeSpec.objectBuilder(className)
            .addSuperinterface(ClassName(packageName, interfaceName))

        processDataForImplementation(data, objectBuilder, "$packageName.$interfaceName")

        FileSpec.builder(packageName, className)
            .addType(objectBuilder.build())
            .build()
            .writeTo(outputDir)
    }

    private fun processDataForImplementation(
        data: Map<String, Any>,
        builder: TypeSpec.Builder,
        interfacePath: String
    ) {
        // 分离普通键名和带点键名
        val dottedKeys = data.filterKeys { it.contains('.') }
        val normalKeys = data.filterKeys { !it.contains('.') }

        // 处理普通键名
        normalKeys.forEach { (key, value) ->
            processSingleKeyForImplementation(key, value, builder, interfacePath)
        }

        // 处理带点键名 - 按第一级分组
        val groupedByFirstLevel = dottedKeys.keys.groupBy { it.split('.').first() }

        groupedByFirstLevel.forEach { (firstLevel, keys) ->
            val nestedInterfaceName = firstLevel.capitalize(Locale.ROOT)
            val fullInterfacePath = "$interfacePath.$nestedInterfaceName"

            // 收集嵌套数据
            val nestedData = mutableMapOf<String, Any>()

            keys.forEach { key ->
                val remainingKey = key.split('.').drop(1).joinToString(".")
                val value = dottedKeys[key]!!
                nestedData[remainingKey] = value
            }

            // 生成嵌套对象代码
            val nestedCode = generateNestedObjectCode(nestedData, fullInterfacePath, 1)

            // 添加嵌套实现
            builder.addProperty(
                PropertySpec.builder(firstLevel, ClassName.bestGuess(fullInterfacePath))
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("%L", nestedCode)
                    .build()
            )
        }
    }

    private fun processSingleKeyForImplementation(
        key: String,
        value: Any,
        builder: TypeSpec.Builder,
        interfacePath: String
    ) {
        when (value) {
            is String -> {
                if (hasPlaceholders(value)) {
                    val params = extractPlaceholders(value)
                    val functionBuilder = FunSpec.builder(key)
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(String::class)

                    params.forEach { paramName ->
                        functionBuilder.addParameter(ParameterSpec.builder(paramName, Any::class).build())
                    }

                    val escapedValue = value.replace("\"", "\\\"")
                    functionBuilder.addStatement("return \"$escapedValue\"")

                    builder.addFunction(functionBuilder.build())
                } else {
                    builder.addProperty(
                        PropertySpec.builder(key, String::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer("%S", value)
                            .build()
                    )
                }
            }
            is Map<*, *> -> {
                val nestedInterfaceName = key.capitalize(Locale.ROOT)
                val fullInterfacePath = "$interfacePath.$nestedInterfaceName"
                val nestedCode = generateNestedObjectCode(value as Map<String, Any>, fullInterfacePath, 1)

                builder.addProperty(
                    PropertySpec.builder(key, ClassName.bestGuess(fullInterfacePath))
                        .addModifiers(KModifier.OVERRIDE)
                        .initializer("%L", nestedCode)
                        .build()
                )
            }
            is List<*> -> {
                builder.addProperty(
                    PropertySpec.builder(key, List::class.asClassName().parameterizedBy(String::class.asTypeName()))
                        .addModifiers(KModifier.OVERRIDE)
                        .initializer("listOf(${value.joinToString { "%S".format(it) }})")
                        .build()
                )
            }
        }
    }

    private fun generateNestedObjectCode(
        data: Map<String, Any>,
        interfacePath: String,
        indentLevel: Int
    ): String {
        val indent = "  ".repeat(indentLevel)
        val sb = StringBuilder()

        sb.append("object : $interfacePath {\n")

        // 分离普通键名和带点键名
        val dottedKeys = data.filterKeys { it.contains('.') }
        val normalKeys = data.filterKeys { !it.contains('.') }

        // 处理普通键
        normalKeys.forEach { (key, value) ->
            when (value) {
                is String -> {
                    if (hasPlaceholders(value)) {
                        val params = extractPlaceholders(value)
                        sb.append("$indent  override fun $key(")
                        sb.append(params.joinToString(", ") { "$it: Any" })
                        sb.append("): String = \"${value.replace("\"", "\\\"")}\"\n")
                    } else {
                        sb.append("$indent  override val $key: String = \"${value.replace("\"", "\\\"")}\"\n")
                    }
                }
                is Map<*, *> -> {
                    val nestedInterfaceName = key.capitalize(Locale.ROOT)
                    val fullInterfacePath = "$interfacePath.$nestedInterfaceName"
                    sb.append("$indent  override val $key: $fullInterfacePath = ")
                    sb.append(generateNestedObjectCode(value as Map<String, Any>, fullInterfacePath, indentLevel + 1))
                    sb.append("\n")
                }
                is List<*> -> {
                    sb.append("$indent  override val $key: List<String> = listOf(${value.joinToString { "\"${it}\"" }})\n")
                }
            }
        }

        // 处理带点键 - 按第一级分组
        val groupedByFirstLevel = dottedKeys.keys.groupBy { it.split('.').first() }
        groupedByFirstLevel.forEach { (firstLevel, keys) ->
            val nestedInterfaceName = firstLevel.capitalize(Locale.ROOT)
            val fullInterfacePath = "$interfacePath.$nestedInterfaceName"

            val nestedData = mutableMapOf<String, Any>()
            keys.forEach { key ->
                val remainingKey = key.split('.').drop(1).joinToString(".")
                val value = dottedKeys[key]!!
                nestedData[remainingKey] = value
            }

            sb.append("$indent  override val $firstLevel: $fullInterfacePath = ")
            sb.append(generateNestedObjectCode(nestedData, fullInterfacePath, indentLevel + 1))
            sb.append("\n")
        }

        sb.append("$indent}")
        return sb.toString()
    }

    private fun hasPlaceholders(text: String): Boolean {
        return text.contains(Regex("\\$\\{[^}]+\\}"))
    }

    private fun extractPlaceholders(text: String): List<String> {
        val regex = Regex("\\$\\{([^}]+)\\}")
        val matches = regex.findAll(text)
        return matches.map {
            val group = it.groupValues[1]
            if (group.isNotEmpty()) group else "param"
        }.toList()
    }
}

// JSONObject 扩展函数，转换为 Map
private fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach { key ->
        val value = get(key)
        when (value) {
            is JSONObject -> map[key] = value.toMap()
            is org.json.JSONArray -> map[key] = value.toList()
            else -> map[key] = value
        }
    }
    return map
}

private fun org.json.JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until length()) {
        val value = get(i)
        when (value) {
            is JSONObject -> list.add(value.toMap())
            is org.json.JSONArray -> list.add(value.toList())
            else -> list.add(value)
        }
    }
    return list
}