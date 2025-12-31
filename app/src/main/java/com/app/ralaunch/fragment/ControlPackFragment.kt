package com.app.ralaunch.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.app.ralaunch.R
import com.app.ralaunch.controls.packs.ControlPackItem
import com.app.ralaunch.controls.packs.ui.ControlPackScreen
import com.app.ralaunch.controls.packs.ui.ControlPackTheme
import com.app.ralaunch.controls.packs.ui.ControlPackViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 控件包下载页面 Fragment (Compose)
 * 
 * 功能：
 * - 显示远程仓库的控件包列表
 * - 支持搜索、筛选
 * - 下载、安装、更新、删除控件包
 * - 将控件包应用到布局管理器
 */
class ControlPackFragment : Fragment() {

    private lateinit var viewModel: ControlPackViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(
            this, 
            ControlPackViewModel.Factory(requireContext())
        )[ControlPackViewModel::class.java]
    }

    private var backListener: OnControlPackBackListener? = null

    interface OnControlPackBackListener {
        fun onControlPackBack()
    }

    fun setOnControlPackBackListener(listener: OnControlPackBackListener?) {
        this.backListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_control_pack, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val composeView = view.findViewById<ComposeView>(R.id.composeView)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            ControlPackTheme {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val uiState by viewModel.uiState.collectAsState()

                    ControlPackScreen(
                        uiState = uiState,
                        onBackClick = { backListener?.onControlPackBack() },
                        onRefresh = { viewModel.loadPacks(forceRefresh = true) },
                        onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                        onPackClick = { showPackDetailDialog(it) },
                        onDownloadClick = { viewModel.downloadPack(it) },
                        onUpdateClick = { viewModel.downloadPack(it) },
                        onApplyClick = { applyPack(it) },
                        onDeleteClick = { confirmDeletePack(it) }
                    )
                }
            }
        }
    }

    private fun showPackDetailDialog(item: ControlPackItem) {
        val info = item.info
        val message = buildString {
            appendLine("${getString(R.string.pack_author_label)}: ${info.author}")
            appendLine("${getString(R.string.pack_version_label)}: ${info.version}")
            if (item.installedVersion != null) {
                appendLine("${getString(R.string.pack_installed_version)}: ${item.installedVersion}")
            }
            appendLine()
            appendLine(info.description)
            if (info.tags.isNotEmpty()) {
                appendLine()
                appendLine("${getString(R.string.pack_tags_label)}: ${info.tags.joinToString(", ")}")
            }
            if (info.fileSize > 0) {
                appendLine("${getString(R.string.pack_size_label)}: ${formatFileSize(info.fileSize)}")
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(info.name)
            .setMessage(message)
            .setPositiveButton(getString(R.string.close), null)
            .show()
    }

    private fun applyPack(item: ControlPackItem) {
        val success = viewModel.applyPack(item)
        if (success) {
            Toast.makeText(
                context,
                getString(R.string.pack_applied_success, item.info.name),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                getString(R.string.pack_apply_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmDeletePack(item: ControlPackItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.pack_delete_title))
            .setMessage(getString(R.string.pack_delete_confirm, item.info.name))
            .setPositiveButton(getString(R.string.control_delete)) { _, _ ->
                deletePack(item)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deletePack(item: ControlPackItem) {
        val success = viewModel.deletePack(item)
        if (success) {
            Toast.makeText(
                context,
                getString(R.string.pack_deleted_success, item.info.name),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                getString(R.string.pack_delete_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
