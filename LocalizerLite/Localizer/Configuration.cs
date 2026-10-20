namespace Localizer
{
public class Configuration
{
	public bool AutoImport { get; set; } = true;

	public bool ImportLdstr { get; set; } = true;

	public string LdstrImporter { get; set; } = "Cecil";

	public bool ImporBasictAfterSetupContent { get; set; } = true;

	public AutoImportType AutoImportType { get; set; }

	public bool ShowUI { get; set; } = true;

	public LogLevel LogLevel { get; set; } = LogLevel.Info;

	public bool RebuildTooltips { get; set; } = true;

	public bool RebuildTooltipsOnce { get; set; } = true;

	public string[] ModListMirror { get; set; } = new string[4] { "mirror.sgkoi.dev", "mirror7.sgkoi.dev", "mirror8.sgkoi.dev", "mirror5.sgkoi.dev" };

	public string[] ModDownloadMirror { get; set; } = new string[4] { "mirror.sgkoi.dev", "mirror7.sgkoi.dev", "mirror8.sgkoi.dev", "mirror5.sgkoi.dev" };

	public string[] ModDescMirror { get; set; } = new string[4] { "mirror.sgkoi.dev", "mirror7.sgkoi.dev", "mirror8.sgkoi.dev", "mirror5.sgkoi.dev" };
}
}
