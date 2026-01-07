package com.app.ralaunch.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.app.ralaunch.R
import com.app.ralaunch.activity.MainActivity
import com.app.ralaunch.model.GameItem
import com.app.ralaunch.game.AssemblyPatcher
import com.app.ralaunch.installer.GameInstaller
import com.app.ralaunch.installer.InstallCallback
import com.app.ralaunch.installer.InstallPluginRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 游戏导入界面
 * 支持导入游戏本体 + 模组加载器（tModLoader/SMAPI）
 */
class GameImportFragment : BaseFragment() {

    // region Listeners
    interface OnImportCompleteListener {
        fun onImportComplete(gameType: String, newGame: GameItem?)
    }

    interface OnBackListener {
        fun onBack()
    }

    private var importCompleteListener: OnImportCompleteListener? = null
    private var backListener: OnBackListener? = null

    fun setOnImportCompleteListener(listener: OnImportCompleteListener?) {
        importCompleteListener = listener
    }

    fun setOnBackListener(listener: OnBackListener?) {
        backListener = listener
    }
    // endregion

    // region State
    private var gameFilePath: String? = null
    private var gameName: String? = null
    private var modLoaderFilePath: String? = null
    private var modLoaderName: String? = null
    private var isImporting = false
    private var installer: GameInstaller? = null
    // endregion

    // region Views
    private var gameFileText: TextView? = null
    private var modLoaderFileText: TextView? = null
    private var detectResultCard: MaterialCardView? = null
    private var detectedGameName: TextView? = null
    private var startImportButton: MaterialButton? = null
    private var importProgressCard: MaterialCardView? = null
    private var importProgress: LinearProgressIndicator? = null
    private var importStatus: TextView? = null
    // endregion

    private val mainActivity: MainActivity?
        get() = if (isAdded && activity is MainActivity) activity as MainActivity else null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_game_import, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    private fun setupViews(view: View) {
        val selectGameFileButton = view.findViewById<MaterialButton>(R.id.selectGameFileButton)
        val selectModLoaderButton = view.findViewById<MaterialButton>(R.id.selectModLoaderButton)
        
        gameFileText = view.findViewById(R.id.gameFileText)
        modLoaderFileText = view.findViewById(R.id.modLoaderFileText)
        detectResultCard = view.findViewById(R.id.detectResultCard)
        detectedGameName = view.findViewById(R.id.detectedGameName)
        startImportButton = view.findViewById(R.id.startImportButton)
        importProgressCard = view.findViewById(R.id.importProgressCard)
        importProgress = view.findViewById(R.id.importProgress)
        importStatus = view.findViewById(R.id.importStatus)

        selectGameFileButton.setOnClickListener {
            selectFile("game", arrayOf(".sh")) { path ->
                gameFilePath = path
                val file = File(path)
                gameFileText?.text = file.name
                
                // 检测游戏类型
                detectGame(path)
                updateImportButtonState()
            }
        }

        selectModLoaderButton.setOnClickListener {
            selectFile("modloader", arrayOf(".zip")) { path ->
                modLoaderFilePath = path
                val file = File(path)
                modLoaderFileText?.text = file.name
                
                // 检测模组加载器
                detectModLoader(path)
                updateImportButtonState()
            }
        }

        startImportButton?.setOnClickListener {
            if (!isImporting) {
                startImport()
            }
        }
    }

    private fun selectFile(type: String, extensions: Array<String>, callback: (String) -> Unit) {
        val mainActivity = mainActivity ?: return
        val fileBrowserFragment = FileBrowserFragment()
        fileBrowserFragment.setFileType(type, extensions)
        fileBrowserFragment.setOnFileSelectedListener { filePath, _ ->
            callback(filePath)
            mainActivity.onFragmentBack()
        }
        fileBrowserFragment.setOnBackListener {
            mainActivity.onFragmentBack()
        }
        mainActivity.fragmentNavigator.showFragment(fileBrowserFragment, "file_browser")
    }

