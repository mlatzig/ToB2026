package de.latzig.game.tob.client.logic;

import java.util.Optional;

import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerExtent;

/**
 * How far from the eye the sun's shadows reach (Phase 4a spec §2 decision 6: {@value #DISTANCE} cells, fading over
 * the last {@value #FADE}). Phase 6a spec decision 4: a tower beyond the original 8 × 8 area adds what its half
 * diagonal (the farthest footprint corner from the grid centre 7.5, 7.5) exceeds the original area's 4√2 cells, so the
 * farthest corner keeps the originals' margin; a tower within the 8 × 8 area keeps {@value #DISTANCE} exactly.
 */
public final class ShadowRange {

    public static final float DISTANCE = 48f;
    public static final float FADE = 8f;
    /** The original 8 × 8 area's half diagonal in cells. */
    static final double ORIGINAL_HALF_DIAGONAL = 4 * Math.sqrt(2);

    private ShadowRange() {
    }

    /** The shadow distance (cells from the eye) for {@code tower}. */
    public static float distance(TowerDefinition tower) {
        Optional<TowerExtent> extent = TowerExtent.of(tower.grid());
        if (extent.isEmpty() || extent.get().withinOriginalFootprint()) {
            return DISTANCE;
        }
        return (float) (DISTANCE + Math.max(0.0, halfDiagonal(extent.get()) - ORIGINAL_HALF_DIAGONAL));
    }

    /** The distance (cells) from the grid centre to the farthest corner of {@code extent}'s footprint. */
    static double halfDiagonal(TowerExtent extent) {
        double centre = CameraPlacement.GRID_CENTRE;
        double dx = Math.max(centre - (extent.minX() - 0.5), extent.maxX() + 0.5 - centre);
        double dz = Math.max(centre - (extent.minY() - 0.5), extent.maxY() + 0.5 - centre);
        return Math.hypot(dx, dz);
    }
}
