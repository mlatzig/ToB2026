package de.latzig.game.tob.client.logic.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.frontend.DoubleClick;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;

/** Phase 6b spec decision 3: the 3D preview's view, its drag, its reset and its double click. */
class PreviewCameraTest {

    private static final float PITCH = (float) Math.toRadians(PreviewCamera.RESET_PITCH_DEGREES);

    private static TowerDefinition tower(int minX, int minY, int maxX, int maxY, int top) {
        TowerGrid grid = TowerGrid.empty();
        for (int level = 0; level <= top; level++) {
            grid = TowerEdits.place(grid, EditorElement.FLOOR, minX, minY, level).grid();
            grid = TowerEdits.place(grid, EditorElement.FLOOR, maxX, maxY, level).grid();
        }
        return EditorTowers.withGrid(EditorTowers.blank("new-000", 0), grid);
    }

    /**
     * How far the box's farthest corner reaches towards the frustum's edge: 1 at the edge, above 1 outside; the
     * horizontal and vertical half fields of view of a view {@code aspect} wide.
     */
    private static double fill(PreviewCamera.View view, float aspect, int minX, int minY, int maxX, int maxY,
            int top) {
        double[] e = {view.eye().x(), view.eye().y(), view.eye().z()};
        double[] f = unit(new double[] {view.target().x() - e[0], view.target().y() - e[1], view.target().z() - e[2]});
        double[] right = unit(new double[] {-f[2], 0.0, f[0]});
        double[] up = {right[1] * f[2] - right[2] * f[1], right[2] * f[0] - right[0] * f[2],
                right[0] * f[1] - right[1] * f[0]};
        double tanV = Math.tan(PreviewCamera.FIELD_OF_VIEW / 2.0);
        double tanH = tanV * aspect;
        double fill = 0.0;
        for (float x : new float[] {minX - 0.5f, maxX + 0.5f}) {
            for (float y : new float[] {0f, top + 1f}) {
                for (float z : new float[] {minY - 0.5f, maxY + 0.5f}) {
                    double[] v = {x - e[0], y - e[1], z - e[2]};
                    double depth = dot(v, f);
                    assertTrue(depth > 0.0, "in front of the eye");
                    fill = Math.max(fill, Math.max(Math.abs(dot(v, right)) / (tanH * depth),
                            Math.abs(dot(v, up)) / (tanV * depth)));
                }
            }
        }
        return fill;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] unit(double[] v) {
        double length = Math.sqrt(dot(v, v));
        return new double[] {v[0] / length, v[1] / length, v[2] / length};
    }

