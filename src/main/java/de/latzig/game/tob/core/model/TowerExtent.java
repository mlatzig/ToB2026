package de.latzig.game.tob.core.model;

import java.util.Optional;

/**
 * The bounding box of a tower's non-empty cells (a base element or an object) over all levels, in grid cells. The
 * original towers fill at most the 8 × 8 × 4 area the converter places at cells 4..11 of the 16 × 16 × 6 grid, levels
 * 0..3 ({@link #withinOriginalVolume}); Phase 6a's large towers reach beyond it. Size-dependent rules (camera limits,
 * the object limit, the world around the tower) keep the original values for towers within that area.
 *
 * @param minX west-most column
 * @param minY north-most row (rows run north to south)
 * @param maxX east-most column
 * @param maxY south-most row
 * @param topLevel the highest level with a non-empty cell
 */
public record TowerExtent(int minX, int minY, int maxX, int maxY, int topLevel) {

    /** The first column and row of the original 8 × 8 area in the 16 × 16 grid. */
    public static final int ORIGINAL_MIN = 4;
    /** The last column and row of the original 8 × 8 area. */
    public static final int ORIGINAL_MAX = 11;
    /** The original's four levels. */
    public static final int ORIGINAL_LEVELS = 4;

    /** The extent of {@code grid}'s non-empty cells, or empty if every cell is empty. */
    public static Optional<TowerExtent> of(TowerGrid grid) {
        int minX = TowerGrid.WIDTH;
        int minY = TowerGrid.DEPTH;
        int maxX = -1;
        int maxY = -1;
        int top = -1;
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    if (!Cell.EMPTY.equals(grid.cell(x, y, level))) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        top = level;
                    }
                }
            }
        }
        return top < 0 ? Optional.empty() : Optional.of(new TowerExtent(minX, minY, maxX, maxY, top));
    }

    /** True if {@code grid} stays within the original 8 × 8 × 4 area (an empty grid does). */
    public static boolean isOriginalSize(TowerGrid grid) {
        return of(grid).map(TowerExtent::withinOriginalVolume).orElse(true);
    }

    /** Columns from west to east edge. */
    public int width() {
        return maxX - minX + 1;
    }

    /** Rows from north to south edge. */
    public int depth() {
        return maxY - minY + 1;
    }

    /** Levels from level 0 to the top level. */
    public int levels() {
        return topLevel + 1;
    }

    /** True if every non-empty cell lies in columns and rows 4..11, whatever its level. */
    public boolean withinOriginalFootprint() {
        return minX >= ORIGINAL_MIN && maxX <= ORIGINAL_MAX && minY >= ORIGINAL_MIN && maxY <= ORIGINAL_MAX;
    }

    /** True if every non-empty cell lies in columns and rows 4..11 on levels 0..3. */
    public boolean withinOriginalVolume() {
        return withinOriginalFootprint() && topLevel < ORIGINAL_LEVELS;
    }
}
