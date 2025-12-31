package com.app.ralaunch.fragment

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.adapter.ControlLayoutAdapter
import com.app.ralaunch.adapter.ControlLayoutAdapter.OnLayoutClickListener
import com.app.ralaunch.controls.packs.ControlLayout
import com.app.ralaunch.controls.packs.ControlPackInfo
import com.app.ralaunch.controls.packs.ControlPackManager
import com.app.ralaunch.controls.editors.ControlEditorActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * 控制布局管理Fragment
 *
 * 提供控制布局的管理界面（Material 3 风格）：
 * - 显示所有保存的控制布局
 * - 创建新的控制布局
 * - 编辑、重命名、复制布局
 * - 设置默认布局
 * - 导出和删除布局
 * - 跳转到布局编辑器
 *
 * 使用 ControlPackManager 管理布局数据
 */
class ControlLayoutFragment : Fragment(), OnLayoutClickListener {
    
    private val packManager: ControlPackManager
        get() = RaLaunchApplication.getControlPackManager()
    
    private var layouts: List<ControlPackInfo> = emptyList()
    private var adapter: ControlLayoutAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var emptyState: LinearLayout? = null
    private var mExportingPackId: String? = null

    private var backListener: OnControlLayoutBackListener? = null

    interface OnControlLayoutBackListener {
        fun onControlLayoutBack()
    }

    fun setOnControlLayoutBackListener(listener: OnControlLayoutBackListener?) {
        this.backListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_control_layout, container, false)

        initUI(view)
        loadLayouts()
        setupRecyclerView()

