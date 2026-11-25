package com.app.ralaunch.feature.terraria.conversion

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.DataInputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.json.JSONObject

internal object MobileTerrariaPackage {
    private const val PACKAGE_NAME = "com.and.games505.TerrariaPaid"
    const val DATA = "assets/bin/Data/"
    const val PROFILE = "mobile-terraria/profile.zip"
    const val CONTROL_ART_DIRECTORY = "mobile-controls"
    const val CONTROL_ART_REVISION = "1"

    fun interface Progress {
        fun update(stage: String, current: Int, total: Int): Boolean
    }

    fun inspect(context: Context): JSONObject {
        val info = installed(context) ?: return JSONObject().put("status", "not-installed")
        val archives = archives(info)
        val resources = inspectArchives(archives)
        return JSONObject()
            .put("status", if (resources) "unity" else "unsupported")
            .put("version", info.versionName.orEmpty())
            .put("apkCount", archives.size)
    }

    fun targetTml(context: Context): String? {
        val stream = try {
            context.assets.open(PROFILE)
        } catch (_: FileNotFoundException) {
            return null
        }
        ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name == "profile.json") {
                    val version = JSONObject(zip.reader(Charsets.UTF_8).readText()).getString("targetTml")
                    return version.takeIf { it.matches(Regex("[0-9]{4}\\.[0-9]{1,2}(\\.[0-9]{1,5}){0,2}")) }
                }
            }
        }
    }

    // Refresh installed content on the next explicit conversion after changing output rules.
    private const val CONVERTER_REVISION = 4

    fun fingerprint(info: PackageInfo): String = "${info.longVersionCode}:${info.lastUpdateTime}:$CONVERTER_REVISION"

    fun installed(context: Context): PackageInfo? = try {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun archives(info: PackageInfo): List<File> {
        val application = checkNotNull(info.applicationInfo)
        return (listOf(application.sourceDir) + application.splitSourceDirs.orEmpty()).map(::File)
    }

    internal fun inspectArchives(archives: List<File>): Boolean {
        var bundle = false
        var streams = false
        for (file in archives) {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(DATA + "data.unity3d")
                if (!bundle && entry != null && !entry.isDirectory && entry.size >= 8) {
                    val signature = ByteArray(8)
                    DataInputStream(zip.getInputStream(entry)).use { it.readFully(signature) }
                    bundle = signature.contentEquals("UnityFS\u0000".toByteArray(Charsets.US_ASCII))
                }
                streams = streams || (zip.getEntry(DATA + "resources.resource")?.size ?: 0) > 0
            }
        }
        return bundle && streams
    }
}
