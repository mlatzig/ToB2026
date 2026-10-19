package de.latzig.game.tob.core.model;

/** Rectangle in the FX atlas image (pixels, origin top-left). */
public record FxSprite(int x, int y, int width, int height) {

    public FxSprite {
        Checks.range("x", x, 0, 65535);
        Checks.range("y", y, 0, 65535);
        Checks.range("width", width, 1, 65536);
        Checks.range("height", height, 1, 65536);
    }
}
