package de.latzig.game.tob.core.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Structural limits: each spider at most once; at most 20 objects in a tower within the original 8 × 8 × 4 area (the
 * original designer's 17 objects plus 3 spiders, {@code S:14315-14329}), at most 64 in a larger one (Phase 6a spec
 * decision 3, {@link TowerExtent#isOriginalSize}). The same switch governs {@code core.sim.ForcerLifts}' limit of
 * lifts moved by one forcer grab ({@link #maxLifts}, author decision 2026-10-14): a game rule, not an object table,
 * so it is not checked by {@link #problems} and never makes a tower unplayable.
 */
public final class TowerLimits {

    /** The original's limit, for towers within the 8 × 8 × 4 area. */
    public static final int MAX_OBJECTS = 20;
    /** The limit of towers beyond the 8 × 8 × 4 area (Phase 6a). */
    public static final int MAX_OBJECTS_LARGE = 64;

    /** The original's forcer lift limit ({@code core.sim.ForcerLifts.MAX_LIFTS}), for towers within the area. */
    public static final int MAX_LIFTS = 20;
    /** The forcer lift limit of towers beyond the 8 × 8 × 4 area (Phase 6a). */
    public static final int MAX_LIFTS_LARGE = 64;

    private TowerLimits() {
    }

    /** {@link #MAX_OBJECTS} for a grid within the original 8 × 8 × 4 area, else {@link #MAX_OBJECTS_LARGE}. */
    public static int maxObjects(TowerGrid grid) {
        return TowerExtent.isOriginalSize(grid) ? MAX_OBJECTS : MAX_OBJECTS_LARGE;
    }

    /**
     * {@link #MAX_LIFTS} for a grid within the original 8 × 8 × 4 area, else {@link #MAX_LIFTS_LARGE}: at most this
     * many lifts move at once on one forcer grab ({@code core.sim.ForcerLifts}).
     */
    public static int maxLifts(TowerGrid grid) {
        return TowerExtent.isOriginalSize(grid) ? MAX_LIFTS : MAX_LIFTS_LARGE;
    }

    public static List<String> problems(TowerDefinition tower) {
        List<String> problems = new ArrayList<>();
        Map<ObjectType, Integer> spiders = new EnumMap<>(ObjectType.class);
        TowerGrid grid = tower.grid();
        for (int level = 0; level < TowerGrid.LEVELS; level++) {
            for (int y = 0; y < TowerGrid.DEPTH; y++) {
                for (int x = 0; x < TowerGrid.WIDTH; x++) {
                    Cell cell = grid.cell(x, y, level);
                    if (cell.hasObject() && cell.object().isSpider()) {
                        spiders.merge(cell.object(), 1, Integer::sum);
                    }
                }
            }
        }
        int objects = grid.objectCount();
        int max = maxObjects(grid);
        if (objects > max) {
            problems.add(objects + " objects, at most " + max + " allowed");
        }
        spiders.forEach((spider, count) -> {
            if (count > 1) {
                problems.add(spider.code() + " placed " + count + " times");
            }
        });
        return problems;
    }
}
