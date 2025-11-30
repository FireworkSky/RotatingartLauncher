# Virtual Xbox Controller Emulation

## Overview
Complete implementation of `SDLJoyStickHandler_API19_VirtualJoystick` that emulates a Microsoft Xbox 360 Controller with full button, axis, and D-pad support.

## Xbox Controller Specifications

### Hardware Identity
- **Device Name**: Virtual Xbox Controller
- **Description**: Virtual Xbox 360 Controller
- **Vendor ID**: `0x045e` (Microsoft)
- **Product ID**: `0x028e` (Xbox 360 Controller)
- **Device ID**: `0x80000000` (Virtual)

### Controller Layout

#### Buttons (15 total)
| Button | Constant | Description |
|--------|----------|-------------|
| 0 | `BUTTON_A` | A button (green) |
| 1 | `BUTTON_B` | B button (red) |
| 2 | `BUTTON_X` | X button (blue) |
| 3 | `BUTTON_Y` | Y button (yellow) |
| 4 | `BUTTON_BACK` | Back button |
| 5 | `BUTTON_GUIDE` | Guide/Xbox button |
| 6 | `BUTTON_START` | Start button |
| 7 | `BUTTON_LEFT_STICK` | Left stick click |
| 8 | `BUTTON_RIGHT_STICK` | Right stick click |
| 9 | `BUTTON_LEFT_SHOULDER` | Left bumper (LB) |
| 10 | `BUTTON_RIGHT_SHOULDER` | Right bumper (RB) |
| 11 | `BUTTON_DPAD_UP` | D-pad Up |
| 12 | `BUTTON_DPAD_DOWN` | D-pad Down |
| 13 | `BUTTON_DPAD_LEFT` | D-pad Left |
| 14 | `BUTTON_DPAD_RIGHT` | D-pad Right |

#### Axes (6 total)
| Axis | Constant | Range | Description |
|------|----------|-------|-------------|
| 0 | `AXIS_LEFT_X` | -1.0 to 1.0 | Left stick horizontal |
| 1 | `AXIS_LEFT_Y` | -1.0 to 1.0 | Left stick vertical |
| 2 | `AXIS_RIGHT_X` | -1.0 to 1.0 | Right stick horizontal |
| 3 | `AXIS_RIGHT_Y` | -1.0 to 1.0 | Right stick vertical |
| 4 | `AXIS_LEFT_TRIGGER` | 0.0 to 1.0 | Left trigger (LT) |
| 5 | `AXIS_RIGHT_TRIGGER` | 0.0 to 1.0 | Right trigger (RT) |

## API Reference

### Constructor
```java
SDLJoyStickHandler_API19_VirtualJoystick handler = 
    new SDLJoyStickHandler_API19_VirtualJoystick();
```

### Core Methods

#### `void pollInputDevices()`
Registers the virtual Xbox controller and polls physical devices. Call this during initialization and when devices change.

```java
handler.pollInputDevices();
```

#### `void setAxis(int axis, float value)`
Set a single axis value.

**Parameters:**
- `axis` - Axis index (0-5) or use AXIS_* constants
- `value` - Axis value
  - Sticks: -1.0 (left/up) to 1.0 (right/down)
  - Triggers: 0.0 (unpressed) to 1.0 (fully pressed)

```java
handler.setAxis(AXIS_LEFT_X, 0.5f);
handler.setAxis(AXIS_LEFT_TRIGGER, 1.0f);
```

#### `void setLeftStick(float x, float y)`
Set left analog stick position.

**Parameters:**
- `x` - Horizontal: -1.0 (left) to 1.0 (right)
- `y` - Vertical: -1.0 (up) to 1.0 (down)

```java
handler.setLeftStick(0.0f, -1.0f); // Push up
```

#### `void setRightStick(float x, float y)`
Set right analog stick position.

**Parameters:**
- `x` - Horizontal: -1.0 (left) to 1.0 (right)
- `y` - Vertical: -1.0 (up) to 1.0 (down)

```java
handler.setRightStick(1.0f, 0.0f); // Push right
```

#### `void setTriggers(float left, float right)`
Set both trigger values at once.

**Parameters:**
- `left` - Left trigger: 0.0 to 1.0
- `right` - Right trigger: 0.0 to 1.0

```java
handler.setTriggers(0.5f, 1.0f);
```

#### `void setButton(int button, boolean pressed)`
Press or release a button.

**Parameters:**
- `button` - Button index (0-14) or use BUTTON_* constants
- `pressed` - `true` to press, `false` to release

```java
handler.setButton(BUTTON_A, true);  // Press A
handler.setButton(BUTTON_A, false); // Release A
```

#### `void setDpad(int x, int y)`
Set D-pad state (automatically handles button events).

**Parameters:**
- `x` - Horizontal: -1 (left), 0 (center), 1 (right)
- `y` - Vertical: -1 (up), 0 (center), 1 (down)

```java
handler.setDpad(0, -1);  // Up
handler.setDpad(1, -1);  // Up-Right (diagonal)
handler.setDpad(0, 0);   // Center (released)
```

#### `void reset()`
Reset all controls to neutral state (axes centered, all buttons released).

```java
handler.reset();
```

#### `float getAxis(int axis)`
Get current value of an axis.

**Returns:** Current axis value or 0.0 if invalid

```java
float leftX = handler.getAxis(AXIS_LEFT_X);
```

#### `boolean getButton(int button)`
Get current state of a button.

**Returns:** `true` if pressed, `false` otherwise

```java
if (handler.getButton(BUTTON_A)) {
    // A button is pressed
}
```

## Usage Examples

