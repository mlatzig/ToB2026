package de.latzig.game.tob.core.sim;

import de.latzig.game.tob.core.model.TowerGrid;

/** A cell in ToB 2026 grid coordinates: x west → east, y row 0 = north, level 0 = bottom. May lie outside the grid. */
public record CellPos(int x, int y, int level) {

    /** Number of cells in the 16 × 16 × 6 grid. */
    public static final int COUNT = TowerGrid.WIDTH * TowerGrid.DEPTH * TowerGrid.LEVELS;

    public boolean inGrid() {
        return x >= 0 && x < TowerGrid.WIDTH && y >= 0 && y < TowerGrid.DEPTH && level >= 0
                && level < TowerGrid.LEVELS;
    }

    public CellPos step(Direction direction) {
        return new CellPos(x + direction.dx(), y + direction.dy(), level);
    }

    public CellPos above() {
        return new CellPos(x, y, level + 1);
    }

    public CellPos below() {
        return new CellPos(x, y, level - 1);
    }

    /** Dense index in {@link TowerGrid} order; only for cells inside the grid. */
    public int index() {
        if (!inGrid()) {
            throw new IllegalStateException("cell outside the grid: " + this);
        }
        return (level * TowerGrid.DEPTH + y) * TowerGrid.WIDTH + x;
    }

    public static CellPos ofIndex(int index) {
        if (index < 0 || index >= COUNT) {
            throw new IllegalArgumentException("cell index out of range: " + index);
        }
        return new CellPos(index % TowerGrid.WIDTH, (index / TowerGrid.WIDTH) % TowerGrid.DEPTH,
                index / (TowerGrid.WIDTH * TowerGrid.DEPTH));
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + level + ")";
    }
}
