package com.app.ralaunch.controls.editor;

import android.app.Dialog;
import android.content.Context;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.google.android.material.slider.Slider;
import android.widget.TextView;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.KeyMapper;
import com.app.ralaunch.utils.LocaleManager;
import com.app.ralaunch.controls.editor.manager.ControlTypeManager;
import com.app.ralaunch.controls.editor.manager.ControlShapeManager;
import com.app.ralaunch.controls.editor.manager.ControlEditDialogVisibilityManager;
import com.app.ralaunch.controls.editor.manager.ControlColorManager;
import com.app.ralaunch.controls.editor.manager.ControlEditDialogUIBinder;
import com.app.ralaunch.controls.editor.manager.ControlEditDialogDataFiller;
import com.app.ralaunch.controls.editor.manager.ControlEditDialogKeymapManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * MD3风格的控件编辑对话框
 * 使用 DialogFragment 实现，自动继承 Activity 的动态主题颜色
 */
public class ControlEditDialogMD extends DialogFragment {

    private static final String ARG_SCREEN_WIDTH = "screen_width";
    private static final String ARG_SCREEN_HEIGHT = "screen_height";

    private ControlData mCurrentData;
    private int mScreenWidth, mScreenHeight;
    private boolean mIsUpdating = false;

    private ViewGroup mContentFrame;
    private com.google.android.material.card.MaterialCardView mSidebarContainer;
    private LinearLayout mCategoryList;
    private com.google.android.material.card.MaterialCardView mCategoryBasic, mCategoryPosition, mCategoryAppearance, mCategoryKeymap;
    private int mCurrentCategory = 0; // 0=基本信息, 1=位置大小, 2=外观样式, 3=键值设置
    
    // 关闭按钮
    private com.google.android.material.button.MaterialButton mBtnClose;
    
    // 内容视图
    private android.view.View mContentBasic, mContentPosition, mContentAppearance, mContentKeymap;

    // UI元素引用已移至管理器类，不再在此处声明
    // 这些字段保留用于向后兼容，但实际使用已由管理器类处理
    private boolean mIsAutoSize = false; // 是否启用自适应尺寸

    // 回调接口
    private OnControlUpdatedListener mUpdateListener;
    private OnControlDeletedListener mDeleteListener;
    private OnControlCopiedListener mCopyListener;

    public interface OnControlUpdatedListener {
        void onControlUpdated(ControlData data);
    }

    public interface OnControlDeletedListener {
        void onControlDeleted(ControlData data);
    }

    public interface OnControlCopiedListener {
        void onControlCopied(ControlData data);
    }

    // DialogFragment 使用静态工厂方法创建实例
    public static ControlEditDialogMD newInstance(int screenWidth, int screenHeight) {
        ControlEditDialogMD dialog = new ControlEditDialogMD();
        Bundle args = new Bundle();
        args.putInt(ARG_SCREEN_WIDTH, screenWidth);
        args.putInt(ARG_SCREEN_HEIGHT, screenHeight);
        dialog.setArguments(args);
        return dialog;
    }

    public ControlEditDialogMD() {
        // DialogFragment 必须有无参构造函数
    }
    
    /**
     * 获取本地化的 Context（用于字符串资源）
     * 使用对话框的 Context 以确保主题正确
     */
    @NonNull
    private Context getLocalizedContext() {
        // 优先使用 Dialog 的 Context，它包含了正确的对话框主题
        Dialog dialog = getDialog();
        Context baseContext = (dialog != null && dialog.getContext() != null) 
            ? dialog.getContext() 
            : requireContext();
        
        return LocaleManager.applyLanguage(baseContext);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置对话框样式
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.ControlEditDialogStyle);
        
