package de.latzig.game.tob.client.view;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import de.latzig.game.tob.client.logic.CameraPlacement;
import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.client.logic.MotionCarry;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.client.logic.game.PoseGlide;
import de.latzig.game.tob.client.logic.game.YawSmoother;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.Viewpoint;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * The view camera. Spider view (Phase 3a spec §3): ToB 2009 {@code AbstractObjectElementSPD.getCamera()} puts the eye
 * at the spider's interpolated cell position with the offset (16, 12, −16) units from the cell corner, i.e. the cell
 * centre 12/32 of a field above the cell bottom, looking level along the spider's facing; vertical field of view
 * 1.2 rad ({@code CRenderer.cpp:325-340}). The yaw follows the facing smoothly ({@link YawSmoother}). The spider that
 * owns the camera is not drawn ({@code ObjectElementsRenderer}). A spider that is no longer alive leaves the camera
 * where it was and shows noise.
 * <p>
 * External camera view (Phase 3b-1 spec §4.1): the eye stands at the camera pose ({@link CameraPlacement}) and looks
 * level along the camera's fixed heading; a step glides in {@value PoseGlide#SECONDS} s ({@link PoseGlide}). All
 * spiders are drawn. Noise only if the tower has cameras disabled. Switching the viewpoint snaps.
 */
public final class CameraController {

    public static final float EYE_HEIGHT = 12f / 32f;
    public static final float FIELD_OF_VIEW_DEGREES = 1.2f * FastMath.RAD_TO_DEG;
    public static final float NEAR = 0.05f;
    public static final float FAR = 400f;

    private final Camera camera;
    private final CameraPlacement placement;
    private final YawSmoother yaw = new YawSmoother();
    private final PoseGlide glide = new PoseGlide();
    private Viewpoint showing;
    private boolean noise;

    public CameraController(Camera camera, CameraPlacement placement) {
        this.camera = camera;
        this.placement = placement;
        resize(camera.getWidth(), camera.getHeight());
    }

    /** Places the camera for the selected viewpoint at {@code alpha} of the next frame (no carry). */
    public void update(Simulation simulation, Viewpoint selected, float alpha, float seconds) {
        update(simulation, selected, alpha, MotionCarry.NONE, seconds);
    }

    /**
     * Places the camera for the selected viewpoint at {@code alpha} of the next frame; a spider view follows the drawn
     * spider ({@link Motion#position} with the driver's {@link MotionCarry}).
     */
    public void update(Simulation simulation, Viewpoint selected, float alpha, MotionCarry carry, float seconds) {
        boolean switched = !selected.equals(showing);
        showing = selected;
        switch (selected) {
            case SpiderId spider -> followSpider(simulation.world(), spider, switched, alpha, carry, seconds);
            case CameraId external -> external(simulation, external, switched, seconds);
        }
    }

    private void followSpider(WorldState world, SpiderId spider, boolean switched, float alpha, MotionCarry carry,
            float seconds) {
        SimObject object = world.spider(spider);
        float heading = Motion.heading(object.facing());
        if (switched) {
            yaw.snap(heading);
        }
        float angle = yaw.update(heading, seconds);
        noise = !object.isAlive();
        Pose pose = Motion.position(object, world.isFrozen(), alpha, carry);
        place(new Pose(pose.x(), pose.y() + EYE_HEIGHT, pose.z()), angle);
    }

    private void external(Simulation simulation, CameraId id, boolean switched, float seconds) {
        Pose target = placement.eye(simulation.camera(id));
        if (switched) {
            glide.snap(target);
        }
        noise = !simulation.world().tower().behaviour().camerasEnabled();
        place(glide.update(target, seconds), CameraPlacement.heading(id));
    }

    private void place(Pose eye, float heading) {
        camera.setLocation(new Vector3f(eye.x(), eye.y(), eye.z()));
        camera.lookAtDirection(new Vector3f(FastMath.sin(heading), 0f, -FastMath.cos(heading)), Vector3f.UNIT_Y);
    }

    /**
     * Keeps the aspect ratio after a window resize. A size without area (a minimized window reports 0 x 0) is
     * ignored and the last valid frustum stays (acceptance fix 2: otherwise jME warns "Invalid aspect given to
     * setFrustumPerspective: NaN").
     */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        camera.setFrustumPerspective(FIELD_OF_VIEW_DEGREES, (float) width / height, NEAR, FAR);
    }

    /**
     * True while the noise overlay shows: in a spider view while that spider is not alive (destroyed or absent), in a
     * camera view while the tower has cameras disabled.
     */
    public boolean showsNoise() {
        return noise;
    }

    /** The viewpoint of the last update, or null before the first. */
    public Viewpoint showing() {
        return showing;
    }
}
