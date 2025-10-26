package com.app.ralaunch.fragment;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.app.ralaunch.R;
import com.app.ralaunch.activity.MainActivity;

import java.io.File;

public class DownloadOptionsFragment extends Fragment {

    private OnDownloadOptionSelectedListener downloadOptionSelectedListener;
    private OnBackListener backListener;

    // 界面控件
    private Button downloadGameButton;
    private Button localImportButton;
    private LinearLayout browserContainer;
    private LinearLayout downloadProgressContainer;
    private WebView webView;
    private ProgressBar webViewProgress;
    private Button backFromBrowserButton;
    private TextView browserTitle;

    // 下载进度控件
    private ProgressBar tmodloaderProgress;
    private ProgressBar gameProgress;
    private TextView tmodloaderStatus;
    private TextView gameStatus;

    // 游戏信息
    private String gameType;
    private String gameName;
    private String engineType;

    // 下载相关变量
    private DownloadManager downloadManager;
    private long tmodloaderDownloadId = -1;
    private long gameDownloadId = -1;
    private String currentDownloadUrl;

    // 选择的选项
    private String selectedOption = null;

    // GOG下载链接
    private static final String GOG_LOGIN_URL = "https://login.gog.com/login";
    private static final String TERRARIA_DOWNLOAD_URL = "https://www.gog.com/downloads/terraria/en3installer0";
    private static final String STARDEW_VALLEY_DOWNLOAD_URL = "https://www.gog.com/downloads/stardew_valley/en3installer0";
    private static final String TMODLOADER_DOWNLOAD_URL = "https://github.com/tModLoader/tModLoader/releases/download/v2025.08.3.1/tModLoader.zip";

    // Handler for progress updates
    private Handler progressHandler = new Handler();

    public interface OnDownloadOptionSelectedListener {
        void onDownloadOptionSelected(String gameType, String gameName, String engineType, String optionType);
    }

    public interface OnBackListener {
        void onBack();
    }

    public void setOnDownloadOptionSelectedListener(OnDownloadOptionSelectedListener listener) {
        this.downloadOptionSelectedListener = listener;
    }

    // 添加这个方法以兼容旧代码
    public void setOnDownloadCompleteListener(OnDownloadOptionSelectedListener listener) {
        this.downloadOptionSelectedListener = listener;
    }

    public void setOnBackListener(OnBackListener listener) {
        this.backListener = listener;
    }

