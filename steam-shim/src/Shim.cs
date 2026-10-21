// RAL Steam shim：把 Steamworks.NET 的原生调用换成托管实现，
// 账号/成就/云存档来自启动器（通过本地 TCP 的 SteamKit2 会话），离线时退化为本地存储。
using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

namespace Steamworks {
	internal static class Shim {
		internal static uint AppId = 105600;
		internal static string Language = "schinese";
		internal static string PersonaName = "Player";
		internal static ulong SteamId = 0;
		internal static bool Initialized;
		internal static string DataDir;

		private static readonly object Gate = new object();
		private static readonly Queue<Tuple<int, byte[]>> Callbacks = new Queue<Tuple<int, byte[]>>();
		private static readonly Dictionary<ulong, byte[]> CallResults = new Dictionary<ulong, byte[]>();
		private static ulong nextCall = 0x1000;

		private static readonly HashSet<string> LoggedOnce = new HashSet<string>();

		internal static void LogOnce(string key) {
			lock (Gate) {
				if (!LoggedOnce.Add(key)) return;
			}
			Log("call " + key);
		}

		internal static void Log(string message) {
			try {
				File.AppendAllText(Path.Combine(Dir, "shim.log"),
					DateTime.Now.ToString("HH:mm:ss.fff") + " " + message + "\n");
			} catch { }
		}

		internal static string Dir {
			get {
				if (DataDir != null) return DataDir;
				lock (Gate) {
					if (DataDir == null) {
						DataDir = Path.Combine(Environment.CurrentDirectory, "steam-shim");
						try { Directory.CreateDirectory(DataDir); } catch { }
					}
				}
				return DataDir;
			}
		}

		internal static string ReadHandle(object hObj) {
			var h = hObj as InteropHelp.UTF8StringHandle;
			if (h == null || h.IsInvalid) return string.Empty;
			var ptr = h.DangerousGetHandle();
			if (ptr == IntPtr.Zero) return string.Empty;
			return PtrToUtf8(ptr);
		}

		// 与启动器侧的 shim 端点（SteamContentCli --serve 起的 loopback TCP）通信
		private static string Rpc(string jsonLine) {
			try {
				var portFile = Path.Combine(Dir, "port.txt");
				if (!File.Exists(portFile)) return null;
				int port;
				if (!int.TryParse(File.ReadAllText(portFile).Trim(), out port) || port <= 0) return null;
				using (var client = new System.Net.Sockets.TcpClient()) {
					var task = client.ConnectAsync("127.0.0.1", port);
					if (!task.Wait(1500)) return null;
					using (var stream = client.GetStream()) {
						var data = Encoding.UTF8.GetBytes(jsonLine + "\n");
						stream.WriteTimeout = 3000;
						stream.Write(data, 0, data.Length);
						var buffer = new byte[65536];
						stream.ReadTimeout = 3000;
						int read = stream.Read(buffer, 0, buffer.Length);
						if (read <= 0) return null;
						return Encoding.UTF8.GetString(buffer, 0, read).Trim();
					}
				}
			} catch {
				return null;
			}
		}

		internal static string JsonEscape(string value) =>
			(value ?? string.Empty).Replace("\\", "\\\\").Replace("\"", "\\\"");

		internal static string PtrToUtf8(IntPtr ptr) {
			if (ptr == IntPtr.Zero) return string.Empty;
			int len = 0;
			while (Marshal.ReadByte(ptr, len) != 0) len++;
			var bytes = new byte[len];
			Marshal.Copy(ptr, bytes, 0, len);
			return Encoding.UTF8.GetString(bytes);
		}

		internal static byte[] Utf8(string s) => Encoding.UTF8.GetBytes(s);

		internal static void QueueCallback(int callbackId, byte[] payload) {
			lock (Gate) Callbacks.Enqueue(Tuple.Create(callbackId, payload));
		}

		internal static void QueueCallbackStruct<T>(T value) where T : struct {
			QueueCallback(CallbackIdentities.GetCallbackIdentity(typeof(T)), Serialize(value));
		}

