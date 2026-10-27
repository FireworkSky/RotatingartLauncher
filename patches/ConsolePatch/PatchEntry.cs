using System;
using RAL.Bootstrap.CoreCLR;

namespace ConsolePatch;

[RALPatch(1, 0)]
public sealed class PatchEntry : PatchBase
{
    public override void Initialize()
    {
        Console.WriteLine("[ConsolePatch] PatchEntry.Initialize via RAL.Bootstrap");
        ConsolePatcher.Initialize(IntPtr.Zero, 0);
    }
}
