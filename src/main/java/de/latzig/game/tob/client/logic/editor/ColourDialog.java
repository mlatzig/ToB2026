package de.latzig.game.tob.client.logic.editor;

import de.latzig.game.tob.client.logic.editor.EditorForm.Field;

/**
 * The colour dialog behind a colour button of the Effects dialog (Phase 6b-2; ToB 2009's {@code ColorDialog},
 * {@code J:editor/dialogs/ColorDialog.java:72-161}): Red, Green and Blue 0-255, − and + by {@value #STEP} (digits can
 * be typed), the colour shown beside them, OK and Cancel; its caption is the button's name ("Sky Color 1" ...). A
 * 12-bit colour (the original palette's sky colours) steps by {@value #TWELVE_BIT_STEP}, the 16 levels of a channel,
 * and is rounded to them.
 */
public final class ColourDialog {

    public static final int RED = 0;
    public static final int GREEN = 1;
    public static final int BLUE = 2;
    /** ToB 2009's {@code ColorDialog.STEP}. */
    public static final int STEP = 16;
    public static final int TWELVE_BIT_STEP = 17;

    private final String captionKey;
    private final boolean twelveBit;
    private final int initial;
    private final EditorForm form;

    /** @param captionKey the colour button's name (a bundle key) */
    public ColourDialog(String captionKey, int rgb, boolean twelveBit) {
        this.captionKey = captionKey;
        this.twelveBit = twelveBit;
        this.initial = rgb;
        int step = twelveBit ? TWELVE_BIT_STEP : STEP;
        form = new EditorForm.Builder()
                .group("")
                .row("editor.colour.red", Field.number("editor.colour.red", (rgb >> 16) & 0xFF, 0, 255, step))
                .row("editor.colour.green", Field.number("editor.colour.green", (rgb >> 8) & 0xFF, 0, 255, step))
                .row("editor.colour.blue", Field.number("editor.colour.blue", rgb & 0xFF, 0, 255, step))
                .build();
    }

    public String captionKey() {
        return captionKey;
    }

    public EditorForm form() {
        return form;
    }

    /** The colour the dialog started with (Cancel). */
    public int initial() {
        return initial;
    }

    /** The colour of the three channels (0xRRGGBB), a 12-bit colour rounded to its 16 levels per channel. */
    public int colour() {
        int rgb = form.field(RED).value() << 16 | form.field(GREEN).value() << 8 | form.field(BLUE).value();
        return twelveBit ? EffectsDialog.twelveBit(rgb) : rgb;
    }
}
