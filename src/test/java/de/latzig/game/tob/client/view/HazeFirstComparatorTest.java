package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.TransparentComparator;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;

class HazeFirstComparatorTest {

    private static Geometry at(String name, float z) {
        Geometry geometry = new Geometry(name, new Quad(1f, 1f));
        geometry.setLocalTranslation(0f, 0f, z);
        geometry.updateGeometricState();
        return geometry;
    }

    @Test
    void theHazeIsDrawnFirstAndEverythingElseBackToFrontAsBefore() {
        Camera cam = new Camera(640, 480);
        cam.setLocation(new Vector3f(0f, 0f, 10f));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        Geometry haze = at("haze", 0f);
        HazeFirstComparator.mark(haze);
        Geometry near = at("near", 5f);
        Geometry far = at("far", -5f);
        HazeFirstComparator comparator = new HazeFirstComparator();
        comparator.setCamera(cam);
        TransparentComparator plain = new TransparentComparator();
        plain.setCamera(cam);

        assertTrue(HazeFirstComparator.isHaze(haze));
        assertFalse(HazeFirstComparator.isHaze(near));
        assertTrue(comparator.compare(haze, near) < 0, "before a nearer geometry");
        assertTrue(comparator.compare(near, haze) > 0);
        assertTrue(comparator.compare(haze, far) < 0, "before a farther geometry");
        assertTrue(comparator.compare(far, haze) > 0);
        assertEquals(0, comparator.compare(haze, haze));
        assertTrue(comparator.compare(far, near) < 0, "the others back to front");
        assertEquals(Integer.signum(plain.compare(far, near)), Integer.signum(comparator.compare(far, near)));
        assertEquals(Integer.signum(plain.compare(near, far)), Integer.signum(comparator.compare(near, far)));
    }
}
