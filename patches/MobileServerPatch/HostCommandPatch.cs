using System;
using System.Collections.Generic;
using System.Reflection;
using Microsoft.Xna.Framework;
using Terraria;
using Terraria.ID;
using Terraria.ModLoader;

namespace MobileServerPatch;

internal static class HostCommandPatch
{
    private const string Command = "/mobilehost";
    private delegate bool HandleOrig(string input, CommandCaller caller);
    private delegate bool HandleHook(HandleOrig orig, string input, CommandCaller caller);
    private delegate List<Tuple<string, string>> HelpOrig(CommandType type);
    private delegate List<Tuple<string, string>> HelpHook(HelpOrig orig, CommandType type);

    internal static void Install(Action<MethodInfo, Delegate> install)
    {
        install(HostSession.Method(typeof(CommandLoader), "HandleCommand", typeof(string), typeof(CommandCaller)), (HandleHook)Handle);
        install(HostSession.Method(typeof(CommandLoader), "GetHelp", typeof(CommandType)), (HelpHook)GetHelp);
    }

    private static bool Handle(HandleOrig orig, string input, CommandCaller caller)
    {
        if (input == null || !input.StartsWith(Command, StringComparison.OrdinalIgnoreCase) ||
            (input.Length > Command.Length && !char.IsWhiteSpace(input[Command.Length])))
            return orig(input, caller);

        if (caller.CommandType != CommandType.Chat || Main.netMode == NetmodeID.MultiplayerClient ||
            caller.Player == null || caller.Player.whoAmI != Main.myPlayer) {
            caller.Reply("只有本机房主可以创建或关闭房间。", Color.OrangeRed);
            return true;
        }

        string password = input.Substring(Command.Length).Trim();
        try {
            if (password.Equals("stop", StringComparison.OrdinalIgnoreCase)) {
                if (!HostSession.IsHosting) caller.Reply("当前没有创建房间。");
                else {
                    HostSession.Shutdown(save: true);
                    caller.Reply("房间已关闭并存档，已恢复单人游戏。");
                }
            }
            else if (HostSession.StartFromCurrentWorld(password)) caller.Reply(HostSession.ConnectionHint());
            else caller.Reply(HostSession.LastError, Color.OrangeRed);
        }
        catch (Exception ex) {
            Patcher.Log("/mobilehost failed.", ex);
            caller.Reply($"房间操作失败：{ex.GetBaseException().Message}", Color.OrangeRed);
        }
        return true;
    }

    private static List<Tuple<string, string>> GetHelp(HelpOrig orig, CommandType type)
    {
        var help = orig(type);
        if ((type & CommandType.Chat) != 0)
            help.Add(Tuple.Create("mobilehost", "同进程局域网开房：/mobilehost [密码|stop]"));
        return help;
    }
}