		internal static ulong QueueCallResult(int callbackId, byte[] payload) {
			lock (Gate) {
				var id = nextCall++;
				CallResults[id] = payload;
				QueueCallbackStruct(new SteamAPICallCompleted_t {
					m_hAsyncCall = new SteamAPICall_t(id),
					m_iCallback = callbackId,
					m_cubParam = (uint)payload.Length,
				});
				return id;
			}
		}

		internal static class Core {
			internal static uint AppId => Shim.AppId;
			internal static string Language => Shim.Language;
			internal static string PersonaName => Shim.PersonaName;

			internal static void Noop() { }

			internal static ESteamAPIInitResult Init() {
				if (Initialized) return ESteamAPIInitResult.k_ESteamAPIInitResult_OK;
				Initialized = true;
				LoadIdentity();
				Log($"Init steamid={SteamId} persona={PersonaName} app={AppId} dir={Dir}");
				QueueCallbackStruct(new SteamServersConnected_t());
				Log("Init -> OK");
				return ESteamAPIInitResult.k_ESteamAPIInitResult_OK;
			}

			internal static void Shutdown() { Log("Shutdown"); Initialized = false; }

			internal static bool RestartAppIfNecessary() { LogOnce("RestartAppIfNecessary"); return false; }

			internal static bool IsSteamRunning() { return true; }

			internal static void RunCallbacks() { LogOnce("RunCallbacks"); }

			internal static void ManualDispatchInit() { }

			internal static bool GetNextCallback(IntPtr pCallbackMsg) {
				lock (Gate) {
					if (Callbacks.Count == 0) return false;
					var entry = Callbacks.Dequeue();
					var size = entry.Item2.Length;
					var ptr = Marshal.AllocHGlobal(size);
					Marshal.Copy(entry.Item2, 0, ptr, size);
					lastPayload = ptr;
					// CallbackMsg_t: m_hSteamUser(0) m_iCallback(4) m_pubParam(8) m_cubParam(16)
					Marshal.WriteInt32(pCallbackMsg, 0, 1);
					Marshal.WriteInt32(pCallbackMsg, 4, entry.Item1);
					Marshal.WriteIntPtr(pCallbackMsg, 8, ptr);
					Marshal.WriteInt32(pCallbackMsg, 16, size);
					return true;
				}
			}

			internal static void FreeLastCallback() {
				lock (Gate) {
					if (lastPayload != IntPtr.Zero) {
						Marshal.FreeHGlobal(lastPayload);
						lastPayload = IntPtr.Zero;
					}
				}
			}

			internal static bool GetAPICallResult(SteamAPICall_t hSteamAPICall, IntPtr pCallback, int cubCallback, int iCallbackExpected, out bool pbFailed) {
				pbFailed = false;
				lock (Gate) {
					byte[] payload;
					if (!CallResults.TryGetValue(hSteamAPICall.m_SteamAPICall, out payload)) return false;
					if (pCallback != IntPtr.Zero && payload.Length > 0)
						Marshal.Copy(payload, 0, pCallback, Math.Min(payload.Length, cubCallback));
					return true;
				}
			}

			internal static IntPtr lastPayload;
			internal static ulong GetSteamID() { LogOnce("GetSteamID"); return SteamId; }

			internal static void LogLine(string message) { Log(message); }

			internal static bool BLoggedOn() { LogOnce("BLoggedOn"); return Initialized; }

			internal static uint GetAuthSessionTicket(byte[] pTicket, int cbMaxTicket, out uint pcbTicket, ref SteamNetworkingIdentity identity) {
				pcbTicket = 0;
				return 0;
			}

			internal static EBeginAuthSessionResult BeginAuthSession(byte[] pAuthTicket, int cbAuthTicket, CSteamID steamID) {
				return EBeginAuthSessionResult.k_EBeginAuthSessionResultOK;
			}

			internal static bool IsSubscribed() { LogOnce("IsSubscribed"); return true; }

