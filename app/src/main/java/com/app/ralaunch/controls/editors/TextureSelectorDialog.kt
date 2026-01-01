package com.app.ralaunch.controls.editors

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.editors.managers.ControlTextureManager
import com.app.ralaunch.controls.editors.managers.ControlTextureManager.TextureFileInfo
import com.app.ralaunch.controls.textures.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 纹理选择对话框
 * 
 * 用于在编辑器中选择和配置控件纹理
 */
class TextureSelectorDialog : DialogFragment() {
    
    companion object {
        private const val TAG = "TextureSelectorDialog"
        private const val ARG_PACK_ID = "pack_id"
        
        // 支持的纹理文件 MIME 类型
        private val SUPPORTED_MIME_TYPES = arrayOf(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/bmp",
            "image/svg+xml"
        )
        
        fun newInstance(packId: String): TextureSelectorDialog {
            return TextureSelectorDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_PACK_ID, packId)
                }
            }
        }
    }
    
    private var packId: String? = null
    private var currentData: ControlData? = null
    private var listener: OnTextureConfiguredListener? = null
    
    private var tabLayout: TabLayout? = null
    private var recyclerView: RecyclerView? = null
    private var emptyView: LinearLayout? = null
    private var btnClearTexture: MaterialButton? = null
    private var btnImportTexture: MaterialButton? = null
    private var btnImportTextureEmpty: MaterialButton? = null
    private var btnClose: MaterialButton? = null
    
    private var textureFiles: List<TextureFileInfo> = emptyList()
    private var adapter: TextureAdapter? = null
    
    // 当前选择的纹理槽位
    private var currentSlot: TextureSlot = TextureSlot.BUTTON_NORMAL
    
    // 文件选择器
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    
    enum class TextureSlot {
        BUTTON_NORMAL,
        BUTTON_PRESSED,
        BUTTON_TOGGLED,
        JOYSTICK_BACKGROUND,
        JOYSTICK_KNOB,
        TOUCHPAD_BACKGROUND,
        TEXT_BACKGROUND
    }
    
    interface OnTextureConfiguredListener {
        fun onTextureConfigured(data: ControlData?)
    }
    
    fun setControlData(data: ControlData?) {
        this.currentData = data
    }
    
    fun setOnTextureConfiguredListener(listener: OnTextureConfiguredListener?) {
        this.listener = listener
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
        packId = arguments?.getString(ARG_PACK_ID)
        
        // 注册文件选择器
        filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.clipData?.let { clipData ->
                    // 多选文件
                    for (i in 0 until clipData.itemCount) {
                        importTextureFromUri(clipData.getItemAt(i).uri)
                    }
                } ?: result.data?.data?.let { uri ->
                    // 单选文件
                    importTextureFromUri(uri)
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // 设置对话框大小
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_texture_selector, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        loadTextures()
        setupTabs()
        setupListeners()
    }
    
    private fun initViews(view: View) {
        tabLayout = view.findViewById(R.id.tab_texture_slots)
        recyclerView = view.findViewById(R.id.recycler_textures)
        emptyView = view.findViewById(R.id.empty_view)
        btnClearTexture = view.findViewById(R.id.btn_clear_texture)
        btnImportTexture = view.findViewById(R.id.btn_import_texture)
        btnImportTextureEmpty = view.findViewById(R.id.btn_import_texture_empty)
        btnClose = view.findViewById(R.id.btn_close)
        
        // 设置 RecyclerView
        recyclerView?.layoutManager = GridLayoutManager(context, 3)
        adapter = TextureAdapter { textureInfo ->
            onTextureSelected(textureInfo)
        }
        recyclerView?.adapter = adapter
    }
    
    private fun loadTextures() {
        textureFiles = ControlTextureManager.getAvailableTextures(packId)
        adapter?.submitList(textureFiles)
        
        updateEmptyState()
    }
    
    private fun updateEmptyState() {
        if (textureFiles.isEmpty()) {
            recyclerView?.visibility = View.GONE
            emptyView?.visibility = View.VISIBLE
        } else {
            recyclerView?.visibility = View.VISIBLE
            emptyView?.visibility = View.GONE
        }
    }
    
    private fun setupTabs() {
        tabLayout?.removeAllTabs()
        
        when (currentData) {
            is ControlData.Button -> {
                tabLayout?.addTab(tabLayout!!.newTab().setText(R.string.control_texture_normal))
                tabLayout?.addTab(tabLayout!!.newTab().setText(R.string.control_texture_pressed))
                if ((currentData as ControlData.Button).isToggle) {
                    tabLayout?.addTab(tabLayout!!.newTab().setText(R.string.control_texture_toggled))
                }
                currentSlot = TextureSlot.BUTTON_NORMAL
            }
            is ControlData.Joystick -> {
                tabLayout?.addTab(tabLayout!!.newTab().setText(R.string.control_texture_background))
                tabLayout?.addTab(tabLayout!!.newTab().setText(R.string.control_texture_knob))
                currentSlot = TextureSlot.JOYSTICK_BACKGROUND
            }
            is ControlData.TouchPad -> {
                tabLayout?.addTab(tabLayout!!.newTab().setText(R.string.control_texture_background))
                currentSlot = TextureSlot.TOUCHPAD_BACKGROUND
            }
            is ControlData.Text -> {
                tabLayout?.addTab(tabLayout!!.newTab().setText(R.string.control_texture_background))
                currentSlot = TextureSlot.TEXT_BACKGROUND
            }
            else -> {}
        }
        
        tabLayout?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateCurrentSlot(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // 高亮已配置的纹理
        updateTabIndicators()
    }
    
    private fun updateCurrentSlot(position: Int) {
        currentSlot = when (currentData) {
            is ControlData.Button -> {
                when (position) {
                    0 -> TextureSlot.BUTTON_NORMAL
                    1 -> TextureSlot.BUTTON_PRESSED
                    2 -> TextureSlot.BUTTON_TOGGLED
                    else -> TextureSlot.BUTTON_NORMAL
                }
            }
            is ControlData.Joystick -> {
                when (position) {
                    0 -> TextureSlot.JOYSTICK_BACKGROUND
                    1 -> TextureSlot.JOYSTICK_KNOB
                    else -> TextureSlot.JOYSTICK_BACKGROUND
                }
            }
            is ControlData.TouchPad -> TextureSlot.TOUCHPAD_BACKGROUND
            is ControlData.Text -> TextureSlot.TEXT_BACKGROUND
            else -> TextureSlot.BUTTON_NORMAL
        }
    }
    
    private fun updateTabIndicators() {
        // 可以在这里更新 Tab 的文字或图标来显示是否已配置纹理
    }
    
    private fun setupListeners() {
        btnClose?.setOnClickListener {
            dismiss()
        }
        
        btnClearTexture?.setOnClickListener {
            clearCurrentSlotTexture()
        }
        
        // 导入纹理按钮
        btnImportTexture?.setOnClickListener {
            openFilePicker()
        }
        
        // 空状态时的导入按钮
        btnImportTextureEmpty?.setOnClickListener {
            openFilePicker()
        }
    }
    
    /**
     * 打开文件选择器
     */
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_MIME_TYPES)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.control_texture_import)))
    }
    
    /**
     * 从 Uri 导入纹理文件并直接应用到当前控件
     */
    private fun importTextureFromUri(uri: Uri) {
        val context = context ?: return
        val packId = this.packId ?: return
        
        try {
            // 获取文件名
            val fileName = getFileNameFromUri(context, uri) ?: "texture_${System.currentTimeMillis()}"
            
            // 检查文件扩展名
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension !in listOf("png", "jpg", "jpeg", "webp", "bmp", "svg")) {
                Toast.makeText(context, R.string.control_texture_unsupported_format, Toast.LENGTH_SHORT).show()
                return
            }
            
            // 获取或创建 assets 目录
            val assetsDir = RaLaunchApplication.getControlPackManager().getOrCreatePackAssetsDir(packId)
            if (assetsDir == null) {
                Toast.makeText(context, R.string.control_texture_import_failed, Toast.LENGTH_SHORT).show()
                return
            }
            
            // 目标文件
            var targetFile = File(assetsDir, fileName)
            
            // 如果文件已存在，添加数字后缀
            var counter = 1
            val nameWithoutExt = fileName.substringBeforeLast('.')
            val ext = fileName.substringAfterLast('.')
            while (targetFile.exists()) {
                targetFile = File(assetsDir, "${nameWithoutExt}_$counter.$ext")
                counter++
            }
            
            // 复制文件
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            Log.i(TAG, "Imported texture: ${targetFile.absolutePath}")
            
            // 创建纹理信息并直接应用到当前控件
            val textureInfo = TextureFileInfo(
                name = targetFile.name,
                relativePath = targetFile.relativeTo(assetsDir).path.replace('\\', '/'),
                absolutePath = targetFile.absolutePath,
                format = extension.uppercase(),
                fileSize = targetFile.length()
            )
            
            // 直接应用纹理到当前槽位
            onTextureSelected(textureInfo)
            
            // 刷新纹理列表（后台刷新，不影响用户体验）
            loadTextures()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import texture", e)
            Toast.makeText(context, R.string.control_texture_import_failed, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 从 Uri 获取文件名
     */
    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        
        return result
    }
    
    private fun onTextureSelected(textureInfo: TextureFileInfo) {
        val data = currentData ?: return
        
        when (currentSlot) {
            TextureSlot.BUTTON_NORMAL -> {
                if (data is ControlData.Button) {
                    ControlTextureManager.setButtonNormalTexture(data, textureInfo.relativePath)
                }
            }
            TextureSlot.BUTTON_PRESSED -> {
                if (data is ControlData.Button) {
                    ControlTextureManager.setButtonPressedTexture(data, textureInfo.relativePath)
                }
            }
            TextureSlot.BUTTON_TOGGLED -> {
                if (data is ControlData.Button) {
                    data.texture = data.texture.copy(
                        toggled = data.texture.toggled.copy(
                            path = textureInfo.relativePath,
                            enabled = true
                        )
                    )
                }
            }
            TextureSlot.JOYSTICK_BACKGROUND -> {
                if (data is ControlData.Joystick) {
                    ControlTextureManager.setJoystickBackgroundTexture(data, textureInfo.relativePath)
                }
            }
            TextureSlot.JOYSTICK_KNOB -> {
                if (data is ControlData.Joystick) {
                    ControlTextureManager.setJoystickKnobTexture(data, textureInfo.relativePath)
                }
            }
            TextureSlot.TOUCHPAD_BACKGROUND -> {
                if (data is ControlData.TouchPad) {
                    data.texture = data.texture.copy(
                        background = data.texture.background.copy(
                            path = textureInfo.relativePath,
                            enabled = true
                        )
                    )
                }
            }
            TextureSlot.TEXT_BACKGROUND -> {
                if (data is ControlData.Text) {
                    data.texture = data.texture.copy(
                        background = data.texture.background.copy(
                            path = textureInfo.relativePath,
                            enabled = true
                        )
                    )
                }
            }
        }
        
        listener?.onTextureConfigured(data)
        Toast.makeText(context, R.string.control_texture_applied, Toast.LENGTH_SHORT).show()
    }
    
    private fun clearCurrentSlotTexture() {
        val data = currentData ?: return
        
        when (currentSlot) {
            TextureSlot.BUTTON_NORMAL -> {
                if (data is ControlData.Button) {
                    data.texture = data.texture.copy(
                        normal = TextureConfig()
                    )
                }
            }
            TextureSlot.BUTTON_PRESSED -> {
                if (data is ControlData.Button) {
                    data.texture = data.texture.copy(
                        pressed = TextureConfig()
                    )
                }
            }
            TextureSlot.BUTTON_TOGGLED -> {
                if (data is ControlData.Button) {
                    data.texture = data.texture.copy(
                        toggled = TextureConfig()
                    )
                }
            }
            TextureSlot.JOYSTICK_BACKGROUND -> {
                if (data is ControlData.Joystick) {
                    data.texture = data.texture.copy(
                        background = TextureConfig()
                    )
                }
            }
            TextureSlot.JOYSTICK_KNOB -> {
                if (data is ControlData.Joystick) {
                    data.texture = data.texture.copy(
                        knob = TextureConfig()
                    )
                }
            }
            TextureSlot.TOUCHPAD_BACKGROUND -> {
                if (data is ControlData.TouchPad) {
                    data.texture = data.texture.copy(
                        background = TextureConfig()
                    )
                }
            }
            TextureSlot.TEXT_BACKGROUND -> {
                if (data is ControlData.Text) {
                    data.texture = data.texture.copy(
                        background = TextureConfig()
                    )
                }
            }
        }
        
        listener?.onTextureConfigured(data)
        Toast.makeText(context, R.string.control_texture_cleared, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 纹理列表适配器
     */
    inner class TextureAdapter(
        private val onItemClick: (TextureFileInfo) -> Unit
    ) : RecyclerView.Adapter<TextureAdapter.ViewHolder>() {
        
        private var items: List<TextureFileInfo> = emptyList()
        
        fun submitList(list: List<TextureFileInfo>) {
            items = list
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_texture, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }
        
        override fun getItemCount(): Int = items.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val card: MaterialCardView = itemView.findViewById(R.id.card_texture)
            private val imageView: ImageView = itemView.findViewById(R.id.img_texture_preview)
            private val textName: TextView = itemView.findViewById(R.id.text_texture_name)
            private val textInfo: TextView = itemView.findViewById(R.id.text_texture_info)
            
            fun bind(info: TextureFileInfo) {
                textName.text = info.name
                textInfo.text = "${info.format} · ${info.fileSizeText}"
                
                // 加载预览图
                loadPreview(info)
                
                card.setOnClickListener {
                    onItemClick(info)
                }
            }
            
            private fun loadPreview(info: TextureFileInfo) {
                try {
                    val file = File(info.absolutePath)
                    if (file.exists() && info.format != "SVG") {
                        // 缩略图加载
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeFile(info.absolutePath, options)
                        
                        // 计算缩放比例
                        val targetSize = 200
                        options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
                        options.inJustDecodeBounds = false
                        
                        val bitmap = BitmapFactory.decodeFile(info.absolutePath, options)
                        imageView.setImageBitmap(bitmap)
                    } else {
                        // SVG 或文件不存在，显示默认图标
                        imageView.setImageResource(R.drawable.ic_texture_placeholder)
                    }
                } catch (e: Exception) {
                    imageView.setImageResource(R.drawable.ic_texture_placeholder)
                }
            }
            
            private fun calculateInSampleSize(
                options: BitmapFactory.Options,
                reqWidth: Int,
                reqHeight: Int
            ): Int {
                val height = options.outHeight
                val width = options.outWidth
                var inSampleSize = 1
                
                if (height > reqHeight || width > reqWidth) {
                    val halfHeight = height / 2
                    val halfWidth = width / 2
                    
                    while (halfHeight / inSampleSize >= reqHeight && 
                           halfWidth / inSampleSize >= reqWidth) {
                        inSampleSize *= 2
                    }
                }
                
                return inSampleSize
            }
        }
    }
}

