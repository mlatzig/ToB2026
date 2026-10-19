package de.latzig.game.tob.client.logic.effects;

import java.util.Optional;

import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.TowerDefinition;

/**
 * Where a tower's moon shows in the sky (Phase 4a spec §2 decision 5). The original ({@code L00C0A4},
 * {@code S:10356-10416}, report 07 §3.3) draws the 16 × 16 moon bitmap only while the view looks along the moon's
 * heading, at screen x = 16 + 16 · X (X ≤ 17) and y = 16 + Y (Y ≤ 55) in its 3D window, whose horizon is line 71, and
 * turns it one of 32 frames per drawn frame (rotation 0 clockwise, 1 none, 2 anticlockwise). Here the moon is a
 * sprite in the sky in the heading's direction: the horizontal offset of its centre from the screen centre (160)
 * turns into azimuth at {@value #DEGREES_PER_PIXEL}° per pixel (320 pixels span the 16:9 view's horizontal field),
 * the height of its centre above the horizon into elevation at {@value #DEGREES_PER_LINE}° per line (the 55 sky lines
 * span the upper half of the vertical field of view, 1.2 rad). The moon does not rotate (author decision at the
 * acceptance on 2026-10-10: a turning shaded moon would make the sun circle it; the rotation byte has no effect).
 * Only azimuth and elevation are kept; the 3D moon of Phase 4b-3 ({@link MoonDesign}) places itself from them.
 *
 * @param azimuth   radians, clockwise from north as {@link Motion#heading}
 * @param elevation radians above the horizon
 */
public record MoonPlacement(float azimuth, float elevation) {

    public static final float DEGREES_PER_PIXEL = 100f / 320f;
    public static final float DEGREES_PER_LINE = 0.625f;

    /** The tower's moon, or empty for heading 4 ("none"; 5–7 are never shown either). */
    public static Optional<MoonPlacement> of(TowerDefinition tower) {
        Moon moon = tower.moon();
        if (moon.heading() > 3) {
            return Optional.empty();
        }
        int x = Math.min(moon.x(), 17);
        int y = Math.min(moon.y(), 55);
        double azimuth = moon.heading() * 90.0 + (16 + 16 * x + 8 - 160) * DEGREES_PER_PIXEL;
        double elevation = (71 - (16 + y + 8)) * DEGREES_PER_LINE;
        return Optional.of(new MoonPlacement((float) Math.toRadians(azimuth), (float) Math.toRadians(elevation)));
    }
}
