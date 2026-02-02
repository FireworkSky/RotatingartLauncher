package org.libsdl.app;

import android.view.KeyEvent;

/**
 * Virtual Xbox 360 Controller implementation.
 * Manages controller state and provides a clean API for controlling all buttons, axes, and triggers.
 */
public class VirtualXboxController {

    // Xbox Controller constants
    public static final int VIRTUAL_DEVICE_ID = 999999;
    public static final String CONTROLLER_NAME = "Virtual Xbox Controller";
    public static final String CONTROLLER_DESC = "Virtual Xbox 360 Controller";

    // Microsoft Xbox 360 Controller vendor/product IDs
    public static final int XBOX_VENDOR_ID = 0x045e;   // Microsoft
    public static final int XBOX_PRODUCT_ID = 0x028e;  // Xbox 360 Controller

    // Number of axes
    public static final int NUM_AXES = 6;
    public static final int NUM_BUTTONS = 15;

    // Xbox button indices (matching SDL gamepad mapping)
    public static final int BUTTON_A = 0;
    public static final int BUTTON_B = 1;
    public static final int BUTTON_X = 2;
    public static final int BUTTON_Y = 3;
    public static final int BUTTON_BACK = 4;
    public static final int BUTTON_GUIDE = 5;
    public static final int BUTTON_START = 6;
    public static final int BUTTON_LEFT_STICK = 7;
    public static final int BUTTON_RIGHT_STICK = 8;
    public static final int BUTTON_LEFT_SHOULDER = 9;
    public static final int BUTTON_RIGHT_SHOULDER = 10;
    public static final int BUTTON_DPAD_UP = 11;
    public static final int BUTTON_DPAD_DOWN = 12;
    public static final int BUTTON_DPAD_LEFT = 13;
    public static final int BUTTON_DPAD_RIGHT = 14;

    // Axis indices
    public static final int AXIS_LEFT_X = 0;
    public static final int AXIS_LEFT_Y = 1;
    public static final int AXIS_RIGHT_X = 2;
    public static final int AXIS_RIGHT_Y = 3;
    public static final int AXIS_LEFT_TRIGGER = 4;
    public static final int AXIS_RIGHT_TRIGGER = 5;

    // Controller state
    private final float[] axisValues;
    private final boolean[] buttonStates;
    private int dpadX = 0;
    private int dpadY = 0;

    // Event listener interface
    public interface ControllerEventListener {
        void onAxisChanged(int axis, float value);
        void onButtonChanged(int button, boolean pressed);
    }

    private ControllerEventListener eventListener;

    public VirtualXboxController() {
        // Initialize axis values (all centered/unpressed)
        axisValues = new float[NUM_AXES];
        for (int i = 0; i < NUM_AXES; i++) {
            axisValues[i] = 0.0f;
        }

        // Initialize button states (all unpressed)
        buttonStates = new boolean[NUM_BUTTONS];
        for (int i = 0; i < buttonStates.length; i++) {
            buttonStates[i] = false;
        }
    }

    /**
     * Set event listener for controller events
     */
    public void setEventListener(ControllerEventListener listener) {
        this.eventListener = listener;
    }

    /**
     * Set an axis value
     * @param axis Axis index (use AXIS_* constants)
     * @param value Axis value (-1.0 to 1.0 for sticks, 0.0 to 1.0 for triggers)
     */
    public void setAxis(int axis, float value) {
        if (axis >= 0 && axis < NUM_AXES) {
            axisValues[axis] = value;
            if (eventListener != null) {
                eventListener.onAxisChanged(axis, value);
            }
        }
    }

    /**
     * Set the left stick position
     * @param x Horizontal position (-1.0 = left, 1.0 = right)
     * @param y Vertical position (-1.0 = up, 1.0 = down)
     */
    public void setLeftStick(float x, float y) {
        setAxis(AXIS_LEFT_X, x);
        setAxis(AXIS_LEFT_Y, y);
    }

    /**
     * Set the right stick position
     * @param x Horizontal position (-1.0 = left, 1.0 = right)
     * @param y Vertical position (-1.0 = up, 1.0 = down)
     */
    public void setRightStick(float x, float y) {
        setAxis(AXIS_RIGHT_X, x);
        setAxis(AXIS_RIGHT_Y, y);
    }

    /**
     * Set trigger values
     * @param left Left trigger (0.0 = unpressed, 1.0 = fully pressed)
     * @param right Right trigger (0.0 = unpressed, 1.0 = fully pressed)
     */
    public void setTriggers(float left, float right) {
        setAxis(AXIS_LEFT_TRIGGER, left);
        setAxis(AXIS_RIGHT_TRIGGER, right);
    }

