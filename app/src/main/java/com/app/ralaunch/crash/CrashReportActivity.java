package com.app.ralaunch.crash;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.app.ralaunch.R;

public class CrashReportActivity extends AppCompatActivity {
    public static final String EXTRA_STACK_TRACE = "stack_trace";
    public static final String EXTRA_ERROR_DETAILS = "error_details";
    public static final String EXTRA_EXCEPTION_CLASS = "exception_class";
    public static final String EXTRA_EXCEPTION_MESSAGE = "exception_message";

    private TextView errorDetailsText;
    private TextView stackTraceText;
    private boolean detailsExpanded = false;
    private String errorDetails;
    private String stackTrace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 强制横屏
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        setContentView(R.layout.activity_crash_report);

        Intent intent = getIntent();
        stackTrace = intent.getStringExtra("stack_trace");
        errorDetails = intent.getStringExtra("error_details");
        String exceptionClass = intent.getStringExtra("exception_class");
        String exceptionMessage = intent.getStringExtra("exception_message");

        TextView titleText = findViewById(R.id.crash_title);
        errorDetailsText = findViewById(R.id.crash_error_details);
        stackTraceText = findViewById(R.id.crash_stack_trace);
        com.google.android.material.button.MaterialButton closeButton = 
            findViewById(R.id.crash_close_button);
        com.google.android.material.button.MaterialButton restartButton = 
            findViewById(R.id.crash_restart_button);
        com.google.android.material.button.MaterialButton detailsButton = 
            findViewById(R.id.crash_details_button);
        com.google.android.material.button.MaterialButton shareButton = 
            findViewById(R.id.crash_share_button);

        if (titleText != null) {
            titleText.setText("应用已停止运行");
        }

        if (errorDetailsText != null) {
            errorDetailsText.setMovementMethod(new ScrollingMovementMethod());
            if (errorDetails != null) {
                errorDetailsText.setText(errorDetails);
            } else {
                errorDetailsText.setText("无法获取错误详情");
            }
        }

        if (stackTraceText != null) {
            stackTraceText.setMovementMethod(new ScrollingMovementMethod());
            stackTraceText.setVisibility(View.GONE);
            if (stackTrace != null) {
                stackTraceText.setText(stackTrace);
            }
        }

        if (detailsButton != null) {
            detailsButton.setOnClickListener(v -> toggleDetails());
        }

        if (closeButton != null) {
            closeButton.setOnClickListener(v -> finishApp());
        }

        if (restartButton != null) {
            restartButton.setOnClickListener(v -> restartApp());
        }

        if (shareButton != null) {
            shareButton.setOnClickListener(v -> shareLog());
        }
    }

    private void toggleDetails() {
        if (stackTraceText == null) return;
        
        detailsExpanded = !detailsExpanded;
        stackTraceText.setVisibility(detailsExpanded ? View.VISIBLE : View.GONE);
        
        com.google.android.material.button.MaterialButton detailsButton = 
            findViewById(R.id.crash_details_button);
        if (detailsButton != null) {
            detailsButton.setText(detailsExpanded ? "隐藏堆栈" : "显示堆栈");
        }
    }

    private void finishApp() {
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }

    private void restartApp() {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }

    private void shareLog() {
        try {
            // 临时隐藏底部按钮区域，避免遮挡分享对话框
            View buttonContainer = findViewById(R.id.button_container);
            if (buttonContainer != null) {
                buttonContainer.setVisibility(View.GONE);
            }
            
            java.io.File logDir = new java.io.File(getFilesDir(), "crash_logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
            String fileName = "crash_" + sdf.format(new java.util.Date()) + ".log";
            java.io.File logFile = new java.io.File(logDir, fileName);
            
            StringBuilder logContent = new StringBuilder();
            
            if (errorDetails != null && !errorDetails.isEmpty()) {
                logContent.append(errorDetails);
            }
            
            if (stackTrace != null && !stackTrace.isEmpty()) {
                if (logContent.length() > 0) {
                    logContent.append("\n\n");
                    logContent.append("=== 堆栈跟踪 ===\n");
                }
                logContent.append(stackTrace);
            }
            
            if (logContent.length() == 0) {
                logContent.append("无法获取错误日志");
            }
            
            java.io.FileWriter writer = new java.io.FileWriter(logFile);
            writer.write(logContent.toString());
            writer.close();
            
            android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "com.app.ralaunch.fileprovider",
                logFile
            );
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "应用崩溃日志");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            Intent chooserIntent = Intent.createChooser(shareIntent, "分享崩溃日志");
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // 使用 startActivityForResult 以便在分享完成后恢复按钮
            startActivityForResult(chooserIntent, 1001);
        } catch (Exception e) {
            android.util.Log.e("CrashReportActivity", "Failed to share log file", e);
            android.widget.Toast.makeText(this, "分享日志失败: " + e.getMessage(), 
                android.widget.Toast.LENGTH_SHORT).show();
            
            // 如果出错，恢复按钮显示
            View buttonContainer = findViewById(R.id.button_container);
            if (buttonContainer != null) {
                buttonContainer.setVisibility(View.VISIBLE);
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // 分享完成后恢复按钮显示
        if (requestCode == 1001) {
            View buttonContainer = findViewById(R.id.button_container);
            if (buttonContainer != null) {
                buttonContainer.setVisibility(View.VISIBLE);
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // 确保按钮在界面恢复时可见
        View buttonContainer = findViewById(R.id.button_container);
        if (buttonContainer != null && buttonContainer.getVisibility() != View.VISIBLE) {
            buttonContainer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        finishApp();
    }
}

