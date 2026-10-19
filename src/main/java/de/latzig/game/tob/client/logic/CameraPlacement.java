package de.latzig.game.tob.client.logic;

import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.CameraPose;

/**
 * Where an external camera stands in the scene. The original measures from the tower centre in units of 512 per cell,
 * {@code $6} east, {@code $A} north and the height above the base of level 0 ({@link CameraPose}). The scene has x
 * east, y up from the base of level 0 and z south (north is −z), one cell per 1.0. So the eye is at
 * (centre x + east / 512, height / 512, centre z − north / 512). The tower centre is the fixed grid centre
 * {@link #GRID_CENTRE}, not the bounding box of a tower's content: the original always measures from its fixed
 * origin {@code x = X·512 − 1792}, the centre of its 8 × 8 grid, which the converter places at cells 4..11 of ToB
 * 2026's fixed 16 × 16 grid, whose centre is also (7.5, 7.5). Many original towers (55 of 117) do not fill that
 * whole 8 × 8 area, but the camera origin stays at the fixed centre regardless. The view is level along the
 * camera's fixed heading.
 */
public record CameraPlacement(float centreX, float centreZ) {

    public static final float UNITS_PER_CELL = 512f;

    /** The fixed grid centre every camera is measured from, whatever cells a tower's content actually fills. */
    public static final float GRID_CENTRE = 7.5f;

    /**
     * The camera origin for the given tower: always {@link #GRID_CENTRE}. The parameter is kept so the camera
     * origin can later depend on tower-specific data (e.g. a Link Cameras option, carry-over to Phase 5) without
     * changing call sites; it is unused today.
     */
    public static CameraPlacement of(TowerDefinition tower) {
        return new CameraPlacement(GRID_CENTRE, GRID_CENTRE);
    }

    /** The eye position of a camera pose. */
    public Pose eye(CameraPose pose) {
        return new Pose(centreX + pose.east() / UNITS_PER_CELL, pose.height() / UNITS_PER_CELL,
                centreZ - pose.north() / UNITS_PER_CELL);
    }

    /** The fixed view heading in radians ({@link Motion#heading}): camera NORTH looks north (0, along −z). */
    public static float heading(CameraId camera) {
        return Motion.heading(camera.heading());
    }
}
