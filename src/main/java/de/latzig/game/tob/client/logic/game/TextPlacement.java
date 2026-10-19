package de.latzig.game.tob.client.logic.game;

/**
 * Vertical placement of a one-line panel, menu or dialog text: its cap band is centred (acceptance fix 1 and round 2,
 * item 1, 2026-10-09). jME's {@code BitmapText} spans one font cell per line downwards from its local translation,
 * i.e. the translation is the top left of the text box, one line height ({@code BitmapText.getLineHeight()}) high.
 * The ToB 2009 font ({@code gui/tob-font.fnt}) gives every glyph the full {@value #CELL}-row cell ({@code yoffset} 0,
 * {@code height} 32, {@code base} 32), so the font file itself does not say where the letters sit; the glyph cells of
 * its capitals (T, E, H, and every other capital and digit, measured in {@code tob-font_0.png} by
 * {@code PanelViewTest}) are inked from row {@value #CAP_TOP} (top of the capitals) to row {@value #BASELINE}
 * (exclusive; the baseline): the cap band. Rows above it are empty, and the font has no descenders below it (its
 * lower case are small capitals in rows 15-28). The placement depends only on this band, never on the ink of the
 * actual string.
 */
public final class TextPlacement {

    /** The font cell height in font pixels ({@code tob-font.fnt} {@code lineHeight}). */
    public static final float CELL = 32f;
    /** The first inked row of the capitals in the cell (top of the capitals). */
    public static final float CAP_TOP = 8f;
    /** One past the last inked row of the capitals: the baseline. */
    public static final float BASELINE = 29f;

    private TextPlacement() {
    }

    /**
     * The GUI y (y upwards) of the text box's top that centres the cap band at {@code centreY}.
     *
     * @param lineHeight the text's line height in screen pixels (one font cell)
     */
    public static float top(float centreY, float lineHeight) {
        return centreY + lineHeight * (CAP_TOP + BASELINE) / 2f / CELL;
    }
}
