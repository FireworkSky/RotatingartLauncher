using System;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using HarmonyLib;

namespace ConsolePatch;

/// <summary>
/// Android 平台兼容补丁
/// 修复 Android 平台不支持的 Console 和 PosixSignalRegistration 等功能
/// </summary>
public static class ConsolePatcher
{
    private static Harmony? _harmony;
    private static bool _initialized = false;
    
    // 存储模拟的控制台颜色状态
    private static ConsoleColor _foregroundColor = ConsoleColor.Gray;
    private static ConsoleColor _backgroundColor = ConsoleColor.Black;
    
    // 存储模拟的控制台编码状态
    private static Encoding _inputEncoding = Encoding.UTF8;
    private static Encoding _outputEncoding = Encoding.UTF8;

    /// <summary>
    /// 补丁初始化方法
    /// 会在游戏程序集加载前被自动调用
    /// </summary>
    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        if (_initialized)
        {
            Console.WriteLine("[ConsolePatch] Already initialized, skipping...");
            return 0;
        }

        try
        {
            Console.WriteLine("========================================");
            Console.WriteLine("[ConsolePatch] Android Console Compatibility Patch");
            Console.WriteLine("========================================");
            Console.WriteLine("[ConsolePatch] Patching Console properties for Android...");

            _harmony = new Harmony("com.ralaunch.consolepatch");

            // Patch Console.Title setter
            PatchConsoleTitleSetter();

            // Patch Console.ForegroundColor getter and setter
            PatchConsoleForegroundColor();

            // Patch Console.BackgroundColor getter and setter
            PatchConsoleBackgroundColor();

            // Patch Console.ResetColor (also not supported)
            PatchConsoleResetColor();

            // Patch Console.Clear (also not supported)
            PatchConsoleClear();
            
            // Patch Console.ReadKey (not supported on Android)
            PatchConsoleReadKey();
            
            // Patch Console.ReadLine (not supported on Android)
            PatchConsoleReadLine();
            
            // Patch Console.InputEncoding (not supported on Android)
            PatchConsoleInputEncoding();
            
            // Patch Console.OutputEncoding (not supported on Android)
            PatchConsoleOutputEncoding();

            // Patch PosixSignalRegistration.Create (not supported on Android)
            PatchPosixSignalRegistration();

            _initialized = true;
            Console.WriteLine("[ConsolePatch] All console patches applied!");
            Console.WriteLine("========================================");

            return 0; // 成功
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] ERROR: {ex.Message}");
            Console.WriteLine($"[ConsolePatch] Stack: {ex.StackTrace}");
            return -1; // 失败
        }
    }

    /// <summary>
    /// Patch Console.Title setter
    /// </summary>
    private static void PatchConsoleTitleSetter()
    {
        try
        {
            var titleProperty = typeof(Console).GetProperty("Title", BindingFlags.Public | BindingFlags.Static);
            var setMethod = titleProperty?.GetSetMethod();

            if (setMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_SetTitle_Prefix));
                _harmony!.Patch(setMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.Title setter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.Title setter not found (may be already unsupported)");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.Title: {ex.Message}");
        }
    }

    /// <summary>
    /// Patch Console.ForegroundColor getter and setter
    /// </summary>
    private static void PatchConsoleForegroundColor()
    {
        try
        {
            var colorProperty = typeof(Console).GetProperty("ForegroundColor", BindingFlags.Public | BindingFlags.Static);
            
            // Patch getter
            var getMethod = colorProperty?.GetGetMethod();
            if (getMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_GetForegroundColor_Prefix));
                _harmony!.Patch(getMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.ForegroundColor getter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.ForegroundColor getter not found");
            }
            
            // Patch setter
            var setMethod = colorProperty?.GetSetMethod();
            if (setMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_SetForegroundColor_Prefix));
                _harmony!.Patch(setMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.ForegroundColor setter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.ForegroundColor setter not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.ForegroundColor: {ex.Message}");
        }
    }

    /// <summary>
    /// Patch Console.BackgroundColor getter and setter
    /// </summary>
    private static void PatchConsoleBackgroundColor()
    {
        try
        {
            var colorProperty = typeof(Console).GetProperty("BackgroundColor", BindingFlags.Public | BindingFlags.Static);
            
            // Patch getter
            var getMethod = colorProperty?.GetGetMethod();
            if (getMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_GetBackgroundColor_Prefix));
                _harmony!.Patch(getMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.BackgroundColor getter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.BackgroundColor getter not found");
            }
            
            // Patch setter
            var setMethod = colorProperty?.GetSetMethod();
            if (setMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_SetBackgroundColor_Prefix));
                _harmony!.Patch(setMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.BackgroundColor setter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.BackgroundColor setter not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.BackgroundColor: {ex.Message}");
        }
    }

    /// <summary>
    /// Patch Console.ResetColor
    /// </summary>
    private static void PatchConsoleResetColor()
    {
        try
        {
            var resetMethod = typeof(Console).GetMethod("ResetColor", BindingFlags.Public | BindingFlags.Static);

            if (resetMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_ResetColor_Prefix));
                _harmony!.Patch(resetMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.ResetColor patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.ResetColor not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.ResetColor: {ex.Message}");
        }
    }

    /// <summary>
    /// Prefix for Console.Title setter - skip the original method
    /// </summary>
    public static bool Console_SetTitle_Prefix(string value)
    {
        // Do nothing - Android doesn't support console title
        // Just silently ignore the call
        return false; // Skip original method
    }

    /// <summary>
    /// Prefix for Console.ForegroundColor getter - return stored value
    /// </summary>
    public static bool Console_GetForegroundColor_Prefix(ref ConsoleColor __result)
    {
        __result = _foregroundColor;
        return false; // Skip original method
    }
    
    /// <summary>
    /// Prefix for Console.ForegroundColor setter - store value but don't actually set
    /// </summary>
    public static bool Console_SetForegroundColor_Prefix(ConsoleColor value)
    {
        _foregroundColor = value;
        return false; // Skip original method
    }
    
    /// <summary>
    /// Prefix for Console.BackgroundColor getter - return stored value
    /// </summary>
    public static bool Console_GetBackgroundColor_Prefix(ref ConsoleColor __result)
    {
        __result = _backgroundColor;
        return false; // Skip original method
    }
    
    /// <summary>
    /// Prefix for Console.BackgroundColor setter - store value but don't actually set
    /// </summary>
    public static bool Console_SetBackgroundColor_Prefix(ConsoleColor value)
    {
        _backgroundColor = value;
        return false; // Skip original method
    }

    /// <summary>
    /// Prefix for Console.ResetColor - skip the original method
    /// </summary>
    public static bool Console_ResetColor_Prefix()
    {
        // Reset to default colors
        _foregroundColor = ConsoleColor.Gray;
        _backgroundColor = ConsoleColor.Black;
        return false; // Skip original method
    }

    /// <summary>
    /// Patch Console.Clear
    /// </summary>
    private static void PatchConsoleClear()
    {
        try
        {
            var clearMethod = typeof(Console).GetMethod("Clear", BindingFlags.Public | BindingFlags.Static);

            if (clearMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_Clear_Prefix));
                _harmony!.Patch(clearMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.Clear patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.Clear not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.Clear: {ex.Message}");
        }
    }

    /// <summary>
    /// Prefix for Console.Clear - skip the original method
    /// </summary>
    public static bool Console_Clear_Prefix()
    {
        // Do nothing - Android doesn't support console clear
        // Just silently ignore the call
        return false; // Skip original method
    }
    
    /// <summary>
    /// Patch Console.ReadKey - not supported on Android
    /// </summary>
    private static void PatchConsoleReadKey()
    {
        try
        {
            // Patch ReadKey() - no parameters
            var readKeyMethod = typeof(Console).GetMethod("ReadKey", BindingFlags.Public | BindingFlags.Static, null, Type.EmptyTypes, null);
            if (readKeyMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_ReadKey_Prefix));
                _harmony!.Patch(readKeyMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.ReadKey() patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.ReadKey() not found");
            }
            
            // Patch ReadKey(bool intercept)
            var readKeyInterceptMethod = typeof(Console).GetMethod("ReadKey", BindingFlags.Public | BindingFlags.Static, null, new[] { typeof(bool) }, null);
            if (readKeyInterceptMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_ReadKey_Prefix));
                _harmony!.Patch(readKeyInterceptMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.ReadKey(bool) patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.ReadKey(bool) not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.ReadKey: {ex.Message}");
        }
    }
    
    /// <summary>
    /// Prefix for Console.ReadKey - return Enter key instead of blocking/throwing
    /// </summary>
    public static bool Console_ReadKey_Prefix(ref ConsoleKeyInfo __result)
    {
        // Return Enter key as a dummy result - Android doesn't support ReadKey
        // This prevents blocking or throwing PlatformNotSupportedException
        __result = new ConsoleKeyInfo('\r', ConsoleKey.Enter, false, false, false);
        return false; // Skip original method
    }
    
    /// <summary>
    /// Patch Console.ReadLine - not supported on Android
    /// </summary>
    private static void PatchConsoleReadLine()
    {
        try
        {
            // Patch ReadLine()
            var readLineMethod = typeof(Console).GetMethod("ReadLine", BindingFlags.Public | BindingFlags.Static, null, Type.EmptyTypes, null);
            if (readLineMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_ReadLine_Prefix));
                _harmony!.Patch(readLineMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.ReadLine() patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.ReadLine() not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.ReadLine: {ex.Message}");
        }
    }
    
    /// <summary>
    /// Prefix for Console.ReadLine - block indefinitely instead of throwing (SMAPI runs this in a background thread)
    /// </summary>
    public static bool Console_ReadLine_Prefix(ref string? __result)
    {
        // Block indefinitely - SMAPI's console input loop runs in a background thread
        // We can't throw an exception here because it would crash the app
        // Instead, we just wait forever (the thread will be abandoned when the app exits)
        try
        {
            // Use a long sleep in a loop - this keeps the thread alive but idle
            while (true)
            {
                System.Threading.Thread.Sleep(int.MaxValue);
            }
        }
        catch (System.Threading.ThreadInterruptedException)
        {
            // Thread was interrupted, return null
            __result = null;
            return false;
        }
    }
    
    /// <summary>
    /// Patch Console.InputEncoding getter and setter - not supported on Android
    /// </summary>
    private static void PatchConsoleInputEncoding()
    {
        try
        {
            var encodingProperty = typeof(Console).GetProperty("InputEncoding", BindingFlags.Public | BindingFlags.Static);
            
            // Patch getter
            var getMethod = encodingProperty?.GetGetMethod();
            if (getMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_GetInputEncoding_Prefix));
                _harmony!.Patch(getMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.InputEncoding getter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.InputEncoding getter not found");
            }
            
            // Patch setter
            var setMethod = encodingProperty?.GetSetMethod();
            if (setMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_SetInputEncoding_Prefix));
                _harmony!.Patch(setMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.InputEncoding setter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.InputEncoding setter not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.InputEncoding: {ex.Message}");
        }
    }
    
    /// <summary>
    /// Prefix for Console.InputEncoding getter - return stored value
    /// </summary>
    public static bool Console_GetInputEncoding_Prefix(ref Encoding __result)
    {
        __result = _inputEncoding;
        return false; // Skip original method
    }
    
    /// <summary>
    /// Prefix for Console.InputEncoding setter - store value but don't actually set
    /// </summary>
    public static bool Console_SetInputEncoding_Prefix(Encoding value)
    {
        _inputEncoding = value;
        return false; // Skip original method
    }
    
    /// <summary>
    /// Patch Console.OutputEncoding getter and setter - not supported on Android
    /// </summary>
    private static void PatchConsoleOutputEncoding()
    {
        try
        {
            var encodingProperty = typeof(Console).GetProperty("OutputEncoding", BindingFlags.Public | BindingFlags.Static);
            
            // Patch getter
            var getMethod = encodingProperty?.GetGetMethod();
            if (getMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_GetOutputEncoding_Prefix));
                _harmony!.Patch(getMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.OutputEncoding getter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.OutputEncoding getter not found");
            }
            
            // Patch setter
            var setMethod = encodingProperty?.GetSetMethod();
            if (setMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(Console_SetOutputEncoding_Prefix));
                _harmony!.Patch(setMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] Console.OutputEncoding setter patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] Console.OutputEncoding setter not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch Console.OutputEncoding: {ex.Message}");
        }
    }
    
    /// <summary>
    /// Prefix for Console.OutputEncoding getter - return stored value
    /// </summary>
    public static bool Console_GetOutputEncoding_Prefix(ref Encoding __result)
    {
        __result = _outputEncoding;
        return false; // Skip original method
    }
    
    /// <summary>
    /// Prefix for Console.OutputEncoding setter - store value but don't actually set
    /// </summary>
    public static bool Console_SetOutputEncoding_Prefix(Encoding value)
    {
        _outputEncoding = value;
        return false; // Skip original method
    }

    /// <summary>
    /// Patch PosixSignalRegistration.Create - not supported on Android
    /// </summary>
    private static void PatchPosixSignalRegistration()
    {
        try
        {
            // PosixSignalRegistration is in System.Runtime.InteropServices
            var posixSignalRegType = typeof(PosixSignalRegistration);

            if (posixSignalRegType == null)
            {
                Console.WriteLine("[ConsolePatch] PosixSignalRegistration type not found");
                return;
            }

            // Find the Create method
            var createMethod = posixSignalRegType.GetMethod("Create", BindingFlags.Public | BindingFlags.Static);

            if (createMethod != null)
            {
                var prefix = new HarmonyMethod(typeof(ConsolePatcher), nameof(PosixSignalRegistration_Create_Prefix));
                _harmony!.Patch(createMethod, prefix: prefix);
                Console.WriteLine("[ConsolePatch] PosixSignalRegistration.Create patched!");
            }
            else
            {
                Console.WriteLine("[ConsolePatch] PosixSignalRegistration.Create method not found");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ConsolePatch] Failed to patch PosixSignalRegistration.Create: {ex.Message}");
        }
    }

    /// <summary>
    /// Prefix for PosixSignalRegistration.Create - return null instead of throwing
    /// </summary>
    public static bool PosixSignalRegistration_Create_Prefix(ref PosixSignalRegistration? __result)
    {
        // Return null - Android doesn't support POSIX signal registration
        // The caller should handle null gracefully
        __result = null;
        return false; // Skip original method
    }
}

