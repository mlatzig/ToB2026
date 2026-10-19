package de.latzig.game.tob.client.input;

import java.util.HashMap;
import java.util.Map;

import com.jme3.input.KeyInput;

/**
 * The display names of the keys a game action can have (Phase 5b decision 5): jME's key codes are positional
 * (DirectInput scan codes, {@link SjglKeys}), so the names are those of the US layout. Modifiers, lock keys and
 * system keys have no name and cannot be bound. ASCII only (the bitmap font).
 */
public final class KeyNames {

    /** jME key code → name. */
    public static final Map<Integer, String> NAMES;

    static {
        Map<Integer, String> names = new HashMap<>();
        int[] letters = {KeyInput.KEY_A, KeyInput.KEY_B, KeyInput.KEY_C, KeyInput.KEY_D, KeyInput.KEY_E,
                KeyInput.KEY_F, KeyInput.KEY_G, KeyInput.KEY_H, KeyInput.KEY_I, KeyInput.KEY_J, KeyInput.KEY_K,
                KeyInput.KEY_L, KeyInput.KEY_M, KeyInput.KEY_N, KeyInput.KEY_O, KeyInput.KEY_P, KeyInput.KEY_Q,
                KeyInput.KEY_R, KeyInput.KEY_S, KeyInput.KEY_T, KeyInput.KEY_U, KeyInput.KEY_V, KeyInput.KEY_W,
                KeyInput.KEY_X, KeyInput.KEY_Y, KeyInput.KEY_Z};
        for (int i = 0; i < letters.length; i++) {
            names.put(letters[i], String.valueOf((char) ('A' + i)));
        }
        int[] digits = {KeyInput.KEY_0, KeyInput.KEY_1, KeyInput.KEY_2, KeyInput.KEY_3, KeyInput.KEY_4,
                KeyInput.KEY_5, KeyInput.KEY_6, KeyInput.KEY_7, KeyInput.KEY_8, KeyInput.KEY_9};
        int[] numpad = {KeyInput.KEY_NUMPAD0, KeyInput.KEY_NUMPAD1, KeyInput.KEY_NUMPAD2, KeyInput.KEY_NUMPAD3,
                KeyInput.KEY_NUMPAD4, KeyInput.KEY_NUMPAD5, KeyInput.KEY_NUMPAD6, KeyInput.KEY_NUMPAD7,
                KeyInput.KEY_NUMPAD8, KeyInput.KEY_NUMPAD9};
        for (int i = 0; i < digits.length; i++) {
            names.put(digits[i], Integer.toString(i));
            names.put(numpad[i], "Num " + i);
        }
        int[] functions = {KeyInput.KEY_F1, KeyInput.KEY_F2, KeyInput.KEY_F3, KeyInput.KEY_F4, KeyInput.KEY_F5,
                KeyInput.KEY_F6, KeyInput.KEY_F7, KeyInput.KEY_F8, KeyInput.KEY_F9, KeyInput.KEY_F10,
                KeyInput.KEY_F11, KeyInput.KEY_F12};
        for (int i = 0; i < functions.length; i++) {
            names.put(functions[i], "F" + (i + 1));
        }
        names.put(KeyInput.KEY_SPACE, "Space");
        names.put(KeyInput.KEY_RETURN, "Enter");
        names.put(KeyInput.KEY_ESCAPE, "Esc");
        names.put(KeyInput.KEY_TAB, "Tab");
        names.put(KeyInput.KEY_BACK, "Backspace");
        names.put(KeyInput.KEY_INSERT, "Insert");
        names.put(KeyInput.KEY_DELETE, "Delete");
        names.put(KeyInput.KEY_HOME, "Home");
        names.put(KeyInput.KEY_END, "End");
        names.put(KeyInput.KEY_PGUP, "Page Up");
        names.put(KeyInput.KEY_PGDN, "Page Down");
        names.put(KeyInput.KEY_UP, "Up");
        names.put(KeyInput.KEY_DOWN, "Down");
        names.put(KeyInput.KEY_LEFT, "Left");
        names.put(KeyInput.KEY_RIGHT, "Right");
        names.put(KeyInput.KEY_NUMPADENTER, "Num Enter");
        names.put(KeyInput.KEY_ADD, "Num +");
        names.put(KeyInput.KEY_SUBTRACT, "Num -");
        names.put(KeyInput.KEY_MULTIPLY, "Num *");
        names.put(KeyInput.KEY_DIVIDE, "Num /");
        names.put(KeyInput.KEY_DECIMAL, "Num .");
        names.put(KeyInput.KEY_MINUS, "-");
        names.put(KeyInput.KEY_EQUALS, "=");
        names.put(KeyInput.KEY_LBRACKET, "[");
        names.put(KeyInput.KEY_RBRACKET, "]");
        names.put(KeyInput.KEY_SEMICOLON, ";");
        names.put(KeyInput.KEY_APOSTROPHE, "'");
        names.put(KeyInput.KEY_GRAVE, "`");
        names.put(KeyInput.KEY_BACKSLASH, "\\");
        names.put(KeyInput.KEY_COMMA, ",");
        names.put(KeyInput.KEY_PERIOD, ".");
        names.put(KeyInput.KEY_SLASH, "/");
        NAMES = Map.copyOf(names);
    }

    private KeyNames() {
    }
}
