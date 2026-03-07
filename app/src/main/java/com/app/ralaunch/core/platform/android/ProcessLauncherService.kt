package com.app.ralaunch.core.platform.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.app.ralaunch.R
import com.app.ralaunch.core.platform.runtime.GameLauncher
import com.app.ralaunch.feature.patch.data.PatchManager
import com.app.ralaunch.core.common.util.NativeMethods
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.core.common.util.AppLogger
import java.io.File  // Thay java.nio.file.Paths

class ProcessLauncherService : Service() {

    companion object {
        private const val TAG = "ProcessLauncher"
        private const val CHANNEL_ID = "process_launcher_channel"
        private const val NOTIFICATION_ID = 9528
        private const val LAUNCHER_STACK_SIZE_BYTES = 8L * 1024 * 1024

        const val EXTRA_ASSEMBLY_PATH = "assembly_path"
        const val EXTRA_ARGS = "args"
        const val EXTRA_TITLE = "title"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_STDIN_INPUT = "stdin_input"
        const val ACTION_SEND_INPUT = "com.app.ralaunch.SEND_STDIN"

        @Volatile
        private var stdinPipeReady = false

        @JvmStatic
        fun launch(assemblyPath: String, args: Array<String>?, title: String?, gameId: String?) {
            val context: Context = KoinJavaComponent.get(Context::class.java)
            val intent = Intent(context, ProcessLauncherService::class.java).apply {
                putExtra(EXTRA_ASSEMBLY_PATH, assemblyPath)
                putExtra(EXTRA_ARGS, args)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_GAME_ID, gameId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun sendInput(context: Context, input: String) {
            val intent = Intent(context, ProcessLauncherService::class.java).apply {
                action = ACTION_SEND_INPUT
                putExtra(EXTRA_STDIN_INPUT, input)
            }
            context.startService(intent)
        }

        @JvmStatic
        fun stop(context: Context) {
            context.stopService(Intent(context, ProcessLauncherService::class.java))
        }
    }

    private var running = false
    private var launcherThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            AppLogger.error(TAG, "Intent is null")
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_SEND_INPUT) {
            val input = intent.getStringExtra(EXTRA_STDIN_INPUT) ?: return START_NOT_STICKY
            writeToStdin(input)
            return START_NOT_STICKY
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        startForeground(
            NOTIFICATION_ID,
            createNotification(getString(R.string.process_launcher_status_starting, title))
        )

        val assemblyPath = intent.getStringExtra(EXTRA_ASSEMBLY_PATH)
        val args = intent.getStringArrayExtra(EXTRA_ARGS)
        val gameId = intent.getStringExtra(EXTRA_GAME_ID)

        if (assemblyPath == null) {
            AppLogger.error(TAG, "Assembly path is null")
            stopSelf()
            return START_NOT_STICKY
        }

        launchAsync(assemblyPath, args, title, gameId)
        return START_STICKY
    }

    private fun launchAsync(assemblyPath: String, args: Array<String>?, title: String, gameId: String?) {
        if (running) return

        launcherThread = Thread(
            null,
            {
                try {
                    running = true
                    updateNotification(getString(R.string.process_launcher_status_running, title))
                    doLaunch(assemblyPath, args, title, gameId)
                } catch (e: Exception) {
                    AppLogger.error(TAG, "Launch error: ${e.message}", e)
                } finally {
                    running = false
                    stopSelf()
                }
            },
            "ProcessLauncher",
            LAUNCHER_STACK_SIZE_BYTES
        ).apply { start() }
    }

    private fun doLaunch(assemblyPath: String, args: Array<String>?, title: String, gameId: String?): Int {
        return try {
            setupStdinPipe()

            val patchManager: PatchManager? = try {
                KoinJavaComponent.getOrNull(PatchManager::class.java)
            } catch (e: Exception) { null }

            // Thay Paths.get(assemblyPath) bang File(assemblyPath)
            val patches = if (gameId != null) {
                patchManager?.getApplicableAndEnabledPatches(gameId, File(assemblyPath)) ?: emptyList()
            } else {
                emptyList()
            }

            AppLogger.info(TAG, "Game: $gameId, Applicable patches: ${patches.size}")
            GameLauncher.launchDotNetAssembly(assemblyPath, args ?: emptyArray(), patches)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Launch failed: ${e.message}", e)
            AppLogger.error(TAG, "Last Error Msg: ${GameLauncher.getLastErrorMessage()}")
            -1
        } finally {
            cleanupStdinPipe()
        }
    }

    private fun setupStdinPipe() {
        val writeFd = NativeMethods.setupStdinPipe()
        if (writeFd >= 0) {
            stdinPipeReady = true
            AppLogger.info(TAG, "stdin pipe ready (native write_fd=$writeFd)")
        } else {
            AppLogger.warn(TAG, "stdin pipe setup failed")
        }
    }

    private fun cleanupStdinPipe() {
        if (stdinPipeReady) {
            NativeMethods.closeStdinPipe()
            stdinPipeReady = false
        }
    }

    private fun writeToStdin(input: String) {
        if (!stdinPipeReady) {
            AppLogger.warn(TAG, "stdin pipe not ready, ignoring: $input")
            return
        }
        val result = NativeMethods.writeStdin(input)
        if (result >= 0) {
            AppLogger.info(TAG, "stdin << $input ($result bytes)")
        } else {
            AppLogger.error(TAG, "stdin write failed: $input")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        running = false
        launcherThread?.takeIf { it.isAlive }?.interrupt()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.process_launcher_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.process_launcher_channel_description)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.process_launcher_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_ral)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, createNotification(text))
    }
}
