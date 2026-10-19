package de.latzig.game.tob.client.logic.game;

import java.util.Optional;

import de.latzig.game.tob.client.logic.Motion;
import de.latzig.game.tob.client.logic.MotionCarry;
import de.latzig.game.tob.client.logic.Pose;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.Viewpoint;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * Spider picking by a click in the 3D view (Phase 3b-2 spec §4.1, the original's pick {@code S:8212-8221}): a ray
 * from the cursor into the scene against one box per alive spider; the nearest hit wins. The box stands on the
 * spider's interpolated render position ({@link Motion#position}, the same as the drawn mesh): {@value #HALF_WIDTH}
 * fields to each side and {@value #HEIGHT} fields high, a little larger than the spider meshes (±0.2 wide, up to
 * 0.46 high). The spider whose view is shown is not drawn ({@code WorldView}) and cannot be picked; cameras never.
 */
public final class SpiderPick {

    public static final float HALF_WIDTH = 0.25f;
    public static final float HEIGHT = 0.5f;

    /** A ray in scene coordinates (1 field = 1.0): origin and direction (any length but zero). */
    public record Ray(float originX, float originY, float originZ, float directionX, float directionY,
            float directionZ) {
    }

    private SpiderPick() {
    }

    /** The nearest alive spider whose box the ray hits, except the shown one; empty for a miss. */
    public static Optional<SpiderId> pick(WorldState world, float alpha, Viewpoint shown, Ray ray) {
        return pick(world, alpha, MotionCarry.NONE, shown, ray);
    }

    /** As {@link #pick(WorldState, float, Viewpoint, Ray)} on the drawn positions with the driver's carry. */
    public static Optional<SpiderId> pick(WorldState world, float alpha, MotionCarry carry, Viewpoint shown,
            Ray ray) {
        SpiderId nearest = null;
        float nearestDistance = Float.POSITIVE_INFINITY;
        for (SpiderId id : SpiderId.values()) {
            SimObject spider = world.spider(id);
            if (!spider.isAlive() || id.equals(shown)) {
                continue;
            }
            Pose pose = Motion.position(spider, world.isFrozen(), alpha, carry);
            float distance = hit(ray, pose.x() - HALF_WIDTH, pose.y(), pose.z() - HALF_WIDTH, pose.x() + HALF_WIDTH,
                    pose.y() + HEIGHT, pose.z() + HALF_WIDTH);
            if (distance >= 0f && distance < nearestDistance) {
                nearest = id;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /**
     * Slab test of a ray against an axis-aligned box.
     *
     * @return the ray parameter of the entry point (0 if the origin is inside), or −1 for a miss
     */
    static float hit(Ray ray, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float[] origin = {ray.originX(), ray.originY(), ray.originZ()};
        float[] direction = {ray.directionX(), ray.directionY(), ray.directionZ()};
        float[] min = {minX, minY, minZ};
        float[] max = {maxX, maxY, maxZ};
        float near = 0f;
        float far = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (direction[axis] == 0f) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
                    return -1f;
                }
                continue;
            }
            float t1 = (min[axis] - origin[axis]) / direction[axis];
            float t2 = (max[axis] - origin[axis]) / direction[axis];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) {
                return -1f;
            }
        }
        return near;
    }
}
