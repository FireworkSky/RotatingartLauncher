package com.app.ralaunch.feature.init

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.app.ralaunch.R
import com.app.ralaunch.core.common.PermissionManager
import com.app.ralaunch.shared.core.platform.AppConstants
import com.app.ralaunch.shared.core.model.ui.ComponentState
import com.app.ralaunch.shared.core.theme.RaLaunchTheme
import com.app.ralaunch.feature.main.MainActivityCompose
import com.app.ralaunch.core.common.util.AppLogger
import com.app.ralaunch.core.common.util.ArchiveExtractor
import com.app.ralaunch.core.common.ErrorHandler
import com.app.ralaunch.core.platform.runtime.RuntimeLibraryLoader
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class InitializationActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var prefs: SharedPreferences

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isExtracting = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ FIX Android 7 crash (EdgeToEdge chỉ bật từ API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enableEdgeToEdge()
        }

        hideSystemUI()

        prefs = getSharedPreferences(AppConstants.PREFS_NAME, 0)

        val extracted =
            prefs.getBoolean(AppConstants.InitKeys.COMPONENTS_EXTRACTED, false)

        if (extracted) {
            navigateToMain()
            return
        }

        permissionManager = PermissionManager(this).apply { initialize() }

        setContent {
            RaLaunchTheme {
                InitializationScreen(
                    permissionManager = permissionManager,
                    onComplete = { navigateToMain() },
                    onExit = { finish() },
                    onExtract = { components, onUpdate ->
                        startExtraction(components, onUpdate)
                    },
                    prefs = prefs,
                    context = this
                )
            }
        }
    }

    // ✅ FIX Android 7 crash (WindowInsets chỉ chạy API 26+)
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller =
                WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivityCompose::class.java))
        finish()
    }

    private fun startExtraction(
        components: List<ComponentState>,
        onUpdate: (Int, Int, Boolean, String) -> Unit
    ) {
        if (isExtracting.getAndSet(true)) return

        executor.execute {
            try {
                extractAll(components, onUpdate)
                mainHandler.post {
                    prefs.edit()
                        .putBoolean(
                            AppConstants.InitKeys.COMPONENTS_EXTRACTED,
                            true
                        ).apply()

                    Toast.makeText(
                        this,
                        getString(R.string.init_dotnet_install_success),
                        Toast.LENGTH_SHORT
                    ).show()

                    mainHandler.postDelayed(
                        { navigateToMain() },
                        1000
                    )
                }
            } catch (e: Exception) {
                AppLogger.error("InitActivity", "Extraction failed", e)
                mainHandler.post {
                    ErrorHandler.handleError(
                        e.message
                            ?: getString(R.string.error_message_default),
                        e
                    )
                }
            } finally {
                isExtracting.set(false)
            }
        }
    }

    private fun extractAll(
        components: List<ComponentState>,
        onUpdate: (Int, Int, Boolean, String) -> Unit
    ) {
        val filesDir = filesDir

        components.forEachIndexed { index, component ->

            if (!component.needsExtraction) {
                mainHandler.post {
                    onUpdate(
                        index,
                        100,
                        true,
                        getString(R.string.init_no_extraction_needed)
                    )
                }
                return@forEachIndexed
            }

            mainHandler.post {
                onUpdate(
                    index,
                    10,
                    false,
                    getString(R.string.init_preparing_file)
                )
            }

            val tempFile =
                File(cacheDir, "temp_${component.fileName}")

            ArchiveExtractor.copyAssetToFile(
                this,
                component.fileName,
                tempFile
            )

            mainHandler.post {
                onUpdate(
                    index,
                    30,
                    false,
                    getString(R.string.init_extracting)
                )
            }

            val outputDir =
                File(filesDir, component.name).apply { mkdirs() }

            val stripPrefix =
                "${component.name.lowercase()}/"

            val callback =
                ArchiveExtractor.ProgressCallback { files, _ ->
                    val progress =
                        (40 + minOf(files / 10, 50))
                    mainHandler.post {
                        onUpdate(
                            index,
                            progress,
                            false,
                            getString(
                                R.string.init_extracting_files,
                                files
                            )
                        )
                    }
                }

            when {
                component.fileName.endsWith(".tar.xz") ->
                    ArchiveExtractor.extractTarXz(
                        tempFile,
                        outputDir,
                        stripPrefix,
                        callback
                    )

                component.fileName.endsWith(".tar.gz") ->
                    ArchiveExtractor.extractTarGz(
                        tempFile,
                        outputDir,
                        stripPrefix,
                        callback
                    )

                else ->
                    ArchiveExtractor.extractTar(
                        tempFile,
                        outputDir,
                        stripPrefix,
                        callback
                    )
            }

            tempFile.delete()

            mainHandler.post {
                onUpdate(
                    index,
                    100,
                    true,
                    getString(R.string.init_complete)
                )
            }
        }

        extractRuntimeLibsIfNeeded()
    }

    private fun extractRuntimeLibsIfNeeded() {
        try {
            val hasRuntimeLibs =
                assets.list("")?.contains("runtime_libs.tar.xz") == true

            if (hasRuntimeLibs &&
                !RuntimeLibraryLoader.isExtracted(this)
            ) {
                runBlocking {
                    RuntimeLibraryLoader.extractRuntimeLibs(
                        this@InitializationActivity
                    ) { _, _ -> }
                }
            }
        } catch (e: Exception) {
            AppLogger.error(
                "InitActivity",
                "Failed to extract runtime libs: ${e.message}"
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!executor.isShutdown)
            executor.shutdownNow()
    }
}
