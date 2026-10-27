using System;
using RAL.Bootstrap.CoreCLR;

namespace PlatformFixPatch;

[RALPatch(1, 0)]
public sealed class PatchEntry : PatchBase
{
    public override void Initialize()
    {
        Console.WriteLine("[PlatformFixPatch] PatchEntry.Initialize via RAL.Bootstrap");
        PlatformFixPatcher.Initialize(IntPtr.Zero, 0);
    }
}
