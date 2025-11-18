@echo off
echo ========================================
echo Building Patch System
echo ========================================

cd /d %~dp0

echo.
echo [1/3] Building ExamplePatch...
cd ExamplePatch
dotnet build -c Release
if errorlevel 1 (
    echo ERROR: ExamplePatch build failed
    pause
    exit /b 1
)
cd ..

echo.
echo [2/3] Copying files to assets...
set ASSETS_DIR=..\app\src\main\assets\patches

if not exist "%ASSETS_DIR%" mkdir "%ASSETS_DIR%"

copy /Y ExamplePatch\bin\Release\net8.0\ExamplePatch.dll "%ASSETS_DIR%\"
copy /Y ExamplePatch\patch.json "%ASSETS_DIR%\"

echo.
echo [3/3] Build complete!
echo ========================================
echo Output: %ASSETS_DIR%
echo.
echo ExamplePatch.dll       [Patch Assembly]
echo patch.json             [Metadata]
echo ========================================

pause
