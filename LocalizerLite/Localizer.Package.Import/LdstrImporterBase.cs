using System.Collections.Generic;
using System.Globalization;
using Localizer.DataModel;
using Localizer.DataModel.Default;

namespace Localizer.Package.Import
{
public abstract class LdstrImporterBase : FileImporter
{
	internal bool HaveTranslation(LdstrEntry entry)
	{
		foreach (BaseEntry instruction in entry.Instructions)
		{
			if (!string.IsNullOrEmpty(instruction.Translation))
			{
				return true;
			}
		}
		return false;
	}

	public override void Import(IFile file, IMod mod, CultureInfo culture)
	{
		ImportInternal(file as LdstrFile, mod, culture);
	}

	public override IFile Merge(IFile main, IFile addition)
	{
		return MergeInternal(main as LdstrFile, addition as LdstrFile);
	}

	protected abstract void ImportInternal(LdstrFile file, IMod mod, CultureInfo culture);

	internal LdstrFile MergeInternal(LdstrFile main, LdstrFile addition)
	{
		LdstrFile ldstrFile = new LdstrFile
		{
			LdstrEntries = new Dictionary<string, LdstrEntry>()
		};
		foreach (KeyValuePair<string, LdstrEntry> ldstrEntry in main.LdstrEntries)
		{
			ldstrFile.LdstrEntries.Add(ldstrEntry.Key, ldstrEntry.Value.Clone() as LdstrEntry);
		}
		foreach (KeyValuePair<string, LdstrEntry> ldstrEntry2 in addition.LdstrEntries)
		{
			if (ldstrFile.LdstrEntries.ContainsKey(ldstrEntry2.Key))
			{
				ldstrFile.LdstrEntries[ldstrEntry2.Key] = Merge(main.LdstrEntries[ldstrEntry2.Key], ldstrEntry2.Value);
			}
			else
			{
				ldstrFile.LdstrEntries.Add(ldstrEntry2.Key, ldstrEntry2.Value);
			}
		}
		return ldstrFile;
	}

	internal LdstrEntry Merge(LdstrEntry main, LdstrEntry addition)
	{
		LdstrEntry ldstrEntry = new LdstrEntry
		{
			Instructions = new List<BaseEntry>()
		};
		foreach (BaseEntry ins in main.Instructions)
		{
			if (!ldstrEntry.Instructions.Exists((BaseEntry i) => i.Origin == ins.Origin))
			{
				ldstrEntry.Instructions.Add(ins.Clone() as BaseEntry);
			}
		}
		foreach (BaseEntry ins2 in addition.Instructions)
		{
			if (!ldstrEntry.Instructions.Exists((BaseEntry i) => i.Origin == ins2.Origin))
			{
				ldstrEntry.Instructions.Add(ins2.Clone() as BaseEntry);
			}
		}
		return ldstrEntry;
	}
}
}
