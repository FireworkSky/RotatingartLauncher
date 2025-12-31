package com.app.ralaunch.controls.editors

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.app.ralaunch.R
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.editors.managers.ControlColorManager
import com.app.ralaunch.controls.editors.managers.ControlColorManager.showColorPickerDialog
import com.app.ralaunch.controls.editors.managers.ControlColorManager.updateColorView
import com.app.ralaunch.controls.editors.managers.ControlEditDialogDataFiller
import com.app.ralaunch.controls.editors.managers.ControlEditDialogKeymapManager
import com.app.ralaunch.controls.editors.managers.ControlEditDialogUIBinder
import com.app.ralaunch.controls.editors.managers.ControlEditDialogUIBinder.bindAppearanceViews
import com.app.ralaunch.controls.editors.managers.ControlEditDialogUIBinder.bindBasicInfoViews
import com.app.ralaunch.controls.editors.managers.ControlEditDialogUIBinder.bindPositionSizeViews
import com.app.ralaunch.controls.editors.managers.ControlEditDialogVisibilityManager
import com.app.ralaunch.controls.editors.managers.ControlShapeManager.OnShapeSelectedListener
import com.app.ralaunch.controls.editors.managers.ControlShapeManager.showShapeSelectDialog
import com.app.ralaunch.controls.editors.managers.ControlShapeManager.updateShapeDisplay
import com.app.ralaunch.controls.editors.managers.ControlTypeManager.OnTypeSelectedListener
import com.app.ralaunch.controls.editors.managers.ControlTypeManager.showTypeSelectDialog
import com.app.ralaunch.controls.editors.managers.ControlTypeManager.updateTypeDisplay
import com.app.ralaunch.utils.LocaleManager
import com.app.ralaunch.utils.OpacityHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * MD3风格的控件编辑对话框
 * 使用 DialogFragment 实现，自动继承 Activity 的动态主题颜色
 */
class ControlEditDialogMD : DialogFragment() {
    var currentData: ControlData? = null
        private set

    var screenWidth: Int = 0
        private set

    var screenHeight: Int = 0
        private set

    var isUpdating: Boolean = false
        private set

    private var mContentFrame: ViewGroup? = null
    private var mCategoryList: LinearLayout? = null
    private var mCategoryBasic: MaterialCardView? = null
    private var mCategoryPosition: MaterialCardView? = null
    private var mCategoryAppearance: MaterialCardView? = null
    private var mCategoryKeymap: MaterialCardView? = null
    private var mCurrentCategory = 0 // 0=基本信息, 1=位置大小, 2=外观样式, 3=键值设置

    // 关闭按钮
    private var mBtnClose: MaterialButton? = null

    // 内容视图
    private var mContentBasic: View? = null
    private var mContentPosition: View? = null
    private var mContentAppearance: View? = null
    private var mContentKeymap: View? = null

    // Callback interfaces
    private var mUpdateListener: OnControlUpdatedListener? = null
    private var mDeleteListener: OnControlDeletedListener? = null
    private var mCopyListener: OnControlCopiedListener? = null

    interface OnControlUpdatedListener {
        fun onControlUpdated(data: ControlData?)
    }

    interface OnControlDeletedListener {
        fun onControlDeleted(data: ControlData?)
    }

    interface OnControlCopiedListener {
        fun onControlCopied(data: ControlData?)
    }

