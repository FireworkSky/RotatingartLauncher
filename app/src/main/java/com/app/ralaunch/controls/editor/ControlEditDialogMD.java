package com.app.ralaunch.controls.editor;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.KeyMapper;
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
 * 使用垂直滚动的卡片列表，类似 dialog_element_properties 风格
 */
public class ControlEditDialogMD extends Dialog {

    private ControlData mCurrentData;
    private int mScreenWidth, mScreenHeight;
    private boolean mIsUpdating = false;

    private ListView mCategoryListView;
    private ViewGroup mContentFrame;
    private android.widget.LinearLayout mCategoriesLinearLayout;
    private int mCurrentCategory = 0; // 0=基本信息, 1=位置大小, 2=外观样式, 3=键值设置
    
    // 关闭按钮
    private android.widget.ImageButton mBtnClose;
    
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

    public ControlEditDialogMD(@NonNull Context context, int screenWidth, int screenHeight) {
        super(context, R.style.ControlEditDialogStyle);
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 加载布局
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_control_edit_md, null);
        setContentView(view);

        // 启用硬件加速，确保 Material Design 的触摸反馈和点击事件正常工作
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 设置对话框样式 - MD3风格
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            android.util.DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
            int screenHeight = metrics.heightPixels;
            int screenWidth = metrics.widthPixels;
            // 使用更大的高度，确保内容完整显示，底部按钮可见
            int maxHeight = (int) (screenHeight * 0.90);
            // MD3对话框推荐宽度为屏幕的85-90%
            int dialogWidth = (int) (screenWidth * 0.90);
            window.setLayout(dialogWidth, maxHeight);
            // 设置MD3对话框的圆角和边距
            window.setDimAmount(0.5f);
        }

        // 绑定UI元素
        initViews(view);

        // 设置监听器
        setupListeners();
    }

    /**
     * 初始化UI元素
     */
    private void initViews(View view) {
        // 关闭按钮
        mBtnClose = view.findViewById(R.id.btn_close);
        
        mCategoryListView = view.findViewById(R.id.categoryListView);
        mContentFrame = view.findViewById(R.id.contentFrame);
        
        // 设置分类列表
        setupCategoryList(view);
        
        // 初始化内容视图
        mContentBasic = view.findViewById(R.id.contentBasic);
        mContentPosition = view.findViewById(R.id.contentPosition);
        mContentAppearance = view.findViewById(R.id.contentAppearance);
        mContentKeymap = view.findViewById(R.id.contentKeymap);
        
        // 默认显示第一个分类
        switchToCategory(0);
    }
    
    /**
     * 设置分类列表
     */
    private void setupCategoryList(View view) {
        // 找到 ListView 的父容器
        ViewGroup listViewParent = (ViewGroup) mCategoryListView.getParent();
        int listViewIndex = listViewParent.indexOfChild(mCategoryListView);
        
        // 移除 ListView
        listViewParent.removeView(mCategoryListView);
        
        // 创建新的容器来替代 ListView
        mCategoriesLinearLayout = new android.widget.LinearLayout(getContext());
        mCategoriesLinearLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        mCategoriesLinearLayout.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        // 添加到原位置
        listViewParent.addView(mCategoriesLinearLayout, listViewIndex);
        
        // 创建分类数据
        List<Map<String, Object>> categories = getCategories();
        for (int i = 0; i < categories.size(); i++) {
            final int position = i;
            Map<String, Object> category = categories.get(i);
            
            View itemView = LayoutInflater.from(getContext()).inflate(
                R.layout.item_settings_category, mCategoriesLinearLayout, false);
            
            ImageView icon = itemView.findViewById(R.id.icon);
            TextView name = itemView.findViewById(R.id.category_name);
            
            icon.setImageResource((Integer) category.get("icon"));
            name.setText((String) category.get("category_name"));
            
            // 设置点击事件
            itemView.setOnClickListener(v -> {
                // 更新所有 item 的背景色
                for (int j = 0; j < mCategoriesLinearLayout.getChildCount(); j++) {
                    View child = mCategoriesLinearLayout.getChildAt(j);
                    if (child instanceof com.google.android.material.card.MaterialCardView) {
                        com.google.android.material.card.MaterialCardView cardView =
                            (com.google.android.material.card.MaterialCardView) child;
                        if (j == position) {
                            // 选中状态
                            cardView.setCardBackgroundColor(
                                ContextCompat.getColor(getContext(), R.color.accent_primary_light));
                            // 更新文字颜色
                            TextView textView = child.findViewById(R.id.category_name);
                            if (textView != null) {
                                textView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
                            }
                            ImageView iconView = child.findViewById(R.id.icon);
                            if (iconView != null) {
                                iconView.setColorFilter(ContextCompat.getColor(getContext(), R.color.text_primary));
                            }
                        } else {
                            // 未选中状态
                            cardView.setCardBackgroundColor(
                                ContextCompat.getColor(getContext(), android.R.color.transparent));
                            // 更新文字颜色
                            TextView textView = child.findViewById(R.id.category_name);
                            if (textView != null) {
                                textView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
                            }
                            ImageView iconView = child.findViewById(R.id.icon);
                            if (iconView != null) {
                                iconView.setColorFilter(ContextCompat.getColor(getContext(), R.color.text_secondary));
                            }
                        }
                    }
                }
                
                // 切换内容面板
                switchToCategory(position);
            });
            
            mCategoriesLinearLayout.addView(itemView);
        }
        
        // 默认选中第一项
        if (mCategoriesLinearLayout.getChildCount() > 0) {
            View firstChild = mCategoriesLinearLayout.getChildAt(0);
            if (firstChild instanceof com.google.android.material.card.MaterialCardView) {
                ((com.google.android.material.card.MaterialCardView) firstChild).setCardBackgroundColor(
                    ContextCompat.getColor(getContext(), R.color.accent_primary_light));
                TextView textView = firstChild.findViewById(R.id.category_name);
                if (textView != null) {
                    textView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
                }
                ImageView iconView = firstChild.findViewById(R.id.icon);
                if (iconView != null) {
                    iconView.setColorFilter(ContextCompat.getColor(getContext(), R.color.text_primary));
                }
            }
        }
    }
    
    /**
     * 获取分类数据
     */
    private List<Map<String, Object>> getCategories() {
        List<Map<String, Object>> categories = new ArrayList<>();
        
        Map<String, Object> basic = new HashMap<>();
        basic.put("icon", R.drawable.ic_info_outline);
        basic.put("category_name", "基本信息");
        categories.add(basic);
        
        Map<String, Object> position = new HashMap<>();
        position.put("icon", R.drawable.ic_location_on);
        position.put("category_name", "位置大小");
        categories.add(position);
        
        Map<String, Object> appearance = new HashMap<>();
        appearance.put("icon", R.drawable.ic_palette);
        appearance.put("category_name", "外观样式");
        categories.add(appearance);
        
        Map<String, Object> keymap = new HashMap<>();
        keymap.put("icon", R.drawable.ic_keyboard);
        keymap.put("category_name", "键值设置");
        categories.add(keymap);
        
        return categories;
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
     * 设置监听器
     */
    private void setupListeners() {
        // 关闭按钮
        if (mBtnClose != null) {
            mBtnClose.setOnClickListener(v -> dismiss());
        }
        
        // 删除按钮
        findViewById(R.id.btn_delete).setOnClickListener(v -> deleteControl());

        // 复制按钮
        findViewById(R.id.btn_copy).setOnClickListener(v -> copyControl());

        // 保存按钮
        findViewById(R.id.btn_save).setOnClickListener(v -> {
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
     * 显示控件数据
     */
    public void show(ControlData data) {
        // 直接使用传入的数据对象，确保引用一致
        mCurrentData = data;

        if (data == null) return;

        // 先显示对话框（触发onCreate初始化视图）
        super.show();

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

    /**
     * 更新键值设置分类的可见性
     */
    private void updateKeymapCategoryVisibility() {
        if (mCurrentData == null || mCategoriesLinearLayout == null) return;

        // 如果当前是摇杆类型，隐藏键值设置分类项
        View keymapCategoryItem = mCategoriesLinearLayout.getChildAt(3);
        if (keymapCategoryItem != null) {
        // 按钮控件显示键值设置分类（文本控件不显示）
        if (mCurrentData.type == ControlData.TYPE_BUTTON &&
            mCurrentData.type != ControlData.TYPE_TEXT) {
                keymapCategoryItem.setVisibility(View.VISIBLE);
        } else {
                keymapCategoryItem.setVisibility(View.GONE);
                // 如果当前正在查看键值设置，切换回基本信息
            if (mCurrentCategory == 3) {
                    switchToCategory(0);
                }
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
            ControlTypeManager.updateTypeDisplay(mCurrentData, tvControlType);
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
            ControlShapeManager.updateShapeDisplay(mCurrentData, tvControlShape, itemControlShape);
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
        ControlShapeManager.showShapeSelectDialog(getContext(), mCurrentData, 
            (data) -> {
                updateShapeDisplay();
                notifyUpdate();
            });
    }

    /**
     * 显示类型选择对话框
     */
    private void showTypeSelectDialog() {
        ControlTypeManager.showTypeSelectDialog(getContext(), mCurrentData, 
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
        ControlColorManager.showColorPickerDialog(getContext(), mCurrentData, isBackground,
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

        new MaterialAlertDialogBuilder(getContext())
            .setTitle("删除控件")
            .setMessage("确定要删除这个控件吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                if (mDeleteListener != null) {
                    mDeleteListener.onControlDeleted(mCurrentData);
                }
                dismiss();
            })
            .setNegativeButton("取消", null)
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
        if (copiedData.name != null) {
            copiedData.name = copiedData.name + " 副本";
        } else {
            copiedData.name = "控件 副本";
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
