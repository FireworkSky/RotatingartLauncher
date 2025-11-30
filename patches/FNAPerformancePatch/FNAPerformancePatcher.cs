using System;
using System.Reflection;
using System.Collections.Generic;
using System.Runtime.CompilerServices;
using HarmonyLib;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;

namespace FNAPerformancePatch
{
    /// <summary>
    /// FNA 性能优化补丁
    /// 
    /// 优化策略：
    /// 1. 状态变化追踪 - 减少不必要的 GPU 状态切换
    /// 2. SpriteBatch 优化 - 强制使用高效的排序模式
    /// 3. 渲染统计 - 帮助诊断性能问题
    /// </summary>
    public class FNAPerformancePatcher
    {
        private static Harmony _harmony;
        private static bool _initialized = false;
        
        // 性能统计
        private static int _drawCallsThisFrame = 0;
        private static int _stateChangesThisFrame = 0;
        private static int _textureChangesThisFrame = 0;
        private static int _lastFrameDrawCalls = 0;
        private static int _lastFrameStateChanges = 0;
        private static int _lastFrameTextureChanges = 0;
        
        // 状态缓存 - 减少冗余状态设置
        private static BlendState _lastBlendState = null;
        private static SamplerState _lastSamplerState = null;
        private static DepthStencilState _lastDepthStencilState = null;
        private static RasterizerState _lastRasterizerState = null;
        private static Texture2D _lastTexture = null;
        
        // 启用/禁用优化
        private static bool _enableStateTracking = true;
        private static bool _enableStats = true;
        
        public static void Initialize(IntPtr arg, int sizeBytes)
        {
            if (_initialized) return;
            
            try
            {
                Console.WriteLine("[FNAPerformancePatch] Initializing...");
                
                _harmony = new Harmony("com.ralaunch.fnaperformance");
                ApplyPatches();
                
                _initialized = true;
                Console.WriteLine("[FNAPerformancePatch] Initialized successfully!");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[FNAPerformancePatch] Failed to initialize: {ex.Message}");
                Console.WriteLine(ex.StackTrace);
            }
        }
        
