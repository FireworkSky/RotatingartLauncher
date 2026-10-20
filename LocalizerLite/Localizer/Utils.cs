using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net;
using System.Reflection;
using System.Reflection.Emit;
using System.Text;
using System.Text.RegularExpressions;
using Localizer.Attributes;
using Localizer.DataModel;
using Localizer.DataModel.Default;
using Localizer.Helpers;
using Microsoft.Xna.Framework;
using Mono.Cecil;
using Mono.Collections.Generic;
using MonoMod.Utils;
using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Terraria;
using Terraria.Localization;
using Terraria.ModLoader;

namespace Localizer
{
public static class Utils
{
	private static Dictionary<Module, Dictionary<string, MethodBase>> _cachedMethod = new Dictionary<Module, Dictionary<string, MethodBase>>();

	private static Regex genericTypeMatch = new Regex("\\[\\[(.*?), .*?\\]\\]", RegexOptions.Compiled);

	public static void WriteZipArchiveEntry(ZipArchive archive, string filePath, string entryName)
	{
		using (Stream stream = archive.CreateEntry(entryName).Open())
		{
			byte[] array = System.IO.File.ReadAllBytes(filePath);
			stream.Write(array, 0, array.Length);
		}
	}

	public static MethodBase GetMethodBase<T>(string findableName)
	{
		MethodInfo methodInfo = MonoMod.Utils.Extensions.FindMethod(typeof(T), findableName, true);
		if (!(methodInfo != null))
		{
			return null;
		}
		return MethodBase.GetMethodFromHandle(methodInfo.MethodHandle);
	}

