package com.app.ralaunch.core.logging.service

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.app.ralaunch.core.di.contract.ISettingsRepositoryServiceV2
import com.app.ralaunch.core.di.service.StoragePathsProviderServiceV1
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.logging.TouchDiagnosticSampler
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID

/** Session-scoped touch evidence, using the existing async writer even when ordinary logs are off. */
class TouchInputDiagnostics(
    private val context: Context,
    private val paths: StoragePathsProviderServiceV1,
    private val settings: ISettingsRepositoryServiceV2
) {
    private val logger = AndroidFileLogger(fileNameProvider = { FILE_NAME }, queueCapacity = 128)
    private val handler = Handler(Looper.getMainLooper())
    private var sampler = TouchDiagnosticSampler()
    private var activity = WeakReference<Activity>(null)
    private var active = false
    private var session = ""
    private var file: File? = null
    private var reportedFailure = false
    private val heartbeat = object : Runnable {
        override fun run() {
            if (!active) return
            safely {
                val owner = activity.get()
                record("heartbeat uptime=${SystemClock.uptimeMillis()} focused=${owner?.hasWindowFocus()} " +
                    "finishing=${owner?.isFinishing} counts(events/edges): ${sampler.summary()}")
            }
            handler.postDelayed(this, 10_000L)
        }
    }

    fun startSession(owner: Activity, backend: String) = safely {
        if (active) stop()
        reportedFailure = false
        val directory = File(paths.logsDirPathFull()).apply { mkdirs() }
        file = File(directory, FILE_NAME)
        rotate()
        logger.configure(directory, enabled = true)
        sampler = TouchDiagnosticSampler()
        activity = WeakReference(owner)
        session = UUID.randomUUID().toString().take(8)
        active = true
        val current = settings.Settings
        record("session-start backend=$backend pid=${android.os.Process.myPid()} " +
            "app=${context.packageManager.getPackageInfo(context.packageName, 0).versionName} " +
            "device=${Build.MANUFACTURER}/${Build.MODEL} Android=${Build.VERSION.RELEASE} SDK=${Build.VERSION.SDK_INT} " +
            "touchMode=${current.touchInputMode} touchForwarding=always legacyTouchEvents=${current.touchEventEnabled} " +
            "multitouch=${current.touchMultitouchEnabled} density=${owner.resources.displayMetrics.density} " +
            "uptime=${SystemClock.uptimeMillis()}")
        InputDevice.getDeviceIds().forEach { id ->
            InputDevice.getDevice(id)?.let {
                record("input-device id=$id name=${it.name} sources=0x${it.sources.toString(16)} " +
                    "vendor=${it.vendorId} product=${it.productId} virtual=${it.isVirtual}")
            }
        }
        handler.postDelayed(heartbeat, 10_000L)
    }

    @JvmOverloads
    fun motion(stage: String, event: MotionEvent, view: View?, detail: () -> String = { "" }) = safely {
        if (!active) return@safely
        val edge = event.actionMasked != MotionEvent.ACTION_MOVE
        val sample = sampler.observe(stage, SystemClock.uptimeMillis(), edge) ?: return@safely
        val pointers = (0 until minOf(event.pointerCount, 8)).joinToString { index ->
            "${event.getPointerId(index)}:${event.getToolType(index)}@" +
                "${event.getX(index).toInt()},${event.getY(index).toInt()}"
        }
        record("$stage n=${sample.events} edges=${sample.edges} omitted=${sample.omitted} " +
            "event=${event.eventTime} down=${event.downTime} action=${MotionEvent.actionToString(event.action)} " +
            "device=${event.deviceId} source=0x${event.source.toString(16)} flags=0x${event.flags.toString(16)} " +
            "pointers=${event.pointerCount} [$pointers] view=${view?.javaClass?.simpleName}:${view?.width}x${view?.height} " +
            "visible=${view?.visibility} attached=${view?.isAttachedToWindow} ${detail()}")
    }

    fun nativeDispatch(event: MotionEvent, view: View, backend: String, route: String,
                       device: Int, finger: Int, action: Int, x: Float, y: Float) = safely {
        if (!active) return@safely
        val stage = "$backend->$route"
        val sample = sampler.observe(stage, SystemClock.uptimeMillis(), action != MotionEvent.ACTION_MOVE)
            ?: return@safely
        record("$stage n=${sample.events} edges=${sample.edges} omitted=${sample.omitted} " +
            "event=${event.eventTime} down=${event.downTime} device=$device finger=$finger " +
            "action=${MotionEvent.actionToString(action)} xy=$x,$y surface=${view.width}x${view.height}")
    }

    fun note(message: String) = safely { if (active) record(message) }

    fun stop() = safely {
        handler.removeCallbacks(heartbeat)
        if (active) record("session-stop counts(events/edges): ${sampler.summary()}")
        active = false
        activity.clear()
        logger.close()
    }

    private fun record(message: String) {
        if ((file?.length() ?: 0) >= MAX_BYTES) {
            logger.close()
            rotate()
            logger.configure(file!!.parentFile!!, enabled = true)
        }
        logger.i("TouchTrace", "session=$session $message")
    }

    private fun rotate() {
        val current = file ?: return
        if (!current.exists()) return
        val previous = File(current.parentFile, "$FILE_NAME.previous")
        check(!previous.exists() || previous.delete()) { "Cannot remove previous touch diagnostic file" }
        check(current.renameTo(previous)) { "Cannot rotate touch diagnostic file" }
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Observability must never consume, alter or throw from the input path.
            active = false
            handler.removeCallbacks(heartbeat)
            runCatching { logger.close() }
            if (!reportedFailure) {
                reportedFailure = true
                AppLog.w("TouchTrace", "Touch diagnostic collection failed: ${e.message}")
            }
        }
    }

    companion object {
        const val FILE_NAME = "touch-android.log"
        private const val MAX_BYTES = 1024L * 1024L
    }
}
