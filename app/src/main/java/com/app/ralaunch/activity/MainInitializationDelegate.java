package com.app.ralaunch.activity;

import android.content.SharedPreferences;
import android.view.View;

import com.app.ralaunch.R;
import com.app.ralaunch.fragment.InitializationFragment;
import com.app.ralaunch.manager.FragmentNavigator;
import com.app.ralaunch.manager.GameDeletionManager;
import com.app.ralaunch.manager.GameImportManager;
import com.app.ralaunch.manager.GameLaunchManager;
import com.app.ralaunch.manager.GameListManager;
import com.app.ralaunch.manager.PermissionManager;
import com.app.ralaunch.manager.RuntimeSelectorManager;
import com.app.ralaunch.manager.UIManager;
import com.app.ralaunch.manager.ThemeManager;
import com.app.ralaunch.utils.AppLogger;


public class MainInitializationDelegate {

    public static class InitBeforeResult {
        public PermissionManager permissionManager;
        public UIManager uiManager;
        public GameListManager gameListManager;
        public RuntimeSelectorManager runtimeSelectorManager;
    }

    public static class InitAfterResult {
        public FragmentNavigator fragmentNavigator;
        public GameImportManager gameImportManager;
        public GameDeletionManager gameDeletionManager;
        public GameLaunchManager gameLaunchManager;
        public ThemeManager themeManager;
    }

    public InitBeforeResult initBeforeContent(MainActivity activity) {
        InitBeforeResult result = new InitBeforeResult();
        result.permissionManager = new PermissionManager(activity);
        result.permissionManager.initialize();

        result.uiManager = new UIManager(activity);
        result.gameListManager = new GameListManager(activity);
        result.runtimeSelectorManager = new RuntimeSelectorManager(activity);
        return result;
    }

    public InitAfterResult initAfterContent(MainActivity activity, View mainLayout) {
        InitAfterResult result = new InitAfterResult();
        result.fragmentNavigator = new FragmentNavigator(activity.getSupportFragmentManager(),
                R.id.fragmentContainer, mainLayout);
        result.gameImportManager = new GameImportManager(activity, result.fragmentNavigator);
        result.gameDeletionManager = new GameDeletionManager(activity);
        result.gameLaunchManager = new GameLaunchManager(activity);
        result.themeManager = new ThemeManager(activity);
        return result;
    }

    public void showInitializationFragment(MainActivity activity,
                                           FragmentNavigator fragmentNavigator,
                                           UIManager uiManager) {
        if (fragmentNavigator == null) return;
        InitializationFragment initFragment = new InitializationFragment();
        initFragment.setOnInitializationCompleteListener(activity::onInitializationCompleteDelegate);

        fragmentNavigator.getFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, initFragment)
                .commit();

        if (uiManager != null) {
            uiManager.hideMainLayout();
        }
        View fragmentContainer = activity.findViewById(R.id.fragmentContainer);
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.VISIBLE);
        }
    }

    public void onInitializationComplete(MainActivity activity,
                                         UIManager uiManager,
                                         FragmentNavigator fragmentNavigator,
                                         RuntimeSelectorManager runtimeSelectorManager,
                                         View btnRuntimeSelector) {
        if (uiManager != null) {
            uiManager.showMainLayout();
        }
        View fragmentContainer = activity.findViewById(R.id.fragmentContainer);
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.GONE);
        }
        initializeApp(activity, runtimeSelectorManager, fragmentNavigator, btnRuntimeSelector);
    }

    public void initializeApp(MainActivity activity,
                              RuntimeSelectorManager runtimeSelectorManager,
                              FragmentNavigator fragmentNavigator,
                              View btnRuntimeSelector) {
        initializeGameData();
        if (runtimeSelectorManager != null && fragmentNavigator != null && btnRuntimeSelector != null) {
            btnRuntimeSelector.setOnClickListener(v ->
                    runtimeSelectorManager.showRuntimeSelectorDialog(fragmentNavigator.getFragmentManager()));
        }
    }

    private void initializeGameData() {
    }

    public boolean needInitialization(MainActivity activity) {
        SharedPreferences prefs = activity.getSharedPreferences("app_prefs", MainActivity.MODE_PRIVATE);
        boolean componentsExtracted = prefs.getBoolean("components_extracted", false);
        boolean legalAgreed = prefs.getBoolean("legal_agreed", false);
        return (!legalAgreed || !componentsExtracted);
    }
}

