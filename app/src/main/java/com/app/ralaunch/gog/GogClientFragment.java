package com.app.ralaunch.gog;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralaunch.R;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralib.error.ErrorHandler;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GOG 客户端界面 Fragment - 现代化 MD3 设计
 * 提供 GOG 游戏库的登录、浏览和下载功能
 */
public class GogClientFragment extends Fragment {
    private static final String TAG = "GogClientFragment";

    private GogApiClient apiClient;
    private GogGameAdapter gameAdapter;
    private List<GogApiClient.GogGame> allGames = new ArrayList<>();

    // UI 组件
    private LinearLayout loginContainer;
    private LinearLayout loggedInContainer;
    private TextInputEditText editUsername;
    private TextInputEditText editPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnVisitGog;
    private MaterialButton btnLogout;
    private RecyclerView gamesRecyclerView;
    private TextInputEditText editSearch;
    private FrameLayout loadingLayout;
    private TextView loadingText;
    private LinearLayout emptyState;
    private ImageView gogLogoImage;

    // 用户信息组件
    private ShapeableImageView userAvatar;
    private TextView userName;
    private TextView userEmail;
    private TextView chipGameCount;

    private ExecutorService downloadExecutor;
    private List<GogApiClient.GogGame> filteredGames = new ArrayList<>();
    private ModLoaderConfigManager modLoaderConfigManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiClient = new GogApiClient(requireContext());
        downloadExecutor = Executors.newSingleThreadExecutor();
        modLoaderConfigManager = new ModLoaderConfigManager(requireContext());

