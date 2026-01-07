# 编译并打包所有补丁到 assets/patches 文件夹
# 使用方法: 在 PowerShell 中运行 .\build_and_package.ps1

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AssetsDir = Join-Path $ScriptDir "..\app\src\main\assets\patches"

# 确保输出目录存在
if (-not (Test-Path $AssetsDir)) {
    New-Item -ItemType Directory -Path $AssetsDir -Force | Out-Null
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "编译并打包补丁到 assets/patches" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 补丁列表和对应的输出名称
# 加载顺序按 priority 从小到大: TouchPatch(45) -> MonoGamePatch(50) -> ConsolePatch/PlatformFixPatch(100)
$patches = @(
    @{ Name = "TouchPatch"; OutputName = "com.app.ralaunch.smapi.touchfix" },
    @{ Name = "MonoGamePatch"; OutputName = "com.app.ralaunch.smapi.monogamefix" },
    @{ Name = "PlatformFixPatch"; OutputName = "com.app.ralaunch.smapi.platformfix" },
    @{ Name = "ConsolePatch"; OutputName = "com.app.ralaunch.tmodloader.consolepatch" },
    @{ Name = "FPSDisplayPatch"; OutputName = "com.app.ralaunch.tmodloader.fpsdisplaypatch" },
    @{ Name = "HostAndPlayPatch"; OutputName = "com.app.ralaunch.tmodloader.hostandplaypatch" },
    @{ Name = "TModLoaderPatch"; OutputName = "com.app.ralaunch.tmodloader.androidfix" }
)

foreach ($patch in $patches) {
    $patchName = $patch.Name
    $outputName = $patch.OutputName
    $patchDir = Join-Path $ScriptDir $patchName
    $csprojPath = Join-Path $patchDir "$patchName.csproj"
    
    if (-not (Test-Path $csprojPath)) {
        Write-Host "跳过 $patchName - 找不到 .csproj 文件" -ForegroundColor Yellow
        continue
    }
    
    Write-Host ""
    Write-Host "正在编译 $patchName..." -ForegroundColor Green
    
    # 编译项目
    Push-Location $patchDir
    try {
        dotnet build -c Release --nologo -v q
        if ($LASTEXITCODE -ne 0) {
            Write-Host "编译 $patchName 失败!" -ForegroundColor Red
            Pop-Location
            continue
        }
        Write-Host "编译成功!" -ForegroundColor Green
    }
    finally {
        Pop-Location
    }
    
    # 查找编译输出目录
    $binDir = Join-Path $patchDir "bin\Release"
    $outputDir = Get-ChildItem -Path $binDir -Directory | Select-Object -First 1
    
    if (-not $outputDir) {
        Write-Host "找不到编译输出目录: $binDir" -ForegroundColor Red
        continue
    }
    
    $buildOutput = $outputDir.FullName
    Write-Host "编译输出目录: $buildOutput" -ForegroundColor Gray
    
    # 创建临时打包目录
    $tempPackageDir = Join-Path $env:TEMP "patch_package_$outputName"
    if (Test-Path $tempPackageDir) {
        Remove-Item -Recurse -Force $tempPackageDir
    }
    New-Item -ItemType Directory -Path $tempPackageDir -Force | Out-Null
    
    # 复制 patch.json
    $patchJsonPath = Join-Path $patchDir "patch.json"
    if (Test-Path $patchJsonPath) {
        Copy-Item $patchJsonPath $tempPackageDir
    } else {
        Write-Host "警告: 找不到 patch.json" -ForegroundColor Yellow
    }
    
    # 复制 DLL 文件 (只复制补丁本身的 DLL，不复制依赖)
    $dllPath = Join-Path $buildOutput "$patchName.dll"
    if (Test-Path $dllPath) {
        Copy-Item $dllPath $tempPackageDir
    } else {
        Write-Host "警告: 找不到 $patchName.dll" -ForegroundColor Yellow
    }
    
    # 创建 ZIP 文件
    $zipPath = Join-Path $AssetsDir "$outputName.zip"
    if (Test-Path $zipPath) {
        Remove-Item $zipPath -Force
    }
    
    Write-Host "正在打包到: $zipPath" -ForegroundColor Green
    Compress-Archive -Path "$tempPackageDir\*" -DestinationPath $zipPath -Force
    
    # 清理临时目录
    Remove-Item -Recurse -Force $tempPackageDir
    
    Write-Host "打包完成: $outputName.zip" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "所有补丁编译打包完成!" -ForegroundColor Cyan
Write-Host "输出目录: $AssetsDir" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 列出生成的文件
Write-Host ""
Write-Host "生成的补丁文件:" -ForegroundColor Yellow
Get-ChildItem -Path $AssetsDir -Filter "*.zip" | ForEach-Object {
    Write-Host "  - $($_.Name) ($([math]::Round($_.Length / 1KB, 2)) KB)" -ForegroundColor White
}

