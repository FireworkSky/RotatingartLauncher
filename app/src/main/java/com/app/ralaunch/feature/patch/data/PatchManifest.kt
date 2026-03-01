package com.app.ralaunch.feature.patch.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

data class PatchManifest(
    @SerializedName("id")
    var id: String = "",

    @SerializedName("name")
    var name: String = "",

    @SerializedName("description")
    var description: String = "",

    @SerializedName("version")
    var version: String = "",

    @SerializedName("author")
    var author: String = "",

    @SerializedName("targetGames")
    var targetGames: List<String>? = null,

    @SerializedName("dllFileName")
    var dllFileName: String = "",

    @SerializedName("entryPoint")
    var entryPoint: EntryPoint? = null,

    @SerializedName("priority")
    var priority: Int = 0,

    @SerializedName("enabled")
    var enabled: Boolean = true,

    @SerializedName("dependencies")
    var dependencies: Dependencies? = null
) {
    data class Dependencies(
        @SerializedName("libs")
        var libs: List<String>? = null
    )

    val entryAssemblyFile: String
        get() = if (!dllFileName.isNullOrEmpty()) dllFileName else ""

    data class EntryPoint(
        @SerializedName("typeName")
        var typeName: String = "",

        @SerializedName("methodName")
        var methodName: String = ""
    )

    companion object {
        private const val TAG = "PatchManifest"
        const val MANIFEST_FILE_NAME = "patch.json"

        @JvmStatic
        val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        // Xoa ham nhan Path, chi giu ham nhan File
        @JvmStatic
        fun fromZip(file: File): PatchManifest? {
            Log.i(TAG, "load Patch zip, file: ${file.absolutePath}")
            return try {
                ZipFile(file).use { zip ->
                    val manifestEntry = zip.getEntry(MANIFEST_FILE_NAME)
                    if (manifestEntry == null) {
                        Log.w(TAG, "Not found in the compressed file $MANIFEST_FILE_NAME")
                        return null
                    }
                    zip.getInputStream(manifestEntry).use { stream ->
                        InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                            gson.fromJson(reader, PatchManifest::class.java)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, Log.getStackTraceString(e))
                null
            }
        }

        @JvmStatic
        fun compareVersions(v1: String, v2: String): Int {
            val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        }

        // Thay Path bang File
        @JvmStatic
        fun fromJson(jsonFile: File): PatchManifest? {
            Log.i(TAG, "load $MANIFEST_FILE_NAME, path: ${jsonFile.absolutePath}")

            if (!jsonFile.exists() || !jsonFile.isFile) {
                Log.w(TAG, "File not found: ${jsonFile.absolutePath}")
                return null
            }

            return try {
                FileInputStream(jsonFile).use { stream ->
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                        gson.fromJson(reader, PatchManifest::class.java)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, Log.getStackTraceString(e))
                null
            }
        }
    }
}