			internal static uint GetAppInstallDir(IntPtr pchFolder, uint cchFolderBufferSize) {
				// 用当前目录冒充安装目录
				var dir = Environment.CurrentDirectory;
				var bytes = Utf8(dir);
				var len = (int)Math.Min((uint)bytes.Length, cchFolderBufferSize > 0 ? cchFolderBufferSize - 1 : 0);
				if (pchFolder != IntPtr.Zero) {
					if (len > 0) Marshal.Copy(bytes, 0, pchFolder, len);
					Marshal.WriteByte(pchFolder, len, 0);
				}
				return (uint)bytes.Length;
			}

			internal static bool SetRichPresence(string key, string value) { return true; }

			internal static IntPtr CachedPtr(ref IntPtr cache, string value) {
				if (cache == IntPtr.Zero) {
					var bytes = Utf8(value);
					cache = Marshal.AllocHGlobal(bytes.Length + 1);
					Marshal.Copy(bytes, 0, cache, bytes.Length);
					Marshal.WriteByte(cache, bytes.Length, 0);
				}
				return cache;
			}

			internal static IntPtr PersonaPtr { get { lock (Gate) return CachedPtr(ref personaPtr, PersonaName); } }
			internal static IntPtr LanguagePtr { get { lock (Gate) return CachedPtr(ref languagePtr, Language); } }
			private static IntPtr personaPtr, languagePtr;

			private static void LoadIdentity() {
				try {
					var path = Path.Combine(Dir, "identity.json");
					if (!File.Exists(path)) return;
					var text = File.ReadAllText(path);
					var id = JsonGet(text, "steamid");
					ulong parsed;
					if (ulong.TryParse(id, out parsed)) SteamId = parsed;
					var persona = JsonGet(text, "persona");
					if (!string.IsNullOrEmpty(persona)) Shim.PersonaName = persona;
					var lang = JsonGet(text, "language");
					if (!string.IsNullOrEmpty(lang)) Shim.Language = lang;
					var app = JsonGet(text, "appid");
					uint appParsed;
					if (uint.TryParse(app, out appParsed)) Shim.AppId = appParsed;
				} catch { }
			}

			internal static string JsonGet(string json, string key) {
				var marker = "\"" + key + "\"";
				var i = json.IndexOf(marker, StringComparison.Ordinal);
				if (i < 0) return null;
				var colon = json.IndexOf(':', i);
				if (colon < 0) return null;
				int start = colon + 1;
				while (start < json.Length && (json[start] == ' ' || json[start] == '\t')) start++;
				if (start >= json.Length) return null;
				if (json[start] == '"') {
					var q2 = json.IndexOf('"', start + 1);
					if (q2 < 0) return null;
					return json.Substring(start + 1, q2 - start - 1);
				}
				// 未加引号的数字（CLI 用 JsonSerializer 写 ulong 时会这样）
				int end = start;
				while (end < json.Length && json[end] != ',' && json[end] != '}') end++;
				return json.Substring(start, end - start).Trim();
			}
		}

		internal static class Stats {
			private static readonly Dictionary<string, bool> Achievements = new Dictionary<string, bool>();
			private static readonly Dictionary<string, int> Ints = new Dictionary<string, int>();
			private static readonly Dictionary<string, float> Floats = new Dictionary<string, float>();
			private static bool loaded;

			internal static void EnsureLoaded() {
				if (loaded) return;
				loaded = true;
				try {
					var path = Path.Combine(Dir, "stats.txt");
					if (!File.Exists(path)) return;
					foreach (var line in File.ReadAllLines(path)) {
						var parts = line.Split('\t');
						if (parts.Length < 3) continue;
						if (parts[0] == "a") { bool b; if (bool.TryParse(parts[2], out b)) Achievements[parts[1]] = b; }
					}
				} catch { }
			}

			internal static void Save() {
				try {
					var lines = new List<string>();
					foreach (var kv in Achievements) lines.Add("a\t" + kv.Key + "\t" + kv.Value);
					File.WriteAllLines(Path.Combine(Dir, "stats.txt"), lines.ToArray());
				} catch { }
			}

