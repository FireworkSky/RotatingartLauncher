package com.app.ralaunch.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;

import androidx.drawerlayout.widget.DrawerLayout;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.editor.ControlEditorManager;
import com.app.ralaunch.manager.GameMenuManager;
import com.app.ralaunch.utils.AppLogger;

/**
 * 负责游戏内抽屉菜单、编辑器按钮等 UI 控制
 */
public class GameMenuController {

    private DrawerLayout drawerLayout;
    private ListView gameMenu;
    private View drawerButton;
    private FrameLayout contentFrame;

    private GameMenuManager menuManager;
    private ControlEditorManager controlEditorManager;

    public void setup(GameActivity activity, ViewGroup sdlRoot, GameVirtualControlsManager controlsManager) {
        try {
            if (sdlRoot == null) {
                AppLogger.error("GameMenuController", "SDL root view is null, cannot setup menu");
                return;
            }

            LayoutInflater inflater = LayoutInflater.from(activity);
            View drawerView = inflater.inflate(R.layout.activity_game, null);

            drawerLayout = drawerView.findViewById(R.id.game_drawer_layout);
            gameMenu = drawerView.findViewById(R.id.game_navigation_view);
            drawerButton = drawerView.findViewById(R.id.game_drawer_button);
            contentFrame = drawerView.findViewById(R.id.game_content_frame);

            if (sdlRoot.getParent() != null) {
                ((ViewGroup) sdlRoot.getParent()).removeView(sdlRoot);
            }
            contentFrame.addView(sdlRoot, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            ViewGroup androidContentView = decorView.findViewById(android.R.id.content);
            androidContentView.removeAllViews();
            androidContentView.addView(drawerLayout, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

            setupDraggableButton(drawerButton, () -> {
                if (controlEditorManager != null) {
                    controlEditorManager.showSettingsDialog();
                }
            });
            drawerButton.postDelayed(() -> drawerButton.setVisibility(View.VISIBLE), 500);

            initializeControlEditorManager(activity, controlsManager);

            menuManager = new GameMenuManager(activity, drawerLayout, gameMenu);
            menuManager.setOnMenuItemClickListener(new GameMenuManager.OnMenuItemClickListener() {
                @Override
                public void onToggleControls() {
                    controlsManager.toggle(activity);
                }

                @Override
                public void onEditControls() {
                    if (controlEditorManager != null) {
                        controlEditorManager.enterEditMode();
                    }
                }

                @Override
                public void onQuickSettings() {
                    if (controlEditorManager != null) {
                        controlEditorManager.showSettingsDialog();
                    }
                }

                @Override
                public void onExitGame() {
                    if (menuManager != null) {
                        menuManager.showExitConfirmDialog();
                    }
                }
            });
            menuManager.setupMenu();
        } catch (Exception e) {
            AppLogger.error("GameMenuController", "Failed to setup game menu", e);
        }
    }

    private void initializeControlEditorManager(GameActivity activity, GameVirtualControlsManager controlsManager) {
        if (controlsManager.getControlLayout() == null || contentFrame == null) return;
        controlEditorManager = new ControlEditorManager(
                activity,
                controlsManager.getControlLayout(),
                contentFrame,
                ControlEditorManager.MODE_IN_GAME
        );
        
        // 设置长按右键监听器
        controlEditorManager.setOnLongPressRightClickChangedListener(enabled -> {
            // 更新GameActivity中的长按检测器状态
            if (activity.longPressDetector != null) {
                activity.longPressDetector.setEnabled(enabled);
            }
        });
        
        // 设置隐藏控件监听器
        controlEditorManager.setOnHideControlsListener(() -> {
            controlsManager.toggle(activity);
        });
        
        // 设置退出游戏监听器
        controlEditorManager.setOnExitGameListener(() -> {
            if (menuManager != null) {
                menuManager.showExitConfirmDialog();
            }
        });
    }

    private void setupDraggableButton(View button, Runnable onClickAction) {
        if (button == null) return;
        button.setOnClickListener(v -> {
            if (onClickAction != null) onClickAction.run();
        });
        button.setOnTouchListener(new View.OnTouchListener() {
            private float lastX, lastY, initialTouchX, initialTouchY, initialButtonX, initialButtonY;
            private boolean isDragging = false;
            private static final float DRAG_THRESHOLD = 10f;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        initialTouchX = lastX;
                        initialTouchY = lastY;
                        initialButtonX = v.getX();
                        initialButtonY = v.getY();
                        isDragging = false;
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - lastX;
                        float dy = event.getRawY() - lastY;
                        v.setX(v.getX() + dx);
                        v.setY(v.getY() + dy);
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        if (Math.abs(event.getRawX() - initialTouchX) > DRAG_THRESHOLD ||
                                Math.abs(event.getRawY() - initialTouchY) > DRAG_THRESHOLD) {
                            isDragging = true;
                        }
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            if (onClickAction != null) onClickAction.run();
                            return false;
                        }
                        android.view.ViewParent parent = v.getParent();
                        if (parent instanceof View) {
                            View parentView = (View) parent;
                            if (v.getX() < 0) v.setX(0);
                            if (v.getY() < 0) v.setY(0);
                            if (v.getX() + v.getWidth() > parentView.getWidth())
                                v.setX(parentView.getWidth() - v.getWidth());
                            if (v.getY() + v.getHeight() > parentView.getHeight())
                                v.setY(parentView.getHeight() - v.getHeight());
                            parent.requestDisallowInterceptTouchEvent(false);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    public void onActivityResultReload() {
        if (controlEditorManager != null) {
            controlEditorManager.exitEditMode();
        }
    }

    public void handleBack(GameVirtualControlsManager controlsManager) {
        if (menuManager != null && menuManager.isMenuOpen()) {
            menuManager.closeMenu();
            return;
        }
        if (controlEditorManager != null && controlEditorManager.isInEditor()) {
            controlEditorManager.exitEditMode();
            return;
        }
        // 返回键不再隐藏控件，用户可以通过游戏设置中的"隐藏控件"按钮来隐藏
    }

    public void stop() {
        // no-op currently
    }
}

