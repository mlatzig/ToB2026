package de.latzig.game.tob.core.sim;

/**
 * The four external cameras (original selections 0–3, "data for the cameras (N,E,S,W)"). A camera is named after the
 * direction it looks: camera NORTH stands south of the tower and looks north. Its pose ({@link CameraPose}) and its
 * program live in {@link SpiderController}; a camera program never runs, it only carries a program between a spider
 * and a camera.
 */
public enum CameraId implements Viewpoint {
    NORTH, EAST, SOUTH, WEST;

    /**
     * The fixed view direction, also the heading given to a spider that receives this camera's program
     * ({@code S:8295-8301} copies the camera record's +$C). The load code ({@code S:17711-17721}) does not set +$C;
     * the camera's own direction is the only value that matches the start positions of the load loop.
     */
    public Direction heading() {
        return Direction.values()[ordinal()];
    }
}
