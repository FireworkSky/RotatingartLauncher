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
import java.nio.file.Paths

/**
 * 通用进程启动服务 - 在独立进程中启动 .NET 程序集
 */
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

        /** stdin 管道是否已建立 */
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

        /**
         * 向服务器进程的 stdin 发送输入
         */
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

        // 处理 stdin 输入
        if (intent.action == ACTION_SEND_INPUT) {
            val input = intent.getStringExtra(EXTRA_STDIN_INPUT) ?: return START_NOT_STICKY
            writeToStdin(input)
            return START_NOT_STICKY
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        startForeground(NOTIFICATION_ID, createNotification("$title 正在启动..."))

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
                    updateNotification("$title 正在运行")
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
            // 设置 stdin 管道：让 .NET Console.ReadLine() 可以读取我们写入的内容
            setupStdinPipe()

            val patchManager: PatchManager? = try {
                KoinJavaComponent.getOrNull(PatchManager::class.java)
            } catch (e: Exception) { null }
            val patches = if (gameId != null) patchManager?.getApplicableAndEnabledPatches(gameId, Paths.get(assemblyPath)) ?: emptyList() else emptyList()
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

    /**
     * 设置 stdin 管道（通过 native 层 pipe + dup2，确保 fd 0 在 .NET 初始化前就已重定向）
     */
    private fun setupStdinPipe() {
        val writeFd = NativeMethods.setupStdinPipe()
        if (writeFd >= 0) {
            stdinPipeReady = true
            AppLogger.info(TAG, "stdin 管道已建立 (native write_fd=$writeFd)")
        } else {
            AppLogger.warn(TAG, "建立 stdin 管道失败")
        }
    }

    private fun cleanupStdinPipe() {
        if (stdinPipeReady) {
            NativeMethods.closeStdinPipe()
            stdinPipeReady = false
        }
    }

    /**
     * 写入内容到 stdin 管道（通过 native write()）
     */
    private fun writeToStdin(input: String) {
        if (!stdinPipeReady) {
            AppLogger.warn(TAG, "stdin 管道未就绪，忽略输入: $input")
            return
        }
        val result = NativeMethods.writeStdin(input)
        if (result >= 0) {
            AppLogger.info(TAG, "stdin << $input ($result bytes)")
        } else {
            AppLogger.error(TAG, "写入 stdin 失败: $input")
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
            val channel = NotificationChannel(CHANNEL_ID, "进程启动器", NotificationManager.IMPORTANCE_LOW).apply {
                description = "后台进程运行通知"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("后台进程")
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
