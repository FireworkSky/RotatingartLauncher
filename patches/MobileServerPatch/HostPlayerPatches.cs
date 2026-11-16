using System;
using System.Linq;
using System.Reflection;
using Mono.Cecil;
using Mono.Cecil.Cil;
using MonoMod.Cil;
using Terraria;
using Terraria.UI;
using Terraria.GameContent.UI;
using Terraria.GameContent.Tile_Entities;
using Terraria.ID;

namespace MobileServerPatch;

// Only the audited field reads below are projected. Called code, hooks and
// serializers continue observing the real server role and the shared world.
internal static class HostPlayerPatches
{
    internal static void Install(Action<MethodInfo, ILContext.Manipulator> install)
    {
        // Local input/visual gates and client-originated state broadcasts.
        Patch(install, typeof(Player), "TrySyncingInput", 216, (0x0000, 1));
        Patch(install, typeof(Player), "Update", 34378, (0x0010, 0), (0x286d, 1), (0x2a77, 1), (0x2c2d, 1), (0x2d7b, 1), (0x858c, 0));
        Patch(install, typeof(Player), "LookForTileInteractions", 285, (0x0000, 0));
        Patch(install, typeof(Player), "HealEffect", 111, (0x003a, 1));
        Patch(install, typeof(Player), "ManaEffect", 108, (0x0037, 1));
        Patch(install, typeof(Player), "dropItemCheck", 1045, (0x00a1, 1), (0x014b, 1), (0x032d, 1));
        Patch(install, typeof(Player), "DropSelectedItem", 496, (0x0130, 0), (0x01ca, 1));
        Patch(install, typeof(Player), "GrabItems", 696, (0x00e5, 1));
        Patch(install, typeof(Player), "PickupItem", 566, (0x0046, 1), (0x020f, 1));
        Patch(install, typeof(Player), "Spawn", 1168, (0x00d8, 1));
        Patch(install, typeof(Player), "Hurt", 2450, (0x002f, 1));
        Patch(install, typeof(Player), "OnHurt_Part1", 643, (0x005d, 1));
        Patch(install, typeof(Player), "KillMe", 2641, (0x02b8, 1), (0x09c1, 1));
        Patch(install, typeof(Player), "DropCoins", 537, (0x01b7, 1));
        Patch(install, typeof(Player), "TryDroppingSingleItem", 188, (0x008f, 1));
        Patch(install, typeof(Player), "QuickSpawnItem", 88, (0x0031, 1));
        Patch(install, typeof(Player), "QuickSpawnClonedItem", 69, (0x001e, 1));
        Patch(install, typeof(Player), "QuickGrapple", 2247, (0x0461, 1));
        Patch(install, typeof(Player), "RefreshInfoAccsFromTeamPlayers", 533, (0x0000, 1));
        Patch(install, typeof(Player), "DoEyebrellaRainEffect", 218, (0x0000, 0));
        Patch(install, typeof(Player), "UpdateHairDyeDust", 872, (0x0000, 0));
        Patch(install, typeof(Player), "WingFrame", 6156, (0x0be5, 0));
        Patch(install, typeof(Player), "DoBootsEffect_PlaceFlowersOnTile", 872, (0x0148, 1), (0x0270, 1), (0x02df, 1), (0x0353, 1));
        Patch(install, typeof(Player), "MowGrassTile", 193, (0x00a5, 1));
        Patch(install, typeof(Player), "CheckCrackedBrickBreak", 948, (0x0372, 1));
        Patch(install, typeof(Player), "CheckIceBreak", 275, (0x00c2, 1));
        Patch(install, typeof(Player), "ApplyTouchDamage", 246, (0x00c0, 1));
        Patch(install, typeof(Player), "StickyMovement", 2235, (0x0173, 1), (0x0194, 1));
        Patch(install, typeof(Player), "TileInteractionsUse", 10612, (0x0c6c, 1), (0x0ee8, 1), (0x15cc, 0), (0x19e5, 1), (0x1aaf, 1), (0x26cd, 1), (0x27a4, 1));
        Patch(install, typeof(Player), "PlaceThing_Walls", 968, (0x0368, 1));
        Patch(install, typeof(Player), "PlaceThing_Walls_FillEmptySpace", 418, (0x015e, 1));
        Patch(install, typeof(Player), "PlaceThing_Tiles_PlaceIt", 568, (0x013b, 1));
        Patch(install, typeof(Player), "PlaceThing_Tiles_PlaceIt_UnslopeForSolids", 351, (0x00bf, 1), (0x013e, 1));
        Patch(install, typeof(Player), "PlaceThing_Tiles_PlaceIt_KillGrassForSolids", 363, (0x0129, 1));
        Patch(install, typeof(Player), "PlaceThing_Tiles_PlaceIt_SpinSmartPlatform", 1981, (0x0295, 1), (0x038c, 1), (0x0422, 1), (0x0590, 1), (0x06fb, 1), (0x0796, 1));
        Patch(install, typeof(Player), "PlaceThing_Tiles_PlaceIt_SpinBedsAndBaths", 71, (0x002c, 1));
        Patch(install, typeof(Player), "PlaceThing_Tiles_PlaceIt_SpinChairs", 134, (0x0067, 1));
        Patch(install, typeof(Player), "PlaceThing_Tiles_PlaceIt_SpinTraps", 95, (0x0045, 1));
        Patch(install, typeof(Player), "PlaceThing_Tiles_PlaceIt_AdjustPlants", 149, (0x007b, 1));
        Patch(install, typeof(Player), "PlaceThing_Tiles_BlockPlacementForAssortedThings", 5617, (0x04f1, 1), (0x05cd, 1), (0x06a6, 1), (0x10f4, 1), (0x14b3, 1), (0x15aa, 1));
        Patch(install, typeof(Player), "PlaceThing_LockChest", 685, (0x0286, 1));
        Patch(install, typeof(Player), "PlaceThing_PaintScrapper_LongMoss", 374, (0x00ab, 1));
        Patch(install, typeof(Player), "DropItemFromExtractinator", 129, (0x005b, 1));
        Patch(install, typeof(Player), "PutItemInInventoryFromItemUsage", 352, (0x00e6, 1));
        Patch(install, typeof(Player), "ItemCheck_Inner", 6376, (0x0c29, 0), (0x0db8, 0), (0x0f18, 0), (0x13b6, 0), (0x147b, 1));
        Patch(install, typeof(Player), "UnityTeleport", 53, (0x0002, 0));
        SyncUnityTeleport(install);
        Patch(install, typeof(Player), "ItemCheck_UseEventItems", 1623, (0x0366, 0), (0x0457, 0), (0x0544, 0));
        Patch(install, typeof(Player), "ItemCheck_UseCombatBook", 268, (0x003a, 0), (0x00c0, 0));
        Patch(install, typeof(Player), "ItemCheck_UsePeddlersSatchel", 134, (0x003a, 0));
        Patch(install, typeof(Player), "ItemCheck_UseTorchGodsFavor", 210, (0x005d, 0));
        Patch(install, typeof(Player), "ItemCheck_CutTiles", 591, (0x01bc, 1), (0x01e2, 1), (0x0211, 1));
        Patch(install, typeof(Player), "ItemCheck_UseMiningTools_ActuallyUseMiningTool", 1189, (0x0178, 1), (0x01a5, 1), (0x03cc, 1), (0x0414, 1));
        Patch(install, typeof(Player), "TryReplantingTree", 165, (0x006e, 1));
        Patch(install, typeof(Player), "PickWall", 149, (0x003e, 1), (0x0067, 1));
        Patch(install, typeof(Player), "ItemCheck_UseMiningTools_TryPoundingTile", 3298, (0x0151, 1), (0x0229, 1), (0x0292, 1), (0x02df, 1), (0x0309, 1), (0x0361, 1), (0x04b7, 1), (0x057b, 1), (0x09c0, 1), (0x0c5c, 1), (0x0c88, 1));
        Patch(install, typeof(Player), "ItemCheck_CheckCanUse", 2390, (0x0921, 1));
        Patch(install, typeof(Player), "PickTile", 871, (0x02f0, 1), (0x0321, 1));
        Patch(install, typeof(Player), "ItemCheck_TryPlacingWearablesOnMannequins", 1095, (0x0237, 1), (0x02a0, 1), (0x033a, 1), (0x03e9, 1));
        Patch(install, typeof(Player), "PlaceWeapon", 654, (0x0117, 1), (0x0136, 1), (0x01ed, 1), (0x01fe, 1));
        Patch(install, typeof(Player), "GetAnglerReward", 318, (0x00fb, 1));
        Patch(install, typeof(Player), "GetDyeTraderReward", 610, (0x023b, 1));

        // Hostile projectiles hurt the graphical host; owner-gated NPC damage
        // and all server world mutations in this same method retain authority.
        Patch(install, typeof(Projectile), "Damage", 20832,
            (0x06e7, 1), (0x2771, 0), (0x28f8, 0), (0x4e43, 0));

        // The chest UI already mutates shared containers before these sends.
        // Leave request-only paths (quick-stack requests, wiring, container
        // creation/deletion and tile entities) on their server execution paths.
        Patch(install, typeof(ChestUI), "LootAll", 645, (0x0079, 1));
        Patch(install, typeof(ChestUI), "DepositAll", 2202, (0x01b7, 1), (0x0218, 1), (0x0685, 1));
        Patch(install, typeof(ChestUI), "QuickStack", 1570, (0x05ba, 1));
        Patch(install, typeof(ChestUI), "Restock", 989, (0x0206, 1), (0x0271, 1), (0x033d, 1));
        Patch(install, typeof(ChestUI), "MoveCoins", 1415, (0x0286, 1), (0x0383, 1), (0x0463, 1));
        Patch(install, typeof(ChestUI), "GetContainerUsageInfo", 163, (0x0039, 1));
        Patch(install, typeof(ChestUI), "RenameChestSubmit", 123, (0x006b, 1));
        Patch(install, typeof(ItemSorting), "SortChest", 373, (0x0113, 1));
        Patch(install, typeof(ItemSlot), "LeftClick", 2581,
            (0x02a2, 1), (0x05a2, 1), (0x05d7, 1), (0x0814, 1), (0x0849, 1));
        Patch(install, typeof(ItemSlot), "SellOrTrash", 444, (0x018e, 1));
        Patch(install, typeof(ItemSlot), "PickupItemIntoMouse", 380,
            (0x0087, 1), (0x00b8, 1), (0x00ea, 1), (0x011c, 1), (0x014e, 1));
        Patch(install, typeof(ItemSlot), "TryPickupDyeToCursor", 300,
            (0x00cc, 1), (0x00fe, 1));
        Patch(install, typeof(ItemSlot), "SwapEquip", 512, (0x01d8, 1));

        // Item arrays have already been changed before these mannequin/rack
        // sends. Placement hooks and interaction anchors retain server authority.
        Patch(install, typeof(TEDisplayDoll), "OverrideItemSlotLeftClick", 198, (0x0073, 1));
        Patch(install, typeof(TEDisplayDoll), "TryFitting", 321, (0x0117, 1));
        Patch(install, typeof(TEHatRack), "OverrideItemSlotLeftClick", 171, (0x007f, 1));
        Patch(install, typeof(TEHatRack), "TryFitting", 212, (0x00aa, 1));

        // These singleplayer branches set world progression fields themselves;
        // announce the committed state rather than broadcasting client requests.
        BroadcastStoredFields(install, "ItemCheck_UseEventItems", 1623,
            "eclipse", "bloodMoon");
        BroadcastStoredFields(install, "ItemCheck_UseCombatBook", 268,
            "combatBookWasUsed", "combatBookVolumeTwoWasUsed");
        BroadcastStoredFields(install, "ItemCheck_UsePeddlersSatchel", 134,
            "peddlersSatchelWasUsed");
    }

