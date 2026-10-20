using Newtonsoft.Json;
using Newtonsoft.Json.Converters;

namespace Localizer
{
[JsonConverter(typeof(StringEnumConverter))]
public enum AutoImportType
{
	All,
	DownloadedOnly,
	SourceOnly
}
}
