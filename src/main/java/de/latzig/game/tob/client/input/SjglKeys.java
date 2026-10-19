package de.latzig.game.tob.client.input;

import com.jme3.input.KeyInput;

import de.latzig.game.tob.client.logic.game.KeyChord;

/**
 * Decoder of the ToB 2009 key codes ({@code key.*} in {@code RToB.properties}). They are SJGL compact keys
 * ({@code CompactKey.java:91}): the low byte is the ordinal of the SJGL enum {@code VirtualKeys}, bits 8–13 are the
 * modifiers left/right Shift, Ctrl and Alt. The SJGL native layer reads DirectInput and maps DIK scan codes to that
 * enum ({@code SJGLDXLib/CInput.cpp} {@code INP_VK_MAPPING}), so the keys are positional on the US layout, like jME's
 * {@link KeyInput} codes (also DirectInput scan codes): the translation keeps the key position, and QWERTZ and QWERTY
 * keyboards behave the same.
 */
public final class SjglKeys {

    public static final int LEFT_SHIFT = 256;
    public static final int RIGHT_SHIFT = 512;
    public static final int LEFT_CTRL = 1024;
    public static final int RIGHT_CTRL = 2048;
    public static final int LEFT_ALT = 4096;
    public static final int RIGHT_ALT = 8192;

    /** jME key per {@code VirtualKeys} ordinal 0 (NONE) .. 79 (KEY_RALT). */
    private static final int[] JME = {
            KeyInput.KEY_UNKNOWN,
            KeyInput.KEY_0, KeyInput.KEY_1, KeyInput.KEY_2, KeyInput.KEY_3, KeyInput.KEY_4,
            KeyInput.KEY_5, KeyInput.KEY_6, KeyInput.KEY_7, KeyInput.KEY_8, KeyInput.KEY_9,
            KeyInput.KEY_A, KeyInput.KEY_B, KeyInput.KEY_C, KeyInput.KEY_D, KeyInput.KEY_E, KeyInput.KEY_F,
            KeyInput.KEY_G, KeyInput.KEY_H, KeyInput.KEY_I, KeyInput.KEY_J, KeyInput.KEY_K, KeyInput.KEY_L,
            KeyInput.KEY_M, KeyInput.KEY_N, KeyInput.KEY_O, KeyInput.KEY_P, KeyInput.KEY_Q, KeyInput.KEY_R,
            KeyInput.KEY_S, KeyInput.KEY_T, KeyInput.KEY_U, KeyInput.KEY_V, KeyInput.KEY_W, KeyInput.KEY_X,
            KeyInput.KEY_Y, KeyInput.KEY_Z,
            KeyInput.KEY_F1, KeyInput.KEY_F2, KeyInput.KEY_F3, KeyInput.KEY_F4, KeyInput.KEY_F5, KeyInput.KEY_F6,
            KeyInput.KEY_F7, KeyInput.KEY_F8, KeyInput.KEY_F9, KeyInput.KEY_F10, KeyInput.KEY_F11, KeyInput.KEY_F12,
            KeyInput.KEY_SPACE, KeyInput.KEY_RETURN, KeyInput.KEY_TAB, KeyInput.KEY_ESCAPE,
            KeyInput.KEY_UP, KeyInput.KEY_DOWN, KeyInput.KEY_LEFT, KeyInput.KEY_RIGHT,
            KeyInput.KEY_PGUP, KeyInput.KEY_PGDN, KeyInput.KEY_INSERT, KeyInput.KEY_DELETE,
            KeyInput.KEY_HOME, KeyInput.KEY_END, KeyInput.KEY_BACK,
            KeyInput.KEY_NUMPAD0, KeyInput.KEY_NUMPAD1, KeyInput.KEY_NUMPAD2, KeyInput.KEY_NUMPAD3,
            KeyInput.KEY_NUMPAD4, KeyInput.KEY_NUMPAD5, KeyInput.KEY_NUMPAD6, KeyInput.KEY_NUMPAD7,
            KeyInput.KEY_NUMPAD8, KeyInput.KEY_NUMPAD9,
            KeyInput.KEY_LSHIFT, KeyInput.KEY_RSHIFT, KeyInput.KEY_LCONTROL, KeyInput.KEY_RCONTROL,
            KeyInput.KEY_LMENU, KeyInput.KEY_RMENU};

    private SjglKeys() {
    }

    /**
     * @throws IllegalArgumentException for NONE, an unknown ordinal, or a modifier other than left Ctrl (no ToB 2009
     *         binding uses one)
     */
    public static KeyChord decode(int compactKey) {
        int ordinal = compactKey & 0xFF;
        int modifiers = compactKey & ~0xFF;
        if (ordinal == 0 || ordinal >= JME.length) {
            throw new IllegalArgumentException("no SJGL key in " + compactKey);
        }
        if (modifiers != 0 && modifiers != LEFT_CTRL) {
            throw new IllegalArgumentException("unsupported SJGL modifiers in " + compactKey);
        }
        return new KeyChord(JME[ordinal], modifiers == LEFT_CTRL);
    }
}