    private static double distance(PreviewCamera.View view) {
        double dx = view.eye().x() - view.target().x();
        double dy = view.eye().y() - view.target().y();
        double dz = view.eye().z() - view.target().z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    void theResetViewLooksFromTheNorthSlightlyFromAboveAtLevel2() {
        PreviewCamera camera = new PreviewCamera();
        PreviewCamera.View view = camera.view(tower(4, 4, 11, 11, 3), 4f / 3f);

        assertEquals(7.5f, view.target().x(), 1e-4f, "the footprint's middle");
        assertEquals(7.5f, view.target().z(), 1e-4f);
        assertEquals(PreviewCamera.TARGET_HEIGHT, view.target().y(), 0f, "the middle of level 2");
        assertEquals(view.target().x(), view.eye().x(), 1e-4f);
        assertTrue(view.eye().z() < view.target().z(), "north of the tower (north is -z)");
        double flat = view.target().z() - view.eye().z();
        assertEquals(PITCH, Math.atan2(view.eye().y() - view.target().y(), flat), 1e-4, "20 degrees from above");
        assertEquals(camera.view(tower(4, 4, 11, 11, 3), 4f / 3f), camera.view(null, 4f / 3f),
                "an empty tower is framed like the original 8 x 8 x 4 area");
    }

    @Test
    void theWholeTowerFillsTheViewWhateverItsSizeTheAspectAndTheTurn() {
        PreviewCamera camera = new PreviewCamera();
        int[][] boxes = {{4, 4, 11, 11, 3}, {0, 0, 15, 15, 5}, {2, 9, 3, 9, 0}, {6, 2, 7, 13, 5}};
        for (int turn = 0; turn < 8; turn++) {
            for (float aspect : new float[] {0.5f, 236f / 183f, 16f / 9f}) {
                for (int[] b : boxes) {
                    double fill = fill(camera.view(tower(b[0], b[1], b[2], b[3], b[4]), aspect), aspect, b[0], b[1],
                            b[2], b[3], b[4]);
                    String at = "turn " + turn + ", aspect " + aspect + ", box " + Arrays.toString(b);
                    assertTrue(fill <= 1.0, at + ": inside the view, fill " + fill);
                    assertTrue(fill > (b[2] - b[0] == b[3] - b[1] ? 0.5 : 0.3), at + ": not lost in it, fill "
                            + fill + " (a narrow footprint is framed by its circumcircle)");
                }
            }
            camera.mouse(0f, 0f);
            camera.press(turn);
            camera.mouse(37f, 0f);
            camera.release();
        }
        PreviewCamera turned = new PreviewCamera();
        double reset = distance(turned.view(tower(6, 2, 7, 13, 5), 1f));
        turned.press(0.0);
        turned.mouse(120f, 0f);
        assertEquals(reset, distance(turned.view(tower(6, 2, 7, 13, 5), 1f)), 1e-4, "turning keeps the distance");
        PreviewCamera.View small = camera.view(tower(4, 4, 11, 11, 3), 1f);
        PreviewCamera.View large = camera.view(tower(0, 0, 15, 15, 5), 1f);
        assertTrue(distance(large) > distance(small), "a large tower is seen from further away");
    }

    @Test
    void draggingTurnsAndTiltsTheViewWithinItsLimits() {
        PreviewCamera camera = new PreviewCamera();
        camera.mouse(100f, 100f);
        camera.mouse(150f, 100f);
        assertEquals(0f, camera.yaw(), 0f, "not grabbed: nothing turns");

        camera.press(0.0);
        assertTrue(camera.grabbed());
        camera.mouse(200f, 100f);
        assertEquals(50 * PreviewCamera.RADIANS_PER_PIXEL, camera.yaw(), 1e-5f, "right: the eye goes east");
        PreviewCamera.View turned = camera.view(null, 1f);
        assertTrue(turned.eye().x() > turned.target().x());
        camera.mouse(200f, 80f);
        assertEquals(PITCH + 20 * PreviewCamera.RADIANS_PER_PIXEL, camera.pitch(), 1e-5f, "down: the eye rises");
        camera.mouse(200f, -2000f);
        assertEquals(Math.toRadians(PreviewCamera.MAX_PITCH_DEGREES), camera.pitch(), 1e-5);
        camera.mouse(200f, 2000f);
        assertEquals(0f, camera.pitch(), 1e-6f, "never from below the horizon");
        camera.release();
        camera.mouse(400f, 400f);
        assertEquals(0f, camera.pitch(), 1e-6f);

        camera.reset();
        assertEquals(0f, camera.yaw(), 0f);
        assertEquals(PITCH, camera.pitch(), 1e-6f);
    }

    @Test
    void aDoubleClickTogglesTheFullScreenATooSlowOrMovedOneDoesNot() {
        PreviewCamera camera = new PreviewCamera();
        camera.mouse(10f, 10f);
        camera.press(1.0);
        camera.release();
        camera.press(1.0 + DoubleClick.INTERVAL);
        assertTrue(camera.maximized());
        assertFalse(camera.grabbed(), "the double click does not grab");

        camera.press(2.0);
        camera.release();
        camera.press(2.0 + DoubleClick.INTERVAL + 0.01);
        assertTrue(camera.maximized(), "too slow: a press, no toggle");
        camera.release();
        camera.mouse(10f + DoubleClick.DISTANCE + 1f, 10f);
        camera.press(2.7);
        assertTrue(camera.maximized(), "moved too far: no toggle");
        camera.release();
        camera.press(2.8);
        assertFalse(camera.maximized(), "a double click again: the small area");
        camera.toggle();
        camera.restore();
        assertFalse(camera.maximized());
    }
}
