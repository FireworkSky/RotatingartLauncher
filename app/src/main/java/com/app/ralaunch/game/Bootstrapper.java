package com.app.ralaunch.game;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.app.ralaunch.activity.GameActivity;
import com.app.ralaunch.adapter.GameItem;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Bootstrapper {
    private static final String TAG = "Bootstrapper";

    public @NonNull BootstrapperManifest manifest;
    public @NonNull String bootstrapperBasePath;

    public Bootstrapper(@NonNull BootstrapperManifest manifest, @NonNull String bootstrapperBasePath) {
        this.manifest = Objects.requireNonNull(manifest);
        this.bootstrapperBasePath = Objects.requireNonNull(bootstrapperBasePath);
    }

    public int launch(Context ctx, GameItem game) {
        Log.i(TAG, "Launching bootstrapper");
        Intent intent = new Intent(ctx, GameActivity.class);
        intent.putExtra("IS_BOOTSTRAPPER", true);

        // TODO: set parameters
        ctx.startActivity(intent);
        return 0;
    }

    public static boolean ExtractBootstrapper(String zipFilePath, String gamePath) {
        Log.i(TAG, "Extracting to directory: " + gamePath);
        @Nullable var manifest = BootstrapperManifest.FromZip(zipFilePath);
        if (manifest == null) {
            Log.e(TAG, "Failed to extract bootstrapper: manifest is null");
            return false;
        }

        var targetPath = Paths.get(gamePath, Objects.requireNonNull(manifest).getExtractDirectory())
                .toAbsolutePath()
                .toString();

        Log.i(TAG, "Target extraction path: " + targetPath);

        // Create target directory if it doesn't exist
        File targetDir = new File(targetPath);
        if (!targetDir.exists()) {
            if (!targetDir.mkdirs()) {
                Log.e(TAG, "Failed to create target directory: " + targetPath);
                return false;
            }
        }

        // Extract all files from zip
        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryDestination = new File(targetDir, entry.getName());

                // Security check: prevent path traversal attacks
                String canonicalPath = entryDestination.getCanonicalPath();
                if (!canonicalPath.startsWith(targetDir.getCanonicalPath() + File.separator)
                    && !canonicalPath.equals(targetDir.getCanonicalPath())) {
                    Log.w(TAG, "Entry is outside of the target dir: " + entry.getName());
                    continue;
                }

                if (entry.isDirectory()) {
                    // Create directory
                    if (!entryDestination.exists() && !entryDestination.mkdirs()) {
                        Log.w(TAG, "Failed to create directory: " + entryDestination.getAbsolutePath());
                    }
                } else {
                    // Create parent directories if needed
                    File parent = entryDestination.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        Log.w(TAG, "Failed to create parent directory: " + parent.getAbsolutePath());
                        continue;
                    }

                    // Extract file
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        java.nio.file.Files.copy(in, entryDestination.toPath(), 
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        Log.d(TAG, "Extracted: " + entry.getName());
                    }
                }
            }

            Log.i(TAG, "Successfully extracted bootstrapper to: " + targetPath);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to extract bootstrapper: " + e.getMessage());
            Log.e(TAG, Log.getStackTraceString(e));
            return false;
        }
    }
}
