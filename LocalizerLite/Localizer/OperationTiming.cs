using System;

namespace Localizer
{
[Flags]
public enum OperationTiming : byte
{
	BeforeModCtor = 1,
	BeforeModLoad = 2,
	BeforeContentLoad = 4,
	PostContentLoad = 8,
	Any = byte.MaxValue
}
}
