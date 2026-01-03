package com.app.ralaunch.controls.data

import com.app.ralaunch.controls.textures.ButtonTextureConfig
import com.app.ralaunch.controls.textures.JoystickTextureConfig
import com.app.ralaunch.controls.textures.TextControlTextureConfig
import com.app.ralaunch.controls.textures.TouchPadTextureConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class ControlData {

    enum class KeyType {
        KEYBOARD,
        MOUSE,
        GAMEPAD,
        SPECIAL
    }

    enum class KeyCode(val code: Int, val type: KeyType = KeyType.KEYBOARD) {
        UNKNOWN(0),

        /**
         *  \name Usage page 0x07
         *
         *  These values are from usage page 0x07 (USB keyboard page).
         */
        /* @{ */

        KEYBOARD_A(4),
        KEYBOARD_B(5),
        KEYBOARD_C(6),
        KEYBOARD_D(7),
        KEYBOARD_E(8),
        KEYBOARD_F(9),
        KEYBOARD_G(10),
        KEYBOARD_H(11),
        KEYBOARD_I(12),
        KEYBOARD_J(13),
        KEYBOARD_K(14),
        KEYBOARD_L(15),
        KEYBOARD_M(16),
        KEYBOARD_N(17),
        KEYBOARD_O(18),
        KEYBOARD_P(19),
        KEYBOARD_Q(20),
        KEYBOARD_R(21),
        KEYBOARD_S(22),
        KEYBOARD_T(23),
        KEYBOARD_U(24),
        KEYBOARD_V(25),
        KEYBOARD_W(26),
        KEYBOARD_X(27),
        KEYBOARD_Y(28),
        KEYBOARD_Z(29),

        KEYBOARD_1(30),
        KEYBOARD_2(31),
        KEYBOARD_3(32),
        KEYBOARD_4(33),
        KEYBOARD_5(34),
        KEYBOARD_6(35),
        KEYBOARD_7(36),
        KEYBOARD_8(37),
        KEYBOARD_9(38),
        KEYBOARD_0(39),

        KEYBOARD_RETURN(40),
        KEYBOARD_ESCAPE(41),
        KEYBOARD_BACKSPACE(42),
        KEYBOARD_TAB(43),
        KEYBOARD_SPACE(44),

        KEYBOARD_MINUS(45),
        KEYBOARD_EQUALS(46),
        KEYBOARD_LEFTBRACKET(47),
        KEYBOARD_RIGHTBRACKET(48),
        KEYBOARD_BACKSLASH(49), /**< Located at the lower left of the return
         *   key on ISO keyboards and at the right end
         *   of the QWERTY row on ANSI keyboards.
         *   Produces REVERSE SOLIDUS (backslash) and
         *   VERTICAL LINE in a US layout, REVERSE
         *   SOLIDUS and VERTICAL LINE in a UK Mac
         *   layout, NUMBER SIGN and TILDE in a UK
         *   Windows layout, DOLLAR SIGN and POUND SIGN
         *   in a Swiss German layout, NUMBER SIGN and
         *   APOSTROPHE in a German layout, GRAVE
         *   ACCENT and POUND SIGN in a French Mac
         *   layout, and ASTERISK and MICRO SIGN in a
         *   French Windows layout.
         */
        KEYBOARD_NONUSHASH(50), /**< ISO USB keyboards actually use this code
         *   instead of 49 for the same key, but all
         *   OSes I've seen treat the two codes
         *   identically. So, as an implementor, unless
         *   your keyboard generates both of those
         *   codes and your OS treats them differently,
         *   you should generate KEYBOARD_BACKSLASH
         *   instead of this code. As a user, you
         *   should not rely on this code because SDL
         *   will never generate it with most (all?)
         *   keyboards.
         */
        KEYBOARD_SEMICOLON(51),
        KEYBOARD_APOSTROPHE(52),
        KEYBOARD_GRAVE(53), /**< Located in the top left corner (on both ANSI
         *   and ISO keyboards). Produces GRAVE ACCENT and
         *   TILDE in a US Windows layout and in US and UK
         *   Mac layouts on ANSI keyboards, GRAVE ACCENT
         *   and NOT SIGN in a UK Windows layout, SECTION
         *   SIGN and PLUS-MINUS SIGN in US and UK Mac
         *   layouts on ISO keyboards, SECTION SIGN and
         *   DEGREE SIGN in a Swiss German layout (Mac:
         *   only on ISO keyboards), CIRCUMFLEX ACCENT and
         *   DEGREE SIGN in a German layout (Mac: only on
         *   ISO keyboards), SUPERSCRIPT TWO and TILDE in a
         *   French Windows layout, COMMERCIAL AT and
         *   NUMBER SIGN in a French Mac layout on ISO
         *   keyboards, and LESS-THAN SIGN and GREATER-THAN
         *   SIGN in a Swiss German, German, or French Mac
         *   layout on ANSI keyboards.
         */
        KEYBOARD_COMMA(54),
        KEYBOARD_PERIOD(55),
        KEYBOARD_SLASH(56),

        KEYBOARD_CAPSLOCK(57),

        KEYBOARD_F1(58),
        KEYBOARD_F2(59),
        KEYBOARD_F3(60),
        KEYBOARD_F4(61),
        KEYBOARD_F5(62),
        KEYBOARD_F6(63),
        KEYBOARD_F7(64),
        KEYBOARD_F8(65),
        KEYBOARD_F9(66),
        KEYBOARD_F10(67),
        KEYBOARD_F11(68),
        KEYBOARD_F12(69),

        KEYBOARD_PRINTSCREEN(70),
        KEYBOARD_SCROLLLOCK(71),
        KEYBOARD_PAUSE(72),
        KEYBOARD_INSERT(73), /**< insert on PC, help on some Mac keyboards (but
        does send code 73, not 117) */
        KEYBOARD_HOME(74),
        KEYBOARD_PAGEUP(75),
        KEYBOARD_DELETE(76),
        KEYBOARD_END(77),
        KEYBOARD_PAGEDOWN(78),
        KEYBOARD_RIGHT(79),
        KEYBOARD_LEFT(80),
        KEYBOARD_DOWN(81),
        KEYBOARD_UP(82),

        KEYBOARD_NUMLOCKCLEAR(83), /**< num lock on PC, clear on Mac keyboards
         */
        KEYBOARD_KP_DIVIDE(84),
        KEYBOARD_KP_MULTIPLY(85),
        KEYBOARD_KP_MINUS(86),
        KEYBOARD_KP_PLUS(87),
        KEYBOARD_KP_ENTER(88),
        KEYBOARD_KP_1(89),
        KEYBOARD_KP_2(90),
        KEYBOARD_KP_3(91),
        KEYBOARD_KP_4(92),
        KEYBOARD_KP_5(93),
        KEYBOARD_KP_6(94),
        KEYBOARD_KP_7(95),
        KEYBOARD_KP_8(96),
        KEYBOARD_KP_9(97),
        KEYBOARD_KP_0(98),
        KEYBOARD_KP_PERIOD(99),

        KEYBOARD_NONUSBACKSLASH(100), /**< This is the additional key that ISO
         *   keyboards have over ANSI ones,
         *   located between left shift and Y.
         *   Produces GRAVE ACCENT and TILDE in a
         *   US or UK Mac layout, REVERSE SOLIDUS
         *   (backslash) and VERTICAL LINE in a
         *   US or UK Windows layout, and
         *   LESS-THAN SIGN and GREATER-THAN SIGN
         *   in a Swiss German, German, or French
         *   layout. */
        KEYBOARD_APPLICATION(101), /**< windows contextual menu, compose */
        KEYBOARD_POWER(102), /**< The USB document says this is a status flag,
         *   not a physical key - but some Mac keyboards
         *   do have a power key. */
        KEYBOARD_KP_EQUALS(103),
        KEYBOARD_F13(104),
        KEYBOARD_F14(105),
        KEYBOARD_F15(106),
        KEYBOARD_F16(107),
        KEYBOARD_F17(108),
        KEYBOARD_F18(109),
        KEYBOARD_F19(110),
        KEYBOARD_F20(111),
        KEYBOARD_F21(112),
        KEYBOARD_F22(113),
        KEYBOARD_F23(114),
        KEYBOARD_F24(115),
        KEYBOARD_EXECUTE(116),
        KEYBOARD_HELP(117),    /**< AL Integrated Help Center */
        KEYBOARD_MENU(118),    /**< Menu (show menu) */
        KEYBOARD_SELECT(119),
        KEYBOARD_STOP(120),    /**< AC Stop */
        KEYBOARD_AGAIN(121),   /**< AC Redo/Repeat */
        KEYBOARD_UNDO(122),    /**< AC Undo */
        KEYBOARD_CUT(123),     /**< AC Cut */
        KEYBOARD_COPY(124),    /**< AC Copy */
        KEYBOARD_PASTE(125),   /**< AC Paste */
        KEYBOARD_FIND(126),    /**< AC Find */
        KEYBOARD_MUTE(127),
        KEYBOARD_VOLUMEUP(128),
        KEYBOARD_VOLUMEDOWN(129),
        /* not sure whether there's a reason to enable these */
        /*     KEYBOARD_LOCKINGCAPSLOCK = 130,  */
        /*     KEYBOARD_LOCKINGNUMLOCK = 131, */
        /*     KEYBOARD_LOCKINGSCROLLLOCK = 132, */
        KEYBOARD_KP_COMMA(133),
        KEYBOARD_KP_EQUALSAS400(134),

        KEYBOARD_INTERNATIONAL1(135), /**< used on Asian keyboards, see
        footnotes in USB doc */
        KEYBOARD_INTERNATIONAL2(136),
        KEYBOARD_INTERNATIONAL3(137), /**< Yen */
        KEYBOARD_INTERNATIONAL4(138),
        KEYBOARD_INTERNATIONAL5(139),
        KEYBOARD_INTERNATIONAL6(140),
        KEYBOARD_INTERNATIONAL7(141),
        KEYBOARD_INTERNATIONAL8(142),
        KEYBOARD_INTERNATIONAL9(143),
        KEYBOARD_LANG1(144), /**< Hangul/English toggle */
        KEYBOARD_LANG2(145), /**< Hanja conversion */
        KEYBOARD_LANG3(146), /**< Katakana */
        KEYBOARD_LANG4(147), /**< Hiragana */
        KEYBOARD_LANG5(148), /**< Zenkaku/Hankaku */
        KEYBOARD_LANG6(149), /**< reserved */
        KEYBOARD_LANG7(150), /**< reserved */
        KEYBOARD_LANG8(151), /**< reserved */
        KEYBOARD_LANG9(152), /**< reserved */

        KEYBOARD_ALTERASE(153),    /**< Erase-Eaze */
        KEYBOARD_SYSREQ(154),
        KEYBOARD_CANCEL(155),      /**< AC Cancel */
        KEYBOARD_CLEAR(156),
        KEYBOARD_PRIOR(157),
        KEYBOARD_RETURN2(158),
        KEYBOARD_SEPARATOR(159),
        KEYBOARD_OUT(160),
        KEYBOARD_OPER(161),
        KEYBOARD_CLEARAGAIN(162),
        KEYBOARD_CRSEL(163),
        KEYBOARD_EXSEL(164),

        KEYBOARD_KP_00(176),
        KEYBOARD_KP_000(177),
        KEYBOARD_THOUSANDSSEPARATOR(178),
        KEYBOARD_DECIMALSEPARATOR(179),
        KEYBOARD_CURRENCYUNIT(180),
        KEYBOARD_CURRENCYSUBUNIT(181),
        KEYBOARD_KP_LEFTPAREN(182),
        KEYBOARD_KP_RIGHTPAREN(183),
        KEYBOARD_KP_LEFTBRACE(184),
        KEYBOARD_KP_RIGHTBRACE(185),
        KEYBOARD_KP_TAB(186),
        KEYBOARD_KP_BACKSPACE(187),
        KEYBOARD_KP_A(188),
        KEYBOARD_KP_B(189),
        KEYBOARD_KP_C(190),
        KEYBOARD_KP_D(191),
        KEYBOARD_KP_E(192),
        KEYBOARD_KP_F(193),
        KEYBOARD_KP_XOR(194),
        KEYBOARD_KP_POWER(195),
        KEYBOARD_KP_PERCENT(196),
        KEYBOARD_KP_LESS(197),
        KEYBOARD_KP_GREATER(198),
        KEYBOARD_KP_AMPERSAND(199),
        KEYBOARD_KP_DBLAMPERSAND(200),
        KEYBOARD_KP_VERTICALBAR(201),
        KEYBOARD_KP_DBLVERTICALBAR(202),
        KEYBOARD_KP_COLON(203),
        KEYBOARD_KP_HASH(204),
        KEYBOARD_KP_SPACE(205),
        KEYBOARD_KP_AT(206),
        KEYBOARD_KP_EXCLAM(207),
        KEYBOARD_KP_MEMSTORE(208),
        KEYBOARD_KP_MEMRECALL(209),
        KEYBOARD_KP_MEMCLEAR(210),
        KEYBOARD_KP_MEMADD(211),
        KEYBOARD_KP_MEMSUBTRACT(212),
        KEYBOARD_KP_MEMMULTIPLY(213),
        KEYBOARD_KP_MEMDIVIDE(214),
        KEYBOARD_KP_PLUSMINUS(215),
        KEYBOARD_KP_CLEAR(216),
        KEYBOARD_KP_CLEARENTRY(217),
        KEYBOARD_KP_BINARY(218),
        KEYBOARD_KP_OCTAL(219),
        KEYBOARD_KP_DECIMAL(220),
        KEYBOARD_KP_HEXADECIMAL(221),

        KEYBOARD_LCTRL(224),
        KEYBOARD_LSHIFT(225),
        KEYBOARD_LALT(226), /**< alt, option */
        KEYBOARD_LGUI(227), /**< windows, command (apple), meta */
        KEYBOARD_RCTRL(228),
        KEYBOARD_RSHIFT(229),
        KEYBOARD_RALT(230), /**< alt gr, option */
        KEYBOARD_RGUI(231), /**< windows, command (apple), meta */

        KEYBOARD_MODE(257),    /**< I'm not sure if this is really not covered
         *   by any of the above, but since there's a
         *   special KMOD_MODE for it I'm adding it here
         */

        /* @} *//* Usage page 0x07 */

        /**
         *  \name Usage page 0x0C
         *
         *  These values are mapped from usage page 0x0C (USB consumer page).
         *  See https://usb.org/sites/default/files/hut1_2.pdf
         *
         *  There are way more keys in the spec than we can represent in the
         *  current scancode range, so pick the ones that commonly come up in
         *  real world usage.
         */
        /* @{ */

        KEYBOARD_AUDIONEXT(258),
        KEYBOARD_AUDIOPREV(259),
        KEYBOARD_AUDIOSTOP(260),
        KEYBOARD_AUDIOPLAY(261),
        KEYBOARD_AUDIOMUTE(262),
        KEYBOARD_MEDIASELECT(263),
        KEYBOARD_WWW(264),             /**< AL Internet Browser */
        KEYBOARD_MAIL(265),
        KEYBOARD_CALCULATOR(266),      /**< AL Calculator */
        KEYBOARD_COMPUTER(267),
        KEYBOARD_AC_SEARCH(268),       /**< AC Search */
        KEYBOARD_AC_HOME(269),         /**< AC Home */
        KEYBOARD_AC_BACK(270),         /**< AC Back */
        KEYBOARD_AC_FORWARD(271),      /**< AC Forward */
        KEYBOARD_AC_STOP(272),         /**< AC Stop */
        KEYBOARD_AC_REFRESH(273),      /**< AC Refresh */
        KEYBOARD_AC_BOOKMARKS(274),    /**< AC Bookmarks */

        /* @} *//* Usage page 0x0C */

        /**
         *  \name Walther keys
         *
         *  These are values that Christian Walther added (for mac keyboard?).
         */
        /* @{ */

        KEYBOARD_BRIGHTNESSDOWN(275),
        KEYBOARD_BRIGHTNESSUP(276),
        KEYBOARD_DISPLAYSWITCH(277), /**< display mirroring/dual display
        switch, video mode switch */
        KEYBOARD_KBDILLUMTOGGLE(278),
        KEYBOARD_KBDILLUMDOWN(279),
        KEYBOARD_KBDILLUMUP(280),
        KEYBOARD_EJECT(281),
        KEYBOARD_SLEEP(282),           /**< SC System Sleep */

        KEYBOARD_APP1(283),
        KEYBOARD_APP2(284),

        /* @} *//* Walther keys */

        /**
         *  \name Usage page 0x0C (additional media keys)
         *
         *  These values are mapped from usage page 0x0C (USB consumer page).
         */
        /* @{ */

        KEYBOARD_AUDIOREWIND(285),
        KEYBOARD_AUDIOFASTFORWARD(286),

        /* @} *//* Usage page 0x0C (additional media keys) */

        /**
         *  \name Mobile keys
         *
         *  These are values that are often used on mobile phones.
         */
        /* @{ */

        KEYBOARD_SOFTLEFT(287), /**< Usually situated below the display on phones and
        used as a multi-function feature key for selecting
        a software defined function shown on the bottom left
        of the display. */
        KEYBOARD_SOFTRIGHT(288), /**< Usually situated below the display on phones and
        used as a multi-function feature key for selecting
        a software defined function shown on the bottom right
        of the display. */
        KEYBOARD_CALL(289), /**< Used for accepting phone calls. */
        KEYBOARD_ENDCALL(290), /**< Used for rejecting phone calls. */

        MOUSE_LEFT(-1, KeyType.MOUSE),
        MOUSE_RIGHT(-2, KeyType.MOUSE),
        MOUSE_MIDDLE(-3, KeyType.MOUSE),
        MOUSE_WHEEL_UP(-4, KeyType.MOUSE),
        MOUSE_WHEEL_DOWN(-5, KeyType.MOUSE),

        SPECIAL_KEYBOARD(-100, KeyType.SPECIAL),
        SPECIAL_TOUCHPAD_RIGHT_BUTTON(-101, KeyType.SPECIAL),

        XBOX_BUTTON_A(-200, KeyType.GAMEPAD),
        XBOX_BUTTON_B(-201, KeyType.GAMEPAD),
        XBOX_BUTTON_X(-202, KeyType.GAMEPAD),
        XBOX_BUTTON_Y(-203, KeyType.GAMEPAD),
        XBOX_BUTTON_BACK(-204, KeyType.GAMEPAD),
        XBOX_BUTTON_GUIDE(-205, KeyType.GAMEPAD),
        XBOX_BUTTON_START(-206, KeyType.GAMEPAD),
        XBOX_BUTTON_LEFT_STICK(-207, KeyType.GAMEPAD),
        XBOX_BUTTON_RIGHT_STICK(-208, KeyType.GAMEPAD),
        XBOX_BUTTON_LB(-209, KeyType.GAMEPAD),
        XBOX_BUTTON_RB(-210, KeyType.GAMEPAD),
        XBOX_BUTTON_DPAD_UP(-211, KeyType.GAMEPAD),
        XBOX_BUTTON_DPAD_DOWN(-212, KeyType.GAMEPAD),
        XBOX_BUTTON_DPAD_LEFT(-213, KeyType.GAMEPAD),
        XBOX_BUTTON_DPAD_RIGHT(-214, KeyType.GAMEPAD),

        XBOX_TRIGGER_LEFT(-220, KeyType.GAMEPAD), // Left Trigger (0.0 = 释放, 1.0 = 按下)
        XBOX_TRIGGER_RIGHT(-221, KeyType.GAMEPAD) // Right Trigger (0.0 = 释放, 1.0 = 按下)
    }

    var name: String = ""
    var x: Float = 0.0f // 屏幕位置 X (0-1相对值，相对于屏幕宽度)
    var y: Float = 0.0f // 屏幕位置 Y (0-1相对值，相对于屏幕高度)
    var width: Float = 0.2f // 控件宽度 (0-1相对值，相对于屏幕高度) **高度!!!!!!**
    var height: Float = 0.2f // 控件高度 (0-1相对值，相对于屏幕高度)
    var rotation: Float = 0.0f // 旋转角度（度）
    var opacity: Float = 0.5f // 0.0 - 1.0 (背景透明度)
    var borderOpacity: Float = 1.0f // 0.0 - 1.0 (边框透明度，默认1.0)
    var textOpacity: Float = 1.0f // 0.0 - 1.0 (文本透明度，默认1.0)
    var bgColor: Int = -0x7f7f80 // 灰色背景（更清晰可见）
    var strokeColor: Int = 0x00000000 // 透明边框（无边框）
    var strokeWidth: Float= 0f // dp单位 // 无边框宽度
    var cornerRadius: Float = 2f // dp单位 // 矩形只有一点点圆角
    var isVisible: Boolean = true
    var isPassThrough: Boolean = false // 触摸穿透：是否将触摸传递给游戏（默认 false）
    var isSizeRatioLocked: Boolean = true // 锁定1:1宽高比（默认 true）

    /**
     * 创建当前控件的深拷贝
     * TODO: This uses JSON serialization for deep copy. Consider using a more efficient approach
     * if performance becomes an issue (e.g., manual copy constructors or a dedicated copy library).
     * Current implementation is simple and reliable but may have overhead for large objects.
     */
    fun deepCopy(): ControlData {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val jsonString = json.encodeToString( this)
        return json.decodeFromString(jsonString)
    }

    @Serializable
    @SerialName("button")
    class Button : ControlData() {

        enum class Mode {
            KEYBOARD,
            GAMEPAD,
        }

        enum class Shape {
            RECTANGLE,
            CIRCLE
        }

        var mode: Mode = Mode.KEYBOARD
        var keycode: KeyCode = KeyCode.UNKNOWN // SDL按键码或鼠标按键或手柄按键
        var isToggle: Boolean = false // 是否是切换按钮（按下保持状态）
        var shape: Shape = Shape.RECTANGLE
        
        /** 按钮纹理配置 */
        var texture: ButtonTextureConfig = ButtonTextureConfig()
    }

    @Serializable
    @SerialName("joystick")
    class Joystick : ControlData() {

        enum class Mode {
            KEYBOARD,
            MOUSE,
            GAMEPAD
        }

        var stickKnobSize: Float = 0.4f // 摇杆圆心大小比例 (0.0-1.0)，默认0.4，不同风格可以设置不同值
        var stickOpacity: Float = 1.0f // 摇杆圆心透明度 0.0 - 1.0（与背景透明度独立）
        var joystickKeys: Array<KeyCode> = arrayOf(
            KeyCode.KEYBOARD_W,
            KeyCode.KEYBOARD_D,
            KeyCode.KEYBOARD_S,
            KeyCode.KEYBOARD_A
        ) // [up, right, down, left] 的键码
        var mode: Mode = Mode.KEYBOARD
        var isRightStick: Boolean = false // 手柄模式：true=右摇杆, false=左摇杆
        
        /** 摇杆纹理配置 */
        var texture: JoystickTextureConfig = JoystickTextureConfig()
    }

    @Serializable
    @SerialName("touchpad")
    class TouchPad : ControlData() {
        var isDoubleClickSimulateJoystick = true // 触控板双指点击模拟摇杆移动（默认 true）
        /** 触控板纹理配置 */
        var texture: TouchPadTextureConfig = TouchPadTextureConfig()
    }

    @Serializable
    @SerialName("text")
    class Text : ControlData() {
        enum class Shape {
            RECTANGLE,
            CIRCLE
        }

        var displayText: String = "" // 显示的文本内容
        var shape: Shape = Shape.RECTANGLE
        
        /** 文本控件纹理配置 */
        var texture: TextControlTextureConfig = TextControlTextureConfig()
    }
}
