package com.app.ralaunch.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.app.ralaunch.R
import com.app.ralaunch.activity.MainActivity
import com.app.ralaunch.core.AssemblyPatcher
import com.app.ralaunch.installer.GameInstaller
import com.app.ralaunch.installer.InstallCallback
import com.app.ralaunch.model.GameItem
import com.app.ralaunch.utils.AppLogger
import com.app.ralib.error.ErrorHandler
import com.app.ralib.extractors.GogShFileExtractor
import com.app.ralib.icon.IconExtractor
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * GOG 游戏导入 Fragment
 * 使用 Python 脚本处理安装
 */
class LocalImportFragment : BaseFragment() {

    private var importCompleteListener: OnImportCompleteListener? = null
    private var backListener: OnBackListener? = null

    // UI 控件
    private var selectGameFileButton: Button? = null
    private var startImportButton: Button? = null
    private var importProgressContainer: LinearLayout? = null
    private var progressIndicator: com.google.android.material.progressindicator.LinearProgressIndicator? = null
    private var statusText: TextView? = null
    private var progressText: TextView? = null
    private var detailText: TextView? = null
    private var progressInfoText: TextView? = null
    private var gameFileText: TextView? = null

    // 游戏数据
    private var gameFilePath: String? = null
    private var gameName: String? = null
    private var gameVersion: String? = null
    private var gameIconPath: String? = null
    private val engineType = "FNA"
    private var isImporting = false
    private var gameDir: File? = null

    interface OnImportCompleteListener {
        fun onImportComplete(gameType: String, newGame: GameItem)
    }

    interface OnBackListener {
        fun onBack()
    }

    fun setOnImportCompleteListener(listener: OnImportCompleteListener?) {
        importCompleteListener = listener
    }

    fun setOnBackListener(listener: OnBackListener?) {
        backListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_local_import, container, false)

        arguments?.let { args ->
            gameFilePath = args.getString("gameFilePath")
            gameName = args.getString("gameName")
            gameVersion = args.getString("gameVersion")
        }

        setupUI(view)

