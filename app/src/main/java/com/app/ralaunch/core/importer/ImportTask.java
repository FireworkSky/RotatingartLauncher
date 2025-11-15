package com.app.ralaunch.core.importer;

/**
 * 游戏导入任务
 *
 * 封装单次游戏导入的所有信息
 */
public class ImportTask {
    private final String gameFilePath;
    private final String modLoaderFilePath;
    private final String outputDirectory;
    private final String gameName;
    private final String gameVersion;

    private ImportTask(Builder builder) {
        this.gameFilePath = builder.gameFilePath;
        this.modLoaderFilePath = builder.modLoaderFilePath;
        this.outputDirectory = builder.outputDirectory;
        this.gameName = builder.gameName;
        this.gameVersion = builder.gameVersion;
    }

    public String getGameFilePath() {
        return gameFilePath;
    }

    public String getModLoaderFilePath() {
        return modLoaderFilePath;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public String getGameName() {
        return gameName;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public boolean hasModLoader() {
        return modLoaderFilePath != null && !modLoaderFilePath.isEmpty();
    }

    /**
     * 构建器模式
     */
    public static class Builder {
        private String gameFilePath;
        private String modLoaderFilePath;
        private String outputDirectory;
        private String gameName;
        private String gameVersion;

        public Builder gameFile(String path) {
            this.gameFilePath = path;
            return this;
        }

        public Builder modLoaderFile(String path) {
            this.modLoaderFilePath = path;
            return this;
        }

        public Builder outputDirectory(String path) {
            this.outputDirectory = path;
            return this;
        }

        public Builder gameName(String name) {
            this.gameName = name;
            return this;
        }

        public Builder gameVersion(String version) {
            this.gameVersion = version;
            return this;
        }

        public ImportTask build() {
            if (gameFilePath == null || gameFilePath.isEmpty()) {
                throw new IllegalArgumentException("游戏文件路径不能为空");
            }
            if (outputDirectory == null || outputDirectory.isEmpty()) {
                throw new IllegalArgumentException("输出目录不能为空");
            }
            return new ImportTask(this);
        }
    }
}
