package com.app.ralaunch.activity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.ralaunch.R;
import com.app.ralaunch.RaLaunchApplication;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralib.Shared;
import com.app.ralib.extractors.BasicSevenZipExtractor;
import com.app.ralib.extractors.ExtractorCollection;
import com.app.ralib.extractors.GogShFileExtractor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class DebugActivity extends AppCompatActivity {
    private static final String TAG = "DebugActivity";

    private Button btn_test_bootstrapper;
    private Button btn_test_extractor;

    private ProgressBar debug_shared_progressbar;
    private TextView debug_shared_text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_debug);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupUI();
    }

    private void setupUI() {
        btn_test_bootstrapper = findViewById(R.id.btn_test_bootstrapper);
        btn_test_bootstrapper.setOnClickListener(v -> {
            // onTestBootstrapperClicked();  // Bootstrapper 已移除
            android.widget.Toast.makeText(this, "Bootstrapper 功能已移除", android.widget.Toast.LENGTH_SHORT).show();
        });

        btn_test_extractor = findViewById(R.id.btn_test_extractor);
        btn_test_extractor.setOnClickListener(v -> {
            onTestExtractorClicked();
        });

        debug_shared_progressbar = findViewById(R.id.debug_shared_progressbar);
        debug_shared_text = findViewById(R.id.debug_shared_text);
    }

    // private void onTestBootstrapperClicked() {
    //     // Bootstrapper 功能已移除
    // }

    private void onTestExtractorClicked() {
        AppLogger.debug(TAG, "Test Extractor button clicked");

        new ExtractorCollection.Builder()
                .addExtractor(new GogShFileExtractor(
                        Paths.get("/sdcard/terraria_v1_4_4_9_v4_60321.sh"),
                        RaLaunchApplication.getAppContext().getExternalCacheDir().toPath()
                                .resolve("extracted_test"),
                        new ExtractorCollection.ExtractionListener() {
                            @Override
                            public void onProgress(String message, float progress, HashMap<String, Object> state) {
                                var logMsg = String.format("Extraction progress: %.2f%% - %s", progress*100, message);
                                AppLogger.info(TAG, logMsg);
                                runOnUiThread(() -> {
                                    debug_shared_text.setText(logMsg);
                                    debug_shared_progressbar.setProgress((int)(progress * 100));
                                });
                            }

                            @Override
                            public void onComplete(String message, HashMap<String, Object> state) {
                                var logMsg = "Extraction complete: " + message;
                                AppLogger.info(TAG, logMsg);
                                runOnUiThread(() -> {
                                    debug_shared_text.setText(logMsg);
                                });
                            }

                            @Override
                            public void onError(String message, Exception ex, HashMap<String, Object> state) {
                                var logMsg = "Extraction error: " + message + "\n" + Log.getStackTraceString(ex);
                              //  AppLogger.info(TAG, "Extraction error: " + message, ex);
                                runOnUiThread(() -> {
                                    debug_shared_text.setText(logMsg);
                                });
                            }
                        }))
                .build()
                .extractAllInNewThread();



//        new ExtractorCollection.Builder()
//                .addExtractor(new BasicSevenZipExtractor(
//                        Paths.get("/sdcard/terraria_v1_4_4_9_v4_60321.zip"),
//                        Paths.get("data/noarch/game"),
//                        RaLaunchApplication.getAppContext().getExternalCacheDir().toPath()
//                                .resolve("extracted_test"),
//                        new ExtractorCollection.ExtractionListener() {
//                            @Override
//                            public void onProgress(String message, float progress, HashMap<String, Object> state) {
//                                var logMsg = String.format("Extraction progress: %.2f%% - %s", progress*100, message);
//                                AppLogger.info(TAG, logMsg);
//                                runOnUiThread(() -> {
//                                    debug_shared_text.setText(logMsg);
//                                    debug_shared_progressbar.setProgress((int)(progress * 100));
//                                });
//                            }
//
//                            @Override
//                            public void onComplete(String message, HashMap<String, Object> state) {
//                                var logMsg = "Extraction complete: " + message;
//                                AppLogger.info(TAG, logMsg);
//                                runOnUiThread(() -> {
//                                    debug_shared_text.setText(logMsg);
//                                });
//                            }
//
//                            @Override
//                            public void onError(String message, Exception ex, HashMap<String, Object> state) {
//                                var logMsg = "Extraction error: " + message + "\n" + Log.getStackTraceString(ex);
//                                AppLogger.info(TAG, "Extraction error: " + message, ex);
//                                runOnUiThread(() -> {
//                                    debug_shared_text.setText(logMsg);
//                                });
//                            }
//                        }))
//                .build()
//                .extractAllInNewThread();
    }
}