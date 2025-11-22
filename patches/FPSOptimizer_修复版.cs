        // ⚡ 修复版：改回 void 返回类型，只清除标志，不阻止 ApplyChanges 执行
        // 原来的 bool 返回 false 会完全跳过 ApplyChanges，导致渲染破坏
        public static void ApplyChanges_Prefix(Microsoft.Xna.Framework.GraphicsDeviceManager __instance)
        {
            try
            {
                // 只清除 prefsChanged 标志，防止不必要的 Device Reset
                // 但仍然让 ApplyChanges 正常执行以应用其他必要的设置
                var prefsChangedField = __instance.GetType().GetField("prefsChanged", BindingFlags.Instance | BindingFlags.NonPublic);
                if (prefsChangedField != null)
                {
                    bool prefsChanged = (bool)(prefsChangedField.GetValue(__instance) ?? false);
                    if (prefsChanged)
                    {
                        prefsChangedField.SetValue(__instance, false);

                        if (!_applyChangesLoggedOnce)
                        {
                            Console.WriteLine("[FPSOptimizer] Cleared prefsChanged flag to prevent Device Reset");
                            _applyChangesLoggedOnce = true;
                        }
                    }
                }

                // 强制禁用 VSync
                var syncProperty = __instance.GetType().GetProperty("SynchronizeWithVerticalRetrace");
                if (syncProperty != null)
                {
                    syncProperty.SetValue(__instance, false);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[FPSOptimizer] ApplyChanges_Prefix error: {ex.Message}");
            }
        }
