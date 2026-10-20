using Newtonsoft.Json;
using Newtonsoft.Json.Converters;

namespace Localizer
{
[JsonConverter(typeof(StringEnumConverter))]
public enum LogLevel : byte
{
	Fatal,
	Error,
	Warn,
	Info,
	Debug
}
}
