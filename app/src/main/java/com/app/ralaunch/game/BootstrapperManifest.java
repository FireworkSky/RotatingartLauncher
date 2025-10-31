package com.app.ralaunch.game;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public class BootstrapperManifest {
    private static final String TAG = "BootstrapperManifest";

    public static Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @SerializedName("dotnet_version")
    private String dotnetVersion = "";

    @SerializedName("runtime_properties")
    private Map<String, String> runtimeProperties;

    @SerializedName("entry_assembly")
    private String entryAssembly = "";

    @SerializedName("entry_point")
    private String entryPoint = "";

    @SerializedName("working_directory")
    private String workingDirectory = "";

    @SerializedName("envs")
    private Map<String, String> envs;

    @SerializedName("args")
    private List<String> args;

    @SerializedName("extract_directory")
    private String extractDirectory = "";

    // Getters and Setters
    public String getDotnetVersion() {
        return dotnetVersion;
    }

    public void setDotnetVersion(String dotnetVersion) {
        this.dotnetVersion = dotnetVersion;
    }

    public Map<String, String> getRuntimeProperties() {
        return runtimeProperties;
    }

    public void setRuntimeProperties(Map<String, String> runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    public String getEntryAssembly() {
        return entryAssembly;
    }

    public void setEntryAssembly(String entryAssembly) {
        this.entryAssembly = entryAssembly;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(String entryPoint) {
        this.entryPoint = entryPoint;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public Map<String, String> getEnvs() {
        return envs;
    }

    public void setEnvs(Map<String, String> envs) {
        this.envs = envs;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public String getExtractDirectory() {
        return extractDirectory;
    }

    public void setExtractDirectory(String extractDirectory) {
        this.extractDirectory = extractDirectory;
    }

    public static @Nullable BootstrapperManifest FromZip(String pathToZip) {
        Log.i(TAG, "加载 Bootstrapper 压缩包, pathToZip: " + pathToZip);

        File file = new File(pathToZip);
        return BootstrapperManifest.FromZip(file);
    }

    public static @Nullable BootstrapperManifest FromZip(File file) {
        Log.i(TAG, "加载 Bootstrapper 压缩包, file: " + file.getAbsolutePath());
        try (ZipFile zip = new ZipFile(file)) {
            @Nullable var manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null) {
                Log.w(TAG, "未在压缩包中找到 manifest.json");
                return null;
            }
            try (var stream = zip.getInputStream(manifestEntry);
                 var ir = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return BootstrapperManifest.gson.fromJson(ir, BootstrapperManifest.class);
            }
        } catch (Exception e) {
            Log.w(TAG, Log.getStackTraceString(e));
        }
        return null;
    }

    public static @Nullable BootstrapperManifest FromJson(String pathToJson) {
        Log.i(TAG, "加载 manifest.json, pathToJson: " + pathToJson);

        if (!new File(pathToJson).exists()) {
            Log.w(TAG, "路径不存在 manifest.json 文件");
            return null;
        }

        try (var stream = new FileInputStream(pathToJson);
             var ir = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return BootstrapperManifest.gson.fromJson(ir, BootstrapperManifest.class);
        } catch (Exception e) {
            Log.w(TAG, Log.getStackTraceString(e));
        }
        return null;
    }
}