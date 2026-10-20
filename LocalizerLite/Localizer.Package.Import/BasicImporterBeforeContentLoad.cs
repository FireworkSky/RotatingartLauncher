using Localizer.Attributes;
using Localizer.DataModel;

namespace Localizer.Package.Import
{
[OperationTiming(OperationTiming.BeforeContentLoad)]
public class BasicImporterBeforeContentLoad<T> : BasicImporter<T> where T : IFile
{
}
}
