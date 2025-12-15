package com.app.ralaunch.manager;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.drawerlayout.widget.DrawerLayout;

import com.app.ralaunch.R;
import com.app.ralaunch.utils.AppLogger;

/**
 * 游戏菜单管理器
 */
public class GameMenuManager {
    private static final String TAG = "GameMenuManager";
    
    private Context mContext;
    private DrawerLayout mDrawerLayout;
    private ListView mGameMenu;
    private GameMenuAdapter mGameMenuAdapter;
    
    private OnMenuItemClickListener mMenuItemListener;
    
    // 菜单项数据
    private static class MenuItem {
        int iconRes;
        String text;
        
        MenuItem(int iconRes, String text) {
            this.iconRes = iconRes;
            this.text = text;
        }
    }
    
    private MenuItem[] mMenuItems;
    
    /**
     * 菜单项点击监听器
     */
    public interface OnMenuItemClickListener {
        void onToggleControls();
        void onEditControls();
        void onQuickSettings();
        void onExitGame();
    }
    
    public GameMenuManager(Context context, DrawerLayout drawerLayout, ListView gameMenu) {
        mContext = context;
        mDrawerLayout = drawerLayout;
        mGameMenu = gameMenu;
        
        // 初始化菜单项
        String[] menuTexts = context.getResources().getStringArray(R.array.game_menu_items);
        mMenuItems = new MenuItem[]{
            new MenuItem(R.drawable.ic_controller, menuTexts[0]), // 切换控制显示
            new MenuItem(R.drawable.ic_edit, menuTexts[1]), // 编辑控制布局
            new MenuItem(R.drawable.ic_settings, menuTexts[2]), // 快速设置
            new MenuItem(R.drawable.ic_close, menuTexts[3]) // 退出游戏
        };
    }
    
    /**
     * 设置菜单项点击监听器
     */
    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        mMenuItemListener = listener;
    }
    
    /**
     * 初始化菜单
     */
    public void setupMenu() {
        try {
            // 设置菜单项适配器
            mGameMenuAdapter = new GameMenuAdapter();
            mGameMenu.setAdapter(mGameMenuAdapter);
            
            // 移除默认分隔线，使用卡片样式
            mGameMenu.setDivider(null);
            mGameMenu.setDividerHeight(0);

        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to setup game menu", e);
        }
    }
    
    /**
     * MD3 风格菜单适配器
     */
    private class GameMenuAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mMenuItems != null ? mMenuItems.length : 0;
        }
        
        @Override
        public Object getItem(int position) {
            return mMenuItems != null ? mMenuItems[position] : null;
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.item_game_menu, parent, false);
            }
            
            MenuItem item = mMenuItems[position];
            ImageView iconView = convertView.findViewById(R.id.iv_menu_icon);
            TextView textView = convertView.findViewById(R.id.tv_menu_text);
            
            iconView.setImageResource(item.iconRes);
            textView.setText(item.text);
            
            // 直接在根 View 上设置点击监听器，确保点击事件被正确处理
            convertView.setOnClickListener(v -> {
                handleMenuClick(position);
                if (mDrawerLayout != null) {
                    mDrawerLayout.closeDrawers();
                }
            });
            
            return convertView;
        }
    }
    
    /**
     * 处理菜单点击事件
     */
    private void handleMenuClick(int position) {
        if (mMenuItemListener == null) return;
        
        switch (position) {
            case 0: // 切换控制显示
                mMenuItemListener.onToggleControls();
                break;
            case 1: // 编辑控制布局
                mMenuItemListener.onEditControls();
                break;
            case 2: // 快速设置
                mMenuItemListener.onQuickSettings();
                break;
            case 3: // 退出游戏
                mMenuItemListener.onExitGame();
                break;
        }
    }
    
    /**
     * 打开菜单
     */
    public void openMenu() {
        if (mDrawerLayout != null && mGameMenu != null) {
            mDrawerLayout.openDrawer(mGameMenu);
        }
    }
    
    /**
     * 关闭菜单
     */
    public void closeMenu() {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawers();
        }
    }
    
    /**
     * 检查菜单是否打开
     */
    public boolean isMenuOpen() {
        return mDrawerLayout != null && mGameMenu != null && mDrawerLayout.isDrawerOpen(mGameMenu);
    }
    
    /**
     * 显示退出确认对话框
     */
    public void showExitConfirmDialog() {
        new AlertDialog.Builder(mContext)
            .setTitle(R.string.game_menu_exit_confirm)
            .setMessage(R.string.game_menu_exit_message)
            .setPositiveButton(R.string.game_menu_yes, (dialog, which) -> {
                if (mContext instanceof android.app.Activity) {
                    ((android.app.Activity) mContext).finish();
                }
            })
            .setNegativeButton(R.string.game_menu_no, null)
            .show();
    }
    

}

