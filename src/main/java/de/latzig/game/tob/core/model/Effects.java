package de.latzig.game.tob.core.model;

/** Visual effects; skyStyle is the Amiga-only sky gradient field (meaning of 0 vs. 1 not yet verified). */
public record Effects(boolean lightning, boolean meteor, boolean bluePulse, int starDensity, int colourPattern,
        int skyStyle) {

    public Effects {
        Checks.range("starDensity", starDensity, 0, 3);
        Checks.range("colourPattern", colourPattern, 0, 7);
        Checks.range("skyStyle", skyStyle, 0, 3);
    }
}
