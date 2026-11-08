# tModLoader multitouch regression

Run from the repository root:

```powershell
python scripts/touch/tests/check_multitouch.py
python scripts/touch/tests/check_overlay_routing.py
python scripts/touch/tests/check_gameplay_controls.py
python scripts/touch/tests/check_inventory_controls.py
dotnet build patches/TModLoaderTouchPatch/TModLoaderTouchPatch.csproj -c Release --no-restore
```

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
