package com.app.ralaunch.gog;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralaunch.R;
import com.app.ralaunch.utils.AppLogger;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    private Toolbar toolbar;
    private Toolbar toolbarLoggedIn;
    private LinearLayout loginContainer;
    private LinearLayout loggedInContainer;
    private MaterialCardView loginCard;
    private MaterialCardView gamesCard;
    private TextInputEditText editUsername;
    private TextInputEditText editPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnVisitGog;
    private MaterialButton btnRefresh;
    private MaterialButton btnLogout;
    private MaterialButton btnViewToggle;
    private RecyclerView gamesRecyclerView;
    private FrameLayout loadingLayout;
    private TextView loadingText;
    private LinearLayout emptyState;
    private ImageView gogLogoImage;

    // 用户信息组件
    private ShapeableImageView userAvatar;
    private TextView userName;
    private TextView userEmail;
    private TextView chipGameCount;

    private boolean isGridView = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiClient = new GogApiClient(requireContext());

        // 设置两步验证回调
        apiClient.setTwoFactorCallback(this::showTwoFactorDialog);
    }

    /**
     * 显示两步验证对话框
     */
    private String showTwoFactorDialog(String type) {
        AppLogger.info(TAG, "showTwoFactorDialog 被调用，类型: " + type);
        final String[] result = {null};
        final Object lock = new Object();

        try {
            requireActivity().runOnUiThread(() -> {
                try {
                    AppLogger.info(TAG, "开始在UI线程创建对话框");
                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_two_factor, null);
                    TextInputEditText editCode = dialogView.findViewById(R.id.editSecurityCode);
                    TextView tvTitle = dialogView.findViewById(R.id.tvTwoFactorTitle);
                    TextView tvMessage = dialogView.findViewById(R.id.tvTwoFactorMessage);

                    // 根据验证类型设置提示
                    if ("email".equals(type)) {
                        tvTitle.setText("邮箱验证");
                        tvMessage.setText("请输入发送到您邮箱的 4 位验证码");
                        editCode.setHint("4位验证码");
                    } else {
                        tvTitle.setText("身份验证器");
                        tvMessage.setText("请输入您的 TOTP 验证器中的 6 位验证码");
                        editCode.setHint("6位验证码");
                    }

                    AppLogger.info(TAG, "准备显示MaterialAlertDialog");
                    new MaterialAlertDialogBuilder(requireContext())
                            .setView(dialogView)
                            .setPositiveButton("确定", (dialog, which) -> {
                                AppLogger.info(TAG, "用户点击确定按钮");
                                synchronized (lock) {
                                    result[0] = editCode.getText() != null ? editCode.getText().toString() : "";
                                    AppLogger.info(TAG, "唤醒等待线程，验证码长度: " + (result[0] != null ? result[0].length() : 0));
                                    lock.notify();
                                }
                            })
                            .setNegativeButton("取消", (dialog, which) -> {
                                AppLogger.info(TAG, "用户点击取消按钮");
                                synchronized (lock) {
                                    lock.notify();
                                }
                            })
                            .setCancelable(false)
                            .show();
                    AppLogger.info(TAG, "对话框已显示");
                } catch (Exception e) {
                    AppLogger.error(TAG, "创建对话框时发生异常", e);
                    synchronized (lock) {
                        lock.notify(); // 发生错误时也要唤醒等待线程
                    }
                }
            });

            // 等待用户输入
            AppLogger.info(TAG, "开始等待用户输入验证码");
            synchronized (lock) {
                try {
                    lock.wait();
                    AppLogger.info(TAG, "等待结束，返回结果: " + (result[0] != null ? "有值" : "null"));
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
        // 工具栏
        toolbar = view.findViewById(R.id.toolbar);
        toolbarLoggedIn = view.findViewById(R.id.toolbarLoggedIn);

        // 登录界面组件
        loginContainer = view.findViewById(R.id.loginContainer);
        loginCard = view.findViewById(R.id.loginCard);
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
        btnRefresh = view.findViewById(R.id.btnRefresh);
        btnViewToggle = view.findViewById(R.id.btnViewToggle);
        btnLogout = view.findViewById(R.id.btnLogout);
        gamesCard = view.findViewById(R.id.gamesCard);
        gamesRecyclerView = view.findViewById(R.id.gamesRecyclerView);
        emptyState = view.findViewById(R.id.emptyState);

        // 加载组件
        loadingLayout = view.findViewById(R.id.loadingLayout);
        loadingText = view.findViewById(R.id.loadingText);

        // GOG Logo - 使用布局文件中的静态资源
        gogLogoImage = view.findViewById(R.id.gogLogoImage);

        // 设置工具栏返回
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        if (toolbarLoggedIn != null) {
            toolbarLoggedIn.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        }

        // 设置游戏列表
        gameAdapter = new GogGameAdapter(new ArrayList<>(), this::onGameClick);
        gamesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
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

        // 刷新按钮
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> refreshGames());
        }

        // 登出按钮
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
        }

        // 视图切换按钮
        if (btnViewToggle != null) {
            btnViewToggle.setOnClickListener(v -> toggleViewMode());
        }
    }

    /**
     * 切换视图模式
     */
    private void toggleViewMode() {
        isGridView = !isGridView;
        if (btnViewToggle != null) {
            btnViewToggle.setIconResource(isGridView ? R.drawable.ic_view_list : R.drawable.ic_grid_view);
        }
        // 可以在这里切换 RecyclerView 的 LayoutManager
        Toast.makeText(requireContext(),
                isGridView ? "网格视图" : "列表视图",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * 更新游戏数量显示
     */
    private void updateGameCount(int count) {
        if (chipGameCount != null) {
            chipGameCount.setText(count + " 款游戏");
        }
    }

    /**
     * 更新空状态显示
     */
    private void updateEmptyState() {
        if (emptyState != null && gamesCard != null) {
            boolean isEmpty = gameAdapter.getItemCount() == 0;
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            gamesCard.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
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
        showLoading("加载用户信息...");

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
                            userEmail.setText(userInfo.email.isEmpty() ? "已登录" : userInfo.email);
                        }

                        // 加载用户头像
                        if (userAvatar != null && userInfo.avatarUrl != null && !userInfo.avatarUrl.isEmpty()) {
                            Glide.with(GogClientFragment.this)
                                    .load(userInfo.avatarUrl)
                                    .placeholder(R.drawable.ic_person)
                                    .error(R.drawable.ic_person)
                                    .circleCrop()
                                    .into(userAvatar);
                            AppLogger.info(TAG, "加载用户头像: " + userInfo.avatarUrl);
                        } else {
                            AppLogger.warn(TAG, "用户头像 URL 为空");
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
            Toast.makeText(requireContext(), "请输入邮箱地址", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.isEmpty()) {
            Toast.makeText(requireContext(), "请输入密码", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("正在登录...");

        new Thread(() -> {
            try {
                boolean success = apiClient.loginWithCredentials(username, password);

                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    if (success) {
                        Toast.makeText(requireContext(), "登录成功", Toast.LENGTH_SHORT).show();
                        // 清空密码
                        editPassword.setText("");
                        updateLoginState();
                    } else {
                        Toast.makeText(requireContext(), "登录失败，请检查邮箱和密码", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                AppLogger.error(TAG, "登录异常", e);
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(requireContext(), "登录异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 刷新游戏列表
     */
    private void refreshGames() {
        showLoading("加载游戏列表...");

        new Thread(() -> {
            try {
                List<GogApiClient.GogGame> games = apiClient.getOwnedGames();

                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    allGames = new ArrayList<>(games);
                    gameAdapter.updateGames(games);
                    updateGameCount(games.size());
                    updateEmptyState();

                    if (games.isEmpty()) {
                        Toast.makeText(requireContext(), "您的游戏库为空", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(),
                                "已加载 " + games.size() + " 款游戏",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                AppLogger.error(TAG, "获取游戏列表失败", e);
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(requireContext(),
                            "获取游戏列表失败: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 登出
     */
    private void logout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认登出")
                .setMessage("确定要登出 GOG 账户吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    apiClient.logout();
                    allGames.clear();
                    gameAdapter.updateGames(new ArrayList<>());
                    updateLoginState();
                    Toast.makeText(requireContext(), "已登出", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 处理游戏点击
     */
    private void onGameClick(GogApiClient.GogGame game) {
        showLoading("加载游戏详情...");

        new Thread(() -> {
            try {
                GogApiClient.GameDetails details = apiClient.getGameDetails(String.valueOf(game.id));

                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    showGameDetailsDialog(game, details);
                });
            } catch (IOException e) {
                AppLogger.error(TAG, "获取游戏详情失败", e);
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(requireContext(),
                            "获取游戏详情失败: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 显示游戏详情对话框
     */
    private void showGameDetailsDialog(GogApiClient.GogGame game, GogApiClient.GameDetails details) {
        StringBuilder message = new StringBuilder();
        message.append("游戏 ID: ").append(game.id).append("\n\n");

        // 显示安装程序
        if (!details.installers.isEmpty()) {
            message.append("📦 安装程序 (").append(details.installers.size()).append(")\n");
            for (GogApiClient.GameFile file : details.installers) {
                message.append("  • ").append(file.name)
                        .append(" (").append(file.getSizeFormatted()).append(")\n");
                message.append("    版本: ").append(file.version)
                        .append(" | 语言: ").append(file.language)
                        .append(" | OS: ").append(file.os).append("\n");
            }
            message.append("\n");
        }

        // 显示额外内容
        if (!details.extras.isEmpty()) {
            message.append("🎁 额外内容 (").append(details.extras.size()).append(")\n");
            for (GogApiClient.GameFile file : details.extras) {
                message.append("  • ").append(file.name)
                        .append(" (").append(file.getSizeFormatted()).append(")\n");
            }
            message.append("\n");
        }

        // 显示补丁
        if (!details.patches.isEmpty()) {
            message.append("🔧 补丁 (").append(details.patches.size()).append(")\n");
            for (GogApiClient.GameFile file : details.patches) {
                message.append("  • ").append(file.name)
                        .append(" (").append(file.getSizeFormatted()).append(")\n");
            }
        }

        if (details.getTotalFiles() == 0) {
            message.append("暂无可下载文件");
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(game.title)
                .setMessage(message.toString())
                .setPositiveButton("关闭", null)
                .setNeutralButton("查看官网", (dialog, which) -> {
                    // 打开 GOG 游戏页面
                    if (!game.url.isEmpty()) {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.gog.com" + game.url));
                        startActivity(intent);
                    }
                })
                .setNegativeButton("下载选项", (dialog, which) -> {
                    showDownloadOptionsDialog(details);
                })
                .show();
    }

    /**
     * 显示下载选项对话框
     */
    private void showDownloadOptionsDialog(GogApiClient.GameDetails details) {
        List<GogApiClient.GameFile> allFiles = new ArrayList<>();
        allFiles.addAll(details.installers);
        allFiles.addAll(details.extras);
        allFiles.addAll(details.patches);

        if (allFiles.isEmpty()) {
            Toast.makeText(requireContext(), "没有可下载的文件", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] fileNames = new String[allFiles.size()];
        for (int i = 0; i < allFiles.size(); i++) {
            GogApiClient.GameFile file = allFiles.get(i);
            fileNames[i] = file.name + " (" + file.getSizeFormatted() + ")";
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择要下载的文件")
                .setItems(fileNames, (dialog, which) -> {
                    GogApiClient.GameFile selectedFile = allFiles.get(which);
                    downloadFile(selectedFile);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 下载文件
     */
    private void downloadFile(GogApiClient.GameFile file) {
        if (file.manualUrl.isEmpty()) {
            Toast.makeText(requireContext(), "文件没有可用的下载链接", Toast.LENGTH_SHORT).show();
            return;
        }

        // 使用浏览器打开下载链接
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("下载 " + file.name)
                .setMessage("文件大小: " + file.getSizeFormatted() + "\n\n" +
                        "将在浏览器中打开下载链接")
                .setPositiveButton("打开", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(file.manualUrl));
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
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