    /**
     * Press or release a button
     * @param button Button index (use BUTTON_* constants)
     * @param pressed True to press, false to release
     */
    public void setButton(int button, boolean pressed) {
        if (button >= 0 && button < buttonStates.length) {
            if (buttonStates[button] != pressed) {
                buttonStates[button] = pressed;
                if (eventListener != null) {
                    eventListener.onButtonChanged(button, pressed);
                }
            }
        }
    }

    /**
     * Set D-pad state
     * @param x Horizontal (-1 = left, 0 = center, 1 = right)
     * @param y Vertical (-1 = up, 0 = center, 1 = down)
     */
    public void setDpad(int x, int y) {
        // Update individual button states based on direction
        setButton(BUTTON_DPAD_LEFT, x < 0);
        setButton(BUTTON_DPAD_RIGHT, x > 0);
        setButton(BUTTON_DPAD_UP, y < 0);
        setButton(BUTTON_DPAD_DOWN, y > 0);

        dpadX = x;
        dpadY = y;
    }

    /**
     * Reset all controls to neutral/unpressed state
     */
    public void reset() {
        // Reset all axes
        for (int i = 0; i < NUM_AXES; i++) {
            setAxis(i, 0.0f);
        }

        // Release all buttons
        for (int i = 0; i < buttonStates.length; i++) {
            setButton(i, false);
        }

        // Reset dpad
        dpadX = 0;
        dpadY = 0;
    }

    /**
     * Get current axis value
     * @param axis Axis index
     * @return Current value or 0.0 if invalid
     */
    public float getAxis(int axis) {
        if (axis >= 0 && axis < NUM_AXES) {
            return axisValues[axis];
        }
        return 0.0f;
    }

    /**
     * Get current button state
     * @param button Button index
     * @return True if pressed, false otherwise
     */
    public boolean getButton(int button) {
        if (button >= 0 && button < buttonStates.length) {
            return buttonStates[button];
        }
        return false;
    }

    /**
     * Get D-pad X state
     * @return -1 (left), 0 (center), or 1 (right)
     */
    public int getDpadX() {
        return dpadX;
    }

    /**
     * Get D-pad Y state
     * @return -1 (up), 0 (center), or 1 (down)
     */
    public int getDpadY() {
        return dpadY;
    }

    /**
     * Calculate button mask for SDL registration
     */
    public int getButtonMask() {
        return (1 << 0)  |  // A
               (1 << 1)  |  // B
               (1 << 2)  |  // X
               (1 << 3)  |  // Y
               (1 << 4)  |  // Back
               (1 << 5)  |  // Guide
               (1 << 6)  |  // Start
               (1 << 7)  |  // Left Stick
               (1 << 8)  |  // Right Stick
               (1 << 9)  |  // Left Shoulder
               (1 << 10) |  // Right Shoulder
               (1 << 11) |  // DPad Up
               (1 << 12) |  // DPad Down
               (1 << 13) |  // DPad Left
               (1 << 14);   // DPad Right
    }

    /**
     * Calculate axis mask for SDL registration
     */
    public int getAxisMask() {
        return 0x003F; // 0b00111111 - all 6 axes
    }

    /**
     * Map button index to Android KeyEvent keycode
     */
    public static int mapButtonToKeycode(int button) {
        switch (button) {
            case BUTTON_A: return KeyEvent.KEYCODE_BUTTON_A;
            case BUTTON_B: return KeyEvent.KEYCODE_BUTTON_B;
            case BUTTON_X: return KeyEvent.KEYCODE_BUTTON_X;
            case BUTTON_Y: return KeyEvent.KEYCODE_BUTTON_Y;
            case BUTTON_BACK: return KeyEvent.KEYCODE_BACK;
            case BUTTON_GUIDE: return KeyEvent.KEYCODE_BUTTON_MODE;
            case BUTTON_START: return KeyEvent.KEYCODE_BUTTON_START;
            case BUTTON_LEFT_STICK: return KeyEvent.KEYCODE_BUTTON_THUMBL;
            case BUTTON_RIGHT_STICK: return KeyEvent.KEYCODE_BUTTON_THUMBR;
            case BUTTON_LEFT_SHOULDER: return KeyEvent.KEYCODE_BUTTON_L1;
            case BUTTON_RIGHT_SHOULDER: return KeyEvent.KEYCODE_BUTTON_R1;
            case BUTTON_DPAD_UP: return KeyEvent.KEYCODE_DPAD_UP;
            case BUTTON_DPAD_DOWN: return KeyEvent.KEYCODE_DPAD_DOWN;
            case BUTTON_DPAD_LEFT: return KeyEvent.KEYCODE_DPAD_LEFT;
            case BUTTON_DPAD_RIGHT: return KeyEvent.KEYCODE_DPAD_RIGHT;
            default: return -1;
        }
    }
}

