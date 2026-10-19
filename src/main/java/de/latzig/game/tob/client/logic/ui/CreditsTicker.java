package de.latzig.game.tob.client.logic.ui;

import java.util.Locale;

/**
 * The credits ticker under the main menu (Phase 5a spec decision 4), ToB 2009's typewriter
 * ({@code MenuWindow.update}): one character every {@value #CHAR_SECONDS} s, upper-cased; a {@code \n} holds the line
 * {@value #LINE_SECONDS} s, a {@code \t} clears it; after the last character the text starts again. A blinking cursor
 * (char 127 of the ToB 2009 font) follows the text at 2 Hz.
 */
public final class CreditsTicker {

    public static final double CHAR_SECONDS = 0.05;
    public static final double LINE_SECONDS = 5.0;
    public static final char CURSOR = (char) 127;

    private final String text;
    private int index;
    private double waited;
    private double duration;
    private String line = "";

    public CreditsTicker(String text) {
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty ticker text");
        }
        this.text = text;
    }

    /** Advances by {@code seconds}: types the next character whenever the current one's time is over. */
    public void advance(double seconds) {
        waited += seconds;
        while (waited >= duration) {
            waited -= duration;
            char c = text.charAt(index);
            if (c == '\n') {
                duration = LINE_SECONDS;
            } else if (c == '\t') {
                duration = CHAR_SECONDS;
                line = "";
            } else {
                duration = CHAR_SECONDS;
                line += c;
            }
            index++;
            if (index >= text.length()) {
                index = 0;
                line = "";
            }
        }
    }

    /** The typed line, upper-cased. */
    public String line() {
        return line.toUpperCase(Locale.ROOT);
    }

    /** The typed line with the cursor in the second half of every {@code clock} second. */
    public String shown(double clock) {
        return (int) (clock * 2.0) % 2 == 1 ? line() + CURSOR : line();
    }
}