        // 从 arguments 中获取屏幕尺寸
        if (getArguments() != null) {
            mScreenWidth = getArguments().getInt(ARG_SCREEN_WIDTH, 0);
            mScreenHeight = getArguments().getInt(ARG_SCREEN_HEIGHT, 0);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 直接使用对话框的 Context，它已经包含了正确的主题和语言设置
        // 不使用 LocaleManager.applyLanguage 以避免丢失对话框样式
        return inflater.inflate(R.layout.dialog_control_edit_md, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 启用硬件加速，确保 Material Design 的触摸反馈和点击事件正常工作
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 应用背景透明度（使用统一工具类）
        try {
            float dialogAlpha = com.app.ralaunch.utils.OpacityHelper.getDialogAlphaFromSettings(requireContext());
            view.setAlpha(dialogAlpha);
        } catch (Exception e) {
            // 忽略错误，使用默认不透明
        }

        // 绑定UI元素
        initViews(view);

        // 设置监听器
        setupListeners();
    }
    
    @Override
    public void onStart() {
        super.onStart();
        
        // 设置对话框窗口大小
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            
            // 设置窗口宽高
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            int height = ViewGroup.LayoutParams.WRAP_CONTENT;
            window.setLayout(width, height);
        }
    }


    /**
     * 初始化UI元素
     */
    private void initViews(View rootView) {
        // 保存根视图引用
        View view = rootView;
        
        // 关闭按钮
        mBtnClose = view.findViewById(R.id.btn_close);
        
        // 侧边栏分类导航（XML 中已定义，无需代码生成）
        mCategoryList = view.findViewById(R.id.categoryList);
        mCategoryBasic = view.findViewById(R.id.categoryBasic);
        mCategoryPosition = view.findViewById(R.id.categoryPosition);
        mCategoryAppearance = view.findViewById(R.id.categoryAppearance);
        mCategoryKeymap = view.findViewById(R.id.categoryKeymap);
        mContentFrame = view.findViewById(R.id.contentFrame);
        
        // 设置侧边栏分类点击监听器
        mCategoryBasic.setOnClickListener(v -> switchToCategory(0));
        mCategoryPosition.setOnClickListener(v -> switchToCategory(1));
        mCategoryAppearance.setOnClickListener(v -> switchToCategory(2));
        mCategoryKeymap.setOnClickListener(v -> switchToCategory(3));
        
        // 初始化内容视图
        mContentBasic = view.findViewById(R.id.contentBasic);
        mContentPosition = view.findViewById(R.id.contentPosition);
        mContentAppearance = view.findViewById(R.id.contentAppearance);
        mContentKeymap = view.findViewById(R.id.contentKeymap);
        
        // 默认显示第一个分类
        switchToCategory(0);
    }
    
    /**
     * 切换到指定分类
     */
    private void switchToCategory(int category) {
        mCurrentCategory = category;
        
        // 隐藏所有内容
        if (mContentBasic != null) mContentBasic.setVisibility(View.GONE);
        if (mContentPosition != null) mContentPosition.setVisibility(View.GONE);
        if (mContentAppearance != null) mContentAppearance.setVisibility(View.GONE);
        if (mContentKeymap != null) mContentKeymap.setVisibility(View.GONE);
        
        // 显示选中的内容
        switch (category) {
            case 0: // 基本信息
                if (mContentBasic != null) mContentBasic.setVisibility(View.VISIBLE);
                break;
            case 1: // 位置大小
                if (mContentPosition != null) mContentPosition.setVisibility(View.VISIBLE);
                break;
            case 2: // 外观样式
                if (mContentAppearance != null) mContentAppearance.setVisibility(View.VISIBLE);
                break;
            case 3: // 键值设置
                if (mContentKeymap != null) {
                    // 根据控件类型决定是否显示键值设置（按钮显示，文本控件不显示）
                    if (mCurrentData != null && 
                        mCurrentData.type == ControlData.TYPE_BUTTON &&
                        mCurrentData.type != ControlData.TYPE_TEXT) {
                        mContentKeymap.setVisibility(View.VISIBLE);
                    } else {
                        // 如果是摇杆类型，切换回基本信息
                        switchToCategory(0);
                        return;
                    }
                }
                break;
        }
        
        // 更新侧边栏选中状态（紧凑极简风格）
        updateCategorySelection(category);
        
        // 绑定当前分类的视图
        bindCategoryViews();
        
        // 更新所有选项的可见性（必须在绑定视图之后）
        if (mContentBasic != null && mContentAppearance != null && mContentKeymap != null) {
            ControlEditDialogVisibilityManager.updateAllOptionsVisibility(mContentBasic, mContentAppearance, mContentKeymap, mCurrentData);
        } else if (mContentAppearance != null && mContentKeymap != null) {
            ControlEditDialogVisibilityManager.updateAllOptionsVisibility(mContentAppearance, mContentKeymap, mCurrentData);
        }
        
        // 填充数据
        fillCategoryData();
    }
    
