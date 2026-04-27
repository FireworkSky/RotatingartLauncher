package com.app.ralaunch.feature.init.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.app.ralaunch.R
import com.app.ralaunch.core.di.service.PermissionManagerServiceV1
import com.app.ralaunch.core.platform.AppConstants
import com.app.ralaunch.core.theme.RaLaunchTheme
import com.app.ralaunch.feature.init.vm.InitializationEffect
import com.app.ralaunch.feature.init.vm.InitializationViewModel
import com.app.ralaunch.feature.main.ui.MainActivityCompose
import kotlinx.coroutines.delay
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 初始化 Activity
 * 独立的横屏初始化界面，完成后跳转主界面
 */
class InitializationActivity : ComponentActivity() {
    private val viewModel: InitializationViewModel by viewModel()

    private lateinit var permissionManager: PermissionManagerServiceV1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        // 检查是否已完成初始化
        val extracted = getSharedPreferences(AppConstants.PREFS_NAME, 0)
            .getBoolean(AppConstants.InitKeys.COMPONENTS_EXTRACTED, false)
        if (extracted) {
            navigateToMain()
            return
        }

        permissionManager = PermissionManagerServiceV1(this).apply { initialize() }
        viewModel.markPermissions(permissionManager.hasRequiredPermissions())

        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

            LaunchedEffect(uiState.isComplete) {
                if (uiState.isComplete) {
                    Toast.makeText(this@InitializationActivity, getString(R.string.init_dotnet_install_success), Toast.LENGTH_SHORT).show()
                    delay(1000)
                    navigateToMain()
                }
            }

            LaunchedEffect(Unit) {
                viewModel.effect.collect { effect ->
                    when (effect) {
                        is InitializationEffect.OpenUrl -> {
                            runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(effect.url)))
                            }.onFailure {
                                Toast.makeText(this@InitializationActivity, getString(R.string.init_cannot_open_browser), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            RaLaunchTheme {
                InitializationScreen(
                    uiState = uiState,
                    appVersionName = viewModel.appVersionName,
                    onAcceptLegal = viewModel::acceptLegal,
                    onExit = { finish() },
                    onOpenOfficialDownload = viewModel::openOfficialDownloadPage,
                    onRequestPermissions = {
                        if (permissionManager.hasRequiredPermissions()) {
                            viewModel.markPermissions(true)
                        } else {
                            permissionManager.requestRequiredPermissions(object : PermissionManagerServiceV1.PermissionCallback {
                                override fun onPermissionsGranted() {
                                    viewModel.markPermissions(true)
                                }

                                override fun onPermissionsDenied() {
                                    viewModel.markPermissions(false)
                                    Toast.makeText(
                                        this@InitializationActivity,
                                        getString(R.string.init_permissions_denied),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            })
                        }
                    },
                    onStartExtraction = viewModel::startExtraction
                )
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivityCompose::class.java))
        finish()
    }
}
