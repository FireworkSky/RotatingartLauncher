// GameConfig.java
package com.app.ralaunch.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GameConfig {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("engine")
    private String engine;

    @SerializedName("description")
    private String description;

    @SerializedName("icon")
    private String icon;

    @SerializedName("assembly")
    private String assembly;

    @SerializedName("requiredFiles")
    private List<RequiredFile> requiredFiles;

    @SerializedName("installSteps")
    private List<InstallStep> installSteps;

    @SerializedName("launchParams")
    private LaunchParams launchParams;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getAssembly() { return assembly; }
    public void setAssembly(String assembly) { this.assembly = assembly; }

    public List<RequiredFile> getRequiredFiles() { return requiredFiles; }
    public void setRequiredFiles(List<RequiredFile> requiredFiles) { this.requiredFiles = requiredFiles; }

    public List<InstallStep> getInstallSteps() { return installSteps; }
    public void setInstallSteps(List<InstallStep> installSteps) { this.installSteps = installSteps; }

    public LaunchParams getLaunchParams() { return launchParams; }
    public void setLaunchParams(LaunchParams launchParams) { this.launchParams = launchParams; }

    public static class RequiredFile {
        @SerializedName("type")
        private String type;

        @SerializedName("description")
        private String description;

        @SerializedName("extensions")
        private List<String> extensions;

        @SerializedName("required")
        private boolean required;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getExtensions() { return extensions; }
        public void setExtensions(List<String> extensions) { this.extensions = extensions; }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }

    public static class InstallStep {
        @SerializedName("name")
        private String name;

        @SerializedName("description")
        private String description;

        @SerializedName("action")
        private String action;

        @SerializedName("sourceFile")
        private String sourceFile;

        @SerializedName("targetPath")
        private String targetPath;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getSourceFile() { return sourceFile; }
        public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

        public String getTargetPath() { return targetPath; }
        public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    }

    public static class LaunchParams {
        @SerializedName("workingDirectory")
        private String workingDirectory;

        @SerializedName("arguments")
        private List<String> arguments;

        @SerializedName("environmentVars")
        private List<EnvironmentVar> environmentVars;

        public String getWorkingDirectory() { return workingDirectory; }
        public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }

        public List<String> getArguments() { return arguments; }
        public void setArguments(List<String> arguments) { this.arguments = arguments; }

        public List<EnvironmentVar> getEnvironmentVars() { return environmentVars; }
        public void setEnvironmentVars(List<EnvironmentVar> environmentVars) { this.environmentVars = environmentVars; }
    }

    public static class EnvironmentVar {
        @SerializedName("name")
        private String name;

        @SerializedName("value")
        private String value;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}