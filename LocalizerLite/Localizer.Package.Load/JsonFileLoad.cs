using System;
using System.Collections.Generic;
using System.IO;
using Localizer.DataModel;
using Localizer.DataModel.Default;

namespace Localizer.Package.Load
{
public class JsonFileLoad : IFileLoadService
{
	private static Dictionary<string, Type> fileTypes = new Dictionary<string, Type>
	{
		{
			typeof(BasicItemFile).Name,
			typeof(BasicItemFile)
		},
		{
			typeof(BasicBuffFile).Name,
			typeof(BasicBuffFile)
		},
		{
			typeof(CustomModTranslationFile).Name,
			typeof(CustomModTranslationFile)
		},
		{
			typeof(BasicNPCFile).Name,
			typeof(BasicNPCFile)
		},
		{
			typeof(BasicProjectileFile).Name,
			typeof(BasicProjectileFile)
		},
		{
			typeof(BasicPrefixFile).Name,
			typeof(BasicPrefixFile)
		},
		{
			typeof(LdstrFile).Name,
			typeof(LdstrFile)
		}
	};

	public IFile Load(Stream stream, string typeName)
	{
		if (!fileTypes.ContainsKey(typeName))
		{
			Localizer.Log.Error((object)$"Not supported file type: {typeName}");
			return null;
		}
		Type type = fileTypes[typeName];
		IFile obj = Utils.ReadFileAndDeserializeJson(type, stream) as IFile;
		if (obj == null)
		{
			Localizer.Log.Error((object)$"File deserialization failed!, FileType: {type.FullName}");
		}
		return obj;
	}

	public void RegisterType(string typeName, Type type)
	{
		if (fileTypes == null)
		{
			fileTypes = new Dictionary<string, Type>();
		}
		if (fileTypes.ContainsKey(typeName))
		{
			fileTypes[typeName] = type;
		}
		else
		{
			fileTypes.Add(typeName, type);
		}
	}

	public void Dispose()
	{
	}
}
}