    private fun detectGame(filePath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val gameFile = File(filePath)
                val result = InstallPluginRegistry.detectGame(gameFile)
                
                withContext(Dispatchers.Main) {
                    if (result != null) {
                        gameName = result.second.gameName
                        detectResultCard?.visibility = View.VISIBLE
                        detectedGameName?.text = gameName
                    } else {
                        gameName = gameFile.nameWithoutExtension
                        detectResultCard?.visibility = View.VISIBLE
                        detectedGameName?.text = gameName
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    gameName = File(filePath).nameWithoutExtension
                    detectResultCard?.visibility = View.VISIBLE
                    detectedGameName?.text = gameName
                }
            }
        }
    }

    private fun detectModLoader(filePath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val modLoaderFile = File(filePath)
                val result = InstallPluginRegistry.detectModLoader(modLoaderFile)
                
                withContext(Dispatchers.Main) {
                    if (result != null) {
                        modLoaderName = result.second.name
                        // 更新检测结果显示模组加载器名称
                        detectResultCard?.visibility = View.VISIBLE
                        detectedGameName?.text = modLoaderName
                    } else {
                        modLoaderName = modLoaderFile.nameWithoutExtension
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    modLoaderName = File(filePath).nameWithoutExtension
                }
            }
        }
    }

    private fun updateImportButtonState() {
        val hasGameFile = !gameFilePath.isNullOrEmpty()
        val hasModLoader = !modLoaderFilePath.isNullOrEmpty()
        
        // 至少需要选择模组加载器文件
        startImportButton?.isEnabled = hasModLoader
    }

    private fun startImport() {
        if (modLoaderFilePath.isNullOrEmpty()) {
            showToast(getString(R.string.import_select_mod_loader_first))
            return
        }

        isImporting = true
        startImportButton?.isEnabled = false
        importProgressCard?.visibility = View.VISIBLE

        val ctx = context ?: return
        installer = GameInstaller(ctx)

        installer?.install(
            gameFilePath = gameFilePath ?: modLoaderFilePath!!,
            modLoaderFilePath = if (gameFilePath != null) modLoaderFilePath else null,
            gameName = modLoaderName ?: gameName,
            callback = object : InstallCallback {
                override fun onProgress(message: String, progress: Int) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        importStatus?.text = message
                        importProgress?.progress = progress
                    }
                }

                override fun onComplete(
                    gamePath: String,
                    gameBasePath: String,
                    installedGameName: String,
                    launchTarget: String?,
                    iconPath: String?
                ) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        isImporting = false
                        importStatus?.text = getString(R.string.import_applying_patches)
                        importProgress?.progress = 98
                    }
                    
                    // 在后台线程应用 MonoMod 补丁
                    lifecycleScope.launch(Dispatchers.IO) {
                        val ctx = context
                        if (ctx != null) {
                            // 应用 MonoMod 补丁到新安装的游戏
                            AssemblyPatcher.applyMonoModPatches(ctx, gamePath, true)
                        }
                        
                        withContext(Dispatchers.Main) {
                        importStatus?.text = getString(R.string.import_complete_exclamation)
                        importProgress?.progress = 100

                            // gamePath 是游戏目录，launchTarget 是启动目标文件名
                            // gamePath 字段需要完整的程序集路径
                            val assemblyPath = if (!launchTarget.isNullOrEmpty()) {
                                File(gamePath, launchTarget).absolutePath
                            } else {
                                gamePath
                            }

                        val gameItem = GameItem().apply {
                            setGameName(installedGameName)
                                setGameBasePath(gameBasePath)  // 使用根安装目录，用于删除
                                setGamePath(assemblyPath)
                                setGameBodyPath(if (!launchTarget.isNullOrEmpty()) File(gamePath, launchTarget).absolutePath else null)
                            setIconPath(iconPath)
                        }

                        importCompleteListener?.onImportComplete("game", gameItem)
                        }
                    }
                }

                override fun onError(error: String) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        isImporting = false
                        startImportButton?.isEnabled = true
                        importProgressCard?.visibility = View.GONE
                        showToast(error)
                    }
                }

                override fun onCancelled() {
                    lifecycleScope.launch(Dispatchers.Main) {
                        isImporting = false
                        startImportButton?.isEnabled = true
                        importProgressCard?.visibility = View.GONE
                        showToast(getString(R.string.import_cancelled))
                    }
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isImporting) {
            installer?.cancel()
        }
        gameFileText = null
        modLoaderFileText = null
        detectResultCard = null
        detectedGameName = null
        startImportButton = null
        importProgressCard = null
        importProgress = null
        importStatus = null
    }
}
