package com.app.ralaunch.activity;

import android.content.Intent;

import androidx.annotation.Nullable;

import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.core.GameLauncher;
import com.app.ralib.patch.Patch;
import com.app.ralib.patch.PatchManager;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralib.error.ErrorHandler;

import java.io.File;
import java.util.ArrayList;

/**
 * 提取游戏启动参数和补丁处理
 */
public class GameLaunchDelegate {

    private static final String TAG = "GameLaunchDelegate";

    public int apply(GameActivity activity, Intent intent) {
        try {
            // 获取程序集路径
            String assemblyPath = intent.getStringExtra("ASSEMBLY_PATH");
            String gameName = intent.getStringExtra("GAME_NAME");
            if (assemblyPath == null || assemblyPath.isEmpty()) {
                AppLogger.error(TAG, "Assembly path is null or empty");
                activity.runOnUiThread(() ->
                        ErrorHandler.showWarning(activity.getString(R.string.game_launch_failed), 
                                activity.getString(R.string.game_launch_assembly_path_empty)));
                return -1;
            }

            // 验证程序集文件是否存在
            File assemblyFile = new File(assemblyPath);
            if (!assemblyFile.exists() || !assemblyFile.isFile()) {
                AppLogger.error(TAG, "Assembly file not found: " + assemblyPath);
                activity.runOnUiThread(() ->
                        ErrorHandler.showWarning(activity.getString(R.string.game_launch_failed), 
                                activity.getString(R.string.game_launch_assembly_not_exist, assemblyPath)));
                return -2;
            }

            ArrayList<String> enabledPatchIds = intent.getStringArrayListExtra("ENABLED_PATCH_IDS");

            @Nullable ArrayList<Patch> enabledPatches = null;
            if (enabledPatchIds != null && !enabledPatchIds.isEmpty()) {
                PatchManager patchManager = RaLaunchApplication.getPatchManager();
                enabledPatches = patchManager.getPatchesByIds(enabledPatchIds);
            }

            int result = GameLauncher.launchAssemblyDirect(activity, assemblyPath, enabledPatches);

            if (result != 0) {
                AppLogger.error(TAG, "Failed to set launch parameters: " + result);
                int finalResult = result;
                activity.runOnUiThread(() ->
                        ErrorHandler.showWarning(activity.getString(R.string.game_launch_failed), 
                                activity.getString(R.string.game_launch_set_params_failed, String.valueOf(finalResult))));
                return result;
            }
            return 0;
        } catch (Exception e) {
            AppLogger.error(TAG, "Exception in setLaunchParams: " + e.getMessage(), e);
            activity.runOnUiThread(() -> ErrorHandler.handleError(activity.getString(R.string.game_launch_failed), e, false));
            return -3;
        }
    }
}