        return view
    }

    private var storeListener: OnControlStoreListener? = null

    interface OnControlStoreListener {
        fun onOpenControlStore()
    }

    fun setOnControlStoreListener(listener: OnControlStoreListener?) {
        this.storeListener = listener
    }

    private fun initUI(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val fabAddLayout = view.findViewById<ExtendedFloatingActionButton>(R.id.fabAddLayout)
        val btnImportLayout = view.findViewById<MaterialButton>(R.id.btnImportLayout)
        val btnImportPreset = view.findViewById<MaterialButton>(R.id.btnImportPreset)
        val btnControlStore = view.findViewById<MaterialButton>(R.id.btnControlStore)
        emptyState = view.findViewById<LinearLayout>(R.id.emptyState)

        toolbar.setNavigationOnClickListener { v: View? ->
            if (backListener != null) {
                backListener!!.onControlLayoutBack()
            }
        }

        fabAddLayout.setOnClickListener { v: View? -> showAddLayoutDialog() }

        // 控件商店按钮
        btnControlStore?.setOnClickListener { 
            storeListener?.onOpenControlStore()
        }

        // 导入布局按钮
        btnImportLayout.setOnClickListener { v: View? -> importLayoutFromFile() }

        // 导入预设按钮
        btnImportPreset.setOnClickListener { v: View? -> showImportPresetDialog() }
    }

    private fun loadLayouts() {
        layouts = packManager.getInstalledPacks()
    }

    private fun setupRecyclerView() {
        adapter = ControlLayoutAdapter(layouts, this)
        adapter!!.setDefaultLayoutId(packManager.getSelectedPackId())
        recyclerView!!.setLayoutManager(LinearLayoutManager(context))
        recyclerView!!.setAdapter(adapter)
        
        // 在设置完适配器后更新空状态
        updateEmptyState()
    }

    private fun showAddLayoutDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_new_layout, null)
        val editText = dialogView.findViewById<EditText>(R.id.layout_name_edit)

        // 设置默认名称
        val defaultName = getString(R.string.control_new_layout)

        // 如果名称已存在，添加数字后缀
        var finalName = defaultName
        var counter = 1
        while (layoutExists(finalName)) {
            counter++
            finalName = "$defaultName $counter"
        }

        editText.setText(finalName)
        editText.selectAll()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.control_create_layout))
            .setView(dialogView)
            .setPositiveButton(
                getString(R.string.control_create)
            ) { dialog: DialogInterface?, which: Int ->
                val layoutName = editText.text.toString().trim()
                if (!layoutName.isEmpty()) {
                    createNewLayout(layoutName)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createNewLayout(name: String) {
        // 检查名称是否已存在
        if (layoutExists(name)) {
            Toast.makeText(
                context,
                getString(R.string.control_layout_name_exists),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val newPack = packManager.createPack(name)

        // 如果当前没有选中的布局，将新布局设为默认
        if (packManager.getSelectedPackId() == null) {
            packManager.setSelectedPackId(newPack.id)
        }

        loadLayouts()
        adapter!!.updateLayouts(layouts)
        adapter!!.setDefaultLayoutId(packManager.getSelectedPackId())
        updateEmptyState()

        // 打开编辑界面
        openLayoutEditor(newPack.id)
    }

    private fun openLayoutEditor(packId: String) {
        ControlEditorActivity.start(requireContext(), packId)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_EDIT_LAYOUT && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getBooleanExtra("return_to_main", false)) {
                // 编辑器请求返回主界面，关闭当前Fragment
                if (backListener != null) {
                    backListener!!.onControlLayoutBack()
                }
            }
        } else if (requestCode == REQUEST_CODE_EXPORT_LAYOUT && resultCode == Activity.RESULT_OK) {
            // 处理导出布局
            if (data != null && data.data != null && mExportingPackId != null) {
                exportLayoutToFile(data.data!!, mExportingPackId!!)
                mExportingPackId = null
            }
        } else if (requestCode == REQUEST_CODE_IMPORT_LAYOUT && resultCode == Activity.RESULT_OK) {
            // 处理导入布局
            if (data != null && data.data != null) {
                importLayoutFromUri(data.data!!)
            }
        }
    }

    /**
     * 将布局导出到文件
     */
    private fun exportLayoutToFile(uri: Uri, packId: String) {
        try {
            val layout = packManager.getPackLayout(packId)
            if (layout == null) {
                Toast.makeText(
                    context,
                    getString(R.string.control_export_failed_write),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            
            val json = layout.toJson()

            // 写入文件
            val outputStream = requireContext().contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                outputStream.write(json.toByteArray(StandardCharsets.UTF_8))
                outputStream.close()
                Toast.makeText(
                    context,
                    getString(R.string.control_export_success),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    getString(R.string.control_export_failed_write),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                getString(R.string.control_export_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // 从编辑器或控件商店返回时刷新列表
        refreshLayoutList()
    }
    
    /**
     * 刷新布局列表
     */
    fun refreshLayoutList() {
        loadLayouts()
        adapter?.updateLayouts(layouts)
        adapter?.setDefaultLayoutId(packManager.getSelectedPackId())
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (layouts.isEmpty()) {
            emptyState!!.visibility = View.VISIBLE
            recyclerView!!.visibility = View.GONE
        } else {
            emptyState!!.visibility = View.GONE
            recyclerView!!.visibility = View.VISIBLE
        }
    }

    override fun onLayoutClick(pack: ControlPackInfo) {
        openLayoutEditor(pack.id)
    }

    override fun onLayoutEdit(pack: ControlPackInfo) {
        openLayoutEditor(pack.id)
    }

    override fun onLayoutRename(pack: ControlPackInfo) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_new_layout, null)
        val editText = dialogView.findViewById<EditText>(R.id.layout_name_edit)
        editText.setText(pack.name)
        editText.selectAll()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.control_rename_layout))
            .setView(dialogView)
            .setPositiveButton(
                getString(R.string.ok)
            ) { dialog: DialogInterface?, which: Int ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != pack.name) {
                    // 检查名称是否已存在
                    if (layoutExists(newName)) {
                        Toast.makeText(
                            context,
                            getString(R.string.control_layout_name_exists),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }
                    
                    packManager.renamePack(pack.id, newName)
                    loadLayouts()
                    adapter!!.updateLayouts(layouts)
                    adapter!!.setDefaultLayoutId(packManager.getSelectedPackId())
                    Toast.makeText(
                        context,
                        getString(R.string.control_layout_renamed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onLayoutDuplicate(pack: ControlPackInfo) {
        var newName = pack.name + " " + getString(R.string.control_layout_copy_suffix)
        var counter = 1
        while (layoutExists(newName)) {
            counter++
            newName = pack.name + " " + getString(R.string.control_layout_copy_suffix_numbered, counter)
        }

        packManager.duplicatePack(pack.id, newName)
        loadLayouts()
        adapter!!.updateLayouts(layouts)
        adapter!!.setDefaultLayoutId(packManager.getSelectedPackId())
        updateEmptyState()
        Toast.makeText(
            context,
            getString(R.string.control_layout_duplicated),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onLayoutSetDefault(pack: ControlPackInfo) {
        packManager.setSelectedPackId(pack.id)
        adapter!!.setDefaultLayoutId(pack.id)
        Toast.makeText(context, getString(R.string.control_set_as_default), Toast.LENGTH_SHORT)
            .show()
    }

    override fun onLayoutExport(pack: ControlPackInfo) {
        try {
            mExportingPackId = pack.id

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/json"
            intent.putExtra(Intent.EXTRA_TITLE, pack.name + ".json")
            startActivityForResult(intent, REQUEST_CODE_EXPORT_LAYOUT)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                getString(R.string.control_export_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onLayoutDelete(pack: ControlPackInfo) {
        val layoutName = pack.name

        // 检查是否是默认布局，给出警告但允许删除
        val isDefaultLayout = getString(R.string.control_layout_keyboard_mode) == layoutName ||
                getString(R.string.control_layout_gamepad_mode) == layoutName
        val message = if (isDefaultLayout)
            getString(R.string.control_delete_default_layout_confirm, layoutName)
        else
            getString(R.string.control_delete_layout_confirm, layoutName)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.control_delete_layout))
            .setMessage(message)
            .setPositiveButton(
                getString(R.string.control_delete)
            ) { _, _ ->
                val deleted = packManager.deletePack(pack.id)

                if (deleted) {
                    Toast.makeText(
                        context,
                        getString(R.string.control_layout_deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        getString(R.string.control_export_failed_write),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                loadLayouts()
                adapter!!.updateLayouts(layouts)
                adapter!!.setDefaultLayoutId(packManager.getSelectedPackId())
                updateEmptyState()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun layoutExists(name: String?): Boolean {
        for (layout in layouts) {
            if (layout.name == name) {
                return true
            }
        }
        return false
    }

    /**
     * 导入布局（从文件选择器）
     */
    private fun importLayoutFromFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/json"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json"))
        startActivityForResult(intent, REQUEST_CODE_IMPORT_LAYOUT)
    }

    /**
     * 从URI导入布局
     */
    private fun importLayoutFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(
                    context,
                    getString(R.string.control_cannot_read_file),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonBuilder = StringBuilder()
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                jsonBuilder.append(line).append("\n")
            }
            reader.close()
            inputStream.close()

            val json = jsonBuilder.toString()
            
            // 解析 JSON 配置
            val layout = ControlLayout.loadFromJson(json)

            if (layout == null || layout.controls.isEmpty()) {
                Toast.makeText(
                    context,
                    getString(R.string.control_layout_file_invalid),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // 生成唯一的布局名称
            var layoutName = layout.name
            var counter = 1
            while (layoutExists(layoutName)) {
                counter++
                layoutName = layout.name + " " + counter
            }

            // 导入为新的控件包
            val result = packManager.importFromJsonString(json, layoutName)
            
            if (result.isFailure) {
                Toast.makeText(
                    context,
                    getString(R.string.control_import_failed, result.exceptionOrNull()?.message),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            
            val newPack = result.getOrNull()!!

            // 如果当前没有默认布局，将导入的布局设为默认
            if (packManager.getSelectedPackId() == null) {
                packManager.setSelectedPackId(newPack.id)
            }

            loadLayouts()
            adapter!!.updateLayouts(layouts)
            adapter!!.setDefaultLayoutId(packManager.getSelectedPackId())
            updateEmptyState()

            Toast.makeText(
                context,
                getString(R.string.control_import_success, layoutName),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                getString(R.string.control_import_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 显示导入预设配置对话框
     */
    private fun showImportPresetDialog() {
        val presetNames = arrayOf<String?>(
            getString(R.string.control_layout_preset_keyboard),
            getString(R.string.control_layout_preset_gamepad)
        )
        val presetFiles = arrayOf<String?>("default_layout.json", "gamepad_layout.json")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.control_select_preset))
            .setItems(
                presetNames
            ) { dialog: DialogInterface?, which: Int ->
                importPresetLayout(
                    presetFiles[which],
                    presetNames[which]!!
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * 导入预设配置文件
     */
    private fun importPresetLayout(fileName: String?, presetName: String) {
        try {
            // 从 assets 读取配置文件
            val inputStream = requireContext().assets.open("controls/$fileName")
            val buffer = ByteArray(inputStream.available())
            val bytesRead = inputStream.read(buffer)
            inputStream.close()

            if (bytesRead <= 0) {
                Toast.makeText(
                    context,
                    getString(R.string.control_preset_file_invalid),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val json = String(buffer, StandardCharsets.UTF_8)

            // 解析 JSON 配置
            val layout = ControlLayout.loadFromJson(json)

            if (layout == null || layout.controls.isEmpty()) {
                Toast.makeText(
                    context,
                    getString(R.string.control_preset_file_invalid),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // 生成唯一的布局名称
            var layoutName = presetName
            var counter = 1
            while (layoutExists(layoutName)) {
                counter++
                layoutName = "$presetName $counter"
            }

            // 导入为新的控件包
            val result = packManager.importFromJsonString(json, layoutName)
            
            if (result.isFailure) {
                Toast.makeText(
                    context,
                    getString(R.string.control_import_failed, result.exceptionOrNull()?.message),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            
            val newPack = result.getOrNull()!!

            // 如果当前没有默认布局，将导入的布局设为默认
            if (packManager.getSelectedPackId() == null) {
                packManager.setSelectedPackId(newPack.id)
            }

            loadLayouts()
            adapter!!.updateLayouts(layouts)
            adapter!!.setDefaultLayoutId(packManager.getSelectedPackId())
            updateEmptyState()

            Toast.makeText(
                context,
                getString(R.string.control_preset_imported, layoutName),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                getString(R.string.control_import_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val REQUEST_CODE_EDIT_LAYOUT = 1001
        private const val REQUEST_CODE_EXPORT_LAYOUT = 1002
        private const val REQUEST_CODE_IMPORT_LAYOUT = 1003
    }
}
