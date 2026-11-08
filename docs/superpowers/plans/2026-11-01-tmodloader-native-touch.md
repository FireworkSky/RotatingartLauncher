# tModLoader Native Touch Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Disable SDL touch-to-mouse synthesis and make tModLoader consume FNA's native `TouchPanel` state through its existing input path.

**Architecture:** Android SDL continues forwarding native finger events. `TModLoaderPatch` hooks `PlayerInput.MouseInput`, selects a stable primary `TouchLocation`, and fills Terraria's existing cursor/left-button fields. The original mouse path remains active when no touch exists, and `Mouse.GetState()` is overridden only during touch frames.

**Tech Stack:** Kotlin, C#, Harmony, FNA `Microsoft.Xna.Framework.Input.Touch`, Gradle/ .NET SDK.

---

### Task 1: Disable duplicate SDL mouse synthesis

**Files:**
- Modify: `app/src/main/java/com/app/ralaunch/core/platform/runtime/GameLauncher.kt:325`

- [ ] Change the `SDL_TOUCH_MOUSE_EVENTS` environment value from `"1"` to `"0"`, leaving `SDL_TOUCH_MOUSE_MULTITOUCH` unchanged for existing settings compatibility.
- [ ] Run `rg -n 'SDL_TOUCH_MOUSE_EVENTS' app patches scripts` and confirm the launcher no longer enables the feature.

### Task 2: Add the FNA touch adapter to the tModLoader patch

**Files:**
- Create: `patches/TModLoaderPatch/TouchInputPatch.cs`
- Modify: `patches/TModLoaderPatch/Patcher.cs` in `ApplyPatchesInternal`

- [ ] Add `TouchInputPatch.Apply(Harmony harmony, Assembly assembly)` that resolves `Terraria.GameInput.PlayerInput.MouseInput` with `BindingFlags.Static | BindingFlags.NonPublic` and patches it with a prefix; also patch public `Microsoft.Xna.Framework.Input.Mouse.GetState` with a postfix.
- [ ] In the prefix, call `TouchPanel.GetState()`, retain the current primary finger ID, use its `TouchLocation.Position`, and map coordinates with `PlayerInput.RawMouseScale`. Populate `MouseInfoOld`, `MouseInfo`, `MouseX`, `MouseY`, `MouseKeys`, `CurrentInputMode`, `SettingsForUI`, and `Triggers.Current.UsedMovementKey` using the same semantics as the original `MouseInput` method.
- [ ] Return `true` from the prefix when no touch or release frame is available so physical mouse handling remains untouched; return `false` after consuming a touch frame.
- [ ] Keep the last position for one release frame, clear the primary ID after release, and catch polling/reflection errors so the original mouse path is used.
- [ ] In the `Mouse.GetState` postfix, replace the result only while the adapter has a current touch/release state, preserving wheel and non-left button fields.
- [ ] Call `TouchInputPatch.Apply(_harmony!, assembly)` from `ApplyPatchesInternal` after the tModLoader assembly is available.

### Task 3: Document and version the built-in patch

**Files:**
- Modify: `patches/TModLoaderPatch/patch.json`

- [ ] Update the description to state that the patch consumes FNA native touch input and no longer relies on SDL touch-to-mouse synthesis.
- [ ] Increment the patch version from `1.0.4` to `1.1.0` so installed built-in patches receive the implementation.

### Task 4: Verify the implementation

**Files:**
- No additional files.

- [ ] Run `dotnet build patches/TModLoaderPatch/TModLoaderPatch.csproj --no-restore`; if the repository's external tModLoader/FNA reference paths are absent, record that limitation and still run source-level checks.
- [ ] Run `git diff --check` and inspect `git diff --stat`.
- [ ] Run `rg -n 'SDL_TOUCH_MOUSE_EVENTS|TouchPanel.GetState|MouseInput|Mouse.GetState' app/src/main/java/com/app/ralaunch/core/platform/runtime/GameLauncher.kt patches/TModLoaderPatch` to confirm each contract is present.
