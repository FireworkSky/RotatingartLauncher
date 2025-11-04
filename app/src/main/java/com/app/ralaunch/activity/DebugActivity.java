package com.app.ralaunch.activity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.ralaunch.R;
import com.app.ralaunch.game.Bootstrapper;

public class DebugActivity extends AppCompatActivity {
    private static final String TAG = "DebugActivity";

    private Button btn_test_bootstrapper;

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
            onTestBootstrapperClicked();
        });
    }

    private void onTestBootstrapperClicked() {
        Log.d(TAG, "Test Bootstrapper button clicked");
        var src = "/sdcard/RotatingArtLauncher.Bootstrap.tModLoader.zip";
        var basePath = getExternalFilesDir(null).getAbsolutePath();

        Log.d(TAG, "Extracting bootstrapper\nfrom: " + src + "\nto: " + basePath);

        Bootstrapper.ExtractBootstrapper(src, basePath);
    }
}