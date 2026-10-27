using System;
using RAL.Bootstrap.CoreCLR;

namespace FPSDisplayPatch;

[RALPatch(1, 0)]
public sealed class PatchEntry : PatchBase
{
    public override void Initialize()
    {
        Console.WriteLine("[FPSDisplayPatch] PatchEntry.Initialize via RAL.Bootstrap");
        FPSDisplayPatcher.Initialize(IntPtr.Zero, 0);
    }
}
