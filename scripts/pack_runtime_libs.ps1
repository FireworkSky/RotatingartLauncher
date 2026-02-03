# ============================================================
# Runtime Libraries Packer
# 
# 将大型 native 库打包成 tar.xz 格式，减小 APK 体积
# 运行时通过 RuntimeLibraryLoader 解压到私有目录
#
# 使用方法：
#   .\pack_runtime_libs.ps1 -BuildType Release
#
# 输出：
#   app\src\main\assets\runtime_libs.tar.xz
# ============================================================

param(
    [ValidateSet("Debug", "Release")]
    [string]$BuildType = "Release",
    
    [switch]$Force,
    
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

# 项目路径
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$JniLibsDir = "$ProjectRoot\app\src\main\jniLibs\arm64-v8a"
$MergedLibDir = "$ProjectRoot\app\build\intermediates\merged_native_libs\$($BuildType.ToLower())\merge$($BuildType)NativeLibs\out\lib\arm64-v8a"
$StrippedLibDir = "$ProjectRoot\app\build\intermediates\stripped_native_libs\$($BuildType.ToLower())\strip$($BuildType)DebugSymbols\out\lib\arm64-v8a"
$MergedJniDir = "$ProjectRoot\app\build\intermediates\merged_jni_libs\$($BuildType.ToLower())\merge$($BuildType)JniLibFolders\out\arm64-v8a"
$AssetsDir = "$ProjectRoot\app\src\main\assets"
$OutputFile = "$AssetsDir\runtime_libs.tar.xz"

# 查找 CMake 编译输出目录（cxx 目录下的动态哈希子目录）
$CxxLibDir = $null
$CxxBaseDir = "$ProjectRoot\app\build\intermediates\cxx\$BuildType"
if (Test-Path $CxxBaseDir) {
    $CxxSubDirs = Get-ChildItem -Path $CxxBaseDir -Directory | Select-Object -First 1
    if ($CxxSubDirs) {
        $CxxLibDir = "$($CxxSubDirs.FullName)\obj\arm64-v8a"
    }
}

# 库查找优先级：stripped > merged > merged_jni > cxx > jniLibs
$SearchDirs = @($StrippedLibDir, $MergedLibDir, $MergedJniDir)
if ($CxxLibDir -and (Test-Path $CxxLibDir)) {
    $SearchDirs += $CxxLibDir
}
$SearchDirs += $JniLibsDir

# 要打包的大型库（按需动态加载）
# 总计约 90+ MB，压缩后约 30-40 MB
# 注意：lib7-Zip-JBinding.so 保留在 APK 中，因为安装游戏时需要立即使用
$RuntimeLibs = @(
    "libbox64.so"              # 56.55 MB - Box64 x86_64 模拟器
    "libmobileglues.so"        # 8.36 MB  - MobileGlues 翻译层
    "libSkiaSharp.so"          # 6.64 MB  - Skia 图形库
    "libEGL_angle.so"          # ~0.3 MB  - ANGLE EGL (必须和 GLES 一起)
    "libGLESv2_angle.so"       # 5.31 MB  - ANGLE OpenGL ES
    "libEGL_gl4es.so"          # ~0.03 MB - GL4ES EGL
    "libGL_gl4es.so"           # 3.54 MB  - GL4ES OpenGL
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Runtime Libraries Packer" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Build Type: $BuildType"
Write-Host "Search Dirs:"
foreach ($dir in $SearchDirs) {
    $exists = if (Test-Path $dir) { "[OK]" } else { "[--]" }
    Write-Host "  $exists $dir"
}
Write-Host "Output:     $OutputFile"
Write-Host ""

# 查找库函数
function Find-Library {
    param([string]$LibName)
    foreach ($dir in $SearchDirs) {
        $path = Join-Path $dir $LibName
        if (Test-Path $path) {
            return $path
        }
    }
    return $null
}

# 检查要打包的库
$LibsToPackage = @()
foreach ($lib in $RuntimeLibs) {
    $libPath = Find-Library $lib
    if ($libPath) {
        $size = [math]::Round((Get-Item $libPath).Length / 1MB, 2)
        Write-Host "  [OK] $lib ($size MB)" -ForegroundColor Green
        $LibsToPackage += $libPath
    } else {
        Write-Host "  [SKIP] $lib (not found)" -ForegroundColor Yellow
    }
}

if ($LibsToPackage.Count -eq 0) {
    Write-Host ""
    Write-Host "No libraries to package!" -ForegroundColor Yellow
    exit 0
}

# 计算总大小
$totalSize = 0
foreach ($lib in $LibsToPackage) {
    $totalSize += (Get-Item $lib).Length
}
Write-Host ""
Write-Host "Total size before compression: $([math]::Round($totalSize / 1MB, 2)) MB" -ForegroundColor Cyan

if ($DryRun) {
    Write-Host ""
    Write-Host "[DRY RUN] Would create: $OutputFile" -ForegroundColor Yellow
    exit 0
}

# 创建临时目录
$TempDir = "$env:TEMP\runtime_libs_pack_$(Get-Random)"
New-Item -ItemType Directory -Path $TempDir -Force | Out-Null

try {
    # 复制库到临时目录
    Write-Host ""
    Write-Host "Copying libraries..."
    foreach ($lib in $LibsToPackage) {
        Copy-Item $lib $TempDir
    }
    
    # 检查是否有 tar 和 xz 命令
    $tarPath = Get-Command tar -ErrorAction SilentlyContinue
    $xzPath = Get-Command xz -ErrorAction SilentlyContinue
    
    if ($tarPath -and $xzPath) {
        # 使用原生 tar + xz
        Write-Host "Creating tar.xz archive (native)..."
        
        $tarFile = "$TempDir\runtime_libs.tar"
        
        Push-Location $TempDir
        tar -cvf $tarFile *
        Pop-Location
        
        # 使用 xz 压缩
        xz -9 -f $tarFile
        
        # 移动到 assets
        if (-not (Test-Path $AssetsDir)) {
            New-Item -ItemType Directory -Path $AssetsDir -Force | Out-Null
        }
        Move-Item "$tarFile.xz" $OutputFile -Force
        
    } else {
        # 使用 7-Zip 作为备选
        $7zPath = Get-Command 7z -ErrorAction SilentlyContinue
        if (-not $7zPath) {
            $7zPath = "C:\Program Files\7-Zip\7z.exe"
            if (-not (Test-Path $7zPath)) {
                Write-Host "Error: Neither tar/xz nor 7-Zip found!" -ForegroundColor Red
                Write-Host "Please install 7-Zip or use WSL with tar/xz"
                exit 1
            }
        }
        
        Write-Host "Creating tar.xz archive (7-Zip)..."
        
        $tarFile = "$TempDir\runtime_libs.tar"
        $xzFile = "$TempDir\runtime_libs.tar.xz"
        
        # 创建 tar
        & $7zPath a -ttar $tarFile "$TempDir\*.so"
        
        # 创建 xz
        & $7zPath a -txz $xzFile $tarFile
        
        # 移动到 assets
        if (-not (Test-Path $AssetsDir)) {
            New-Item -ItemType Directory -Path $AssetsDir -Force | Out-Null
        }
        Move-Item $xzFile $OutputFile -Force
    }
    
    # 显示结果
    $compressedSize = (Get-Item $OutputFile).Length
    $ratio = [math]::Round(($compressedSize / $totalSize) * 100, 1)
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  Packaging Complete!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Output: $OutputFile"
    Write-Host "Original:   $([math]::Round($totalSize / 1MB, 2)) MB"
    Write-Host "Compressed: $([math]::Round($compressedSize / 1MB, 2)) MB ($ratio%)"
    Write-Host "Saved:      $([math]::Round(($totalSize - $compressedSize) / 1MB, 2)) MB"
    Write-Host ""
    
} finally {
    # 清理临时目录
    Remove-Item -Path $TempDir -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Add runtime_libs.tar.xz to .gitignore (large file)"
Write-Host "  2. Exclude libbox64.so from APK packaging in build.gradle"
Write-Host "  3. The RuntimeLibraryLoader will extract at first run"
