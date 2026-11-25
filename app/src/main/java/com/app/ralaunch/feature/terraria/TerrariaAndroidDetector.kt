package com.app.ralaunch.feature.terraria

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.zip.ZipFile

/**
 * Discovers the official Terraria Android package and checks whether its APK
 * archives expose the Unity resource containers required by the Bunny PE
 * conversion pipeline. This does not copy private app data or execute the APK.
 */
object TerrariaAndroidDetector {
    const val OFFICIAL_PACKAGE = "com.and.games505.TerrariaPaid"
    private const val UNITY_DATA = "assets/bin/Data/"
    private const val UNITY_BUNDLE = "${UNITY_DATA}data.unity3d"
    private const val UNITY_STREAMS = "${UNITY_DATA}resources.resource"

    data class PayloadInspection(
        val hasUnityBundle: Boolean,
        val hasResourceStream: Boolean,
        val archiveCount: Int,
        val readableArchiveCount: Int
    ) {
        val conversionSourceAvailable: Boolean
            get() = hasUnityBundle && hasResourceStream && readableArchiveCount > 0
    }

    sealed interface Detection {
        data class Installed(
            val packageName: String,
            val versionName: String?,
            val versionCode: Long,
            val launchIntent: Intent?,
            val payload: PayloadInspection
        ) : Detection

        data object NotInstalled : Detection
        data class Error(val message: String) : Detection
    }

    fun detect(context: Context): Detection {
        val pm = context.packageManager
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    OFFICIAL_PACKAGE,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(OFFICIAL_PACKAGE, PackageManager.GET_META_DATA)
            }

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            val application = info.applicationInfo
                ?: return Detection.Error("O Android não informou os arquivos do Terraria.")
            val archives = (listOf(application.sourceDir) + application.splitSourceDirs.orEmpty())
                .filter { it.isNotBlank() }
                .distinct()
                .map(::File)

            Detection.Installed(
                packageName = OFFICIAL_PACKAGE,
                versionName = info.versionName,
                versionCode = versionCode,
                launchIntent = pm.getLaunchIntentForPackage(OFFICIAL_PACKAGE),
                payload = inspectArchives(archives)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            Detection.NotInstalled
        } catch (e: Exception) {
            Detection.Error(e.message ?: "Não foi possível verificar o Terraria.")
        }
    }

    /**
     * Inspect base.apk and split APKs without extracting or modifying them.
     * UnityFS is checked by its eight-byte signature, not by filename alone.
     */
    internal fun inspectArchives(archives: List<File>): PayloadInspection {
        var hasBundle = false
        var hasStreams = false
        var readable = 0

        for (archive in archives) {
            if (!archive.isFile || !archive.canRead()) continue
            try {
                ZipFile(archive).use { zip ->
                    readable++
                    if (!hasBundle) {
                        val entry = zip.getEntry(UNITY_BUNDLE)
                        if (entry != null && !entry.isDirectory && entry.size >= 8) {
                            val signature = ByteArray(8)
                            zip.getInputStream(entry).use { input ->
                                var offset = 0
                                while (offset < signature.size) {
                                    val count = input.read(signature, offset, signature.size - offset)
                                    if (count < 0) break
                                    offset += count
                                }
                                hasBundle = offset == signature.size &&
                                    signature.contentEquals("UnityFS\u0000".toByteArray(Charsets.US_ASCII))
                            }
                        }
                    }
                    if (!hasStreams) {
                        val entry = zip.getEntry(UNITY_STREAMS)
                        hasStreams = entry != null && !entry.isDirectory && entry.size > 0
                    }
                }
            } catch (_: Exception) {
                // A broken split must not prevent inspecting the remaining APKs.
            }
        }

        return PayloadInspection(
            hasUnityBundle = hasBundle,
            hasResourceStream = hasStreams,
            archiveCount = archives.size,
            readableArchiveCount = readable
        )
    }

    /** Opens the installed Terraria app through Android's normal app launcher. */
    fun launch(context: Context): Boolean {
        val intent = (detect(context) as? Detection.Installed)?.launchIntent ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