    private static MethodInfo FindMethod(Type type, string name, int codeSize)
    {
        MethodInfo[] matches = type.GetMethods(BindingFlags.Public | BindingFlags.NonPublic |
            BindingFlags.Instance | BindingFlags.Static).Where(method => method.Name == name &&
            method.GetMethodBody()?.GetILAsByteArray()?.Length == codeSize).ToArray();
        if (matches.Length != 1)
            throw new InvalidOperationException($"Unsupported tModLoader method: {type.FullName}.{name} ({codeSize} bytes).");
        return matches[0];
    }

    private static void Patch(Action<MethodInfo, ILContext.Manipulator> install,
        Type type, string name, int codeSize, params (int Offset, int Role)[] projections)
    {
        MethodInfo method = FindMethod(type, name, codeSize);
        install(method, il => {
            Instruction[] reads = projections.Select(projection => {
                Instruction instruction = il.Body.Instructions.SingleOrDefault(candidate => candidate.Offset == projection.Offset);
                if (instruction == null || instruction.OpCode != OpCodes.Ldsfld ||
                    instruction.Operand is not FieldReference field || field.DeclaringType.FullName != typeof(Main).FullName ||
                    field.Name != nameof(Main.netMode) || field.FieldType.FullName != "System.Int32")
                    throw new InvalidOperationException($"Unsupported tModLoader IL: {il.Method.FullName} at IL_{projection.Offset:x4}.");
                return instruction;
            }).ToArray();
            var cursor = new ILCursor(il);
            for (int i = 0; i < projections.Length; i++) {
                int role = projections[i].Role;
                cursor.Goto(reads[i], MoveType.After);
                if (type == typeof(Player) && !method.IsStatic) {
                    cursor.Emit(OpCodes.Ldarg_0);
                    cursor.EmitDelegate<Func<int, Player, int>>((actual, player) =>
                        HostSession.IsHosting && player.whoAmI == Main.myPlayer ? role : actual);
                }
                else {
                    cursor.EmitDelegate<Func<int, int>>(actual => HostSession.IsHosting ? role : actual);
                }
            }
        });
    }