        // 设置两步验证回调
        apiClient.setTwoFactorCallback(this::showTwoFactorDialog);
    }

    /**
     * 显示两步验证对话框
     */
    private String showTwoFactorDialog(String type) {
        final String[] result = {null};
        final Object lock = new Object();

        try {
            requireActivity().runOnUiThread(() -> {
                try {
                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_two_factor, null);
                    TextInputLayout codeLayout = dialogView.findViewById(R.id.codeLayout);
                    TextInputEditText editCode = dialogView.findViewById(R.id.editSecurityCode);
                    TextView tvTitle = dialogView.findViewById(R.id.tvTwoFactorTitle);
                    TextView tvMessage = dialogView.findViewById(R.id.tvTwoFactorMessage);

                    if ("email".equals(type)) {
                        tvTitle.setText(getString(R.string.gog_email_verification));
                        tvMessage.setText(getString(R.string.gog_email_code_prompt));
                        codeLayout.setHint(getString(R.string.gog_4_digit_code));
                    } else {
                        tvTitle.setText(getString(R.string.gog_authenticator));
                        tvMessage.setText(getString(R.string.gog_totp_code_prompt));
                        codeLayout.setHint(getString(R.string.gog_6_digit_code));
                    }

                    new MaterialAlertDialogBuilder(requireContext())
                            .setView(dialogView)
                            .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                                synchronized (lock) {
                                    result[0] = editCode.getText() != null ? editCode.getText().toString() : "";
                                    lock.notify();
                                }
                            })
                            .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                                synchronized (lock) {
                                    lock.notify();
                                }
                            })
                            .setCancelable(false)
                            .show();
                } catch (Exception e) {
                    AppLogger.error(TAG, "创建对话框时发生异常", e);
                    synchronized (lock) {
                        lock.notify(); // 发生错误时也要唤醒等待线程
                    }
                }
            });

            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    AppLogger.error(TAG, "等待验证码输入被中断", e);
                }
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "showTwoFactorDialog 发生异常", e);
        }

        return result[0];
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gog_client, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化 UI 组件
        initViews(view);

        // 设置监听器
        setupListeners();

        // 检查登录状态
        updateLoginState();
    }

    private void initViews(View view) {
        // 登录界面组件
        loginContainer = view.findViewById(R.id.loginContainer);
        editUsername = view.findViewById(R.id.editUsername);
        editPassword = view.findViewById(R.id.editPassword);
        btnLogin = view.findViewById(R.id.btnLogin);
        btnVisitGog = view.findViewById(R.id.btnVisitGog);

        // 已登录界面组件
        loggedInContainer = view.findViewById(R.id.loggedInContainer);
        userAvatar = view.findViewById(R.id.userAvatar);
        userName = view.findViewById(R.id.userName);
        userEmail = view.findViewById(R.id.userEmail);
        chipGameCount = view.findViewById(R.id.chipGameCount);
        editSearch = view.findViewById(R.id.editSearch);
        btnLogout = view.findViewById(R.id.btnLogout);
        gamesRecyclerView = view.findViewById(R.id.gamesRecyclerView);
        emptyState = view.findViewById(R.id.emptyState);

        // 加载组件
        loadingLayout = view.findViewById(R.id.loadingLayout);
        loadingText = view.findViewById(R.id.loadingText);

        // GOG Logo - 使用布局文件中的静态资源
        gogLogoImage = view.findViewById(R.id.gogLogoImage);

        // 设置游戏列表 - 横屏网格布局
        gameAdapter = new GogGameAdapter(new ArrayList<>(), this::onGameClick);
        gameAdapter.setViewType(true); // 默认网格视图
        androidx.recyclerview.widget.GridLayoutManager gridLayoutManager = 
            new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3); // 3列网格
        gamesRecyclerView.setLayoutManager(gridLayoutManager);
        gamesRecyclerView.setAdapter(gameAdapter);
    }

    private void setupListeners() {
        // 登录按钮
        btnLogin.setOnClickListener(v -> startLogin());

        // 访问 GOG 官网
        if (btnVisitGog != null) {
            btnVisitGog.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gog.com"));
                startActivity(intent);
            });
        }

        // 搜索框
        if (editSearch != null) {
            editSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterGames(s.toString());
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // 登出按钮
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
        }
    }

    /**
     * 搜索过滤游戏
     */
    private void filterGames(String query) {
        if (query == null || query.trim().isEmpty()) {
            // 显示所有游戏
            filteredGames.clear();
            filteredGames.addAll(allGames);
        } else {
            // 根据标题过滤
            filteredGames.clear();
            String lowerQuery = query.toLowerCase();
            for (GogApiClient.GogGame game : allGames) {
                if (game.title != null && game.title.toLowerCase().contains(lowerQuery)) {
                    filteredGames.add(game);
                }
            }
        }
        
        // 更新适配器
            if (gameAdapter != null) {
            gameAdapter.updateGames(filteredGames);
        }
        
        // 更新空状态
        if (emptyState != null && gamesRecyclerView != null) {
            emptyState.setVisibility(filteredGames.isEmpty() ? View.VISIBLE : View.GONE);
            gamesRecyclerView.setVisibility(filteredGames.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * 更新游戏数量显示
     */
    private void updateGameCount(int count) {
        if (chipGameCount != null) {
            chipGameCount.setText(getString(R.string.gog_games_count, count));
        }
    }

    /**
     * 更新空状态显示
     */
    private void updateEmptyState() {
        if (emptyState != null && gamesRecyclerView != null) {
            boolean isEmpty = gameAdapter.getItemCount() == 0;
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            gamesRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * 更新登录状态显示
     */
    private void updateLoginState() {
        if (apiClient.isLoggedIn()) {
            loginContainer.setVisibility(View.GONE);
            loggedInContainer.setVisibility(View.VISIBLE);

            // 加载用户信息和游戏列表
            loadUserInfoAndGames();
        } else {
            loginContainer.setVisibility(View.VISIBLE);
            loggedInContainer.setVisibility(View.GONE);
        }
    }

    /**
     * 加载用户信息和游戏列表
     */
    private void loadUserInfoAndGames() {
        showLoading(getString(R.string.gog_loading_user_info));

        new Thread(() -> {
            try {
                // 获取用户信息
                GogApiClient.UserInfo userInfo = apiClient.getUserInfo();

                requireActivity().runOnUiThread(() -> {
                    if (userInfo != null) {
                        if (userName != null) {
                            userName.setText(userInfo.username);
                        }
                        if (userEmail != null) {
                            userEmail.setText(userInfo.email.isEmpty() ? getString(R.string.gog_logged_in) : userInfo.email);
                        }

                        // 加载用户头像
                        if (userAvatar != null && userInfo.avatarUrl != null && !userInfo.avatarUrl.isEmpty()) {
                            Glide.with(GogClientFragment.this)
                                    .load(userInfo.avatarUrl)
                                    .placeholder(R.drawable.ic_person)
                                    .error(R.drawable.ic_person)
                                    .circleCrop()
                                    .into(userAvatar);
                        }
                    }
                });

                // 刷新游戏列表（在主线程中调用 showLoading）
                requireActivity().runOnUiThread(() -> refreshGames());
            } catch (IOException e) {
                AppLogger.error(TAG, "加载用户信息失败", e);
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    // 即使用户信息加载失败，也继续刷新游戏列表
                    refreshGames();
                });
            }
        }).start();
    }

    /**
     * 开始登录
     */
    private void startLogin() {
        String username = editUsername.getText() != null ? editUsername.getText().toString().trim() : "";
        String password = editPassword.getText() != null ? editPassword.getText().toString().trim() : "";

        if (username.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.gog_enter_email), Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.gog_enter_password), Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(getString(R.string.gog_logging_in));

        new Thread(() -> {
            try {
                boolean success = apiClient.loginWithCredentials(username, password);

                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    if (success) {
                        Toast.makeText(requireContext(), getString(R.string.gog_login_success), Toast.LENGTH_SHORT).show();
                        // 清空密码
                        editPassword.setText("");
                        updateLoginState();
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.gog_login_failed), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                AppLogger.error(TAG, "登录异常", e);
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    // 使用统一的错误弹窗
                    com.app.ralib.error.ErrorHandler.handleError(
                        getString(R.string.gog_login_error, e.getMessage()),
                        e
                    );
                });
            }
        }).start();
    }

    /**
     * 刷新游戏列表
     */
    private void refreshGames() {
        showLoading(getString(R.string.gog_loading_games));

        new Thread(() -> {
            try {
                List<GogApiClient.GogGame> games = apiClient.getOwnedGames();

                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    allGames = new ArrayList<>(games);
                    filteredGames = new ArrayList<>(games);
                    gameAdapter.updateGames(filteredGames);
                    updateGameCount(games.size());
                    updateEmptyState();

                    if (games.isEmpty()) {
                        Toast.makeText(requireContext(), getString(R.string.gog_library_empty), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(),
                                getString(R.string.gog_games_count, games.size()),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                AppLogger.error(TAG, "获取游戏列表失败", e);
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    // 使用统一的错误弹窗
                    com.app.ralib.error.ErrorHandler.handleError(
                        getString(R.string.gog_load_games_failed, e.getMessage()),
                        e
                    );
                });
            }
        }).start();
    }

    /**
     * 登出
     */
    private void logout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.gog_logout_confirm_title))
                .setMessage(getString(R.string.gog_logout_confirm_message))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    apiClient.logout();
                    allGames.clear();
                    filteredGames.clear();
                    gameAdapter.updateGames(new ArrayList<>());
                    if (editSearch != null) {
                        editSearch.setText("");
                    }
                    updateLoginState();
                    Toast.makeText(requireContext(), getString(R.string.gog_logged_out), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    /**
     * 处理游戏点击 - 显示版本选择对话框
     */
    private void onGameClick(GogApiClient.GogGame game) {
        if (!isAdded()) return;
        
        // 检查是否有 ModLoader 规则
        ModLoaderConfigManager.ModLoaderRule rule = modLoaderConfigManager.getRule(game.id);
        if (rule == null) {
            Toast.makeText(requireContext(), getString(R.string.gog_one_click_not_supported), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 显示加载状态
        showLoading(getString(R.string.gog_loading_version_info, game.title));

        new Thread(() -> {
            try {
                GogApiClient.GameDetails details = apiClient.getGameDetails(String.valueOf(game.id));

                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    // 显示版本选择对话框
                    showVersionSelectionDialog(game, details, rule);
                });
            } catch (IOException e) {
                AppLogger.error(TAG, "获取游戏详情失败", e);
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    
                    // 根据错误类型给出友好提示
                    String errorMsg;
                    if (e.getMessage() != null && e.getMessage().contains("connection abort")) {
                        errorMsg = getString(R.string.gog_network_abort);
                    } else if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                        errorMsg = getString(R.string.gog_network_timeout);
                    } else if (e.getMessage() != null && e.getMessage().contains("DNS")) {
                        errorMsg = getString(R.string.gog_network_dns);
                    } else {
                        errorMsg = getString(R.string.gog_get_details_failed);
                    }
                    
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.gog_load_failed_title))
                            .setMessage(errorMsg + "\n\n" + getString(R.string.gog_error_details, e.getMessage()))
                            .setPositiveButton(getString(R.string.gog_retry), (dialog, which) -> onGameClick(game))
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show();
                });
            }
        }).start();
    }

    /**
     * 显示版本选择对话框
     */
    private void showVersionSelectionDialog(GogApiClient.GogGame game, 
                                           GogApiClient.GameDetails details,
                                           ModLoaderConfigManager.ModLoaderRule rule) {
        if (!isAdded()) return;
        
        // 构建游戏版本列表
        List<GogApiClient.GameFile> gameVersions = details.installers;
        if (gameVersions.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.gog_no_game_version), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 构建ModLoader版本列表
        List<ModLoaderConfigManager.ModLoaderVersion> modLoaderVersions = rule.versions;
        if (modLoaderVersions.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.gog_no_modloader_version), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 选中的索引
        final int[] selectedGameVersion = {0};
        final int[] selectedModLoaderVersion = {0};
        
        // 构建游戏版本字符串数组
        String[] gameVersionNames = new String[gameVersions.size()];
        for (int i = 0; i < gameVersions.size(); i++) {
            GogApiClient.GameFile file = gameVersions.get(i);
            gameVersionNames[i] = file.version + " (" + file.getSizeFormatted() + ")";
        }
        
        // 构建ModLoader版本字符串数组
        String[] modLoaderVersionNames = new String[modLoaderVersions.size()];
        for (int i = 0; i < modLoaderVersions.size(); i++) {
            modLoaderVersionNames[i] = modLoaderVersions.get(i).getDisplayString(requireContext());
        }
        
        // 加载 XML 布局
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_version_selection, null);
        
        // 获取视图组件
        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvModLoaderTitle = dialogView.findViewById(R.id.tvModLoaderTitle);
        android.widget.Spinner spinnerGameVersion = dialogView.findViewById(R.id.spinnerGameVersion);
        android.widget.Spinner spinnerModLoaderVersion = dialogView.findViewById(R.id.spinnerModLoaderVersion);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnInstall = dialogView.findViewById(R.id.btnInstall);
        
        // 设置标题
        tvDialogTitle.setText(getString(R.string.gog_install_game, game.title));
        tvModLoaderTitle.setText(getString(R.string.gog_modloader_version, rule.name));
        
        // 设置游戏版本适配器
        android.widget.ArrayAdapter<String> gameAdapter = new android.widget.ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, gameVersionNames);
        gameAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGameVersion.setAdapter(gameAdapter);
        spinnerGameVersion.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                selectedGameVersion[0] = position;
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        
        // 设置ModLoader版本适配器
        android.widget.ArrayAdapter<String> modLoaderAdapter = new android.widget.ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, modLoaderVersionNames);
        modLoaderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModLoaderVersion.setAdapter(modLoaderAdapter);
        spinnerModLoaderVersion.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                selectedModLoaderVersion[0] = position;
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        
        // 创建对话框 - 使用自定义的 GOG 对话框样式（透明背景）
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.GogDialogStyle)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        
        // 设置按钮点击事件
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnInstall.setOnClickListener(v -> {
            GogApiClient.GameFile selectedGame = gameVersions.get(selectedGameVersion[0]);
            ModLoaderConfigManager.ModLoaderVersion selectedModLoader = modLoaderVersions.get(selectedModLoaderVersion[0]);
            dialog.dismiss();
            startModLoaderFlow(game, details, selectedGame, selectedModLoader, rule);
        });
        
        dialog.show();
        
        // 只移除窗口装饰和内边距，不设置窗口大小（由 XML 控制）
        if (dialog.getWindow() != null) {
            android.view.Window window = dialog.getWindow();
            // 移除窗口装饰视图的所有内边距
            android.view.View decorView = window.getDecorView();
            decorView.setPadding(0, 0, 0, 0);
            // 设置窗口背景为完全透明
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }



    private GogDownloadProgressDialog progressDialog;

    /**
     * 针对有 ModLoader 规则的游戏，执行下载+安装流程
     */
    private void startModLoaderFlow(GogApiClient.GogGame game,
                                    GogApiClient.GameDetails details,
                                    GogApiClient.GameFile installer,
                                    ModLoaderConfigManager.ModLoaderVersion modLoaderVersion,
                                    ModLoaderConfigManager.ModLoaderRule rule) {
        if (!isAdded()) return;

        // 显示进度对话框
        String gameFileName = installer.name != null && !installer.name.isEmpty() 
                ? installer.name : game.title;
        String modLoaderName = rule.name + " " + modLoaderVersion.version;
        
        progressDialog = GogDownloadProgressDialog.newInstance(gameFileName, modLoaderName);
        progressDialog.setOnCancelListener(() -> {
        });
        progressDialog.show(getParentFragmentManager(), "gog_download_progress");

        Context appContext = requireContext().getApplicationContext();

        downloadExecutor.execute(() -> {
            File external = appContext.getExternalFilesDir(null);
            if (external == null) {
                requireActivity().runOnUiThread(() -> {
                    if (progressDialog != null) progressDialog.dismiss();
                    Toast.makeText(requireContext(), getString(R.string.gog_cannot_access_storage), Toast.LENGTH_LONG).show();
                });
                return;
            }

            // 获取安装程序链接
            requireActivity().runOnUiThread(() -> {
                if (progressDialog != null) {
                    progressDialog.setGameDownloadStatus(getString(R.string.gog_download_status_getting_link));
                }
            });
            String installerUrl = resolveDownloadUrl(game.id, installer);
            if (installerUrl == null || installerUrl.isEmpty()) {
                requireActivity().runOnUiThread(() -> {
                    if (progressDialog != null) progressDialog.dismiss();
                    Toast.makeText(requireContext(), getString(R.string.gog_cannot_get_download_link), Toast.LENGTH_SHORT).show();
                });
            return;
        }

            // 校验 ModLoader 链接
            requireActivity().runOnUiThread(() -> {
                if (progressDialog != null) {
                    progressDialog.setModLoaderDownloadStatus(getString(R.string.gog_download_status_getting_link));
                }
            });
            String modLoaderUrl = modLoaderVersion.url;
            if (modLoaderUrl == null || modLoaderUrl.isEmpty()) {
                requireActivity().runOnUiThread(() -> {
                    if (progressDialog != null) progressDialog.dismiss();
                    Toast.makeText(requireContext(), getString(R.string.gog_modloader_link_invalid), Toast.LENGTH_SHORT).show();
                });
                return;
            }
            File downloadDir = new File(external, "gog_downloads/" + game.id);
            File installerFile = new File(downloadDir, sanitizeFileName(
                    installer.name != null && !installer.name.isEmpty() ? installer.name : game.title) + ".sh");
            File modLoaderFile = new File(downloadDir, sanitizeFileName(
                    modLoaderVersion.fileName != null && !modLoaderVersion.fileName.isEmpty() ? 
                    modLoaderVersion.fileName : "modloader.zip"));

            try {
                // 下载游戏本体
                String downloadingStatus = getString(R.string.gog_download_status_downloading);
                String completedStatus = getString(R.string.gog_download_status_completed);
                requireActivity().runOnUiThread(() -> {
                    if (progressDialog != null) {
                        progressDialog.setGameDownloadStatus(downloadingStatus);
                    }
                });
                apiClient.downloadWithAuth(installerUrl, installerFile,
                        (downloaded, total) -> {
                            if (progressDialog != null) {
                                progressDialog.updateGameDownloadProgress(downloaded, total, downloadingStatus);
                            }
                        });
                requireActivity().runOnUiThread(() -> {
                    if (progressDialog != null) {
                        progressDialog.setGameDownloadStatus(completedStatus);
                    }
                });

                // 下载 ModLoader
                requireActivity().runOnUiThread(() -> {
                    if (progressDialog != null) {
                        progressDialog.setModLoaderDownloadStatus(downloadingStatus);
                    }
                });
                apiClient.downloadWithAuth(modLoaderUrl, modLoaderFile,
                        (downloaded, total) -> {
                            if (progressDialog != null) {
                                progressDialog.updateModLoaderDownloadProgress(downloaded, total, downloadingStatus);
                            }
                        });
                requireActivity().runOnUiThread(() -> {
                    if (progressDialog != null) {
                        progressDialog.setModLoaderDownloadStatus(completedStatus);
                    }
                });

                // 下载完成，使用导入游戏相同的 API 进行安装
                requireActivity().runOnUiThread(() -> {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                    }
                    
                    // 使用 MainActivity 的 startGameImport 方法，复用导入游戏的逻辑
                    if (getActivity() instanceof com.app.ralaunch.activity.MainActivity) {
                        com.app.ralaunch.activity.MainActivity activity = 
                                (com.app.ralaunch.activity.MainActivity) getActivity();
                        
                        // 调用 MainActivity 的导入方法
                        // 这会显示 LocalImportFragment 并使用相同的安装流程
                        Bundle args = new Bundle();
                        args.putString("gameFilePath", installerFile.getAbsolutePath());
                        args.putString("modLoaderFilePath", modLoaderFile.getAbsolutePath());
                        args.putString("gameName", game.title);
                        args.putString("gameVersion", installer.version);
                        
                        com.app.ralaunch.fragment.LocalImportFragment importFragment = 
                                new com.app.ralaunch.fragment.LocalImportFragment();
                        importFragment.setArguments(args);
                        importFragment.setOnImportCompleteListener((gameType, newGame) -> {
                            com.app.ralib.utils.FileUtils.deleteDirectoryRecursively(installerFile);
                            com.app.ralib.utils.FileUtils.deleteDirectoryRecursively(modLoaderFile);
                            com.app.ralib.utils.FileUtils.deleteDirectoryRecursively(downloadDir);
                            
                            // 使用 MainActivity 的 onImportComplete 方法添加游戏到列表
                            // 但不要重复添加，所以先检查 LocalImportFragment 是否已经添加了
                            activity.onImportComplete(gameType, newGame);
                            
                            Toast.makeText(requireContext(), 
                                    getString(R.string.gog_installed_and_added, newGame.getGameName()), 
                                    Toast.LENGTH_SHORT).show();
                        });
                        importFragment.setOnBackListener(() -> {
                            com.app.ralib.utils.FileUtils.deleteDirectoryRecursively(installerFile);
                            com.app.ralib.utils.FileUtils.deleteDirectoryRecursively(modLoaderFile);
                            com.app.ralib.utils.FileUtils.deleteDirectoryRecursively(downloadDir);
                        });
                        
                        // 显示导入页面
                        View importPage = activity.findViewById(R.id.importPage);
                        if (importPage != null) {
                            // 先切换到导入页面
                            importPage.setVisibility(View.VISIBLE);
                            View gameListPage = activity.findViewById(R.id.gameListPage);
                            View fileManagerPage = activity.findViewById(R.id.fileManagerPage);
                            View controlPage = activity.findViewById(R.id.controlPage);
                            View downloadPage = activity.findViewById(R.id.downloadPage);
                            View settingsPage = activity.findViewById(R.id.settingsPage);
                            if (gameListPage != null) gameListPage.setVisibility(View.GONE);
                            if (fileManagerPage != null) fileManagerPage.setVisibility(View.GONE);
                            if (controlPage != null) controlPage.setVisibility(View.GONE);
                            if (downloadPage != null) downloadPage.setVisibility(View.GONE);
                            if (settingsPage != null) settingsPage.setVisibility(View.GONE);
                            
                            // 然后显示导入 Fragment
                            activity.getSupportFragmentManager().beginTransaction()
                                    .replace(R.id.importPage, importFragment, "gog_game_import")
                                    .addToBackStack("gog_import")
                                    .commit();
                        }
                    }
                });
                
            } catch (Exception e) {
                AppLogger.error(TAG, "下载或安装失败", e);
                requireActivity().runOnUiThread(() -> {
                    if (progressDialog != null) progressDialog.dismiss();
                    // 使用统一的错误弹窗
                    com.app.ralib.error.ErrorHandler.handleError(
                        getString(R.string.gog_download_failed, e.getMessage()),
                        e
                    );
                });
            }
        });
    }

    private void updateLoadingProgress(String title, long downloaded, long total) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            String progressText = total > 0
                    ? String.format("%s %.1f%%", title, downloaded * 100f / total)
                    : title + " " + downloaded / 1024 + "KB";
            showLoading(progressText);
        });
    }

    private String resolveDownloadUrl(long productId, GogApiClient.GameFile file) {
        if (file == null) return null;
        if (file.manualUrl != null && !file.manualUrl.isEmpty()) {
            return file.manualUrl;
        }
        if (file.path == null || file.path.isEmpty()) {
            return null;
        }
        String normalizedPath = normalizeSecurePath(file.path);
        try {
            JSONObject json = apiClient.getSecureLink(String.valueOf(productId), normalizedPath);
            if (json != null) {
                String link = json.optString("link", "");
                if (link.isEmpty()) {
                    link = json.optString("download_link", "");
                }
                if (link.isEmpty()) {
                    link = json.optString("href", "");
                }
                return link;
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "获取安全下载链接失败", e);
        }
        return null;
    }

    private String normalizeSecurePath(String rawPath) {
        return rawPath == null ? "" : rawPath;
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) return "gog_file";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }


    /**
     * 显示加载进度
     */
    private void showLoading(String message) {
        if (loadingText != null) {
            loadingText.setText(message);
        }
        if (loadingLayout != null) {
            loadingLayout.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 隐藏加载进度
     */
    private void hideLoading() {
        if (loadingLayout != null) {
            loadingLayout.setVisibility(View.GONE);
        }
    }
}
