// RAL: tModLoader 1.3 在「工作线程加载模组纹理」这条路径上依赖旧版 FNA 的
// GLCallLocker（OpenGLDevice.actions/RunActions）把 GL 调用转发回主线程。
// 新版 FNA 走 FNA3D，GraphicsDevice.GLDevice 变成 FNA3D 的 IntPtr 设备句柄，
// GLCallLocker.InvokeAsync 里 GetType().GetField("actions") 直接 NullReferenceException，
// 于是 CalamityMod 等模组在加载贴图时被禁用。
//
// 这里不碰 GLCallLocker 本身，而是接管它的两个调用点（ImageIO 的 PNG/Raw 贴图加载）：
//  1. 在调用线程上完成 CPU 侧的图像解码（System.Drawing / raw 解析，线程安全）；
//  2. 把创建 Texture2D + SetData 的 GL 部分排进主线程队列；
//  3. 钩住 FNA 的 Game.Tick，每帧在主线程（GL 上下文有效）里排空队列。
using System;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using System.Threading;
using System.Threading.Tasks;
using MonoMod.RuntimeDetour;

namespace TML13Launcher
{
    internal static class GlCompat
    {
        private static readonly Queue<Action> s_queue = new Queue<Action>();
        private static bool s_lateFixesDone;
        private static int s_mainThreadId = -1;

        private static Assembly s_tml;
        private static Type s_texture2d;        // Terraria.Graphics.Texture2D (tML)
        private static MethodInfo s_textureDataFromStreamExt;
        private static MethodInfo s_setData;

        public static void Install(Assembly tmlAsm)
        {
            s_tml = tmlAsm;
            s_mainThreadId = Thread.CurrentThread.ManagedThreadId;

            var fnaAsm = Assembly.Load("FNA");
            var gdType = fnaAsm.GetType("Microsoft.Xna.Framework.Graphics.GraphicsDevice", true);
            var gameType = fnaAsm.GetType("Microsoft.Xna.Framework.Game", true);
            s_texture2d = fnaAsm.GetType("Microsoft.Xna.Framework.Graphics.Texture2D", true);

            var taskTexture = typeof(Task<>).MakeGenericType(s_texture2d);

            HookMethod(
                tmlAsm.GetType("Terraria.ModLoader.IO.ImageIO", true),
                "PngToTexture2DAsync", gdType, typeof(Stream), taskTexture);
            HookMethod(
                tmlAsm.GetType("Terraria.ModLoader.IO.ImageIO", true),
                "RawToTexture2DAsync", gdType, typeof(BinaryReader), taskTexture);

            // 主线程每帧排空队列（Game.Tick 里 Update/Draw/Present 都会在 GL 上下文有效时执行）
            var tick = gameType.GetMethod("Tick", BindingFlags.Instance | BindingFlags.NonPublic | BindingFlags.Public);
            var tickHookType = typeof(Action<,>).MakeGenericType(
                typeof(Action<>).MakeGenericType(gameType), gameType);
            var tickDelegate = Delegate.CreateDelegate(
                tickHookType,
                typeof(GlCompat).GetMethod(nameof(HookTick), BindingFlags.Static | BindingFlags.NonPublic)
                    .MakeGenericMethod(gameType));
            new Hook(tick, tickDelegate);
            Console.WriteLine("[TML13] Game.Tick drain hook installed");
        }

        private static void HookMethod(Type owner, string name, Type gdType, Type argType, Type taskType)
        {
            var target = owner.GetMethod(name, BindingFlags.Public | BindingFlags.Static);
            if (target == null)
            {
                Console.WriteLine("[TML13] ImageIO." + name + " not found, skip");
                return;
            }
            var origType = typeof(Func<,,>).MakeGenericType(gdType, argType, taskType);
            var hookType = typeof(Func<,,,>).MakeGenericType(origType, gdType, argType, taskType);
            var hookMethod = typeof(GlCompat)
                .GetMethod(nameof(HookAsync), BindingFlags.Static | BindingFlags.NonPublic)
                .MakeGenericMethod(gdType, argType, s_texture2d);
            new Hook(target, Delegate.CreateDelegate(hookType, hookMethod));
            Console.WriteLine("[TML13] ImageIO." + name + " hooked");
        }