    public void setGameInfo(String gameType, String gameName, String engineType) {
        this.gameType = gameType;
        this.gameName = gameName;
        this.engineType = engineType;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_download_options, container, false);
        setupUI(view);
        setupDownloadManager();
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 取消注册广播接收器
        if (getActivity() != null) {
            try {
                getActivity().unregisterReceiver(downloadCompleteReceiver);
                progressHandler.removeCallbacksAndMessages(null);
            } catch (IllegalArgumentException e) {
                // 接收器可能未注册，忽略异常
            }
        }
    }

    private void setupUI(View view) {
        // 返回按钮
        ImageButton backButton = view.findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            if (backListener != null) {
                backListener.onBack();
            }
        });

        // 设置页面标题
        TextView pageTitle = view.findViewById(R.id.pageTitle);
        pageTitle.setText("下载选项 - " + gameName);

        // 初始化控件
        downloadGameButton = view.findViewById(R.id.downloadGameButton);
        localImportButton = view.findViewById(R.id.localImportButton);
        browserContainer = view.findViewById(R.id.browserContainer);
        downloadProgressContainer = view.findViewById(R.id.downloadProgressContainer);
        webView = view.findViewById(R.id.webView);
        webViewProgress = view.findViewById(R.id.webViewProgress);
        backFromBrowserButton = view.findViewById(R.id.backFromBrowserButton);
        browserTitle = view.findViewById(R.id.browserTitle);

        // 下载进度控件
        tmodloaderProgress = view.findViewById(R.id.tmodloaderProgress);
        gameProgress = view.findViewById(R.id.gameProgress);
        tmodloaderStatus = view.findViewById(R.id.tmodloaderStatus);
        gameStatus = view.findViewById(R.id.gameStatus);

        // 登录并下载按钮
        downloadGameButton.setOnClickListener(v -> selectOption("download"));

        // 本地导入按钮
        localImportButton.setOnClickListener(v -> selectOption("local"));

        // 浏览器返回按钮
        backFromBrowserButton.setOnClickListener(v -> hideBrowser());

        // 设置WebView
        setupWebView();
    }

    private void selectOption(String optionType) {
        selectedOption = optionType;

        switch (optionType) {
            case "download":
                openBrowserForDownload();
                break;
            case "local":
                // 直接通知下载选项选择完成，跳转到本地导入
                if (downloadOptionSelectedListener != null) {
                    downloadOptionSelectedListener.onDownloadOptionSelected(gameType, gameName, engineType, "local");
                }
                break;
        }
    }

    private void setupWebView() {
        // 启用JavaScript
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        // 设置WebView客户端
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // 处理GOG下载链接
                if (url.contains("gog.com/downloads")) {
                    handleGogDownload(url);
                    return true;
                }
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                browserTitle.setText(view.getTitle());

                // 自动跳转到下载页面（如果已经登录）
                if (url.contains("login.gog.com") && view.getTitle().contains("GOG.com")) {
                    // 登录成功后自动跳转到对应游戏的下载页面
                    String downloadUrl = getDownloadUrlForSelectedGame();
                    if (downloadUrl != null) {
                        view.loadUrl(downloadUrl);
                    }
                }

                // 如果页面包含下载链接，自动开始下载
                if (url.contains("gog.com/downloads") && "tmodloader".equals(gameType)) {
                    // 对于tmodloader，开始下载游戏本体和tmodloader
                    startTmodloaderDownloads();
                }
            }
        });

        // 设置Chrome客户端用于进度显示
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    webViewProgress.setVisibility(View.VISIBLE);
                    webViewProgress.setProgress(newProgress);
                } else {
                    webViewProgress.setVisibility(View.GONE);
                }
            }
        });

        // 设置下载监听器
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
                // 处理文件下载
                handleFileDownload(url, contentDisposition, mimetype);
            }
        });
    }

    private void openBrowserForDownload() {
        // 显示浏览器容器
        browserContainer.setVisibility(View.VISIBLE);

        // 根据游戏类型决定加载的URL
        String url;
        if ("tmodloader".equals(gameType)) {
            // tmodloader直接显示下载进度
            showDownloadProgress();
            startTmodloaderDownloads();
            return;
        } else {
            // GOG游戏需要先登录
            url = GOG_LOGIN_URL;
        }

        webView.loadUrl(url);
        browserTitle.setText("GOG登录 - 正在加载...");
    }

    private void startTmodloaderDownloads() {
        // 显示下载进度区域
        showDownloadProgress();

        // 开始下载tmodloader
        startTmodloaderDownload();

        // 开始下载游戏本体
        startGameDownload();
    }

    private void startTmodloaderDownload() {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(TMODLOADER_DOWNLOAD_URL));
            request.setTitle("tModLoader 下载");
            request.setDescription("下载中...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // 设置下载路径
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File gameDir = new File(downloadsDir, "GameLauncher");
            if (!gameDir.exists()) {
                gameDir.mkdirs();
            }

            request.setDestinationUri(Uri.fromFile(new File(gameDir, "tModLoader.zip")));

            // 开始下载
            tmodloaderDownloadId = downloadManager.enqueue(request);

            // 更新状态
            tmodloaderStatus.setText("下载中... 0%");

            // 开始进度监控
            startProgressMonitoring();

        } catch (Exception e) {
            e.printStackTrace();
            tmodloaderStatus.setText("下载失败: " + e.getMessage());
            if (getActivity() != null) {
                ((MainActivity) getActivity()).showToast("tModLoader下载失败: " + e.getMessage());
            }
        }
    }

    private void startGameDownload() {
        try {
            String downloadUrl = getDownloadUrlForSelectedGame();
            String fileName = getDownloadFileName();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle(gameName + " 下载");
            request.setDescription("下载中...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // 设置下载路径
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File gameDir = new File(downloadsDir, "GameLauncher");
            if (!gameDir.exists()) {
                gameDir.mkdirs();
            }

            request.setDestinationUri(Uri.fromFile(new File(gameDir, fileName)));

            // 开始下载
            gameDownloadId = downloadManager.enqueue(request);

            // 更新状态
            gameStatus.setText("下载中... 0%");

        } catch (Exception e) {
            e.printStackTrace();
            gameStatus.setText("下载失败: " + e.getMessage());
            if (getActivity() != null) {
                ((MainActivity) getActivity()).showToast(gameName + "下载失败: " + e.getMessage());
            }
        }
    }

    private void startProgressMonitoring() {
        progressHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateDownloadProgress();
                progressHandler.postDelayed(this, 1000); // 每秒更新一次
            }
        }, 1000);
    }

    private void updateDownloadProgress() {
        if (tmodloaderDownloadId != -1) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(tmodloaderDownloadId);

            try {
                var  cursor = downloadManager.query(query);
                if (cursor != null && cursor.moveToFirst()) {
                    @SuppressLint("Range") int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    @SuppressLint("Range") long bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    @SuppressLint("Range") long bytesTotal = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    if (bytesTotal > 0) {
                        int progress = (int) ((bytesDownloaded * 100) / bytesTotal);
                        tmodloaderProgress.setProgress(progress);
                        tmodloaderStatus.setText("下载中... " + progress + "%");
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        tmodloaderStatus.setText("下载完成");
                        checkAllDownloadsComplete();
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        tmodloaderStatus.setText("下载失败");
                    }
                }
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (gameDownloadId != -1) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(gameDownloadId);

            try {
                var cursor = downloadManager.query(query);
                if (cursor != null && cursor.moveToFirst()) {
                    @SuppressLint("Range") int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    @SuppressLint("Range") long bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    @SuppressLint("Range") long bytesTotal = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    if (bytesTotal > 0) {
                        int progress = (int) ((bytesDownloaded * 100) / bytesTotal);
                        gameProgress.setProgress(progress);
                        gameStatus.setText("下载中... " + progress + "%");
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        gameStatus.setText("下载完成");
                        checkAllDownloadsComplete();
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        gameStatus.setText("下载失败");
                    }
                }
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void checkAllDownloadsComplete() {
        if ((tmodloaderDownloadId == -1 || tmodloaderStatus.getText().toString().contains("完成")) &&
                (gameDownloadId == -1 || gameStatus.getText().toString().contains("完成"))) {
            // 所有下载完成，自动进入下一步
            if (downloadOptionSelectedListener != null) {
                downloadOptionSelectedListener.onDownloadOptionSelected(gameType, gameName, engineType, "download");
            }
        }
    }

    private void showDownloadProgress() {
        downloadProgressContainer.setVisibility(View.VISIBLE);
        browserContainer.setVisibility(View.GONE);
    }

    private void hideBrowser() {
        browserContainer.setVisibility(View.GONE);
        webView.stopLoading();
    }

    private String getDownloadUrlForSelectedGame() {
        switch (gameType) {
            case "tmodloader":
                return TERRARIA_DOWNLOAD_URL;
            case "stardew":
                return STARDEW_VALLEY_DOWNLOAD_URL;
            default:
                return TERRARIA_DOWNLOAD_URL;
        }
    }

    private String getDownloadFileName() {
        switch (gameType) {
            case "tmodloader":
                return "Terraria_GOG.sh";
            case "stardew":
                return "StardewValley_GOG.sh";
            default:
                return "Terraria_GOG.sh";
        }
    }

    private void handleGogDownload(String url) {
        // 处理GOG下载链接
        currentDownloadUrl = url;
        startGameDownload();
    }

    private void handleFileDownload(String url, String contentDisposition, String mimetype) {
        // 处理普通文件下载
        currentDownloadUrl = url;
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
        startGameDownload();
    }

    private void setupDownloadManager() {
        try {
            downloadManager = (DownloadManager) getActivity().getSystemService(Context.DOWNLOAD_SERVICE);

            // 注册下载完成接收器
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13 (API 33) 及以上版本
                getActivity().registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 (API 31-32)
                getActivity().registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                // Android 11 及以下版本
                ContextCompat.registerReceiver(getActivity(), downloadCompleteReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (getActivity() != null) {
                ((MainActivity) getActivity()).showToast("下载管理器初始化失败");
            }
        }
    }

    private BroadcastReceiver downloadCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == tmodloaderDownloadId) {
                tmodloaderStatus.setText("下载完成");
                checkAllDownloadsComplete();
            } else if (id == gameDownloadId) {
                gameStatus.setText("下载完成");
                checkAllDownloadsComplete();
            }
        }
    };
}