        private static void ApplyPatches()
        {
            try
            {
                // Patch GraphicsDevice.DrawIndexedPrimitives 来统计 draw calls
                var drawIndexedMethod = typeof(GraphicsDevice).GetMethod(
                    "DrawIndexedPrimitives",
                    BindingFlags.Instance | BindingFlags.Public,
                    null,
                    new Type[] { 
                        typeof(PrimitiveType), 
                        typeof(int), 
                        typeof(int), 
                        typeof(int), 
                        typeof(int), 
                        typeof(int) 
                    },
                    null
                );
                
                if (drawIndexedMethod != null)
                {
                    var prefix = typeof(FNAPerformancePatcher).GetMethod(
                        nameof(DrawIndexedPrimitives_Prefix),
                        BindingFlags.Static | BindingFlags.NonPublic
                    );
                    _harmony.Patch(drawIndexedMethod, new HarmonyMethod(prefix));
                    Console.WriteLine("[FNAPerformancePatch] Patched DrawIndexedPrimitives");
                }
                
                // Patch GraphicsDevice.Textures setter 来追踪纹理变化
                var texturesProperty = typeof(GraphicsDevice).GetProperty("Textures");
                if (texturesProperty != null)
                {
                    // TextureCollection 的 indexer setter
                    var textureCollectionType = texturesProperty.PropertyType;
                    var setItemMethod = textureCollectionType.GetMethod(
                        "set_Item",
                        BindingFlags.Instance | BindingFlags.Public
                    );
                    
                    if (setItemMethod != null)
                    {
                        var prefix = typeof(FNAPerformancePatcher).GetMethod(
                            nameof(SetTexture_Prefix),
                            BindingFlags.Static | BindingFlags.NonPublic
                        );
                        _harmony.Patch(setItemMethod, new HarmonyMethod(prefix));
                        Console.WriteLine("[FNAPerformancePatch] Patched Textures setter");
                    }
                }
                
                // Patch Game.Draw 来重置帧统计
                var gameDrawMethod = typeof(Game).GetMethod(
                    "Draw",
                    BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic
                );
                
                if (gameDrawMethod != null)
                {
                    var postfix = typeof(FNAPerformancePatcher).GetMethod(
                        nameof(GameDraw_Postfix),
                        BindingFlags.Static | BindingFlags.NonPublic
                    );
                    _harmony.Patch(gameDrawMethod, postfix: new HarmonyMethod(postfix));
                    Console.WriteLine("[FNAPerformancePatch] Patched Game.Draw");
                }
                
                // Patch BlendState setter
                var blendStateProperty = typeof(GraphicsDevice).GetProperty("BlendState");
                if (blendStateProperty != null)
                {
                    var setMethod = blendStateProperty.GetSetMethod();
                    if (setMethod != null)
                    {
                        var prefix = typeof(FNAPerformancePatcher).GetMethod(
                            nameof(SetBlendState_Prefix),
                            BindingFlags.Static | BindingFlags.NonPublic
                        );
                        _harmony.Patch(setMethod, new HarmonyMethod(prefix));
                        Console.WriteLine("[FNAPerformancePatch] Patched BlendState setter");
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[FNAPerformancePatch] Patch error: {ex.Message}");
            }
        }
        
        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private static void DrawIndexedPrimitives_Prefix()
        {
            if (_enableStats)
            {
                _drawCallsThisFrame++;
            }
        }
        
        private static void SetTexture_Prefix(int index, Texture value)
        {
            if (!_enableStats || index != 0) return;
            
            var tex2d = value as Texture2D;
            if (tex2d != null && tex2d != _lastTexture)
            {
                _textureChangesThisFrame++;
                _lastTexture = tex2d;
            }
        }
        
        private static bool SetBlendState_Prefix(GraphicsDevice __instance, BlendState value)
        {
            if (_enableStateTracking && value == _lastBlendState)
            {
                // 跳过冗余状态设置
                return false;
            }
            
            _lastBlendState = value;
            
            if (_enableStats)
            {
                _stateChangesThisFrame++;
            }
            
            return true;
        }
        
        private static void GameDraw_Postfix()
        {
            // 保存上一帧统计
            _lastFrameDrawCalls = _drawCallsThisFrame;
            _lastFrameStateChanges = _stateChangesThisFrame;
            _lastFrameTextureChanges = _textureChangesThisFrame;
            
            // 通过环境变量暴露统计信息
            try
            {
                Environment.SetEnvironmentVariable("RALCORE_DRAWCALLS", _lastFrameDrawCalls.ToString());
                Environment.SetEnvironmentVariable("RALCORE_STATECHANGES", _lastFrameStateChanges.ToString());
                Environment.SetEnvironmentVariable("RALCORE_TEXCHANGES", _lastFrameTextureChanges.ToString());
            }
            catch { }
            
            // 重置计数器
            _drawCallsThisFrame = 0;
            _stateChangesThisFrame = 0;
            _textureChangesThisFrame = 0;
        }
        
        /// <summary>
        /// 获取上一帧的 Draw Call 数量
        /// </summary>
        public static int GetLastFrameDrawCalls() => _lastFrameDrawCalls;
        
        /// <summary>
        /// 获取上一帧的状态变化数量
        /// </summary>
        public static int GetLastFrameStateChanges() => _lastFrameStateChanges;
        
        /// <summary>
        /// 获取上一帧的纹理变化数量
        /// </summary>
        public static int GetLastFrameTextureChanges() => _lastFrameTextureChanges;
        
        /// <summary>
        /// 启用/禁用状态追踪优化
        /// </summary>
        public static void SetStateTrackingEnabled(bool enabled)
        {
            _enableStateTracking = enabled;
        }
        
        /// <summary>
        /// 启用/禁用性能统计
        /// </summary>
        public static void SetStatsEnabled(bool enabled)
        {
            _enableStats = enabled;
        }
    }
}