			internal static bool RequestCurrentStats() {
				LogOnce("RequestCurrentStats");
				try {
					var response = Rpc("{\"cmd\":\"achievement.list\"}");
					if (response != null && response.Contains("\"achievements\"")) {
						var start = response.IndexOf('[');
						var end = response.LastIndexOf(']');
						if (start >= 0 && end > start) {
							foreach (var raw in response.Substring(start + 1, end - start - 1).Split(',')) {
								var name = raw.Trim().Trim('"');
								if (name.Length > 0) Achievements[name] = true;
							}
						}
					}
				} catch { }
				EnsureLoaded();
				QueueCallbackStruct(new UserStatsReceived_t {
					m_nGameID = (ulong)Shim.AppId,
					m_eResult = EResult.k_EResultOK,
					m_steamIDUser = new CSteamID(Shim.SteamId)
				});
				return true;
			}

			internal static uint GetNumAchievements() { return 0; }

			internal static IntPtr GetAchievementName(uint index) { return IntPtr.Zero; }

			internal static bool GetAchievement(string name, out bool achieved) {
				LogOnce("GetAchievement");
				EnsureLoaded();
				achieved = Achievements.ContainsKey(name) && Achievements[name];
				return achieved;
			}

			internal static bool SetAchievement(string name) {
				LogOnce("SetAchievement");
				EnsureLoaded();
				Achievements[name] = true;
				Save();
				Rpc("{\"cmd\":\"achievement.set\",\"name\":\"" + JsonEscape(name) + "\"}");
				return true;
			}

			internal static bool GetAchievementAndUnlockTime(string name, out bool achieved, out uint unlockTime) {
				GetAchievement(name, out achieved);
				unlockTime = 0;
				return achieved;
			}

			internal static bool StoreStats() { LogOnce("StoreStats"); Save(); return true; }

			internal static bool ResetAllStats(bool achievementsToo) {
				if (achievementsToo) Achievements.Clear();
				Save();
				return true;
			}

			internal static bool GetStatInt32(string name, out int value) {
				value = Ints.ContainsKey(name) ? Ints[name] : 0;
				return true;
			}

			internal static bool SetStatInt32(string name, int value) { Ints[name] = value; return true; }

			internal static bool GetStatFloat(string name, out float value) {
				value = Floats.ContainsKey(name) ? Floats[name] : 0f;
				return true;
			}

			internal static bool SetStatFloat(string name, float value) { Floats[name] = value; return true; }
		}

		internal static class Cloud {
			internal static bool IsCloudEnabled = true;

			private static string Root => Path.Combine(Shim.Dir, "cloud");

			private static string PathFor(string file) {
				var safe = (file ?? string.Empty).Replace('\\', '/').TrimStart('/');
				return Path.Combine(Root, safe.Replace('/', Path.DirectorySeparatorChar));
			}

			internal static bool FileWrite(string file, byte[] data, int size) {
				LogOnce("FileWrite");
				try {
					var path = PathFor(file);
					Directory.CreateDirectory(Path.GetDirectoryName(path));
					var bytes = Slice(data, size);
					File.WriteAllBytes(path, bytes);
					Rpc("{\"cmd\":\"cloud.put\",\"file\":\"" + JsonEscape(file) +
						"\",\"data\":\"" + Convert.ToBase64String(bytes) + "\"}");
					return true;
				} catch { return false; }
			}

			internal static int FileRead(string file, byte[] buffer, int size) {
				LogOnce("FileRead");
				try {
					var path = PathFor(file);
					if (!File.Exists(path)) {
						var response = Rpc("{\"cmd\":\"cloud.get\",\"file\":\"" + JsonEscape(file) + "\"}");
						if (response == null || !response.Contains("\"exists\":true")) return 0;
						var start = response.IndexOf("\"data\":\"");
						if (start < 0) return 0;
						start += 8;
						var end = response.IndexOf('"', start);
						if (end <= start) return 0;
						var fetched = Convert.FromBase64String(response.Substring(start, end - start));
						Directory.CreateDirectory(Path.GetDirectoryName(path));
						File.WriteAllBytes(path, fetched);
					}
					var bytes = File.ReadAllBytes(path);
					var count = Math.Min(bytes.Length, size);
					Array.Copy(bytes, buffer, count);
					return count;
				} catch { return 0; }
			}

