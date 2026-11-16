# tModLoader multitouch regression

Run from the repository root:

```powershell
python scripts/touch/check_touch_adapter.py
python scripts/touch/tests/check_multitouch.py
python scripts/touch/tests/check_overlay_routing.py
python scripts/touch/tests/check_gameplay_controls.py
python scripts/touch/tests/check_inventory_controls.py
dotnet build patches/TModLoaderTouchPatch/TModLoaderTouchPatch.csproj -c Release --no-restore
```

`check_touch_adapter.py` is a standalone check for the bundled touch adapter.
It extracts the current patch's input methods and runs them with the referenced
FNA touch types and deterministic SDL device snapshots. It covers late device
detection, cursor visibility before/after touch, blocking mouse fallback,
synthetic-device exclusion, four-finger reordering over 300 frames, and recovery
from native polling failures. The initial reproduction failed the device gate
and idle cursor checks. These checks do not replace tests on the affected phone.

Touch patch 1.0.13 keeps FNA's normal `TouchPanel.Update` path enabled through
`FrameworkDispatcher.Update`, preserving mod hooks when touch devices appear
after game initialization. Mod mode always owns the cursor input path, hides
the cursor, and supplies a released touch mouse state while idle or recovering
from polling failure. Native mouse movement, buttons and wheel cannot trigger
an automatic fallback.

Settings > Controls > Touch input mode offers Mod touch and SDL touch. The
choice is saved in `settings.json` as `touchInputMode: "mod"` or `"sdl"` and
takes effect on the next game launch. Old settings and unknown values use mod
mode without resetting other preferences. Mod mode disables SDL's touch/mouse
synthesis in both directions. SDL mode enables touch-to-mouse synthesis and
omits the launcher touch adapter from startup hooks, preserving the installed
patches and their enabled states so switching back can restore the adapter.
The existing Touch Events switch still controls SDL3 event forwarding.

Runtime diagnostics include the FNA assembly, polling and Terraria input-hook
counts, SDL device IDs/finger counts, and touch display dimensions. The first
sample, first detected finger, device-list changes, and a 30-second heartbeat
are logged; repeated exceptions are limited to once per five seconds. Logs are
also written beside the installed patch DLL in `touch-input.log`, with one
previous log retained and a 1 MiB rotation threshold. Launcher log export
includes both files even if console/logcat capture is unavailable. Android
`SDLTouch` diagnostics show incoming event source/tool/device and surface size.
SDL3 also reports when the existing Touch Events setting blocks forwarding.

For a remote-device reproduction, install the updated launcher, reproduce the
failure, wait 30 seconds, and export logs from settings. Check for `Adapter
applied`, `Terraria input hook running`, `Runtime:`, and `SDLTouch` to locate the
boundary that stopped receiving input. Missing initialization remains a patch
loading problem; zero native fingers despite Android events needs routing or
SDL investigation. A visible virtual joystick alone does not prove native
touch events are reaching FNA.

The test uses the actual input methods extracted from the launcher patch and
MobileJoystick source, deterministic SDL snapshots, and the real referenced FNA
TouchPanel/TouchLocation implementation. Override source locations with `--fna`
and `--mod`. Generated test sources stay in `build/multitouch-tests`.

The original code failed 9 of the initial 15 checks. The repaired code passes
these checks and a 300-frame randomized four-finger replay:
independent finger IDs/history when SDL compacts its list, new/replacement
contacts, release expiry, a second finger first observed as Moved, and mouse-only
fallback without duplicate synthetic contacts.

FNA polls TouchPanel in Game.Update/FrameworkDispatcher.Update, while Terraria's
DoUpdate may return before PostUpdateInput on partial frames. MobileJoystick must
therefore recognize newly observed IDs even if FNA's one-frame Pressed state has
already elapsed. Existing contacts still require a new press to acquire another
control; dragging a held finger across controls does not become a new press.

The launcher patch matches previous locations by finger ID rather than SDL array
index and emits releases for missing IDs. This is implemented in the existing
Harmony touch patch, so no replacement FNA assembly is required.

References:

- https://wiki.libsdl.org/SDL2/SDL_GetTouchFinger
- https://wiki.libsdl.org/SDL2/SDL_HINT_TOUCH_MOUSE_EVENTS
- https://github.com/libsdl-org/SDL/blob/release-2.32.10/src/events/SDL_touch.c
- https://github.com/FNA-XNA/FNA/blob/master/src/FNAPlatform/SDL2_FNAPlatform.cs

These are deterministic input regression checks, not physical touchscreen tests.

The overlay test compiles ControlLayout's actual Kotlin down handler with minimal
Android stubs. Before the fix, 3 of 6 routing checks failed: acquired pointers,
including pass-through controls, returned before forwarding DOWN to SDL, although
MOVE/UP were forwarded. SDL synthesized the missing DOWN only after movement.
All six checks pass after forwarding the accepted DOWN after native consumption
is registered. The test uses the cached Kotlin compiler and JDK 21.

The gameplay test compiles MobileJoystickPlayer.SetControls and the actual
Terraria delayUseItem gate. It reproduces the left-stick-first tool-use latch,
checks both stick orders and held use, preserves inventory and aim-only guards,
and verifies smart-cursor on/off transitions plus backpack touch geometry. The
first run failed 9 checks; all 15 pass with the repair and the held-toggle check.
Mobile smart cursor now starts enabled and explicitly synchronizes the off state.
Backpack touch bounds include the icon corners and custom layouts receive a
minimum normal button size without rewriting their persisted JSON presets.

The inventory test compiles the actual pointer methods and complete control and
inventory layouts. It replays closing the bag while another finger holds the
joystick, opening-touch suppression, lost contacts, focus changes, mouse fallback,
and saved backpack/hotbar separation at four resolutions. The first run failed
6 of 19 checks. The repair keeps touch edges independent of SDL's primary-finger
mouse state, blocks stale mouse fallback, and reserves hotbar space for a custom
left-side backpack without modifying saved coordinates or sizes.
