package de.latzig.game.tob.client.view;

import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryComparator;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.TransparentComparator;
import com.jme3.scene.Geometry;

/**
 * The transparent bucket's order (Phase 4b-2): the ground haze ({@link HazeView}) first, every other transparent
 * geometry back to front as jME's {@link TransparentComparator} sorts it. The haze quad's bound reaches ±4096 units,
 * so jME's distance sort would draw it last and blend it over far bolts, effect sprites and glass. Drawn first, it
 * lies under every transparent geometry above its plane (3.5 units), which is right for them.
 *
 * <p><b>Limit:</b> the haze is drawn before <em>every</em> transparent geometry, also the parts below its plane
 * (level-0 glass bottoms, ground debris of the lightning on the flat ground); as the haze writes no depth, those are
 * then blended over the haze instead of under it. This is harmless while the haze and the other transparent effects
 * are on different towers (only {@code original-058} has the blue pulse flag, without lightning or meteors); if the
 * flags are combined later (or a height fog replaces the haze in Phase 4b-3), revisit this order.</p>
 *
 * <p>Installed once on the main viewport ({@link #install}).</p>
 */
public final class HazeFirstComparator implements GeometryComparator {

    /** The user-data key that marks a geometry as haze. */
    public static final String HAZE = "tob.haze";

    private final TransparentComparator others = new TransparentComparator();

    /** Marks {@code geometry} as haze: drawn before the other transparent geometries. */
    public static void mark(Geometry geometry) {
        geometry.setUserData(HAZE, Boolean.TRUE);
    }

    /** Whether {@code geometry} is marked as haze. */
    public static boolean isHaze(Geometry geometry) {
        return Boolean.TRUE.equals(geometry.getUserData(HAZE));
    }

    /** Sorts {@code viewPort}'s transparent bucket with a new {@link HazeFirstComparator}. */
    public static void install(ViewPort viewPort) {
        viewPort.getQueue().setGeometryComparator(Bucket.Transparent, new HazeFirstComparator());
    }

    @Override
    public void setCamera(Camera cam) {
        others.setCamera(cam);
    }

    @Override
    public int compare(Geometry a, Geometry b) {
        boolean hazeA = isHaze(a);
        boolean hazeB = isHaze(b);
        if (hazeA || hazeB) {
            return hazeA == hazeB ? 0 : hazeA ? -1 : 1;
        }
        return others.compare(a, b);
    }
}