			internal static bool FileExists(string file) {
				try { return File.Exists(PathFor(file)); } catch { return false; }
			}

			internal static bool FileDelete(string file) {
				try { if (File.Exists(PathFor(file))) File.Delete(PathFor(file)); return true; } catch { return false; }
			}

			internal static int GetFileSize(string file) {
				try { var f = new FileInfo(PathFor(file)); return f.Exists ? (int)f.Length : 0; } catch { return 0; }
			}

			internal static bool GetQuota(out ulong total, out ulong available) {
				total = 1UL << 30;
				available = total;
				return true;
			}

			internal static int GetFileCount() {
				try { return Directory.Exists(Root) ? Directory.GetFiles(Root, "*", SearchOption.AllDirectories).Length : 0; } catch { return 0; }
			}

			internal static IntPtr GetFileNameAndSize(int index, out int size) {
				size = 0;
				return IntPtr.Zero;
			}

			internal static long GetFileTimestamp(string file) {
				try { var f = new FileInfo(PathFor(file)); return f.Exists ? (long)(f.LastWriteTimeUtc - new DateTime(1970, 1, 1)).TotalSeconds : 0; } catch { return 0; }
			}

			internal static ERemoteStoragePlatform GetSyncPlatforms(string file) {
				return ERemoteStoragePlatform.k_ERemoteStoragePlatformAll;
			}

			internal static bool SetSyncPlatforms(string file, ERemoteStoragePlatform platforms) { return true; }

			internal static ulong FileWriteAsync(string file, byte[] data, uint size) {
				LogOnce("FileWriteAsync");
				FileWrite(file, data, (int)size);
				return QueueCallResult(
					CallbackIdentities.GetCallbackIdentity(typeof(RemoteStorageFileWriteAsyncComplete_t)),
					Serialize(new RemoteStorageFileWriteAsyncComplete_t { m_eResult = EResult.k_EResultOK }));
			}

			internal static ulong FileReadAsync(string file, uint offset, uint count) {
				var bytes = new byte[count];
				var read = FileRead(file, bytes, (int)count);
				return QueueCallResult(
					CallbackIdentities.GetCallbackIdentity(typeof(RemoteStorageFileReadAsyncComplete_t)),
					Serialize(new RemoteStorageFileReadAsyncComplete_t {
						m_hFileReadAsync = default(SteamAPICall_t),
						m_eResult = read > 0 ? EResult.k_EResultOK : EResult.k_EResultFileNotFound,
						m_nOffset = offset,
						m_cubRead = (uint)read
					}));
			}

			internal static bool FileReadAsyncComplete(SteamAPICall_t call, byte[] buffer, uint count) {
				lock (Gate) {
					byte[] payload;
					if (!CallResults.TryGetValue(call.m_SteamAPICall, out payload)) return false;
					return payload.Length > 4;
				}
			}

			private static byte[] Slice(byte[] data, int size) {
				if (data == null) return new byte[0];
				var count = Math.Min(data.Length, size);
				var copy = new byte[count];
				Array.Copy(data, copy, count);
				return copy;
			}
		}

		internal static byte[] Serialize<T>(T value) where T : struct {
			var size = Marshal.SizeOf(typeof(T));
			var ptr = Marshal.AllocHGlobal(size);
			try {
				Marshal.StructureToPtr(value, ptr, false);
				var buffer = new byte[size];
				Marshal.Copy(ptr, buffer, 0, size);
				return buffer;
			} finally {
				Marshal.FreeHGlobal(ptr);
			}
		}
	}
}
