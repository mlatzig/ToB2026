package de.latzig.game.tob.core.model;

import java.util.Arrays;
import java.util.Objects;

/** Immutable tower grid: 16 (x, west → east) × 16 (y, row 0 = north) × 6 levels (0 = bottom). */
public final class TowerGrid {

    public static final int WIDTH = 16;
    public static final int DEPTH = 16;
    public static final int LEVELS = 6;

    private static final TowerGrid EMPTY = builder().build();

    private final Cell[] cells;

    private TowerGrid(Cell[] cells) {
        this.cells = cells;
    }

    public static TowerGrid empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(cells.clone());
    }

    public Cell cell(int x, int y, int level) {
        return cells[index(x, y, level)];
    }

    public boolean isLevelEmpty(int level) {
        Checks.range("level", level, 0, LEVELS - 1);
        for (int y = 0; y < DEPTH; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (!Cell.EMPTY.equals(cell(x, y, level))) {
                    return false;
                }
            }
        }
        return true;
    }

    public int objectCount() {
        int count = 0;
        for (Cell cell : cells) {
            if (cell.hasObject()) {
                count++;
            }
        }
        return count;
    }

    private static int index(int x, int y, int level) {
        Checks.range("x", x, 0, WIDTH - 1);
        Checks.range("y", y, 0, DEPTH - 1);
        Checks.range("level", level, 0, LEVELS - 1);
        return (level * DEPTH + y) * WIDTH + x;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TowerGrid grid && Arrays.equals(cells, grid.cells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cells);
    }

    @Override
    public String toString() {
        return "TowerGrid[objects=" + objectCount() + "]";
    }

    /** Mutable builder; {@link #build()} returns an independent immutable grid. */
    public static final class Builder {

        private final Cell[] cells;

        private Builder() {
            this(filledWithEmpty());
        }

        private Builder(Cell[] cells) {
            this.cells = cells;
        }

        private static Cell[] filledWithEmpty() {
            Cell[] empty = new Cell[WIDTH * DEPTH * LEVELS];
            Arrays.fill(empty, Cell.EMPTY);
            return empty;
        }

        public Builder set(int x, int y, int level, Cell cell) {
            cells[index(x, y, level)] = Objects.requireNonNull(cell, "cell");
            return this;
        }

        public Cell get(int x, int y, int level) {
            return cells[index(x, y, level)];
        }

        public TowerGrid build() {
            return new TowerGrid(cells.clone());
        }
    }
}
