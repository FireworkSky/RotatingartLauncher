package com.app.ralaunch.core.platform.android.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.AtomicFile
import android.view.Display
import android.view.Surface
import com.app.ralaunch.core.di.contract.ISettingsRepositoryServiceV2
import com.app.ralaunch.core.logging.AppLog
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Standard Android display votes plus opt-in, permission-gated system refresh overrides. */
class RefreshRateService(
    private val context: Context,
    private val settingsRepository: ISettingsRepositoryServiceV2
) {
    data class Status(
        val canWriteSettings: Boolean = false,
        val canWriteSecureSettings: Boolean = false,
        val needsVendorPermission: Boolean = false,
        val restorePending: Boolean = false,
        val failed: Boolean = false
    )

    @Serializable
    private data class Journal(val version: Int = 1, val entries: List<RefreshRateOverride> = emptyList())

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val journalFile = AtomicFile(File(context.noBackupFilesDir, "refresh-rate-backup.json"))
    private val isXiaomi = listOf(Build.MANUFACTURER, Build.BRAND).any {
        it.lowercase() in setOf("xiaomi", "redmi", "poco")
    }

    fun onAppStart() {
        scope.launch { reconcile() }
    }

    suspend fun reconcile(): Status = withContext(Dispatchers.IO) {
        mutex.withLock {
            val enabled = settingsRepository.Settings.unlockRefreshRateLimit
            val writable = Settings.System.canWrite(context)
            val secureWritable = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
            var pending = false
            var failed = false
            try {
                // Activities run in :game; serialize the journal across both Android processes.
                RandomAccessFile(File(context.noBackupFilesDir, "refresh-rate-backup.lock"), "rw").use { file ->
                    file.channel.lock().use {
                        val journal = if (journalFile.baseFile.exists() ||
                            File(journalFile.baseFile.path + ".bak").exists()
                        ) {
                            json.decodeFromString<Journal>(journalFile.openRead().bufferedReader().use { it.readText() })
                        } else Journal()
                        check(journal.version == 1) { "Unsupported refresh journal version" }
                        val entries = journal.entries.toMutableList()
                        val overrides = RefreshRateOverrides(entries, ::readSetting, ::writeSetting) {
                            val stream = journalFile.startWrite()
                            try {
                                stream.write(json.encodeToString(Journal(entries = entries)).toByteArray(Charsets.UTF_8))
                                journalFile.finishWrite(stream)
                            } catch (e: Exception) {
                                journalFile.failWrite(stream)
                                throw e
                            }
                        }
                        if (enabled) {
                            val display = context.getSystemService(DisplayManager::class.java)
                                ?.getDisplay(Display.DEFAULT_DISPLAY)
                            val target = display?.let { selectMode(it)?.refreshRate }
                            if (target != null && target > 60f) {
                                fun apply(namespace: String, key: String, value: String) {
                                    try {
                                        overrides.apply(namespace, key, value)
                                    } catch (e: Exception) {
                                        failed = true
                                        AppLog.e(TAG, "Cannot apply $namespace.$key=$value", e)
                                    }
                                }
                                if (writable) apply("system", "peak_refresh_rate", target.toString())
                                if (isXiaomi && secureWritable) {
                                    listOf("user_refresh_rate", "miui_refresh_rate").forEach { key ->
                                        // Only touch vendor settings actually provided by this ROM.
                                        if (readSetting("secure", key) != null) {
                                            apply("secure", key, target.toInt().toString())
                                        }
                                    }
                                }
                            }
                        } else {
                            val errors = overrides.restore()
                            pending = entries.isNotEmpty() || errors.isNotEmpty()
                            errors.forEach { AppLog.w(TAG, it) }
                            failed = errors.isNotEmpty()
                        }
                    }
                }
            } catch (e: Exception) {
                failed = true
                pending = !enabled
                AppLog.e(TAG, "Refresh override failed (enabled=$enabled)", e)
            }
            Status(writable, secureWritable, isXiaomi, pending, failed)
        }
    }

    /** Called on the UI thread, using the actual SDL Surface for either backend. */
    fun requestGameRefreshRate(activity: Activity, surface: Surface?, enabled: Boolean, caller: String) {
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else null
            val currentDisplay = display ?: activity.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return
            val mode = if (enabled) selectMode(currentDisplay) else null
            val rate = mode?.refreshRate ?: 0f
            val params = activity.window.attributes
            params.preferredDisplayModeId = mode?.modeId ?: 0
            params.preferredRefreshRate = rate
            activity.window.attributes = params
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && surface?.isValid == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    surface.setFrameRate(
                        rate,
                        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                        Surface.CHANGE_FRAME_RATE_ALWAYS
                    )
                } else {
                    surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                }
            }
            AppLog.i(TAG, "[$caller] enabled=$enabled requested=${rate}Hz observed=${currentDisplay.refreshRate}Hz " +
                "mode=${mode?.modeId}; system policy may override the request")
        } catch (e: Exception) {
            AppLog.w(TAG, "[$caller] Display request failed", e)
        }
    }

    private fun selectMode(display: Display): Display.Mode? {
        val current = display.mode
        return display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }.maxByOrNull { it.refreshRate }
    }

    private fun readSetting(namespace: String, key: String): String? = when (namespace) {
        "system" -> Settings.System.getString(context.contentResolver, key)
        "secure" -> Settings.Secure.getString(context.contentResolver, key)
        else -> error("Unsupported settings namespace")
    }

    private fun writeSetting(namespace: String, key: String, value: String?): Boolean {
        val success = when (namespace) {
            "system" -> Settings.System.putString(context.contentResolver, key, value)
            "secure" -> Settings.Secure.putString(context.contentResolver, key, value)
            else -> error("Unsupported settings namespace")
        }
        AppLog.i(TAG, "System setting $namespace.$key=$value accepted=$success")
        return success
    }

    private companion object {
        const val TAG = "RefreshRate"
    }
}
