using System;
using RAL.Bootstrap.CoreCLR;

namespace HostAndPlayPatch;

[RALPatch(1, 0)]
public sealed class PatchEntry : PatchBase
{
    public override void Initialize()
    {
        Console.WriteLine("[HostAndPlayPatch] PatchEntry.Initialize via RAL.Bootstrap");
        HostAndPlayPatcher.Initialize();
    }
}
