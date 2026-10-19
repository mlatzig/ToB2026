package de.latzig.game.tob.client.logic.game;

import java.util.Locale;

/**
 * A jME key code ({@code com.jme3.input.KeyInput}, positional, US layout) with or without the left Ctrl key. Phase 5b
 * moved it from {@code client.input} to {@code client.logic}: the options model edits key bindings engine-free. In
 * {@code settings.json} a chord is its code, with {@code ctrl+} in front for the left Ctrl key ({@link #toText}).
 */
public record KeyChord(int keyCode, boolean leftCtrl) {

    private static final String CTRL = "ctrl+";

    public static KeyChord of(int keyCode) {
        return new KeyChord(keyCode, false);
    }

    public static KeyChord ctrl(int keyCode) {
        return new KeyChord(keyCode, true);
    }

    /** {@code "57"} or {@code "ctrl+200"}. */
    public String toText() {
        return leftCtrl ? CTRL + keyCode : Integer.toString(keyCode);
    }

    /**
     * The chord of {@link #toText}'s form, ignoring case and surrounding blanks.
     *
     * @throws IllegalArgumentException if {@code text} is no such chord or the code is not positive
     */
    public static KeyChord parse(String text) {
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        boolean ctrl = trimmed.startsWith(CTRL);
        int code = Integer.parseInt(ctrl ? trimmed.substring(CTRL.length()) : trimmed);
        if (code <= 0) {
            throw new IllegalArgumentException("no key code: " + text);
        }
        return new KeyChord(code, ctrl);
    }
}
