package de.latzig.game.tob.core.model;

/**
 * Frame sequence in the FX atlas, stored as a grid: frame {@code i} is the cell in column {@code i % columns} and
 * row {@code i / columns}, counted from the top-left cell at ({@code x}, {@code y}).
 */
public record FxSequence(int x, int y, int cellWidth, int cellHeight, int columns, int frames) {

    public FxSequence {
        Checks.range("x", x, 0, 65535);
        Checks.range("y", y, 0, 65535);
        Checks.range("cellWidth", cellWidth, 1, 65536);
        Checks.range("cellHeight", cellHeight, 1, 65536);
        Checks.range("columns", columns, 1, 4096);
        Checks.range("frames", frames, 1, 4096);
    }

    public int rows() {
        return (frames + columns - 1) / columns;
    }

    /** Width of the used grid in pixels. */
    public int width() {
        return Math.min(columns, frames) * cellWidth;
    }

    /** Height of the used grid in pixels. */
    public int height() {
        return rows() * cellHeight;
    }

    public FxSprite frame(int index) {
        Checks.range("frame", index, 0, frames - 1);
        return new FxSprite(x + (index % columns) * cellWidth, y + (index / columns) * cellHeight, cellWidth,
                cellHeight);
    }
}