    /**
     * 更新侧边栏分类选中状态（MD3圆角卡片风格）
     */
    private void updateCategorySelection(int selectedCategory) {
        // 重置所有分类样式
        updateCategoryCardStyle(mCategoryBasic, false);
        updateCategoryCardStyle(mCategoryPosition, false);
        updateCategoryCardStyle(mCategoryAppearance, false);
        updateCategoryCardStyle(mCategoryKeymap, false);
        
        // 设置选中分类样式
        com.google.android.material.card.MaterialCardView selectedCard = null;
        switch (selectedCategory) {
            case 0:
                selectedCard = mCategoryBasic;
                break;
            case 1:
                selectedCard = mCategoryPosition;
                break;
            case 2:
                selectedCard = mCategoryAppearance;
                break;
            case 3:
                selectedCard = mCategoryKeymap;
                break;
        }
        
        if (selectedCard != null) {
            updateCategoryCardStyle(selectedCard, true);
        }
    }
    
    /**
     * 更新分类卡片样式 (MD3圆角卡片效果)
     */
    private void updateCategoryCardStyle(com.google.android.material.card.MaterialCardView card, boolean selected) {
        if (card == null) return;
        
        if (selected) {
            // MD3 选中状态：主色背景 + 加粗文字 + 白色图标
            android.util.TypedValue colorValue = new android.util.TypedValue();
            getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, colorValue, true);
            card.setCardBackgroundColor(colorValue.data);
            card.setStrokeWidth(0);
            
            TextView textView = findTextViewInCard(card);
            if (textView != null) {
                textView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
                textView.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            
            ImageView imageView = findImageViewInCard(card);
            if (imageView != null) {
                android.util.TypedValue primaryColor = new android.util.TypedValue();
                getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, primaryColor, true);
                imageView.setColorFilter(primaryColor.data, PorterDuff.Mode.SRC_IN);
            }
        } else {
          
            card.setCardBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
            card.setStrokeWidth(0);
            
            TextView textView = findTextViewInCard(card);
            if (textView != null) {
                textView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
                textView.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
            
            ImageView imageView = findImageViewInCard(card);
            if (imageView != null) {
                android.util.TypedValue onSurfaceColor = new android.util.TypedValue();
                getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, onSurfaceColor, true);
                imageView.setColorFilter(onSurfaceColor.data, PorterDuff.Mode.SRC_IN);
            }
        }
    }
    
    /**
     * 在卡片中查找 TextView
     */
    private TextView findTextViewInCard(ViewGroup card) {
        if (card == null) return null;
        for (int i = 0; i < card.getChildCount(); i++) {
            View child = card.getChildAt(i);
            if (child instanceof TextView) {
                return (TextView) child;
            } else if (child instanceof ViewGroup) {
                TextView found = findTextViewInCard((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }
    
    /**
     * 在卡片中查找 ImageView
     */
    private ImageView findImageViewInCard(ViewGroup card) {
        if (card == null) return null;
        for (int i = 0; i < card.getChildCount(); i++) {
            View child = card.getChildAt(i);
            if (child instanceof ImageView) {
                return (ImageView) child;
            } else if (child instanceof ViewGroup) {
                ImageView found = findImageViewInCard((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * 设置监听器
     */
    private void setupListeners() {
        View view = getView();
        if (view == null) return;
        
        // 关闭按钮
        if (mBtnClose != null) {
            mBtnClose.setOnClickListener(v -> dismiss());
        }
        
        // 删除按钮
        view.findViewById(R.id.btn_delete).setOnClickListener(v -> deleteControl());

        // 复制按钮
        view.findViewById(R.id.btn_copy).setOnClickListener(v -> copyControl());

        // 保存按钮
        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            if (mUpdateListener != null && mCurrentData != null) {
                mUpdateListener.onControlUpdated(mCurrentData);
            }
            dismiss();
        });

    }

    /**
     * 绑定当前分类的视图
     */
    private void bindCategoryViews() {
        View currentContentView = null;
        switch (mCurrentCategory) {
            case 0: currentContentView = mContentBasic; break;
            case 1: currentContentView = mContentPosition; break;
            case 2: currentContentView = mContentAppearance; break;
            case 3: currentContentView = mContentKeymap; break;
        }
        
        if (currentContentView == null) return;
        
        // 创建UI引用对象
        ControlEditDialogUIBinder.UIReferences uiRefs = createUIReferences();
        
        // 根据分类绑定不同的视图
        switch (mCurrentCategory) {
            case 0: // 基本信息
                ControlEditDialogUIBinder.bindBasicInfoViews(currentContentView, uiRefs, this);
                break;
            case 1: // 位置大小
                ControlEditDialogUIBinder.bindPositionSizeViews(currentContentView, uiRefs);
                break;
            case 2: // 外观样式
                ControlEditDialogUIBinder.bindAppearanceViews(currentContentView, uiRefs, this);
                break;
            case 3: // 键值设置
                ControlEditDialogKeymapManager.bindKeymapViews(currentContentView, createKeymapReferences(), this);
                break;
        }
    }


    /**
     * 创建UI引用对象
     */
    private ControlEditDialogUIBinder.UIReferences createUIReferences() {
        return new ControlEditDialogUIBinder.UIReferences() {
            @Override
            public ControlData getCurrentData() {
                return mCurrentData;
            }
            
            @Override
            public int getScreenWidth() {
                return mScreenWidth;
            }
            
            @Override
            public int getScreenHeight() {
                return mScreenHeight;
            }
            
            @Override
            public boolean isAutoSize() {
                return mIsAutoSize;
            }
            
            @Override
            public void setAutoSize(boolean autoSize) {
                mIsAutoSize = autoSize;
            }
            
            @Override
            public void notifyUpdate() {
                ControlEditDialogMD.this.notifyUpdate();
            }
            
            @Override
            public Context getContext() {
                return ControlEditDialogMD.this.getLocalizedContext();
            }
        };
    }
    
    /**
     * 创建数据填充UI引用对象
     */
    private ControlEditDialogDataFiller.UIReferences createDataFillerReferences() {
        return new ControlEditDialogDataFiller.UIReferences() {
            @Override
            public ControlData getCurrentData() {
                return mCurrentData;
            }
            
            @Override
            public int getScreenWidth() {
                return mScreenWidth;
            }
            
            @Override
            public int getScreenHeight() {
                return mScreenHeight;
            }
            
            @Override
            public boolean isAutoSize() {
                return mIsAutoSize;
            }
            
            @Override
            public Context getContext() {
                return ControlEditDialogMD.this.getLocalizedContext();
            }
        };
    }
    
    /**
     * 创建键值设置管理器UI引用对象
     */
    private ControlEditDialogKeymapManager.UIReferences createKeymapReferences() {
        return new ControlEditDialogKeymapManager.UIReferences() {
            @Override
            public ControlData getCurrentData() {
                return mCurrentData;
            }
            
            @Override
            public void notifyUpdate() {
                ControlEditDialogMD.this.notifyUpdate();
            }
        };
    }
    
    
    /**
     * 填充当前分类的数据
     */
    private void fillCategoryData() {
        if (mCurrentData == null) return;
        
        ControlEditDialogDataFiller.UIReferences dataRefs = createDataFillerReferences();

        switch (mCurrentCategory) {
            case 0: // 基本信息
                ControlEditDialogDataFiller.fillBasicInfoData(mContentBasic, dataRefs);
                break;
            case 1: // 位置大小
                ControlEditDialogDataFiller.fillPositionSizeData(mContentPosition, dataRefs);
                break;
            case 2: // 外观样式
                ControlEditDialogDataFiller.fillAppearanceData(mContentAppearance, dataRefs);
                break;
            case 3: // 键值设置
                ControlEditDialogKeymapManager.updateKeymapVisibility(mContentKeymap, mCurrentData);
                ControlEditDialogDataFiller.fillKeymapData(mContentKeymap, dataRefs);
                break;
        }
    }
    


    /**
     * 显示控件数据（DialogFragment 方式）
     */
    public void showWithData(androidx.fragment.app.FragmentManager fragmentManager, String tag, ControlData data) {
        // 保存数据
        mCurrentData = data;
        
        if (data == null) return;
        
        // 显示对话框
        show(fragmentManager, tag);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // 对话框显示后，更新UI
        if (mCurrentData != null) {
            // 根据控件类型决定是否显示键值设置分类
            updateKeymapCategoryVisibility();

            // 重新绑定视图，确保使用最新的数据
            bindCategoryViews();
            
            // 更新所有选项的可见性（必须在绑定视图之后）
            if (mContentBasic != null && mContentAppearance != null && mContentKeymap != null) {
                ControlEditDialogVisibilityManager.updateAllOptionsVisibility(mContentBasic, mContentAppearance, mContentKeymap, mCurrentData);
            } else if (mContentAppearance != null && mContentKeymap != null) {
                ControlEditDialogVisibilityManager.updateAllOptionsVisibility(mContentAppearance, mContentKeymap, mCurrentData);
            }
            
            // 填充当前分类的数据
            fillCategoryData();
        }
    }

    /**
     * 更新键值设置分类的可见性
     */
    private void updateKeymapCategoryVisibility() {
        if (mCurrentData == null || mCategoryKeymap == null) return;

        // 按钮控件显示键值设置分类（文本控件不显示）
        if (mCurrentData.type == ControlData.TYPE_BUTTON &&
            mCurrentData.type != ControlData.TYPE_TEXT) {
            mCategoryKeymap.setVisibility(View.VISIBLE);
        } else {
            mCategoryKeymap.setVisibility(View.GONE);
            // 如果当前正在查看键值设置，切换回基本信息
            if (mCurrentCategory == 3) {
                switchToCategory(0);
            }
        }
    }

    /**
     * 更新颜色视图显示
     */
    private void updateColorViews() {
        if (mCurrentData == null || mContentAppearance == null) return;

        // 背景颜色
        View viewBgColor = mContentAppearance.findViewById(R.id.view_bg_color);
        if (viewBgColor != null) {
            ControlColorManager.updateColorView(viewBgColor, mCurrentData.bgColor, 
                dpToPx(8), dpToPx(2));
        }

        // 边框颜色
        View viewStrokeColor = mContentAppearance.findViewById(R.id.view_stroke_color);
        if (viewStrokeColor != null) {
            ControlColorManager.updateColorView(viewStrokeColor, mCurrentData.strokeColor, 
                dpToPx(8), dpToPx(2));
        }
    }

    /**
     * 更新类型显示
     */
    private void updateTypeDisplay() {
        if (mCurrentData == null || mContentBasic == null) return;
        TextView tvControlType = mContentBasic.findViewById(R.id.tv_control_type);
        if (tvControlType != null) {
            ControlTypeManager.updateTypeDisplay(getLocalizedContext(), mCurrentData, tvControlType);
        }
        
        // 类型改变时，更新所有选项的可见性
        if (mContentBasic != null && mContentAppearance != null && mContentKeymap != null) {
            ControlEditDialogVisibilityManager.updateAllOptionsVisibility(mContentBasic, mContentAppearance, mContentKeymap, mCurrentData);
        } else if (mContentAppearance != null && mContentKeymap != null) {
            ControlEditDialogVisibilityManager.updateAllOptionsVisibility(mContentAppearance, mContentKeymap, mCurrentData);
        }
    }

    /**
     * 更新形状显示
     */
    private void updateShapeDisplay() {
        if (mCurrentData == null || mContentBasic == null) return;
        TextView tvControlShape = mContentBasic.findViewById(R.id.tv_control_shape);
        View itemControlShape = mContentBasic.findViewById(R.id.item_control_shape);
        if (tvControlShape != null && itemControlShape != null) {
            ControlShapeManager.updateShapeDisplay(getLocalizedContext(), mCurrentData, tvControlShape, itemControlShape);
        }
        
        // 形状改变时，更新外观选项的可见性
        if (mContentAppearance != null) {
            ControlEditDialogVisibilityManager.updateAppearanceOptionsVisibility(mContentAppearance, mCurrentData);
        }
    }

    /**
     * 显示形状选择对话框
     */
    private void showShapeSelectDialog() {
        ControlShapeManager.showShapeSelectDialog(getLocalizedContext(), mCurrentData, 
            (data) -> {
                updateShapeDisplay();
                notifyUpdate();
            });
    }

    /**
     * 显示类型选择对话框
     */
    private void showTypeSelectDialog() {
        ControlTypeManager.showTypeSelectDialog(getLocalizedContext(), mCurrentData, 
            (data) -> {
                updateTypeDisplay();
                // 类型改变时，更新键值设置分类的可见性
                updateKeymapCategoryVisibility();
                notifyUpdate();
            });
    }


    /**
     * 显示颜色选择对话框
     */
    private void showColorPickerDialog(boolean isBackground) {
        ControlColorManager.showColorPickerDialog(getLocalizedContext(), mCurrentData, isBackground,
            (data, color, isBg) -> {
                updateColorViews();
                notifyUpdate();
            });
    }

    /**
     * 删除控件
     */
    private void deleteControl() {
        if (mCurrentData == null) return;

        // 使用getContext()获取带主题的Context，getLocalizedContext()获取本地化字符串
        Context localizedContext = getLocalizedContext();
        new MaterialAlertDialogBuilder(getContext())
            .setTitle(localizedContext.getString(R.string.editor_delete_control))
            .setMessage(localizedContext.getString(R.string.editor_delete_control_confirm))
            .setPositiveButton(localizedContext.getString(R.string.ok), (dialog, which) -> {
                if (mDeleteListener != null) {
                    mDeleteListener.onControlDeleted(mCurrentData);
                }
                dismiss();
            })
            .setNegativeButton(localizedContext.getString(R.string.cancel), null)
            .show();
    }

    /**
     * 复制控件
     */
    private void copyControl() {
        if (mCurrentData == null) return;

        // 创建控件的深拷贝
        ControlData copiedData = new ControlData(mCurrentData);
        // 为新控件设置一个唯一的名称（添加"副本"后缀）
        Context localizedContext = getLocalizedContext();
        if (copiedData.name != null) {
            copiedData.name = copiedData.name + " " + localizedContext.getString(R.string.editor_copy_suffix);
        } else {
            copiedData.name = localizedContext.getString(R.string.editor_control_copy);
        }
        // 稍微偏移位置，避免完全重叠
        copiedData.x += copiedData.width * 0.1f;
        copiedData.y += copiedData.height * 0.1f;

        if (mCopyListener != null) {
            mCopyListener.onControlCopied(copiedData);
        }
        dismiss();
    }

    /**
     * 通知数据更新
     */
    private void notifyUpdate() {
        if (mUpdateListener != null && mCurrentData != null && !mIsUpdating) {
            mUpdateListener.onControlUpdated(mCurrentData);
        }
    }

    public void setOnControlUpdatedListener(OnControlUpdatedListener listener) {
        mUpdateListener = listener;
    }

    public void setOnControlDeletedListener(OnControlDeletedListener listener) {
        mDeleteListener = listener;
    }

    public void setOnControlCopiedListener(OnControlCopiedListener listener) {
        mCopyListener = listener;
    }


    private int dpToPx(int dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }
}