    private static void BroadcastStoredFields(Action<MethodInfo, ILContext.Manipulator> install,
        string name, int codeSize, params string[] fieldNames)
    {
        install(FindMethod(typeof(Player), name, codeSize), il => {
            Instruction[] stores = il.Body.Instructions.Where(instruction =>
                instruction.OpCode == OpCodes.Stsfld && instruction.Operand is FieldReference field &&
                (field.DeclaringType.FullName == typeof(Main).FullName || field.DeclaringType.FullName == typeof(NPC).FullName) &&
                fieldNames.Contains(field.Name)).ToArray();
            if (stores.Length != fieldNames.Length)
                throw new InvalidOperationException($"Unsupported progression stores in {il.Method.FullName}.");
            Instruction[] eventStarts = name == "ItemCheck_UseEventItems"
                ? il.Body.Instructions.Where(instruction => instruction.OpCode == OpCodes.Call &&
                    instruction.Operand is MethodReference method && method.DeclaringType.FullName == typeof(Main).FullName &&
                    method.Name is "StartInvasion" or "startPumpkinMoon" or "startSnowMoon").ToArray()
                : Array.Empty<Instruction>();
            if (name == "ItemCheck_UseEventItems" && eventStarts.Length != 5)
                throw new InvalidOperationException("Unsupported world event actions.");
            var changed = new VariableDefinition(il.Import(typeof(bool)));
            il.Body.Variables.Add(changed);
            il.Body.InitLocals = true;
            Instruction[] returns = il.Body.Instructions.Where(instruction => instruction.OpCode == OpCodes.Ret).ToArray();
            var cursor = new ILCursor(il);
            foreach (Instruction store in stores.Concat(eventStarts)) {
                cursor.Goto(store, MoveType.After);
                cursor.Emit(OpCodes.Ldc_I4_1);
                cursor.Emit(OpCodes.Stloc, changed);
            }
            foreach (Instruction instruction in returns) {
                cursor.Goto(instruction, MoveType.Before);
                cursor.Emit(OpCodes.Ldloc, changed);
                cursor.EmitDelegate<Action<bool>>(didChange => {
                    if (HostSession.IsHosting && didChange)
                        NetMessage.SendData(MessageID.WorldData);
                });
            }
        });
    }

    private static void SyncUnityTeleport(Action<MethodInfo, ILContext.Manipulator> install)
    {
        install(FindMethod(typeof(Player), "UnityTeleport", 53), il => {
            Instruction[] calls = il.Body.Instructions.Where(instruction =>
                instruction.OpCode == OpCodes.Call && instruction.Operand is MethodReference method &&
                method.DeclaringType.FullName == typeof(Player).FullName && method.Name == "Teleport").ToArray();
            if (calls.Length != 1)
                throw new InvalidOperationException("Unsupported UnityTeleport action.");
            var cursor = new ILCursor(il);
            cursor.Goto(calls[0], MoveType.After);
            cursor.Emit(OpCodes.Ldarg_0);
            cursor.EmitDelegate<Action<Player>>(player => {
                if (HostSession.IsHosting && player.whoAmI == Main.myPlayer)
                    NetMessage.SendData(MessageID.TeleportEntity, -1, -1, null, 0,
                        player.whoAmI, player.position.X, player.position.Y, 3);
            });
        });
    }
}
