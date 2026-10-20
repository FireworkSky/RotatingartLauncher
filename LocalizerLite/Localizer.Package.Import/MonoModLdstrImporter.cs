using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Reflection;
using Localizer.DataModel;
using Localizer.DataModel.Default;
using Mono.Cecil.Cil;
using Mono.Collections.Generic;
using MonoMod.Cil;
using MonoMod.RuntimeDetour.HookGen;
using Terraria.ModLoader;

namespace Localizer.Package.Import
{
public class MonoModLdstrImporter : LdstrImporterBase
{
	private Dictionary<MethodBase, ILContext.Manipulator> modifications;

	public MonoModLdstrImporter()
	{
		modifications = new Dictionary<MethodBase, ILContext.Manipulator>();
	}

	protected override void ImportInternal(LdstrFile file, IMod mod, CultureInfo culture)
	{
		//IL_0073: Unknown result type (might be due to invalid IL or missing references)
		//IL_007a: Expected Obj, but got Unknown
		ContentInstance.Register((object)Utils.GetModByName(mod.Name));
		Module manifestModule = mod.Code.ManifestModule;
		foreach (KeyValuePair<string, LdstrEntry> ldstrEntry in file.LdstrEntries)
		{
			MethodBase methodBase = Utils.FindMethodByID(manifestModule, ldstrEntry.Key);
			if (methodBase == null)
			{
				continue;
			}
			LdstrEntry e = ldstrEntry.Value;
			if (!HaveTranslation(e))
			{
				continue;
			}
			ILContext.Manipulator val = (ILContext il) =>
			{
				foreach (Instruction instruction in il.Instrs)
				{
					BaseEntry baseEntry = e.Instructions.FirstOrDefault(i => ILPatternMatchingExt.MatchLdstr(instruction, i.Origin));
					if (baseEntry != null && !string.IsNullOrEmpty(baseEntry.Translation))
					{
						instruction.Operand = baseEntry.Translation;
						foreach (ILLabel label in il.Labels)
						{
							if (ILPatternMatchingExt.MatchLdstr(label.Target, baseEntry.Origin))
							{
								label.Target = instruction;
							}
						}
					}
				}
			};
			if (!modifications.ContainsKey(methodBase))
			{
				HookEndpointManager.Modify(methodBase, (Delegate)(object)val);
				modifications.Add(methodBase, val);
			}
		}
	}

	public override void Reset()
	{
	}

	protected override void DisposeUnmanaged()
	{
		modifications = null;
	}
}
}