	public static MethodBase FindMethodByID(Module m, string findableName)
	{
		if (!_cachedMethod.ContainsKey(m))
		{
			_cachedMethod.Add(m, new Dictionary<string, MethodBase>());
			Type[] types = m.GetTypes();
			for (int i = 0; i < types.Length; i++)
			{
				MethodInfo[] methods = types[i].GetMethods(BindingFlags.Instance | BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
				foreach (MethodInfo methodInfo in methods)
				{
					string iD = MonoMod.Utils.Extensions.GetID((MethodBase)methodInfo, (string)null, (string)null, true, false, false);
					if (!_cachedMethod[m].ContainsKey(iD))
					{
						_cachedMethod[m].Add(iD, methodInfo);
					}
				}
			}
		}
		if (!_cachedMethod[m].ContainsKey(findableName))
		{
			return null;
		}
		return _cachedMethod[m][findableName];
	}

	public static MethodDefinition FindMethodByID(ModuleDefinition m, string findableName)
	{
		//IL_002a: Unknown result type (might be due to invalid IL or missing references)
		//IL_002f: Unknown result type (might be due to invalid IL or missing references)
		string text = genericTypeMatch.Replace(findableName, "<$1>");
		foreach (TypeDefinition type in m.GetTypes())
		{
			foreach (MethodDefinition current in type.Methods)
			{
				string iD = MonoMod.Utils.Extensions.GetID((MethodReference)(object)current, null, null, true, false);
				if (iD == findableName || iD == text)
				{
					return current;
				}
			}
		}
		return null;
	}

	public static void SerializeJsonAndCreateFile(object obj, string path, bool indent = true)
	{
		//IL_0021: Unknown result type (might be due to invalid IL or missing references)
		//IL_0027: Expected Obj, but got Unknown
		using (FileStream stream = new FileStream(path, FileMode.Create, FileAccess.Write))
		using (StreamWriter streamWriter = new StreamWriter(stream))
		{
			streamWriter.Write(JsonConvert.SerializeObject(obj, (Formatting)(indent ? 1 : 0), new JsonConverter[1] { (JsonConverter)new VersionConverter() }));
		}
	}

	public static T ReadFileAndDeserializeJson<T>(string path)
	{
		using (FileStream stream = new FileStream(path, FileMode.Open, FileAccess.Read))
		{
			return ReadFileAndDeserializeJson<T>(stream);
		}
	}

	public static T ReadFileAndDeserializeJson<T>(Stream stream)
	{
		//IL_0017: Unknown result type (might be due to invalid IL or missing references)
		//IL_001d: Expected Obj, but got Unknown
		using (StreamReader streamReader = new StreamReader(stream))
		{
			string text = streamReader.ReadToEnd();
			try
			{
				return JsonConvert.DeserializeObject<T>(text, new JsonConverter[1] { (JsonConverter)new VersionConverter() });
			}
			catch
			{
				return JsonConvert.DeserializeObject<T>(text);
			}
		}
	}

	public static object ReadFileAndDeserializeJson(Type t, string path)
	{
		using (FileStream stream = new FileStream(path, FileMode.Open, FileAccess.Read))
		{
			return ReadFileAndDeserializeJson(t, stream);
		}
	}

	public static object ReadFileAndDeserializeJson(Type t, Stream stream)
	{
		using (StreamReader streamReader = new StreamReader(stream))
		{
			return JsonConvert.DeserializeObject(streamReader.ReadToEnd(), t);
		}
	}

	public static void LogFatal(object o)
	{
		Localizer.Log.Fatal(o);
	}

	public static void LogError(object o)
	{
		if ((int)Localizer.Config.LogLevel >= 1)
		{
			Localizer.Log.Error(o);
		}
	}

	public static void LogWarn(object o)
	{
		if ((int)Localizer.Config.LogLevel >= 2)
		{
			Localizer.Log.Warn(o);
		}
	}

	public static void LogInfo(object o)
	{
		if ((int)Localizer.Config.LogLevel >= 3)
		{
			Localizer.Log.Info(o);
		}
	}

	public static void LogDebug(object o)
	{
		if ((int)Localizer.Config.LogLevel >= 4)
		{
			Localizer.Log.Debug(o);
		}
	}

	internal static string UserAgent(bool localizer = true)
	{
		if (!localizer)
		{
			return string.Format("tModLoader/{0} ({1}; {2})", ModLoader.versionTag, Environment.OSVersion, Environment.Is64BitOperatingSystem ? "x64" : "x86");
		}
		return string.Format("Localizer/{0} ({1}; {2}) tModLoader/{3} ({4})", new object[5]
		{
			((Mod)Localizer.Instance).Version,
			Environment.OSVersion,
			Environment.Is64BitOperatingSystem ? "x64" : "x86",
			ModLoader.versionTag,
			Environment.Is64BitOperatingSystem ? "x64" : "x86"
		});
	}

	public static HttpWebResponse GET(string url)
	{
		HttpWebRequest httpWebRequest = (HttpWebRequest)WebRequest.Create(url);
		httpWebRequest.Method = "GET";
		httpWebRequest.ContentType = "application/json;charset=UTF-8";
		httpWebRequest.Accept = "application/vnd.github.v3+json";
		httpWebRequest.UserAgent = UserAgent();
		httpWebRequest.Timeout = 9000;
		httpWebRequest.Headers["Accept-Language"] = LanguageManager.Instance.ActiveCulture.CultureInfo.ToString();
		return (HttpWebResponse)httpWebRequest.GetResponse();
	}

	public static string GetResponseBody(HttpWebResponse response)
	{
		if (response == null)
		{
			return null;
		}
		Encoding uTF = Encoding.UTF8;
		using (Stream stream = response.GetResponseStream())
		using (StreamReader streamReader = new StreamReader(stream, uTF))
		{
			return streamReader.ReadToEnd();
		}
	}

	public static string AsRainbow(string text, int frameCounter, int? unit = null)
	{
		//IL_0062: Unknown result type (might be due to invalid IL or missing references)
		//IL_0067: Unknown result type (might be due to invalid IL or missing references)
		string text2 = "";
		float num = 4f / (float)(unit ?? text.Length);
		float num2 = (float)(frameCounter % 300) / 300f;
		for (int i = 0; i < text.Length; i++)
		{
			float num3 = num2 + num * (float)i / (float)text.Length;
			Color val = Main.hslToRgb(1.5f - num3, 1f, 0.7f);
			text2 += string.Format("[c/{0:X2}{1:X2}{2:X2}:{3}]", new object[4]
			{
				val.R,
				val.G,
				val.B,
				text[i]
			});
		}
		return text2;
	}

	public static Dictionary<string, PropertyInfo> CreateEntryMappings(Type entryType)
	{
		if (entryType == null)
		{
			throw new ArgumentNullException();
		}
		Dictionary<string, PropertyInfo> dictionary = new Dictionary<string, PropertyInfo>();
		PropertyInfo[] array = entryType.ModTranslationProp();
		foreach (PropertyInfo propertyInfo in array)
		{
			ModTranslationPropAttribute modTranslationPropAttribute = propertyInfo.GetCustomAttribute(typeof(ModTranslationPropAttribute)) as ModTranslationPropAttribute;
			dictionary.Add(modTranslationPropAttribute.PropName, propertyInfo);
		}
		return dictionary;
	}

	public static string GetTranslationOfEntry(IEntry entry, PropertyInfo prop)
	{
		return (prop.GetValue(entry) as BaseEntry)?.Translation;
	}

	public static void EnsureDir(string path)
	{
		if (!Directory.Exists(path))
		{
			Directory.CreateDirectory(path);
		}
	}

	public static ICollection<IMod> GetLoadedMods()
	{
		return ModLoader.Mods?.Select((Mod m) => Localizer.GetWrappedMod(m.Name)).ToArray();
	}

	public static Mod GetModByName(string name)
	{
		return ModLoader.Mods.FirstOrDefault((Mod m) => m.Name == name);
	}

	public static string DateTimeToFileName(DateTime dateTime)
	{
		return $"{dateTime:yyyy-MM-dd_hh-mm-ss-tt}";
	}

	public static string EscapePath(string path)
	{
		return path.Trim(Path.GetInvalidPathChars());
	}

	public static void SafeWrap(Action action)
	{
		SafeWrap(action, out var _);
	}

	public static void SafeWrap(Action action, out Exception ex)
	{
		try
		{
			ex = null;
			action();
		}
		catch (Exception ex2)
		{
			LogError(ex = ex2);
		}
	}

	public static T SafeWrap<T>(Func<T> func)
	{
		Exception ex;
		return SafeWrap(func, out ex);
	}

	public static T SafeWrap<T>(Func<T> func, out Exception ex)
	{
		try
		{
			ex = null;
			return func();
		}
		catch (Exception ex2)
		{
			LogError(ex = ex2);
		}
		return default;
	}

}
}