        if (!gameFilePath.isNullOrEmpty()) {
            view.post {
                selectGameFileButton?.visibility = View.GONE
                startImportButton?.visibility = View.GONE
                gameFileText?.visibility = View.GONE
                startImport()
            }
        }
        return view
    }

    private fun setupUI(view: View) {
        selectGameFileButton = view.findViewById(R.id.selectGameFileButton)
        startImportButton = view.findViewById(R.id.startImportButton)
        importProgressContainer = view.findViewById(R.id.importProgressContainer)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        statusText = view.findViewById(R.id.statusText)
        progressText = view.findViewById(R.id.progressText)
        detailText = view.findViewById(R.id.detailText)
        progressInfoText = view.findViewById(R.id.progressInfoText)
        gameFileText = view.findViewById(R.id.gameFileText)

        selectGameFileButton?.setOnClickListener { selectGameFile() }
        startImportButton?.setOnClickListener { startImport() }
        updateImportButtonState()
    }

    private fun updateProgress(message: String, progress: Int) {
        progressIndicator?.progress = progress
        statusText?.text = message
        progressText?.text = String.format("%d%%", progress)
        progressInfoText?.text = message
        detailText?.text = when (progress) {
            100 -> getString(R.string.init_complete)
            0 -> getString(R.string.init_start_extracting)
            else -> String.format("%.1f%%", progress.toFloat())
        }
    }

    private fun selectGameFile() {
        openFileBrowser("game", arrayOf(".sh", ".zip")) { path ->
            gameFilePath = path
            val file = File(path)
            activity?.runOnUiThread {
                if (isFragmentValid) {
                    gameFileText?.text = getString(R.string.import_file_selected, file.name)
                    updateImportButtonState()
                }
            }
            // 如果是 GOG .sh 文件，解析游戏信息
            if (path.endsWith(".sh", ignoreCase = true)) {
                Thread {
                    val gdzf = GogShFileExtractor.GameDataZipFile.parseFromGogShFile(Paths.get(path))
                    activity?.runOnUiThread {
                        if (isFragmentValid) {
                            if (gdzf != null) {
                                gameName = gdzf.id
                                gameVersion = gdzf.version
                                showToast(getString(R.string.import_game_detected, gameName, gameVersion))
                            }
                        }
                    }
                }.start()
            }
        }
    }

    private fun openFileBrowser(type: String?, exts: Array<String>?, cb: (String) -> Unit) {
        val mainActivity = mainActivity ?: return
        val fileBrowserFragment = FileBrowserFragment()
        if (type != null && exts != null) {
            fileBrowserFragment.setFileType(type, exts)
        }
        fileBrowserFragment.setOnFileSelectedListener({ filePath, _ ->
            cb(filePath)
            mainActivity.onFragmentBack()
        })
        fileBrowserFragment.setOnBackListener { mainActivity.onFragmentBack() }
        mainActivity.fragmentNavigator.showFragment(fileBrowserFragment, "file_browser")
    }

    private val mainActivity: MainActivity?
        get() = if (isAdded && activity is MainActivity) activity as MainActivity else null

    private fun updateImportButtonState() {
        val hasGameFile = !gameFilePath.isNullOrEmpty()
        startImportButton?.isEnabled = hasGameFile
        startImportButton?.alpha = if (hasGameFile) 1.0f else 0.5f
    }

    private fun startImport() {
        if (gameFilePath.isNullOrEmpty()) {
            showToast(getString(R.string.import_select_game_first))
            return
        }
        if (isImporting) return
        isImporting = true

        importProgressContainer?.visibility = View.VISIBLE
        startImportButton?.isEnabled = false

        // 如果没有游戏名，从文件名提取
        if (gameName.isNullOrEmpty()) {
            gameName = File(gameFilePath!!).nameWithoutExtension
        }

        gameDir = createGameDirectory(gameName)
        copyIconToGameDirIfNeeded()


        val ctx = context ?: return
        val installer = GameInstaller(ctx)
        
        installer.install(
            gameFilePath = gameFilePath!!,
            gameName = gameName,
            callback = object : InstallCallback {
                override fun onProgress(message: String, progress: Int) {
                    activity?.runOnUiThread { updateProgress(message, progress) }
                }

                override fun onComplete(
                    gamePath: String,
                    gameBasePath: String,
                    installedGameName: String,
                    launchTarget: String?,
                    iconPath: String?
                ) {
                    activity?.runOnUiThread {
                        updateProgress(getString(R.string.import_applying_patches), 98)
                    }
                    
                    // 在后台线程应用 MonoMod 补丁（仅对 .NET 游戏）
                    Thread {
                        val ctx = context
                        // 只对 .NET 游戏（.dll/.exe）应用 MonoMod 补丁
                        // Box64 原生 Linux 游戏不需要 MonoMod
                        val needsMonoMod = launchTarget?.lowercase()?.let {
                            it.endsWith(".dll") || it.endsWith(".exe")
                        } ?: false
                        
                        if (ctx != null && needsMonoMod) {
                            // 应用 MonoMod 补丁到新安装的游戏
                            AssemblyPatcher.applyMonoModPatches(ctx, gamePath, true)
                        }
                        
                        activity?.runOnUiThread {
                            isImporting = false
                            updateProgress(getString(R.string.import_complete_exclamation), 100)

                            // 根据启动目标判断运行时类型
                            val runtimeType = if (launchTarget?.lowercase()?.let { 
                                it.endsWith(".dll") || it.endsWith(".exe") 
                            } == true) "dotnet" else "box64"

                            val newGame = GameItem()
                            newGame.gameBasePath = gameBasePath
                            newGame.gameName = installedGameName
                            newGame.gamePath = gamePath
                            newGame.gameBodyPath = launchTarget ?: findGameBodyPath(gamePath)
                            newGame.engineType = engineType
                            newGame.runtime = runtimeType

                            val extractedIconPath = iconPath ?: extractIconFromExecutable(gamePath, gameIconPath)
                            newGame.iconPath = extractedIconPath

                            importCompleteListener?.onImportComplete("game", newGame)
                        }
                    }.start()
                }

                override fun onError(error: String) {
                    activity?.runOnUiThread {
                        isImporting = false
                        updateProgress(getString(R.string.import_failed_colon, error), 0)
                        ErrorHandler.showWarning(getString(R.string.import_error, ""), error)
                        startImportButton?.isEnabled = true
                    }
                }

                override fun onCancelled() {
                    activity?.runOnUiThread {
                        isImporting = false
                        updateProgress(getString(R.string.import_failed_colon, getString(R.string.cancel)), 0)
                        startImportButton?.isEnabled = true
                    }
                }
            }
        )
    }

    private fun findGameBodyPath(gamePath: String?): String? {
        if (gamePath.isNullOrEmpty()) return null
        return File(gamePath).absolutePath
    }

    private fun createGameDirectory(baseName: String?): File {
        val externalDir = MainActivity.mainActivity.getExternalFilesDir(null)
        val gamesDir = File(externalDir, "games")
        if (!gamesDir.exists()) {
            gamesDir.mkdirs()
        }

        var dirName = baseName ?: "Unknown"
        if (!gameVersion.isNullOrEmpty()) {
            dirName += "_$gameVersion"
        }
        dirName += "_${System.currentTimeMillis()}"

        val dir = File(gamesDir, dirName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun copyIconToGameDirIfNeeded() {
        if (gameIconPath == null) return
        val iconSrc = Paths.get(gameIconPath)
        if (Files.exists(iconSrc) && Files.isRegularFile(iconSrc)) {
            try {
                val iconDest = Paths.get(gameDir!!.absolutePath, "icon.png")
                Files.copy(iconSrc, iconDest, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                gameIconPath = iconDest.toAbsolutePath().toString()
            } catch (e: Exception) {
                AppLogger.error("LocalImportFragment", "Failed to copy icon", e)
            }
        }
    }

    private fun extractIconFromExecutable(exePath: String?, fallbackIconPath: String?): String? {
        if (exePath.isNullOrEmpty()) return fallbackIconPath
        val exeFile = File(exePath)
        if (!exeFile.exists()) return fallbackIconPath

        var tryPath = exePath
        if (exePath.lowercase().endsWith(".dll")) {
            val gameDir = exeFile.parentFile
            val baseName = exeFile.name.substring(0, exeFile.name.length - 4)
            val winExe = File(gameDir, "$baseName.exe")
            if (winExe.exists()) {
                tryPath = winExe.absolutePath
            }
        }

        return try {
            val gameFile = File(tryPath)
            if (!gameFile.exists()) return fallbackIconPath
            val nameWithoutExt = gameFile.name.replace("\\.[^.]+$".toRegex(), "")
            val iconPath = gameFile.parent + File.separator + "${nameWithoutExt}_icon.png"
            val success = IconExtractor.extractIconToPng(tryPath, iconPath)
            val extractedPath = if (success && File(iconPath).length() > 0) iconPath else null

            if (extractedPath != null && File(extractedPath).exists()) {
                val iconFile = File(extractedPath)
                if (iconFile.length() < 5 * 1024) {
                    val upscaledPath = IconExtractor.upscaleIcon(context, extractedPath)
                    upscaledPath ?: fallbackIconPath
                } else {
                    extractedPath
                }
            } else {
                fallbackIconPath
            }
        } catch (e: Exception) {
            AppLogger.error("LocalImportFragment", "Failed to extract icon from executable: ${e.message}", e)
            fallbackIconPath
        }
    }
}
