# Virtual Xbox Controller - Architecture Overview

## Refactored Architecture

The virtual Xbox controller implementation has been refactored into a clean, modular architecture:

### Class Structure

```
VirtualXboxController.java              (Controller Logic)
    ↓
SDLJoyStickHandler_API19_VirtualJoystick.java   (SDL Integration)
    ↓
SDLControllerManager.java               (Native Bridge)
```

## Components

### 1. VirtualXboxController
**Location**: `app/src/main/java/org/libsdl/app/VirtualXboxController.java`

**Purpose**: Pure controller logic - manages button and axis state

**Responsibilities**:
- Store controller state (buttons, axes, dpad)
- Provide API for setting buttons/axes
- Fire events via listener interface
- No SDL-specific code

**Key Features**:
- Event-driven architecture via `ControllerEventListener`
- All Xbox 360 constants defined here
- State management (get/set)
- Button to keycode mapping

### 2. SDLJoyStickHandler_API19_VirtualJoystick
**Location**: `app/src/main/java/org/libsdl/app/SDLControllerManager.java`

**Purpose**: SDL integration layer - bridges controller to SDL

**Responsibilities**:
- Create and manage `VirtualXboxController` instance
- Register virtual device with SDL
- Forward controller events to SDL native layer
- Manage virtual device lifecycle
- Handle physical controller polling

**Key Features**:
- Minimal SDL integration code
- Event listener forwards to SDL natives
- Clean separation of concerns
- Provides `getController()` for external access

## Usage Pattern

### Basic Setup

```java
// 1. Create the SDL handler (creates controller internally)
SDLJoyStickHandler_API19_VirtualJoystick handler = 
    new SDLJoyStickHandler_API19_VirtualJoystick();

// 2. Register with SDL
handler.pollInputDevices();

// 3. Get controller instance for use
VirtualXboxController xbox = handler.getController();

// 4. Use the controller
xbox.setButton(VirtualXboxController.BUTTON_A, true);
xbox.setLeftStick(0.5f, -1.0f);
```

### Why This Design?

#### Separation of Concerns
- **VirtualXboxController**: Controller logic only
- **SDLJoyStickHandler**: SDL integration only

#### Reusability
- `VirtualXboxController` can be used independently
- Can easily add other output targets (not just SDL)

#### Testability
- Controller logic can be tested without SDL
- Mock the event listener for testing

#### Maintainability
- Changes to controller logic don't affect SDL code
- Changes to SDL integration don't affect controller logic

## Event Flow

```
User Code
  ↓
VirtualXboxController.setButton()
  ↓
ControllerEventListener.onButtonChanged()
  ↓
SDLControllerManager.onNativePadDown/Up()
  ↓
SDL Native Layer
```

## API Reference

### VirtualXboxController

#### Constants
All button and axis constants are in `VirtualXboxController`:
```java
VirtualXboxController.BUTTON_A
VirtualXboxController.AXIS_LEFT_X
VirtualXboxController.XBOX_VENDOR_ID
// etc.
```

#### Control Methods
```java
void setButton(int button, boolean pressed)
void setAxis(int axis, float value)
void setLeftStick(float x, float y)
void setRightStick(float x, float y)
void setTriggers(float left, float right)
void setDpad(int x, int y)
void reset()
```

#### Query Methods
```java
boolean getButton(int button)
float getAxis(int axis)
int getDpadX()
int getDpadY()
```

#### Event Listener
```java
interface ControllerEventListener {
    void onAxisChanged(int axis, float value);
    void onButtonChanged(int button, boolean pressed);
}

controller.setEventListener(listener);
```

### SDLJoyStickHandler_API19_VirtualJoystick

#### Public Methods
```java
VirtualXboxController getController()
void pollInputDevices()  // inherited
```

## Code Examples

### Example 1: Basic Usage
```java
SDLJoyStickHandler_API19_VirtualJoystick handler = 
    new SDLJoyStickHandler_API19_VirtualJoystick();
handler.pollInputDevices();

VirtualXboxController xbox = handler.getController();
xbox.setButton(VirtualXboxController.BUTTON_A, true);
Thread.sleep(50);
xbox.setButton(VirtualXboxController.BUTTON_A, false);
```

### Example 2: Custom Event Handling
```java
VirtualXboxController controller = new VirtualXboxController();

controller.setEventListener(new VirtualXboxController.ControllerEventListener() {
    @Override
    public void onAxisChanged(int axis, float value) {
        System.out.println("Axis " + axis + " = " + value);
    }

    @Override
    public void onButtonChanged(int button, boolean pressed) {
        System.out.println("Button " + button + " " + (pressed ? "pressed" : "released"));
    }
});

controller.setAxis(VirtualXboxController.AXIS_LEFT_X, 0.5f);
controller.setButton(VirtualXboxController.BUTTON_A, true);
```

### Example 3: State Query
```java
VirtualXboxController xbox = handler.getController();

float leftX = xbox.getAxis(VirtualXboxController.AXIS_LEFT_X);
boolean aPressed = xbox.getButton(VirtualXboxController.BUTTON_A);

if (aPressed && Math.abs(leftX) > 0.5f) {
    System.out.println("Moving while pressing A");
}
```

## Migration Guide

If you're updating from the old implementation:

### Old Code
```java
SDLJoyStickHandler_API19_VirtualJoystick xbox = new SDLJoyStickHandler_API19_VirtualJoystick();
xbox.pollInputDevices();
xbox.setButton(SDLJoyStickHandler_API19_VirtualJoystick.BUTTON_A, true);
```

### New Code
```java
SDLJoyStickHandler_API19_VirtualJoystick handler = new SDLJoyStickHandler_API19_VirtualJoystick();
handler.pollInputDevices();
VirtualXboxController xbox = handler.getController();
xbox.setButton(VirtualXboxController.BUTTON_A, true);
```

### Changes
1. Handler is now separate from controller
2. Use `getController()` to get the controller instance
3. Constants moved to `VirtualXboxController` class
4. API remains the same on the controller

## Benefits of New Architecture

✅ **Clean Separation**: Controller logic separate from SDL integration  
✅ **More Testable**: Can test controller without SDL  
✅ **More Flexible**: Can use controller with other output systems  
✅ **Better Organization**: Each class has single responsibility  
✅ **Easier Maintenance**: Changes isolated to specific layers  
✅ **Reusable**: Controller can be used in non-SDL contexts  

## File Locations

| File | Purpose |
|------|---------|
| `VirtualXboxController.java` | Controller state and logic |
| `SDLControllerManager.java` | SDL integration handler |
| `VirtualXboxControllerExample.java` | Usage examples |
| `XBOX_CONTROLLER_ARCHITECTURE.md` | This document |

## Complete Example

See `VirtualXboxControllerExample.java` for complete working examples including:
- Button presses
- Analog stick movement
- Trigger control
- D-pad navigation
- State queries
- And more!