    private val localizedContext: Context
        /**
         * 获取本地化的 Context（用于字符串资源）
         * 使用对话框的 Context 以确保主题正确
         */
        get() {
            // 优先使用 Dialog 的 Context，它包含了正确的对话框主题
            val dialog = dialog
            val baseContext = dialog?.context ?: requireContext()
            return LocaleManager.applyLanguage(baseContext)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置对话框样式
        setStyle(STYLE_NO_TITLE, R.style.ControlEditDialogStyle)

        // 从 arguments 中获取屏幕尺寸
        arguments?.let {
            screenWidth = it.getInt(ARG_SCREEN_WIDTH, 0)
            screenHeight = it.getInt(ARG_SCREEN_HEIGHT, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 直接使用对话框的 Context，它已经包含了正确的主题和语言设置
        // 不使用 LocaleManager.applyLanguage 以避免丢失对话框样式
        return inflater.inflate(R.layout.dialog_control_edit_md, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 启用硬件加速，确保 Material Design 的触摸反馈和点击事件正常工作
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // 应用背景透明度（使用统一工具类）
        try {
            val dialogAlpha = OpacityHelper.getDialogAlphaFromSettings(requireContext())
            view.setAlpha(dialogAlpha)
        } catch (_: Exception) {
            // 忽略错误，使用默认不透明
        }

        // 绑定UI元素
        initViews(view)

        // 设置监听器
        setupListeners()
    }

    override fun onStart() {
        super.onStart()

        // 设置对话框窗口大小
        val dialog = getDialog()
        if (dialog != null && dialog.getWindow() != null) {
            val window = dialog.getWindow()

            // 设置窗口宽高
            val width = (getResources().getDisplayMetrics().widthPixels * 0.9).toInt()
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            window!!.setLayout(width, height)
        }
    }


    /**
     * 初始化UI元素
     */
    private fun initViews(rootView: View) {
        // 保存根视图引用
        val view = rootView

        // 关闭按钮
        mBtnClose = view.findViewById<MaterialButton?>(R.id.btn_close)

        // 侧边栏分类导航（XML 中已定义，无需代码生成）
        mCategoryList = view.findViewById<LinearLayout?>(R.id.categoryList)
        mCategoryBasic = view.findViewById<MaterialCardView>(R.id.categoryBasic)
        mCategoryPosition = view.findViewById<MaterialCardView>(R.id.categoryPosition)
        mCategoryAppearance = view.findViewById<MaterialCardView>(R.id.categoryAppearance)
        mCategoryKeymap = view.findViewById<MaterialCardView?>(R.id.categoryKeymap)
        mContentFrame = view.findViewById<ViewGroup?>(R.id.contentFrame)

        // 设置侧边栏分类点击监听器
        mCategoryBasic!!.setOnClickListener { switchToCategory(0) }
        mCategoryPosition!!.setOnClickListener { switchToCategory(1) }
        mCategoryAppearance!!.setOnClickListener { switchToCategory(2) }
        mCategoryKeymap!!.setOnClickListener { switchToCategory(3) }

        // 初始化内容视图
        mContentBasic = view.findViewById<View?>(R.id.contentBasic)
        mContentPosition = view.findViewById<View?>(R.id.contentPosition)
        mContentAppearance = view.findViewById<View?>(R.id.contentAppearance)
        mContentKeymap = view.findViewById<View?>(R.id.contentKeymap)

        // 默认显示第一个分类
        switchToCategory(0)
    }

    /**
     * 切换到指定分类
     */
    private fun switchToCategory(category: Int) {
        mCurrentCategory = category

        // 隐藏所有内容
        if (mContentBasic != null) mContentBasic!!.setVisibility(View.GONE)
        if (mContentPosition != null) mContentPosition!!.setVisibility(View.GONE)
        if (mContentAppearance != null) mContentAppearance!!.setVisibility(View.GONE)
        if (mContentKeymap != null) mContentKeymap!!.setVisibility(View.GONE)

        // 显示选中的内容
        when (category) {
            0 -> if (mContentBasic != null) mContentBasic!!.setVisibility(View.VISIBLE)
            1 -> if (mContentPosition != null) mContentPosition!!.setVisibility(View.VISIBLE)
            2 -> if (mContentAppearance != null) mContentAppearance!!.setVisibility(View.VISIBLE)
            3 -> if (mContentKeymap != null) {
                // 根据控件类型决定是否显示键值设置（按钮显示，其他控件不显示）
                if (this.currentData != null && currentData is ControlData.Button) {
                    mContentKeymap!!.setVisibility(View.VISIBLE)
                } else {
                    // 如果不是按钮类型，切换回基本信息
                    switchToCategory(0)
                    return
                }
            }
        }

        // 更新侧边栏选中状态（紧凑极简风格）
        updateCategorySelection(category)

        // 绑定当前分类的视图
        bindCategoryViews()

        // 更新所有选项的可见性（必须在绑定视图之后）
        if (this.currentData != null) {
            if (mContentBasic != null) {
                ControlEditDialogVisibilityManager.updateAllOptionsVisibility(
                    mContentBasic!!,
                    this.currentData!!
                )
            }
            if (mContentAppearance != null) {
                ControlEditDialogVisibilityManager.updateAllOptionsVisibility(
                    mContentAppearance!!,
                    this.currentData!!
                )
            }
            if (mContentKeymap != null) {
                ControlEditDialogVisibilityManager.updateAllOptionsVisibility(
                    mContentKeymap!!,
                    this.currentData!!
                )
            }
        }

        // 填充数据
        fillCategoryData()
    }

    /**
     * 更新侧边栏分类选中状态（MD3圆角卡片风格）
     */
    private fun updateCategorySelection(selectedCategory: Int) {
        // 重置所有分类样式
        updateCategoryCardStyle(mCategoryBasic, false)
        updateCategoryCardStyle(mCategoryPosition, false)
        updateCategoryCardStyle(mCategoryAppearance, false)
        updateCategoryCardStyle(mCategoryKeymap, false)

        // 设置选中分类样式
        var selectedCard: MaterialCardView? = null
        when (selectedCategory) {
            0 -> selectedCard = mCategoryBasic
            1 -> selectedCard = mCategoryPosition
            2 -> selectedCard = mCategoryAppearance
            3 -> selectedCard = mCategoryKeymap
        }

        if (selectedCard != null) {
            updateCategoryCardStyle(selectedCard, true)
        }
    }

    /**
     * 更新分类卡片样式 (MD3圆角卡片效果)
     */
    private fun updateCategoryCardStyle(card: MaterialCardView?, selected: Boolean) {
        if (card == null) return

        if (selected) {
            // MD3 选中状态：主色背景 + 加粗文字 + 白色图标
            val colorValue = TypedValue()
            requireContext().theme.resolveAttribute(
                com.google.android.material.R.attr.colorPrimaryContainer,
                colorValue,
                true
            )
            card.setCardBackgroundColor(colorValue.data)
            card.setStrokeWidth(0)

            val textView = findTextViewInCard(card)
            if (textView != null) {
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                textView.setTypeface(null, Typeface.BOLD)
            }

            val imageView = findImageViewInCard(card)
            if (imageView != null) {
                val primaryColor = TypedValue()
                requireContext().theme.resolveAttribute(
                    com.google.android.material.R.attr.colorPrimary,
                    primaryColor,
                    true
                )
                imageView.setColorFilter(primaryColor.data, PorterDuff.Mode.SRC_IN)
            }
        } else {
            card.setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    android.R.color.transparent
                )
            )
            card.setStrokeWidth(0)

            val textView = findTextViewInCard(card)
            if (textView != null) {
                textView.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.text_secondary
                    )
                )
                textView.setTypeface(null, Typeface.NORMAL)
            }

            val imageView = findImageViewInCard(card)
            if (imageView != null) {
                val onSurfaceColor = TypedValue()
                requireContext().theme.resolveAttribute(
                    com.google.android.material.R.attr.colorOnSurface,
                    onSurfaceColor,
                    true
                )
                imageView.setColorFilter(onSurfaceColor.data, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    /**
     * 在卡片中查找 TextView
     */
    private fun findTextViewInCard(card: ViewGroup?): TextView? {
        if (card == null) return null
        for (i in 0..<card.getChildCount()) {
            val child = card.getChildAt(i)
            if (child is TextView) {
                return child
            } else if (child is ViewGroup) {
                val found = findTextViewInCard(child)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * 在卡片中查找 ImageView
     */
    private fun findImageViewInCard(card: ViewGroup?): ImageView? {
        if (card == null) return null
        for (i in 0..<card.getChildCount()) {
            val child = card.getChildAt(i)
            if (child is ImageView) {
                return child
            } else if (child is ViewGroup) {
                val found = findImageViewInCard(child)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        val view = getView()
        if (view == null) return

        // 关闭按钮
        if (mBtnClose != null) {
            mBtnClose!!.setOnClickListener { dismiss() }
        }

        // 删除按钮
        view.findViewById<View?>(R.id.btn_delete)!!
            .setOnClickListener { deleteControl() }

        // 复制按钮
        view.findViewById<View?>(R.id.btn_copy)!!
            .setOnClickListener { copyControl() }

        // 保存按钮
        view.findViewById<View?>(R.id.btn_save)!!
            .setOnClickListener {
                if (mUpdateListener != null && this.currentData != null) {
                    mUpdateListener!!.onControlUpdated(this.currentData)
                }
                dismiss()
            }
    }

    /**
     * 绑定当前分类的视图
     */
    private fun bindCategoryViews() {
        var currentContentView: View? = null
        when (mCurrentCategory) {
            0 -> currentContentView = mContentBasic
            1 -> currentContentView = mContentPosition
            2 -> currentContentView = mContentAppearance
            3 -> currentContentView = mContentKeymap
        }

        if (currentContentView == null) return

        // 创建UI引用对象
        val uiRefs = createUIReferences()

        // 根据分类绑定不同的视图
        when (mCurrentCategory) {
            0 -> bindBasicInfoViews(currentContentView, uiRefs, this)
            1 -> bindPositionSizeViews(currentContentView, uiRefs)
            2 -> bindAppearanceViews(currentContentView, uiRefs, this)
            3 -> ControlEditDialogKeymapManager.bindKeymapViews(
                currentContentView,
                createKeymapReferences(),
                this
            )
        }
    }


    /**
     * 创建UI引用对象
     */
    private fun createUIReferences(): ControlEditDialogUIBinder.UIReferences {
        return object : ControlEditDialogUIBinder.UIReferences {
            override val currentData: ControlData?
                get() = this@ControlEditDialogMD.currentData

            override val screenWidth: Int
                get() = this@ControlEditDialogMD.screenWidth

            override val screenHeight: Int
                get() = this@ControlEditDialogMD.screenHeight


            override val isUpdating: Boolean
                get() = this@ControlEditDialogMD.isUpdating

            override val context: Context
                get() = this@ControlEditDialogMD.localizedContext

            override fun notifyUpdate() {
                this@ControlEditDialogMD.notifyUpdate()
            }
        }
    }

    /**
     * 创建数据填充UI引用对象
     */
    private fun createDataFillerReferences(): ControlEditDialogDataFiller.UIReferences {
        return object : ControlEditDialogDataFiller.UIReferences {
            override val currentData: ControlData?
                get() = this@ControlEditDialogMD.currentData

            override val screenWidth: Int
                get() = this@ControlEditDialogMD.screenWidth

            override val screenHeight: Int
                get() = this@ControlEditDialogMD.screenHeight

            override val context: Context
                get() = this@ControlEditDialogMD.localizedContext
        }
    }

    /**
     * 创建键值设置管理器UI引用对象
     */
    private fun createKeymapReferences(): ControlEditDialogKeymapManager.UIReferences {
        return object : ControlEditDialogKeymapManager.UIReferences {
            override val currentData: ControlData?
                get() = this@ControlEditDialogMD.currentData

            override fun notifyUpdate() {
                this@ControlEditDialogMD.notifyUpdate()
            }
        }
    }


    /**
     * 填充当前分类的数据
     */
    private fun fillCategoryData() {
        if (this.currentData == null) return

        val dataRefs = createDataFillerReferences()

        when (mCurrentCategory) {
            0 -> ControlEditDialogDataFiller.fillBasicInfoData(mContentBasic!!, dataRefs)
            1 -> ControlEditDialogDataFiller.fillPositionSizeData(mContentPosition!!, dataRefs)
            2 -> ControlEditDialogDataFiller.fillAppearanceData(mContentAppearance!!, dataRefs)
            3 -> {
                ControlEditDialogKeymapManager.updateKeymapVisibility(
                    mContentKeymap!!,
                    this.currentData!!
                )
                ControlEditDialogDataFiller.fillKeymapData(mContentKeymap!!, dataRefs)
            }
        }
    }


    /**
     * 显示控件数据（DialogFragment 方式）
     */
    fun showWithData(fragmentManager: FragmentManager, tag: String?, data: ControlData?) {
        if (data == null) return

        // 关键：在设置新数据之前就设置标志，防止旧的 TextWatcher 把旧文本写入新控件
        // 无论对话框是否已显示，都需要这个保护
        this.isUpdating = true
        this.currentData = data

        if (isAdded() && getView() != null) {
            // 对话框已显示，直接刷新UI
            refreshUIForCurrentData()
            // refreshUIForCurrentData() 的 finally 块会清除 mIsUpdating
        } else {
            // 对话框未显示，显示对话框
            // mIsUpdating 会在 onResume() 的 refreshUIForCurrentData() 完成后被清除
            show(fragmentManager, tag)
        }
    }

    override fun onResume() {
        super.onResume()

        refreshUIForCurrentData()
    }

    /**
     * 使用当前数据刷新对话框UI（不重新显示对话框）
     */
    private fun refreshUIForCurrentData() {
        if (this.currentData == null) return

        // 设置更新标志，防止 TextWatcher 在切换控件时把旧文本写入新控件
        this.isUpdating = true
        try {
            // 根据控件类型决定是否显示键值设置分类
            updateKeymapCategoryVisibility()

            // 重新绑定视图，确保使用最新的数据
            bindCategoryViews()

            // 更新所有选项的可见性（必须在绑定视图之后）
            if (this.currentData != null) {
                if (mContentBasic != null) {
                    ControlEditDialogVisibilityManager.updateAllOptionsVisibility(
                        mContentBasic!!,
                        this.currentData!!
                    )
                }
                if (mContentAppearance != null) {
                    ControlEditDialogVisibilityManager.updateAllOptionsVisibility(
                        mContentAppearance!!,
                        this.currentData!!
                    )
                }
                if (mContentKeymap != null) {
                    ControlEditDialogVisibilityManager.updateAllOptionsVisibility(
                        mContentKeymap!!,
                        this.currentData!!
                    )
                }
            }

            // 填充当前分类的数据
            fillCategoryData()
        } finally {
            // 确保标志被清除
            this.isUpdating = false
        }
    }

    /**
     * 更新键值设置分类的可见性
     */
    private fun updateKeymapCategoryVisibility() {
        if (this.currentData == null || mCategoryKeymap == null) return

        // 按钮控件显示键值设置分类
        if (currentData is ControlData.Button) {
            mCategoryKeymap!!.setVisibility(View.VISIBLE)
        } else {
            mCategoryKeymap!!.setVisibility(View.GONE)
            // 如果当前正在查看键值设置，切换回基本信息
            if (mCurrentCategory == 3) {
                switchToCategory(0)
            }
        }
    }

    /**
     * 更新颜色视图显示
     */
    private fun updateColorViews() {
        if (this.currentData == null || mContentAppearance == null) return

        // 背景颜色
        val viewBgColor = mContentAppearance!!.findViewById<View?>(R.id.view_bg_color)
        if (viewBgColor != null) {
            updateColorView(
                viewBgColor, currentData!!.bgColor,
                dpToPx(8).toFloat(), dpToPx(2).toFloat()
            )
        }

        // 边框颜色
        val viewStrokeColor = mContentAppearance!!.findViewById<View?>(R.id.view_stroke_color)
        if (viewStrokeColor != null) {
            updateColorView(
                viewStrokeColor, currentData!!.strokeColor,
                dpToPx(8).toFloat(), dpToPx(2).toFloat()
            )
        }
    }

    /**
     * 更新类型显示
     */
    private fun updateTypeDisplay() {
        if (this.currentData == null || mContentBasic == null) return
        val tvControlType = mContentBasic!!.findViewById<TextView?>(R.id.tv_control_type)
        if (tvControlType != null) {
            updateTypeDisplay(
                this.localizedContext,
                this.currentData, tvControlType
            )
        }

        // 类型改变时，更新所有选项的可见性
        if (this.currentData != null) {
            if (mContentBasic != null) {
                ControlEditDialogVisibilityManager.updateAllOptionsVisibility(
                    mContentBasic!!,
                    this.currentData!!
                )
            }
            if (mContentAppearance != null) {
                ControlEditDialogVisibilityManager.updateAllOptionsVisibility(
                    mContentAppearance!!,
                    this.currentData!!
                )
            }
            if (mContentKeymap != null) {
                ControlEditDialogVisibilityManager.updateAllOptionsVisibility(
                    mContentKeymap!!,
                    this.currentData!!
                )
            }
        }
    }

    /**
     * 更新形状显示
     */
    private fun updateShapeDisplay() {
        if (this.currentData == null || mContentBasic == null) return
        val tvControlShape = mContentBasic!!.findViewById<TextView?>(R.id.tv_control_shape)
        val itemControlShape = mContentBasic!!.findViewById<View?>(R.id.item_control_shape)
        if (tvControlShape != null && itemControlShape != null) {
            updateShapeDisplay(
                this.localizedContext,
                this.currentData, tvControlShape, itemControlShape
            )
        }

        // 形状改变时，更新外观选项的可见性
        if (mContentAppearance != null) {
            ControlEditDialogVisibilityManager.updateAllOptionsVisibility(
                mContentAppearance!!,
                this.currentData!!
            )
        }
    }

    /**
     * 显示形状选择对话框
     */
    private fun showShapeSelectDialog() {
        showShapeSelectDialog(
            this.localizedContext, this.currentData,
            object : OnShapeSelectedListener {
                override fun onShapeSelected(data: ControlData?) {
                    updateShapeDisplay()
                    notifyUpdate()
                }
            })
    }

    /**
     * 显示类型选择对话框
     */
    private fun showTypeSelectDialog() {
        showTypeSelectDialog(
            this.localizedContext, this.currentData,
            object : OnTypeSelectedListener {
                override fun onTypeSelected(data: ControlData?) {
                    updateTypeDisplay()
                    // 类型改变时，更新键值设置分类的可见性
                    updateKeymapCategoryVisibility()
                    notifyUpdate()
                }
            })
    }


    /**
     * 显示颜色选择对话框
     */
    private fun showColorPickerDialog(isBackground: Boolean) {
        showColorPickerDialog(
            this.localizedContext, this.currentData, isBackground,
            object : ControlColorManager.OnColorSelectedListener {
                override fun onColorSelected(data: ControlData?, color: Int, isBackground: Boolean) {
                    updateColorViews()
                    notifyUpdate()
                }
            })
    }

    /**
     * 删除控件
     */
    private fun deleteControl() {
        if (this.currentData == null) return

        // 使用requireContext()获取带主题的Context，getLocalizedContext()获取本地化字符串
        val localizedContext = this.localizedContext
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(localizedContext.getString(R.string.editor_delete_control))
            .setMessage(localizedContext.getString(R.string.editor_delete_control_confirm))
            .setPositiveButton(localizedContext.getString(R.string.ok)) { _, _ ->
                if (mDeleteListener != null) {
                    mDeleteListener!!.onControlDeleted(this.currentData)
                }
                dismiss()
            }
            .setNegativeButton(localizedContext.getString(R.string.cancel), null)
            .show()
    }

    /**
     * 复制控件
     */
    private fun copyControl() {
        if (this.currentData == null) return

        // 创建控件的深拷贝
        val copiedData = this.currentData!!.deepCopy()
        // 为新控件设置一个唯一的名称（添加"副本"后缀）
        val localizedContext = this.localizedContext
        copiedData.name = copiedData.name + " " + localizedContext.getString(R.string.editor_copy_suffix)

        // 稍微偏移位置，避免完全重叠
        copiedData.x += copiedData.width * 0.1f
        copiedData.y += copiedData.height * 0.1f

        if (mCopyListener != null) {
            mCopyListener!!.onControlCopied(copiedData)
        }
        dismiss()
    }

    /**
     * 通知数据更新
     */
    private fun notifyUpdate() {
        if (mUpdateListener != null && this.currentData != null && !this.isUpdating) {
            mUpdateListener!!.onControlUpdated(this.currentData)
        }
    }

    fun setOnControlUpdatedListener(listener: OnControlUpdatedListener?) {
        mUpdateListener = listener
    }

    fun setOnControlDeletedListener(listener: OnControlDeletedListener?) {
        mDeleteListener = listener
    }

    fun setOnControlCopiedListener(listener: OnControlCopiedListener?) {
        mCopyListener = listener
    }


    private fun dpToPx(dp: Int): Int {
        return (dp * requireContext().resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val ARG_SCREEN_WIDTH = "screen_width"
        private const val ARG_SCREEN_HEIGHT = "screen_height"

        // DialogFragment 使用静态工厂方法创建实例
        fun newInstance(screenWidth: Int, screenHeight: Int): ControlEditDialogMD {
            val dialog = ControlEditDialogMD()
            val args = Bundle()
            args.putInt(ARG_SCREEN_WIDTH, screenWidth)
            args.putInt(ARG_SCREEN_HEIGHT, screenHeight)
            dialog.setArguments(args)
            return dialog
        }
    }
}
