using System;
using RAL.Bootstrap.CoreCLR;

namespace EverestPatch;

[RALPatch(1, 0)]
public sealed class PatchEntry : PatchBase
{
    public override void Initialize()
    {
        Console.WriteLine("[EverestPatch] PatchEntry.Initialize via RAL.Bootstrap");
        Patcher.Initialize(IntPtr.Zero, 0);
    }
}
