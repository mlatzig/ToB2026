package de.latzig.game.tob.client.logic.ui;

import de.latzig.game.tob.core.progress.Profile;

/**
 * The profile name field of New Game (Phase 5a spec §5, the bitmap-font text entry without Lemur): characters as the
 * operating system delivers them (the keyboard layout applies), only printable ASCII is accepted
 * ({@link Profile#isNameChar}; umlauts, ß and every other character are ignored, decision of 2026-10-12), at most
 * {@link Profile#NAME_LENGTH}, no leading space. Backspace deletes the last character.
 */
public final class TextField {

    private final StringBuilder text = new StringBuilder();

    /** Types {@code c} if it is allowed here; returns whether it was taken. */
    public boolean type(char c) {
        if (!Profile.isNameChar(c) || text.length() >= Profile.NAME_LENGTH || c == ' ' && text.isEmpty()) {
            return false;
        }
        text.append(c);
        return true;
    }

    public void backspace() {
        if (!text.isEmpty()) {
            text.setLength(text.length() - 1);
        }
    }

    public void clear() {
        text.setLength(0);
    }

    public String text() {
        return text.toString();
    }

    /** The name to save: without trailing spaces. */
    public String name() {
        return text.toString().stripTrailing();
    }
}
