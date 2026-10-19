package de.latzig.game.tob.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import de.latzig.game.tob.client.logic.CameraPlacement;
import de.latzig.game.tob.client.logic.game.InputMapper;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.WorldState;

/**
 * No jME window is needed here: {@link Camera} is a plain data holder, not bound to a renderer.
 * <p>
 * Review finding M1 ({@code GameState.java}, {@code CameraController.java}): {@code Simulation.selected()} starts
 * as the zapper ({@code SpiderController}) even for a zapperless tower, and stays that way until the first
 * {@code SimDriver} step has applied the queued {@code SelectSpider} command (about 0.14 s at 7.14 frames/s). A
 * camera fed that still-unapplied selection shows the full-screen noise overlay, because the zapper slot is absent.
 * {@link de.latzig.game.tob.client.GameState} now shows {@link InputMapper#initialSpider} instead until the first
 * step (see {@code GameState.update(float)}).
 */
class CameraControllerTest {

    private static final float EPS = 1e-4f;

    private static TowerDefinition zapperlessTower() {
        return TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.PSH).object(1, 0, 0, ObjectType.GRB)
                .build();
    }

    /** A full 8 × 8 level like an original tower: the centre is (7.5, 7.5). */
    private static TowerDefinition fullTower(boolean cameras) {
        String row = "FFFFFFFF";
        return TestTowers.tower().level(0, row, row, row, row, row, row, row, row).object(0, 0, 0, ObjectType.ZAP)
                .cameras(cameras).build();
    }

    private static CameraController controller(TowerDefinition tower) {
        return new CameraController(new Camera(800, 600), CameraPlacement.of(tower));
    }

    private static void assertVector(Vector3f expected, Vector3f actual, String message) {
        assertEquals(expected.x, actual.x, EPS, message + " x");
        assertEquals(expected.y, actual.y, EPS, message + " y");
        assertEquals(expected.z, actual.z, EPS, message + " z");
    }

    @Test
    void theInitialViewpointOfAZapperlessTowerIsItsFirstPresentSpiderNotTheAbsentZapper() {
        Simulation sim = Simulation.start(zapperlessTower(), SimOptions.DEFAULT);
        WorldState world = sim.world();
        assertNull(world.spider(SpiderId.ZAPPER).cell(), "this tower has no zapper");

        SpiderId initial = InputMapper.initialSpider(world);
        assertTrue(world.spider(initial).cell() != null, "the chosen spider must actually be present");

        CameraController camera = controller(zapperlessTower());
        camera.update(sim, initial, 0f, 0f);

        assertFalse(camera.showsNoise(), "the first present spider is alive: no noise overlay at start");
    }

    @Test
    void theUnappliedSimulationSelectionWouldHaveShownTheNoiseTheFixAvoids() {
        Simulation sim = Simulation.start(zapperlessTower(), SimOptions.DEFAULT);

        CameraController camera = controller(zapperlessTower());
        // Simulation.selected() before the queued SelectSpider command has been applied by the first step: the
        // absent zapper. GameState used to pass this straight through (the M1 defect); it now passes
        // InputMapper.initialSpider() instead until driver.ticks() > 0, see the test above.
        camera.update(sim, SpiderId.ZAPPER, 0f, 0f);

        assertTrue(camera.showsNoise(), "documents the M1 symptom this test suite guards against");
    }

    @ParameterizedTest
    @EnumSource(CameraId.class)
    void eachExternalCameraStandsOnItsSideAndLooksLevelAcrossTheTower(CameraId id) {
        Simulation sim = Simulation.start(fullTower(true), SimOptions.DEFAULT);
        Camera jmeCamera = new Camera(800, 600);
        CameraController camera = new CameraController(jmeCamera, CameraPlacement.of(fullTower(true)));

        camera.update(sim, id, 0f, 0.016f);

        Vector3f look = switch (id) {
            case NORTH -> new Vector3f(0f, 0f, -1f);
            case EAST -> new Vector3f(1f, 0f, 0f);
            case SOUTH -> new Vector3f(0f, 0f, 1f);
            case WEST -> new Vector3f(-1f, 0f, 0f);
        };
        Vector3f centre = new Vector3f(7.5f, 0.5f, 7.5f);
        assertVector(centre.subtract(look.mult(6.125f)), jmeCamera.getLocation(), id + " eye");
        assertVector(look, jmeCamera.getDirection(), id + " direction");
        assertFalse(camera.showsNoise());
    }

    @Test
    void aCameraStepGlidesAndACameraViewShowsNoiseOnlyWithCamerasDisabled() {
        Simulation sim = Simulation.start(fullTower(true), SimOptions.DEFAULT);
        Camera jmeCamera = new Camera(800, 600);
        CameraController camera = new CameraController(jmeCamera, CameraPlacement.of(fullTower(true)));
        camera.update(sim, CameraId.NORTH, 0f, 0.016f);

        sim.step(List.of(new Command.CameraForward(CameraId.NORTH)));
        camera.update(sim, CameraId.NORTH, 0f, 0.1f);
        assertEquals(0.625f, jmeCamera.getLocation().y, EPS, "half way from 0.5 to 0.75");
        camera.update(sim, CameraId.NORTH, 0f, 0.1f);
        assertEquals(0.75f, jmeCamera.getLocation().y, EPS);

        Simulation disabled = Simulation.start(fullTower(false), SimOptions.DEFAULT);
        CameraController noisy = controller(fullTower(false));
        noisy.update(disabled, CameraId.WEST, 0f, 0f);
        assertTrue(noisy.showsNoise(), "cameras disabled: noise");
        noisy.update(disabled, SpiderId.ZAPPER, 0f, 0f);
        assertFalse(noisy.showsNoise(), "the zapper is alive");
    }

    /**
     * Acceptance fix 2: a minimized window reports 0 × 0 (jME logged "Invalid aspect given to setFrustumPerspective:
     * NaN"); a resize without area is ignored and the last valid frustum stays.
     */
    @Test
    void aResizeWithoutAreaKeepsTheLastValidFrustum() {
        Camera jmeCamera = new Camera(800, 600);
        CameraController camera = new CameraController(jmeCamera, CameraPlacement.of(fullTower(true)));
        camera.resize(1600, 900);
        float right = jmeCamera.getFrustumRight();
        float top = jmeCamera.getFrustumTop();

        camera.resize(0, 0);
        camera.resize(1600, 0);
        camera.resize(0, 900);

        assertEquals(right, jmeCamera.getFrustumRight(), EPS, "aspect 16:9 kept");
        assertEquals(top, jmeCamera.getFrustumTop(), EPS);
        assertFalse(Float.isNaN(jmeCamera.getFrustumRight()));
        Camera minimized = new Camera(0, 0);
        new CameraController(minimized, CameraPlacement.of(fullTower(true)));
        assertFalse(Float.isNaN(minimized.getFrustumRight()), "a 0 x 0 camera at construction: no NaN aspect");
    }
}
