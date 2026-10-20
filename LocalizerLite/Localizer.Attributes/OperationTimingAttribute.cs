using System;

namespace Localizer.Attributes
{
public class OperationTimingAttribute : Attribute
{
	public OperationTiming Timing { get; }

	public OperationTimingAttribute(OperationTiming timing = OperationTiming.Any)
	{
		Timing = timing;
	}
}
}
