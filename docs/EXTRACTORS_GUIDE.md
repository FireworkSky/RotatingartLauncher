# RALib Extractors 工具类使用指南

## 概述

`ralib.extractors` 包提供了一套用于解压和提取各种压缩文件格式的工具类。这些工具类专门为 Rotating Art Launcher 设计，支持链式解压、进度监听和状态管理。

## 目录

- [核心组件](#核心组件)
- [ExtractorCollection - 提取器集合](#extractorcollection---提取器集合)
- [BasicSevenZipExtractor - 7Z 解压器](#basicsevenzipiextractor---7z-解压器)
- [GogShFileExtractor - GOG 安装文件提取器](#gogshfileextractor---gog-安装文件提取器)
- [使用示例](#使用示例)
- [最佳实践](#最佳实践)

---

## 核心组件

### IExtractor 接口

所有提取器都实现了 `IExtractor` 接口，该接口定义了标准的提取操作：

```java
public interface IExtractor {
    void setSourcePath(Path sourcePath);           // 设置源文件路径
    void setDestinationPath(Path destinationPath); // 设置目标路径
    void setExtractionListener(ExtractionListener listener); // 设置监听器
    void setState(HashMap<String, Object> state);  // 设置状态对象
    HashMap<String, Object> getState();            // 获取状态对象
    boolean extract();                             // 执行解压操作
}
```

### ExtractionListener 监听器

用于监听提取过程中的事件：

```java
public interface ExtractionListener {
    // 进度更新回调
    void onProgress(String message, float progress, HashMap<String, Object> state);
    
    // 完成回调
    void onComplete(String message, HashMap<String, Object> state);
    
    // 错误回调
    void onError(String message, Exception ex, HashMap<String, Object> state);
}
```

**参数说明：**
- `message`: 当前操作的描述信息
- `progress`: 进度值，范围 0.0 ~ 1.0
- `state`: 状态对象，可用于在不同阶段传递数据
- `ex`: 发生的异常（仅在 onError 中）

---

## ExtractorCollection - 提取器集合

### 功能

`ExtractorCollection` 用于管理和执行多个提取器的链式操作。它可以按顺序执行多个解压任务，并在新线程中运行。

### 使用 Builder 模式创建

```java
ExtractorCollection collection = new ExtractorCollection.Builder()
    .configureState("custom_key", customValue)  // 配置共享状态
    .addExtractor(extractor1)                   // 添加提取器1
    .addExtractor(extractor2)                   // 添加提取器2
    .build();

// 在新线程中执行所有提取操作
collection.extractAllInNewThread();
```

### 状态管理

ExtractorCollection 提供了两个内置状态键：

```java
// 当前执行的提取器索引（类型：int）
ExtractorCollection.STATE_KEY_EXTRACTOR_INDEX

// 所有提取器列表（类型：ArrayList<IExtractor>）
ExtractorCollection.STATE_KEY_EXTRACTORS
```

### 完整示例

```java
ExtractorCollection extractorCollection = new ExtractorCollection.Builder()
    .configureState("user_id", userId)
    .configureState("install_path", installPath)
    .addExtractor(new GogShFileExtractor(sourcePath, destPath, listener))
    .addExtractor(new BasicSevenZipExtractor(archivePath, outputPath, listener))
    .build();

extractorCollection.extractAllInNewThread();
```

### 特点

- **自动线程管理**：`extractAllInNewThread()` 会在新线程中执行，避免阻塞 UI
- **状态注入**：通过 `configureState()` 配置的状态会自动注入到所有提取器中
- **顺序执行**：提取器按添加顺序依次执行
- **失败停止**：如果某个提取器返回 `false`，后续提取器将不会执行

---

## BasicSevenZipExtractor - 7Z 解压器

### 功能

`BasicSevenZipExtractor` 用于解压 7z、zip、tar、gz 等多种压缩格式。它基于 SevenZipJBinding 库，支持广泛的压缩格式。

### 构造函数

```java
// 基础构造函数
BasicSevenZipExtractor(Path sourcePath, Path destinationPath)

// 带监听器的构造函数
BasicSevenZipExtractor(Path sourcePath, Path destinationPath, 
                       ExtractionListener listener)

// 带前缀过滤的构造函数
BasicSevenZipExtractor(Path sourcePath, Path sourceExtractionPrefix, 
                       Path destinationPath, ExtractionListener listener)
```

### 参数说明

- **sourcePath**: 源压缩文件路径
- **sourceExtractionPrefix**: 只解压压缩包内特定前缀路径下的文件
- **destinationPath**: 解压目标路径
- **listener**: 进度监听器

### 使用示例

#### 1. 基础解压

```java
Path sourceZip = Paths.get("/sdcard/Download/game.zip");
Path destDir = Paths.get("/sdcard/Games/MyGame");

BasicSevenZipExtractor extractor = new BasicSevenZipExtractor(sourceZip, destDir);
boolean success = extractor.extract();

if (success) {
    Log.d(TAG, "解压成功！");
} else {
    Log.e(TAG, "解压失败！");
}
```

#### 2. 带进度监听的解压

```java
Path sourceZip = Paths.get("/sdcard/Download/game.7z");
Path destDir = Paths.get("/sdcard/Games/MyGame");

BasicSevenZipExtractor extractor = new BasicSevenZipExtractor(
    sourceZip, 
    destDir,
    new ExtractorCollection.ExtractionListener() {
        @Override
        public void onProgress(String message, float progress, HashMap<String, Object> state) {
            runOnUiThread(() -> {
                progressBar.setProgress((int)(progress * 100));
                statusText.setText(message);
            });
        }

        @Override
        public void onComplete(String message, HashMap<String, Object> state) {
            runOnUiThread(() -> {
                Toast.makeText(context, "解压完成！", Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public void onError(String message, Exception ex, HashMap<String, Object> state) {
            Log.e(TAG, message, ex);
            runOnUiThread(() -> {
                Toast.makeText(context, "解压失败：" + message, Toast.LENGTH_LONG).show();
            });
        }
    }
);

extractor.extract();
```

#### 3. 只解压特定目录

```java
// 只解压压缩包内 "data/game/" 目录下的文件
Path sourceZip = Paths.get("/sdcard/Download/game.zip");
Path prefix = Paths.get("data/game");  // 只解压此路径下的内容
Path destDir = Paths.get("/sdcard/Games/MyGame");

BasicSevenZipExtractor extractor = new BasicSevenZipExtractor(
    sourceZip,
    prefix,      // 前缀过滤
    destDir,
    listener
);

extractor.extract();
```

### 特性

- **路径安全**：自动防止路径遍历攻击
- **自动创建目录**：自动创建必要的父目录
- **进度报告**：实时报告解压进度和当前文件
- **文件夹支持**：正确处理文件夹结构

---

## GogShFileExtractor - GOG 安装文件提取器

### 功能

`GogShFileExtractor` 专门用于提取 GOG (Good Old Games) Linux 安装文件（.sh 格式）。这些文件是 MakeSelf 自解压脚本，包含游戏数据和安装脚本。

### 文件结构

GOG .sh 文件的结构：
```
|-- MakeSelf SH Header --|-- mojosetup.tar.gz --|-- game_data.zip --|
```

### 构造函数

```java
GogShFileExtractor(Path sourcePath, Path destinationPath, 
                   ExtractionListener listener)
```

### 使用示例

#### 基础使用

```java
Path gogInstaller = Paths.get("/sdcard/Download/terraria_1.4.5.sh");
Path installDir = Paths.get("/sdcard/Games");

GogShFileExtractor extractor = new GogShFileExtractor(
    gogInstaller,
    installDir,
    new ExtractorCollection.ExtractionListener() {
        @Override
        public void onProgress(String message, float progress, HashMap<String, Object> state) {
            Log.d(TAG, String.format("进度：%.1f%% - %s", progress * 100, message));
        }

        @Override
        public void onComplete(String message, HashMap<String, Object> state) {
            // 获取提取的游戏路径
            Path gamePath = (Path) state.get(GogShFileExtractor.STATE_KEY_GAME_PATH);
            
            // 获取游戏信息
            GogShFileExtractor.GameDataZipFile gameData = 
                (GogShFileExtractor.GameDataZipFile) state.get(
                    GogShFileExtractor.STATE_KEY_GAME_DATA_ZIP_FILE);
            
            Log.d(TAG, "游戏ID: " + gameData.id);
            Log.d(TAG, "版本: " + gameData.version);
            Log.d(TAG, "安装路径: " + gamePath);
        }

        @Override
        public void onError(String message, Exception ex, HashMap<String, Object> state) {
            Log.e(TAG, "提取失败: " + message, ex);
        }
    }
);

extractor.extract();
```

### 状态键

提取完成后，可通过以下键从 state 中获取信息：

```java
// 游戏安装路径（类型：Path）
GogShFileExtractor.STATE_KEY_GAME_PATH

// 游戏数据信息（类型：GameDataZipFile）
GogShFileExtractor.STATE_KEY_GAME_DATA_ZIP_FILE
```

### GameDataZipFile 类

包含从 GOG 安装文件中解析的游戏元数据：

```java
public class GameDataZipFile {
    public String id;          // 游戏ID
    public String version;     // 版本号
    public String build;       // 构建号
    public String locale;      // 语言区域
    public String timestamp1;  // 时间戳1
    public String timestamp2;  // 时间戳2
    public String gogId;       // GOG ID
}
```

#### 使用示例

```java
@Override
public void onComplete(String message, HashMap<String, Object> state) {
    GogShFileExtractor.GameDataZipFile gameData = 
        (GogShFileExtractor.GameDataZipFile) state.get(
            GogShFileExtractor.STATE_KEY_GAME_DATA_ZIP_FILE);
    
    if (gameData != null) {
        Log.d(TAG, "游戏信息: " + gameData.toString());
        
        // 显示游戏信息
        textViewGameId.setText(gameData.id);
        textViewVersion.setText(gameData.version);
        textViewBuild.setText(gameData.build);
    }
}
```

### 静态方法

#### 解析 GOG .sh 文件信息

```java
// 只解析文件信息，不进行解压
GameDataZipFile gameInfo = GameDataZipFile.parseFromGogShFile(gogFilePath);

if (gameInfo != null) {
    Log.d(TAG, "游戏ID: " + gameInfo.id);
    Log.d(TAG, "版本: " + gameInfo.version);
}
```

#### 解析 game_data.zip 文件

```java
// 直接解析已提取的 game_data.zip
GameDataZipFile gameInfo = GameDataZipFile.parse(zipFilePath);
```

### 提取流程

1. **解析头部**（进度 1%）：读取 MakeSelf 脚本头部，获取偏移量和大小信息
2. **提取 MojoSetup**（进度 2%）：提取安装脚本到临时文件
3. **提取游戏数据**（进度 3%）：提取 game_data.zip 到临时文件
4. **解析游戏信息**（进度 9%）：从 game_data.zip 中读取游戏元数据
5. **解压游戏数据**（进度 10%-100%）：解压游戏文件到目标目录

---

## 使用示例

### 示例 1：解压单个 ZIP 文件

```java
public void extractZipFile(Path zipPath, Path outputPath) {
    BasicSevenZipExtractor extractor = new BasicSevenZipExtractor(
        zipPath,
        outputPath,
        new ExtractorCollection.ExtractionListener() {
            @Override
            public void onProgress(String message, float progress, HashMap<String, Object> state) {
                updateProgressUI(message, progress);
            }

            @Override
            public void onComplete(String message, HashMap<String, Object> state) {
                showSuccessMessage("解压完成！");
            }

            @Override
            public void onError(String message, Exception ex, HashMap<String, Object> state) {
                showErrorMessage("解压失败：" + message);
            }
        }
    );

    // 在后台线程执行
    new Thread(() -> extractor.extract()).start();
}
```

### 示例 2：链式解压多个文件

```java
public void extractMultipleArchives() {
    Path tempDir = Paths.get(getCacheDir().getAbsolutePath(), "temp");
    Path finalDir = Paths.get(getFilesDir().getAbsolutePath(), "game");

    ExtractorCollection collection = new ExtractorCollection.Builder()
        // 第一步：解压外层 ZIP
        .addExtractor(new BasicSevenZipExtractor(
            Paths.get("/sdcard/Download/game.zip"),
            tempDir,
            createProgressListener("解压外层文件...")
        ))
        // 第二步：解压内层 7Z
        .addExtractor(new BasicSevenZipExtractor(
            tempDir.resolve("game.7z"),
            finalDir,
            createProgressListener("解压游戏数据...")
        ))
        .build();

    collection.extractAllInNewThread();
}

private ExtractionListener createProgressListener(String prefix) {
    return new ExtractorCollection.ExtractionListener() {
        @Override
        public void onProgress(String message, float progress, HashMap<String, Object> state) {
            int index = (int) state.get(ExtractorCollection.STATE_KEY_EXTRACTOR_INDEX);
            runOnUiThread(() -> {
                statusText.setText(prefix + " " + message);
                progressBar.setProgress((int)(progress * 100));
            });
        }

        @Override
        public void onComplete(String message, HashMap<String, Object> state) {
            Log.d(TAG, prefix + " 完成");
        }

        @Override
        public void onError(String message, Exception ex, HashMap<String, Object> state) {
            Log.e(TAG, prefix + " 失败", ex);
        }
    };
}
```

### 示例 3：处理 GOG 游戏安装文件

```java
public void installGogGame(Path gogInstallerPath) {
    Path installDir = Paths.get(getExternalFilesDir(null).getAbsolutePath(), "Games");
    
    GogShFileExtractor extractor = new GogShFileExtractor(
        gogInstallerPath,
        installDir,
        new ExtractorCollection.ExtractionListener() {
            @Override
            public void onProgress(String message, float progress, HashMap<String, Object> state) {
                runOnUiThread(() -> {
                    progressBar.setProgress((int)(progress * 100));
                    statusText.setText(message);
                });
            }

            @Override
            public void onComplete(String message, HashMap<String, Object> state) {
                Path gamePath = (Path) state.get(GogShFileExtractor.STATE_KEY_GAME_PATH);
                GogShFileExtractor.GameDataZipFile gameData = 
                    (GogShFileExtractor.GameDataZipFile) state.get(
                        GogShFileExtractor.STATE_KEY_GAME_DATA_ZIP_FILE);
                
                runOnUiThread(() -> {
                    showGameInstalledDialog(gameData.id, gameData.version, gamePath);
                });
            }

            @Override
            public void onError(String message, Exception ex, HashMap<String, Object> state) {
                runOnUiThread(() -> {
                    showErrorDialog("安装失败", message + "\n" + ex.getMessage());
                });
            }
        }
    );

    // 在后台线程执行
    new Thread(() -> extractor.extract()).start();
}
```

### 示例 4：使用共享状态在提取器间传递数据

```java
public void extractWithSharedState() {
    HashMap<String, Object> sharedState = new HashMap<>();
    
    ExtractorCollection collection = new ExtractorCollection.Builder()
        .configureState("app_name", "MyGame")
        .configureState("user_id", getCurrentUserId())
        .addExtractor(new BasicSevenZipExtractor(
            sourcePath,
            destPath,
            new ExtractorCollection.ExtractionListener() {
                @Override
                public void onComplete(String message, HashMap<String, Object> state) {
                    // 将解压结果存入 state，供下一个提取器使用
                    state.put("first_extract_path", destPath);
                }
                // ... 其他方法
            }
        ))
        .addExtractor(new BasicSevenZipExtractor(
            // 使用前一个提取器存入的路径
            destPath.resolve("inner.zip"),
            finalPath,
            listener
        ))
        .build();

    collection.extractAllInNewThread();
}
```

---

## 最佳实践

### 1. 始终在后台线程执行

解压操作是 I/O 密集型任务，应避免在主线程执行：

```java
// ✅ 推荐
new Thread(() -> extractor.extract()).start();

// ✅ 或使用 ExtractorCollection
collection.extractAllInNewThread();

// ❌ 避免在主线程直接调用
extractor.extract(); // 这会阻塞 UI！
```

### 2. 使用监听器更新 UI

在监听器回调中更新 UI 时，记得切换到主线程：

```java
@Override
public void onProgress(String message, float progress, HashMap<String, Object> state) {
    runOnUiThread(() -> {
        progressBar.setProgress((int)(progress * 100));
        statusText.setText(message);
    });
}
```

### 3. 合理处理错误

始终实现 `onError` 回调，并给用户明确的错误提示：

```java
@Override
public void onError(String message, Exception ex, HashMap<String, Object> state) {
    Log.e(TAG, "解压失败: " + message, ex);
    
    runOnUiThread(() -> {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("解压失败")
               .setMessage(message + "\n\n详细信息: " + ex.getMessage())
               .setPositiveButton("确定", null)
               .show();
    });
}
```

### 4. 使用临时文件管理

对于多步骤解压，使用 `TemporaryFileAcquirer` 自动管理临时文件：

```java
try (var tfa = new TemporaryFileAcquirer()) {
    Path tempFile = tfa.acquireTempFilePath("temp_archive.zip");
    // 使用 tempFile...
    // 退出 try 块时自动清理临时文件
}
```

### 5. 验证文件路径

解压前验证源文件和目标路径：

```java
public boolean validatePaths(Path source, Path dest) {
    if (!Files.exists(source)) {
        Log.e(TAG, "源文件不存在: " + source);
        return false;
    }
    
    if (!Files.isReadable(source)) {
        Log.e(TAG, "源文件不可读: " + source);
        return false;
    }
    
    // 确保有写入权限
    try {
        Files.createDirectories(dest.getParent());
    } catch (IOException e) {
        Log.e(TAG, "无法创建目标目录: " + dest.getParent(), e);
        return false;
    }
    
    return true;
}
```

### 6. 处理中断和取消

虽然当前 API 不直接支持取消，但可以通过状态标志实现：

```java
private volatile boolean isCancelled = false;

@Override
public void onProgress(String message, float progress, HashMap<String, Object> state) {
    if (isCancelled) {
        // 抛出异常中断解压
        throw new RuntimeException("用户取消了操作");
    }
    // 更新 UI...
}

// 在某处调用以取消操作
public void cancelExtraction() {
    isCancelled = true;
}
```

### 7. 记录日志便于调试

在关键步骤记录日志：

```java
@Override
public void onProgress(String message, float progress, HashMap<String, Object> state) {
    Log.d(TAG, String.format("解压进度: %.1f%% - %s", progress * 100, message));
}

@Override
public void onComplete(String message, HashMap<String, Object> state) {
    Log.i(TAG, "解压成功完成: " + message);
}
```

### 8. 处理存储空间不足

解压前检查可用空间：

```java
public boolean hasEnoughSpace(Path targetDir, long requiredBytes) {
    File dir = targetDir.toFile();
    long availableBytes = dir.getUsableSpace();
    
    if (availableBytes < requiredBytes * 1.1) { // 预留 10% 缓冲
        Log.e(TAG, String.format("存储空间不足。需要: %d MB, 可用: %d MB",
            requiredBytes / 1024 / 1024,
            availableBytes / 1024 / 1024));
        return false;
    }
    
    return true;
}
```

---

## 常见问题

### Q: 解压进度不准确怎么办？

A: `BasicSevenZipExtractor` 的进度基于已解压的字节数。某些压缩格式可能导致进度不够线性。这是正常现象。

### Q: 如何知道支持哪些压缩格式？

A: `BasicSevenZipExtractor` 基于 SevenZipJBinding，支持：
- 7z, zip, tar, gz, bz2, xz, rar, iso, cab 等多种格式

### Q: 能否解压加密的压缩包？

A: 当前版本不支持密码保护的压缩包。

### Q: 内存占用如何？

A: 提取器使用流式处理，内存占用较小。临时缓冲区大小为 8KB。

### Q: 如何处理非常大的文件？

A: 提取器已经优化用于处理大文件：
- 使用 `FileChannel.transferTo()` 进行高效文件复制
- 流式解压避免一次性加载到内存
- 建议对超过 1GB 的文件显示进度条

---

## 技术细节

### 线程安全

- `ExtractorCollection.extractAllInNewThread()` 会创建新线程执行
- 监听器回调在解压线程中调用，更新 UI 需要切换到主线程
- 多个提取器不应同时操作同一文件

### 性能优化

- 使用 `RandomAccessFile` 和 `FileChannel` 进行高效 I/O
- 7z 解压使用 SevenZipJBinding 原生库，性能优异
- 临时文件使用 8KB 缓冲区平衡内存和性能

### 安全性

- **路径遍历防护**：自动检测并阻止 `..` 路径攻击
- **边界检查**：验证压缩包内文件大小和偏移量
- **异常处理**：完善的错误处理机制

---

## 版本历史

- **v1.0** - 初始版本
  - BasicSevenZipExtractor 支持常见压缩格式
  - GogShFileExtractor 支持 GOG Linux 安装文件
  - ExtractorCollection 支持链式解压

---

## 参考资源

- [SevenZipJBinding 文档](http://sevenzipjbind.sourceforge.net/)
- [GOG MakeSelf 格式说明](https://github.com/megastep/makeself)
- [Android 文件存储最佳实践](https://developer.android.com/training/data-storage)

---

**最后更新**: 2025-11-08

