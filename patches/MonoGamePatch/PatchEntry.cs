using System;
using RAL.Bootstrap.CoreCLR;

namespace MonoGamePatch;

[RALPatch(1, 0)]
public sealed class PatchEntry : PatchBase
{
    public override void Initialize()
    {
        Console.WriteLine("[MonoGamePatch] PatchEntry.Initialize via RAL.Bootstrap");
        MonoGamePatcher.Initialize(IntPtr.Zero, 0);
    }
}