        /// <summary>
        /// 替换 ImageIO 的异步贴图加载：CPU 侧解码留在当前线程，GL 侧排到主线程。
        /// </summary>
        private static Task<TTexture> HookAsync<TDevice, TArg, TTexture>(
            Func<TDevice, TArg, Task<TTexture>> orig,
            TDevice device,
            TArg arg)
        {
            if (Thread.CurrentThread.ManagedThreadId == s_mainThreadId)
            {
                // 主线程本来就能直接建纹理，交给原实现（GLCallLocker 主线程分支是 inline 执行）
                return orig(device, arg);
            }

            int width;
            int height;
            byte[] raw;
            try
            {
                Decode(arg, out width, out height, out raw);
            }
            catch (Exception e)
            {
                var failed = new TaskCompletionSource<TTexture>();
                failed.SetException(e);
                return failed.Task;
            }

            var tcs = new TaskCompletionSource<TTexture>();
            Enqueue(() =>
            {
                try
                {
                    var texture = Activator.CreateInstance(
                        s_texture2d,
                        new object[] { device, width, height });
                    if (s_setData == null)
                    {
                        foreach (var m in s_texture2d.GetMethods(BindingFlags.Instance | BindingFlags.Public))
                        {
                            if (m.Name == "SetData" && m.IsGenericMethodDefinition &&
                                m.GetParameters().Length == 1)
                            {
                                s_setData = m;
                                break;
                            }
                        }
                    }
                    // SetData<byte>(byte[]) —— 泛型参数是元素类型 byte，不是 byte[]
                    s_setData.MakeGenericMethod(typeof(byte))
                        .Invoke(texture, new object[] { raw });
                    tcs.SetResult((TTexture)texture);
                }
                catch (Exception e)
                {
                    tcs.SetException(e.InnerException ?? e);
                }
            });
            return tcs.Task;
        }

        private static void Decode(object arg, out int width, out int height, out byte[] raw)
        {
            width = 0;
            height = 0;
            raw = null;

            if (arg is Stream stream)
            {
                // Terraria.Graphics.Texture2D.TextureDataFromStreamEXT(Stream, out int, out int, out byte[])
                if (s_textureDataFromStreamExt == null)
                {
                    // FNA 的 Texture2D.TextureDataFromStreamEXT(Stream, out int, out int, out byte[], ...)
                    s_textureDataFromStreamExt = s_texture2d.GetMethod(
                        "TextureDataFromStreamEXT",
                        BindingFlags.Public | BindingFlags.Static);
                }
                // FNA 的签名：TextureDataFromStreamEXT(Stream, out int, out int, out byte[],
                //                                     int requestedWidth = -1, int requestedHeight = -1, bool zoom = false)
                var args = new object[] { stream, 0, 0, null, -1, -1, false };
                s_textureDataFromStreamExt.Invoke(null, args);
                width = (int)args[1];
                height = (int)args[2];
                raw = (byte[])args[3];
                return;
            }

            // raw 版本：ImageIO.ReadRaw(BinaryReader) -> Tuple<int,int,byte[]>
            var imageIo = s_tml.GetType("Terraria.ModLoader.IO.ImageIO", true);
            var readRaw = imageIo.GetMethod("ReadRaw", new[] { typeof(BinaryReader) });
            var tuple = readRaw.Invoke(null, new[] { arg });
            width = (int)tuple.GetType().GetProperty("Item1").GetValue(tuple, null);
            height = (int)tuple.GetType().GetProperty("Item2").GetValue(tuple, null);
            raw = (byte[])tuple.GetType().GetProperty("Item3").GetValue(tuple, null);
        }

        private static void Enqueue(Action action)
        {
            lock (s_queue)
            {
                s_queue.Enqueue(action);
            }
        }

        private static void Drain()
        {
            while (true)
            {
                Action action;
                lock (s_queue)
                {
                    if (s_queue.Count == 0) return;
                    action = s_queue.Dequeue();
                }
                try
                {
                    action();
                }
                catch (Exception e)
                {
                    Console.WriteLine("[TML13] drain action failed: " + e);
                }
            }
        }

        private static void HookTick<TGame>(Action<TGame> orig, TGame self)
        {
            if (!s_lateFixesDone && s_tml != null)
            {
                s_lateFixesDone = true;
                try
                {
                    Program.ApplyLateFixes(s_tml);
                }
                catch (Exception e)
                {
                    Console.WriteLine("[TML13] late fixes failed: " + e);
                }
            }
            orig(self);
            try
            {
                Drain();
            }
            catch (Exception e)
            {
                Console.WriteLine("[TML13] drain failed: " + e);
            }
        }
    }
}