### Example 1: Basic Button Press
```java
// Initialize
SDLJoyStickHandler_API19_VirtualJoystick xbox = 
    new SDLJoyStickHandler_API19_VirtualJoystick();
xbox.pollInputDevices();

// Press A button
xbox.setButton(SDLJoyStickHandler_API19_VirtualJoystick.BUTTON_A, true);
Thread.sleep(100);
xbox.setButton(SDLJoyStickHandler_API19_VirtualJoystick.BUTTON_A, false);
```

### Example 2: Analog Stick Movement
```java
// Move left stick in a circle
for (int i = 0; i < 360; i += 10) {
    double rad = Math.toRadians(i);
    float x = (float) Math.cos(rad);
    float y = (float) Math.sin(rad);
    xbox.setLeftStick(x, y);
    Thread.sleep(16); // ~60 FPS
}
xbox.setLeftStick(0, 0); // Center
```

### Example 3: Trigger Control
```java
// Gradually press left trigger
for (int i = 0; i <= 100; i += 5) {
    float value = i / 100.0f;
    xbox.setAxis(SDLJoyStickHandler_API19_VirtualJoystick.AXIS_LEFT_TRIGGER, value);
    Thread.sleep(16);
}
```

### Example 4: D-pad Navigation
```java
// Navigate menu: right, right, down, A
xbox.setDpad(1, 0);  // Right
Thread.sleep(100);
xbox.setDpad(0, 0);  // Release
Thread.sleep(100);

xbox.setDpad(1, 0);  // Right again
Thread.sleep(100);
xbox.setDpad(0, 0);
Thread.sleep(100);

xbox.setDpad(0, 1);  // Down
Thread.sleep(100);
xbox.setDpad(0, 0);
Thread.sleep(100);

xbox.setButton(SDLJoyStickHandler_API19_VirtualJoystick.BUTTON_A, true);
Thread.sleep(50);
xbox.setButton(SDLJoyStickHandler_API19_VirtualJoystick.BUTTON_A, false);
```

### Example 5: Game Control Simulation
```java
// FPS game: Move forward while looking around and shooting
xbox.setLeftStick(0, -1.0f);  // Forward
xbox.setRightStick(0.3f, 0.2f); // Look right and up
xbox.setAxis(SDLJoyStickHandler_API19_VirtualJoystick.AXIS_RIGHT_TRIGGER, 1.0f); // Shoot

Thread.sleep(1000); // Hold for 1 second

// Stop everything
xbox.reset();
```

### Example 6: Touch Screen to Virtual Controller
```java
// Convert touch coordinates to analog stick
public void onTouchMove(float touchX, float touchY, 
                       float centerX, float centerY, 
                       float radius) {
    float dx = (touchX - centerX) / radius;
    float dy = (touchY - centerY) / radius;
    
    // Clamp to unit circle
    float distance = (float) Math.sqrt(dx * dx + dy * dy);
    if (distance > 1.0f) {
        dx /= distance;
        dy /= distance;
    }
    
    xbox.setLeftStick(dx, dy);
}

public void onTouchUp() {
    xbox.setLeftStick(0, 0); // Reset to center
}
```

## Integration with SDLControllerManager

The virtual Xbox controller automatically integrates with SDL's joystick system:

1. **Registration**: Called via `nativeAddJoystick()` with Xbox vendor/product IDs
2. **Events**: All button and axis changes are forwarded to SDL native layer
3. **Device ID**: Uses unique ID (0x80000000) to avoid conflicts
4. **Persistence**: Virtual controller is never removed during device polling
5. **Coexistence**: Works alongside physical controllers

## Technical Details

### Button Mapping
Buttons are mapped to Android KeyEvent keycodes:
- A → `KEYCODE_BUTTON_A`
- B → `KEYCODE_BUTTON_B`
- X → `KEYCODE_BUTTON_X`
- Y → `KEYCODE_BUTTON_Y`
- Back → `KEYCODE_BACK`
- Guide → `KEYCODE_BUTTON_MODE`
- Start → `KEYCODE_BUTTON_START`
- L/R Stick → `KEYCODE_BUTTON_THUMBL` / `KEYCODE_BUTTON_THUMBR`
- L/R Shoulder → `KEYCODE_BUTTON_L1` / `KEYCODE_BUTTON_R1`
- D-pad → `KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT`

### Axis Mask
The virtual controller reports axis mask `0x003F` (binary: 00111111), indicating:
- Bit 0-1: Left stick (X, Y)
- Bit 2-3: Right stick (X, Y)
- Bit 4-5: Triggers (Left, Right)

### Button Mask
Full button mask: `0x7FFF` (15 buttons)

## Best Practices

1. **Initialization**: Call `pollInputDevices()` once during setup
2. **Event Timing**: Add small delays between button presses for realistic input
3. **Cleanup**: Call `reset()` when losing focus or pausing
4. **Stick Deadzone**: Implement deadzones in your input handling (typically 0.1-0.2)
5. **State Tracking**: Use `getAxis()` and `getButton()` to query current state
6. **Thread Safety**: Access from UI thread or use proper synchronization

## Troubleshooting

**Q: Buttons not working?**
- Ensure `pollInputDevices()` was called
- Check button index is valid (0-14)
- Verify SDL native layer is initialized

**Q: Axes not responding?**
- Check axis values are in correct range (-1 to 1 for sticks, 0 to 1 for triggers)
- Verify axis index is valid (0-5)

**Q: D-pad not working?**
- Use `setDpad()` instead of manual button setting
- Ensure x, y values are -1, 0, or 1

**Q: Multiple presses detected?**
- Add delays between press and release
- Check you're not calling setButton multiple times

## Build Status
✅ Compilation successful  
✅ All methods implemented  
✅ Full Xbox 360 controller emulation  
✅ Compatible with SDL joystick system  
✅ No compilation errors

