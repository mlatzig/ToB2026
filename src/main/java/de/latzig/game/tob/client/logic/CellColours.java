package de.latzig.game.tob.client.logic;

/**
 * Phase 5e: the colours of the base geometry's faces (0xRRGGBB): a floor's or box's top in a cell and its sides, and
 * a lift platform's top and rim. The Remake look ({@link TowerColours}) draws a cell's sides in its top colour and a
 * lift in one colour; the Classic Look ({@link ClassicColours}) takes the original's separate side colours.
 * {@link BaseGeometry} darkens every bottom by a quarter of its top colour.
 */
public interface CellColours {

    /** The top colour of a floor or box at grid ({@code x}, {@code y}, {@code level}). */
    int top(int x, int y, int level);

    /** The side colour of a floor or box at grid ({@code x}, {@code y}, {@code level}); by default its top's. */
    default int side(int x, int y, int level) {
        return top(x, y, level);
    }

    /** A lift platform's top. */
    int liftTop();

    /** A lift platform's rim; by default its top's colour. */
    default int liftSide() {
        return liftTop();
    }

    /**
     * Whether the vertex colours are these colours converted from sRGB to linear ({@link SrgbColours}; controller
     * gamma rule): true for the Classic Look's flat palette colours, false (the default) for the Remake look, whose
     * vertex colours tint its textures as before.
     */
    default boolean linear() {
        return false;
    }
}
