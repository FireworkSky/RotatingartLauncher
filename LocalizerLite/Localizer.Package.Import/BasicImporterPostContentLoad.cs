using Localizer.Attributes;
using Localizer.DataModel;

namespace Localizer.Package.Import
{
[OperationTiming(OperationTiming.PostContentLoad)]
public class BasicImporterPostContentLoad<T> : BasicImporter<T> where T : IFile
{
}
}
