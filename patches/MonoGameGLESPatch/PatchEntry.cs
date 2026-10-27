using System;
using RAL.Bootstrap.CoreCLR;

namespace MonoGameGLESPatch;

[RALPatch(1, 0)]
public sealed class PatchEntry : PatchBase
{
    public override void Initialize()
    {
        Console.WriteLine("[MonoGameGLESPatch] PatchEntry.Initialize via RAL.Bootstrap");
        MonoGameGLESPatcher.Initialize(IntPtr.Zero, 0);
    }
}
