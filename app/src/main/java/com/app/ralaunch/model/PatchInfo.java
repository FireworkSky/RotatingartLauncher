package com.app.ralaunch.model;

/**
 * 补丁信息类
 * 定义一个可用的补丁程序集
 */
public class PatchInfo {
    private String patchId;           // 补丁唯一标识
    private String patchName;         // 补丁显示名称
    private String patchDescription;  // 补丁描述
    private String dllFileName;       // DLL 文件名
    private String targetGamePattern; // 目标游戏路径匹配模式(正则表达式或包含字符串)

    public PatchInfo(String patchId, String patchName, String patchDescription,
                     String dllFileName, String targetGamePattern) {
        this.patchId = patchId;
        this.patchName = patchName;
        this.patchDescription = patchDescription;
        this.dllFileName = dllFileName;
        this.targetGamePattern = targetGamePattern;
    }

    /**
     * 检查此补丁是否适用于指定游戏
     */
    public boolean isApplicableToGame(GameItem gameItem) {
        if (gameItem == null || gameItem.getGamePath() == null) {
            return false;
        }

        String gamePath = gameItem.getGamePath().toLowerCase();
        String pattern = targetGamePattern.toLowerCase();

        return gamePath.contains(pattern);
    }

    // Getters
    public String getPatchId() {
        return patchId;
    }

    public String getPatchName() {
        return patchName;
    }

    public String getPatchDescription() {
        return patchDescription;
    }

    public String getDllFileName() {
        return dllFileName;
    }

    public String getTargetGamePattern() {
        return targetGamePattern;
    }

    @Override
    public String toString() {
        return "PatchInfo{" +
                "patchId='" + patchId + '\'' +
                ", patchName='" + patchName + '\'' +
                ", dllFileName='" + dllFileName + '\'' +
                '}';
    }
}
