package de.latzig.game.tob.core.model;

/** Rectangle in the GUI atlas image (pixels, origin top-left). */
public record GuiRegion(int x, int y, int width, int height) {

    public GuiRegion {
        Checks.range("x", x, 0, Integer.MAX_VALUE);
        Checks.range("y", y, 0, Integer.MAX_VALUE);
        Checks.range("width", width, 1, Integer.MAX_VALUE);
        Checks.range("height", height, 1, Integer.MAX_VALUE);
    }

    /** Converts ToB 2009 inclusive coordinates {@code x1,y1,x2,y2}. */
    public static GuiRegion fromInclusive(int x1, int y1, int x2, int y2) {
        return new GuiRegion(x1, y1, x2 - x1 + 1, y2 - y1 + 1);
    }
}
