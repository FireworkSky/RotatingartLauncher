# Virtual Xbox Controller - Quick Reference

## Setup
```java
SDLJoyStickHandler_API19_VirtualJoystick xbox = 
    new SDLJoyStickHandler_API19_VirtualJoystick();
xbox.pollInputDevices(); // Initialize
```

## Constants Access
All constants are in `SDLJoyStickHandler_API19_VirtualJoystick`:

### Buttons
```java
BUTTON_A, BUTTON_B, BUTTON_X, BUTTON_Y          // Face buttons
BUTTON_BACK, BUTTON_GUIDE, BUTTON_START         // Center buttons
BUTTON_LEFT_STICK, BUTTON_RIGHT_STICK           // Stick clicks
BUTTON_LEFT_SHOULDER, BUTTON_RIGHT_SHOULDER     // Bumpers
BUTTON_DPAD_UP, BUTTON_DPAD_DOWN               // D-pad
BUTTON_DPAD_LEFT, BUTTON_DPAD_RIGHT
```

### Axes
```java
AXIS_LEFT_X, AXIS_LEFT_Y                        // Left stick
AXIS_RIGHT_X, AXIS_RIGHT_Y                      // Right stick
AXIS_LEFT_TRIGGER, AXIS_RIGHT_TRIGGER           // Triggers
```

## Quick Commands

### Buttons
```java
// Press/Release
xbox.setButton(BUTTON_A, true);   // Press
xbox.setButton(BUTTON_A, false);  // Release

// Quick tap
xbox.setButton(BUTTON_A, true);
Thread.sleep(50);
xbox.setButton(BUTTON_A, false);
```

### Analog Sticks
```java
// Individual axis
xbox.setAxis(AXIS_LEFT_X, 0.5f);      // -1.0 to 1.0

// Convenience methods
xbox.setLeftStick(0.5f, -0.8f);       // x, y
xbox.setRightStick(-1.0f, 0.0f);      // x, y

// Common positions
xbox.setLeftStick(0, -1);    // Up
xbox.setLeftStick(0, 1);     // Down
xbox.setLeftStick(-1, 0);    // Left
xbox.setLeftStick(1, 0);     // Right
xbox.setLeftStick(0, 0);     // Center
```

### Triggers
```java
// Individual
xbox.setAxis(AXIS_LEFT_TRIGGER, 1.0f);   // 0.0 to 1.0

// Both at once
xbox.setTriggers(0.5f, 1.0f);            // left, right
```

### D-pad
```java
xbox.setDpad(x, y);  // x: -1/0/1, y: -1/0/1

// Directions
xbox.setDpad(0, -1);    // Up
xbox.setDpad(0, 1);     // Down
xbox.setDpad(-1, 0);    // Left
xbox.setDpad(1, 0);     // Right
xbox.setDpad(1, -1);    // Up-Right (diagonal)
xbox.setDpad(0, 0);     // Center (release)
```

### Query State
```java
float x = xbox.getAxis(AXIS_LEFT_X);
boolean pressed = xbox.getButton(BUTTON_A);
```

### Reset
```java
xbox.reset();  // All axes to 0, all buttons released
```

## Common Patterns

### Button Combo
```java
// A + B combo
xbox.setButton(BUTTON_A, true);
xbox.setButton(BUTTON_B, true);
Thread.sleep(50);
xbox.setButton(BUTTON_A, false);
xbox.setButton(BUTTON_B, false);
```

### Smooth Stick Movement
```java
// Gradual movement
for (int i = 0; i <= 100; i++) {
    float value = (i / 100.0f) * 2.0f - 1.0f; // -1 to 1
    xbox.setAxis(AXIS_LEFT_X, value);
    Thread.sleep(16); // 60 FPS
}
```

### Sprint Action
```java
// Hold left stick forward + press left stick button
xbox.setLeftStick(0, -1.0f);
xbox.setButton(BUTTON_LEFT_STICK, true);
Thread.sleep(100);
xbox.setButton(BUTTON_LEFT_STICK, false);
```

### Aim and Shoot
```java
// Right stick to aim, trigger to shoot
xbox.setRightStick(0.3f, 0.2f);
xbox.setAxis(AXIS_RIGHT_TRIGGER, 1.0f);
Thread.sleep(100);
xbox.setAxis(AXIS_RIGHT_TRIGGER, 0.0f);
```

## Value Ranges

| Control | Minimum | Center | Maximum |
|---------|---------|--------|---------|
| Stick X | -1.0 (left) | 0.0 | 1.0 (right) |
| Stick Y | -1.0 (up) | 0.0 | 1.0 (down) |
| Trigger | 0.0 (released) | - | 1.0 (pressed) |
| D-pad | -1 | 0 | 1 |

## Important Notes

- ✅ Call `pollInputDevices()` once during initialization
- ✅ Use constants instead of magic numbers
- ✅ Add delays between button presses (50-100ms recommended)
- ✅ Call `reset()` when losing focus
- ⚠️ Stick values outside -1 to 1 are clamped
- ⚠️ Trigger values should be 0 to 1 (not -1 to 1)
- ⚠️ D-pad only accepts -1, 0, or 1

