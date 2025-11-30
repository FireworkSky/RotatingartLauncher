package com.app.ralaunch.utils

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("UNCHECKED_CAST")
class JsonSerializer {

    // 序列化对象到JSONObject
    fun toJson(obj: Any): JSONObject {
        val json = JSONObject()
        val fields = obj.javaClass.declaredFields

        fields.forEach { field ->
            field.isAccessible = true
            val value = field.get(obj)

            if (value != null) {
                try {
                    when (value) {
                        is String -> json.put(field.name, value)
                        is Number -> json.put(field.name, value)
                        is Boolean -> json.put(field.name, value)
                        is Date -> json.put(field.name, formatDate(value))
                        is Collection<*> -> json.put(field.name, toJsonArray(value))
                        is Array<*> -> json.put(field.name, toJsonArray(value.toList()))
                        is Map<*, *> -> json.put(field.name, toJsonObject(value))
                        is Enum<*> -> json.put(field.name, value.name)
                        else -> {
                            if (isPrimitiveOrString(value::class.java)) {
                                json.put(field.name, value.toString())
                            } else {
                                json.put(field.name, toJson(value))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.error(message = "Serialization error: ", throwable = e)
                }
            }
        }

        return json
    }

    // 从JSONObject反序列化到对象 - 移除了内联和泛型
    fun <T> fromJson(json: JSONObject, clazz: Class<T>): T {
        val instance = clazz.getDeclaredConstructor().newInstance()
        val fields = clazz.declaredFields

        fields.forEach { field ->
            field.isAccessible = true
            if (json.has(field.name)) {
                try {
                    val value = when (field.type) {
                        String::class.java -> json.getString(field.name)
                        Int::class.java -> json.getInt(field.name)
                        Long::class.java -> json.getLong(field.name)
                        Double::class.java -> json.getDouble(field.name)
                        Float::class.java -> json.getDouble(field.name).toFloat()
                        Boolean::class.java -> json.getBoolean(field.name)
                        else -> {
                            if (field.type.isEnum) {
                                getEnumValue(field.type, json.getString(field.name))
                            } else if (isPrimitiveOrString(field.type)) {
                                json.get(field.name).toString()
                            } else {
                                fromJson(json.getJSONObject(field.name), field.type)
                            }
                        }
                    }
                    field.set(instance, value)
                } catch (e: Exception) {
                    Logger.error(message = "Serialization error: ", throwable = e)
                }
            }
        }

        return instance
    }

    // 重载方法，支持类型推断
    inline fun <reified T> fromJson(json: JSONObject): T {
        return fromJson(json, T::class.java)
    }

    // 序列化列表到JSONArray
    private fun toJsonArray(collection: Collection<*>): JSONArray {
        val array = JSONArray()
        collection.forEach { item ->
            if (item != null) {
                try {
                    when (item) {
                        is String -> array.put(item)
                        is Number -> array.put(item)
                        is Boolean -> array.put(item)
                        is Date -> array.put(formatDate(item))
                        is Collection<*> -> array.put(toJsonArray(item))
                        is Map<*, *> -> array.put(toJsonObject(item))
                        else -> {
                            if (isPrimitiveOrString(item::class.java)) {
                                array.put(item.toString())
                            } else {
                                array.put(toJson(item))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.error(message = "Serialization error: ", throwable = e)
                }
            }
        }
        return array
    }

    // 序列化Map到JSONObject
    private fun toJsonObject(map: Map<*, *>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) ->
            if (key != null && value != null) {
                try {
                    when (value) {
                        is String -> json.put(key.toString(), value)
                        is Number -> json.put(key.toString(), value)
                        is Boolean -> json.put(key.toString(), value)
                        is Date -> json.put(key.toString(), formatDate(value))
                        is Collection<*> -> json.put(key.toString(), toJsonArray(value))
                        is Map<*, *> -> json.put(key.toString(), toJsonObject(value))
                        else -> {
                            if (isPrimitiveOrString(value::class.java)) {
                                json.put(key.toString(), value.toString())
                            } else {
                                json.put(key.toString(), toJson(value))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.error(message = "Serialization error: ", throwable = e)
                }
            }
        }
        return json
    }

    // 从JSONArray反序列化到列表 - 移除了内联
    private fun <T> fromJsonArray(jsonArray: JSONArray, clazz: Class<T>): List<T> {
        val list = mutableListOf<T>()
        for (i in 0 until jsonArray.length()) {
            try {
                val item = when (clazz) {
                    String::class.java -> jsonArray.getString(i) as T
                    Int::class.java -> jsonArray.getInt(i) as T
                    Long::class.java -> jsonArray.getLong(i) as T
                    Double::class.java -> jsonArray.getDouble(i) as T
                    Boolean::class.java -> jsonArray.getBoolean(i) as T
                    else -> {
                        if (clazz.isEnum) {
                            getEnumValue(clazz, jsonArray.getString(i)) as T
                        } else {
                            fromJson(jsonArray.getJSONObject(i), clazz)
                        }
                    }
                }
                list.add(item)
            } catch (e: Exception) {
                Logger.error(message = "Serialization error: ", throwable = e)
            }
        }
        return list
    }

    // 重载方法，支持类型推断
    private inline fun <reified T> fromJsonArray(jsonArray: JSONArray): List<T> {
        return fromJsonArray(jsonArray, T::class.java)
    }

    // 文件操作 - 移除了内联
    fun saveToFile(obj: Any, file: File): Boolean {
        return try {
            file.parentFile?.mkdirs()
            val json = toJson(obj)
            file.writeText(json.toString(2))
            true
        } catch (e: Exception) {
            Logger.error(message = "Save failed: ", throwable = e)
            false
        }
    }

    fun <T> loadFromFile(file: File, clazz: Class<T>): T? {
        return try {
            if (!file.exists()) return null
            val jsonString = file.readText()
            val jsonObject = JSONObject(jsonString)
            fromJson(jsonObject, clazz)
        } catch (_: Exception) {
            null
        }
    }

    // 重载方法，支持类型推断
    inline fun <reified T> loadFromFile(file: File): T? {
        return loadFromFile(file, T::class.java)
    }

    // 工具方法 - 全部改为public
    private fun isPrimitiveOrString(clazz: Class<*>): Boolean {
        return clazz.isPrimitive ||
                clazz == String::class.java ||
                clazz == Int::class.java || clazz == Integer::class.java ||
                clazz == Long::class.java || clazz == java.lang.Long::class.java ||
                clazz == Double::class.java || clazz == java.lang.Double::class.java ||
                clazz == Float::class.java || clazz == java.lang.Float::class.java ||
                clazz == Boolean::class.java || clazz == java.lang.Boolean::class.java
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getEnumValue(enumClass: Class<*>, value: String): T {
        val method = enumClass.getMethod("valueOf", String::class.java)
        return method.invoke(null, value) as T
    }

    private fun formatDate(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(date)
    }

    // JSON验证和格式化
    fun isValidJson(jsonString: String): Boolean {
        return try {
            JSONObject(jsonString)
            true
        } catch (e: Exception) {
            try {
                JSONArray(jsonString)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun formatJson(jsonString: String): String {
        return try {
            if (jsonString.trimStart().startsWith("[")) {
                JSONArray(jsonString).toString(2)
            } else {
                JSONObject(jsonString).toString(2)
            }
        } catch (e: Exception) {
            jsonString
        }
    }
}