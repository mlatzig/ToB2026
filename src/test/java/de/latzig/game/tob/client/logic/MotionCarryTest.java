package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.latzig.game.tob.client.logic.game.LiftHeights;
import de.latzig.game.tob.client.logic.game.SimDriver;
import de.latzig.game.tob.client.logic.game.SpiderPick;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.CellPos;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.Lift;
import de.latzig.game.tob.core.sim.MoveKind;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

/**
 * Acceptance fix "reversal interpolation": a Back that reverses a lift ride or a walk, or a zap that reverses a worm,
 * changes the move before the frame's advance, so the extrapolating {@link Motion#position} used to draw the object
 * one frame on in the old direction and then snap back by two frames' worth. {@link MotionCarry} carries that
 * prediction error over the next frame: the drawn path is continuous across every step boundary, and without a
 * reversal nothing changes.
 */
class MotionCarryTest {

    /** The largest alpha the driver reports (just below 1). */
    private static final float END = Math.nextDown(1f);
    /** Continuity across a step boundary, in fields (float rounding only). */
    private static final float EPS = 1e-5f;
    /** The most a reversal frame moves: ease(c) + 2 · ease(c + 1) ≤ 3 · 18 units (a walk: 32 + 16). */
    private static final float MAX_FRAME_MOTION = 54f / Motion.UNITS_PER_FIELD;

    private static final SpiderId Z = SpiderId.ZAPPER;
    private static final SpiderId G = SpiderId.GRABBER;

    /**
     * The drawn jumps of one object at one step boundary (fields, largest axis); {@code moving} = the object was moving
     * before the step or is a spider (Task 16 fix B: a spider's start from standing is carried too); another object's
     * move that starts from standing is not carried and keeps its jump of one frame.
     */
    private record Boundary(int tick, boolean moving, float oldJump, float newJump, float frameMotion) {
    }

    /** A driver stepped one frame at a time with the commands scripted per tick (1 = the first step). */
    private static final class Run {

        private final SimDriver driver;
        private int tick;

        Run(TowerDefinition tower, Map<Integer, Command> script) {
            Simulation simulation = Simulation.start(tower, SimOptions.DEFAULT);
            driver = new SimDriver(simulation, sim -> Optional.ofNullable(script.get(tick + 1)), (events, t) -> {
            });
        }

        SimObject object(int slot) {
            return driver.simulation().world().object(slot);
        }

        boolean frozen() {
            return driver.simulation().world().isFrozen();
        }

        Pose drawn(int slot, float alpha) {
            return Motion.position(object(slot), frozen(), alpha, driver.carry());
        }

        Pose drawnBefore(int slot, float alpha) {
            return Motion.position(object(slot), frozen(), alpha);
        }

        /** Runs one step and measures the boundary of {@code slot}. */
        Boundary step(int slot) {
            boolean moving = object(slot).isMoving() || object(slot).isSpider();
            Pose endNew = drawn(slot, END);
            Pose endOld = drawnBefore(slot, END);
            driver.update(SimDriver.FRAME_SECONDS);
            tick++;
            assertEquals(tick, driver.ticks());
            assertEquals(0f, driver.alpha());
            Pose startNew = drawn(slot, 0f);
            return new Boundary(tick, moving, distance(endOld, drawnBefore(slot, 0f)), distance(endNew, startNew),
                    distance(startNew, drawn(slot, END)));
        }
    }

    private static float distance(Pose a, Pose b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.max(Math.abs(a.y() - b.y()), Math.abs(a.z() - b.z())));
    }

    /** Runs {@code steps} steps; every boundary of {@code slot} is continuous and every frame moves a normal amount. */
    private static Boundary[] runContinuous(Run run, int slot, int steps) {
        Boundary[] boundaries = new Boundary[steps + 1];
        for (int i = 1; i <= steps; i++) {
            Boundary boundary = run.step(slot);
            boundaries[i] = boundary;
            int tick = boundary.tick();
            if (boundary.moving()) {
                assertTrue(boundary.newJump() <= EPS, "drawn jump at step " + tick + ": " + boundary.newJump() * 512
                        + " units (before the fix: " + boundary.oldJump() * 512 + ")");
            } else {
                assertEquals(boundary.oldJump(), boundary.newJump(),
                        "a start from standing is unchanged, step " + tick);
            }
            assertTrue(boundary.frameMotion() <= MAX_FRAME_MOTION,
                    "frame after step " + boundary.tick() + " moves " + boundary.frameMotion() * 512 + " units");
        }
        return boundaries;
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 17, 29, 32})
    void aLiftRideReversedByBackIsDrawnWithoutAJump(int reverseTick) {
        // zapper on the up lift at (0,0,0), its stop above: Back starts the ride in step 1 and reverses it later (the
        // 8-frame move throttle allows it from step 10 = counter 23; step 32 = counter 1, the ride's last frame)
        Run run = new Run(TestTowers.tower().level(0, "U").level(1, "u").object(0, 0, 0, ObjectType.ZAP).build(),
                Map.of(1, new Command.Back(Z), reverseTick, new Command.Back(Z)));

        Boundary[] boundaries = runContinuous(run, Z.slot(), 70);

        assertTrue(boundaries[reverseTick].oldJump() >= 9f / 512f, "the scenario reproduces the snap: "
                + boundaries[reverseTick].oldJump() * 512 + " units");
        assertEquals(0, run.object(Z.slot()).worldY(), "the ride returned to the start");
        assertTrue(!run.object(Z.slot()).isMoving());
    }

    @Test
    void theLiftPlatformAndTheSpiderPickFollowTheCarriedRider() {
        Run run = new Run(TestTowers.tower().level(0, "U").level(1, "u").object(0, 0, 0, ObjectType.ZAP)
                .object(7, 7, 0, ObjectType.PSH).build(), Map.of(1, new Command.Back(Z), 17, new Command.Back(Z)));
        for (int i = 1; i <= 17; i++) {
            run.step(Z.slot());
        }
        MotionCarry carry = run.driver.carry();
        assertEquals(36, carry.y(Z.slot()), "ease(16) + ease(17) = 18 + 18");
        Lift lift = run.driver.simulation().world().liftAt(new CellPos(4, 11, 0)).orElseThrow();

        for (float alpha : new float[] {0f, 0.5f, END}) {
            Pose drawn = run.drawn(Z.slot(), alpha);
            assertEquals(drawn.y(), LiftHeights.ratio(lift, run.driver.simulation().world(), alpha, carry), EPS);
            SpiderPick.Ray down = new SpiderPick.Ray(drawn.x(), drawn.y() + SpiderPick.HEIGHT - 0.01f, drawn.z(), 0f,
                    -1f, 0f);
            assertEquals(Optional.of(Z), SpiderPick.pick(run.driver.simulation().world(), alpha, carry,
                    SpiderId.PUSHER, down));
        }
        assertEquals(274 / 512f, run.drawn(Z.slot(), 0f).y(), EPS, "where it was drawn before the step: 256 + 18");
    }

    @Test
    void aWalkReversedByBackIsDrawnWithoutAJump() {
        // zapper at (0,0) facing north on a column of three floors; Back in step 9 reverses the walk (counter 24)
        Run run = new Run(TestTowers.tower().level(0, "F", "F", "F").object(0, 0, 0, ObjectType.ZAP).build(),
                Map.of(1, new Command.Forward(Z), 9, new Command.Back(Z)));

        Boundary[] boundaries = runContinuous(run, Z.slot(), 30);

        assertEquals(32f / 512f, boundaries[9].oldJump(), EPS, "the scenario reproduces the snap");
        assertEquals(TestTowers.pos(0, 0, 0), run.object(Z.slot()).cell(), "back at the start");
    }

    @Test
    void aWormReversedByAZapIsDrawnWithoutAJump() {
        // worm E/W at (1,0) (slot 3), zapper at (0,0): turned east in READY, it ends READY with a shot at the standing
        // worm (absorbed) and fires again while the worm walks east
        Run run = new Run(TestTowers.tower().level(0, "FFF").object(1, 0, 0, ObjectType.WOE)
                .object(0, 0, 0, ObjectType.ZAP).build(),
                Map.of(1, new Command.TurnRight(Z), 5, new Command.Fire(Z), 12, new Command.Fire(Z)));
        int worm = 3;
        int reversal = -1;
        Boundary[] boundaries = new Boundary[21];

        for (int i = 1; i <= 20; i++) {
            Direction before = run.object(worm).moveDirection();
            boundaries[i] = runContinuous(run, worm, 1)[1];
            if (reversal < 0 && before == Direction.EAST && run.object(worm).moveDirection() == Direction.WEST) {
                reversal = i;
            }
        }

        assertEquals(12, reversal, "the zap reversed the walk");
        assertEquals(MoveKind.WALK, run.object(worm).moveKind());
        assertEquals(32f / 512f, boundaries[reversal].oldJump(), EPS, "the scenario reproduces the snap");
    }

    @Test
    void withoutAReversalTheCarryIsZeroAndTheDrawnPositionsAreUnchanged() {
        TowerDefinition walkAndRide = TestTowers.tower().level(0, "FFF", "FF", "FU").level(1, ".u")
                .object(0, 0, 0, ObjectType.ZAP).object(1, 1, 0, ObjectType.PSH).object(2, 2, 0, ObjectType.WOE)
                .build();
        TowerDefinition push = TestTowers.tower().level(0, "F", "F", "F").object(0, 0, 0, ObjectType.PSH)
                .object(0, 1, 0, ObjectType.BLK).build();
        // the zapper turns east in READY, walks onto the lift and rides it up; the worm walks to and fro meanwhile
        Run walking = new Run(walkAndRide, Map.of(1, new Command.TurnRight(Z), 5, new Command.Forward(Z),
                40, new Command.Back(Z)));
        assertEquals(2, assertUnchanged(walking, 90), "the walk and the ride start from standing");
        assertEquals(512, walking.object(Z.slot()).worldY(), "the zapper rode up");
        Run pushing = new Run(push, Map.of(1, new Command.Fire(SpiderId.PUSHER)));
        assertUnchanged(pushing, 30);
        assertEquals(TestTowers.pos(0, 2, 0), pushing.object(3).cell(), "the block was pushed");
    }

    /**
     * Every carry is 0 and every drawn position unchanged, except a spider's move that starts from standing (Task 16
     * fix B): that one is carried, and drawn without a jump.
     *
     * @return the spider starts from standing seen
     */
    private static int assertUnchanged(Run run, int steps) {
        int moving = 0;
        int spiderStarts = 0;
        for (int i = 1; i <= steps; i++) {
            List<SimObject> objects = run.driver.simulation().world().objects();
            boolean[] stood = new boolean[objects.size()];
            Pose[] end = new Pose[objects.size()];
            for (SimObject object : objects) {
                stood[object.slot()] = !object.isMoving();
                end[object.slot()] = run.drawn(object.slot(), END);
            }
            run.step(0);
            for (SimObject object : objects) {
                int slot = object.slot();
                if (object.isSpider() && stood[slot] && object.isMoving()) {
                    spiderStarts++;
                    assertTrue(distance(end[slot], run.drawn(slot, 0f)) <= EPS, "step " + i + " slot " + slot);
                    continue;
                }
                assertEquals(0, run.driver.carry().x(slot), "step " + i + " slot " + slot);
                assertEquals(0, run.driver.carry().y(slot), "step " + i + " slot " + slot);
                assertEquals(0, run.driver.carry().z(slot), "step " + i + " slot " + slot);
                moving += object.isMoving() ? 1 : 0;
                for (float alpha : new float[] {0f, 0.25f, 0.5f, END}) {
                    assertEquals(run.drawnBefore(slot, alpha), run.drawn(slot, alpha), "step " + i + " slot " + slot);
                }
            }
        }
        assertTrue(moving >= 10, "the scenario moves things");
        return spiderStarts;
    }

    @Test
    void aSwapIsNotCarried() {
        // grabber at (0,0) turned east grabs the exchanger at (3,0): the two swap cells (a teleport, not a reversal)
        Run run = new Run(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.GRB)
                .object(3, 0, 0, ObjectType.EXC).conditions(0, false, 0, 1).build(),
                Map.of(1, new Command.TurnRight(G), 5, new Command.Fire(G)));
        int startX = run.object(G.slot()).worldX();

        for (int i = 1; i <= 5; i++) {
            run.step(G.slot());
        }

        assertNotEquals(startX, run.object(G.slot()).worldX(), "the grabber was swapped");
        assertEquals(0, run.driver.carry().x(G.slot()));
        assertEquals(0, run.driver.carry().x(3));
        assertEquals(run.drawnBefore(G.slot(), 0f), run.drawn(G.slot(), 0f));
    }

    @Test
    void theDriverWithoutStepsCarriesNothing() {
        SimDriver driver = new SimDriver(Simulation.start(TestTowers.tower().level(0, "F")
                .object(0, 0, 0, ObjectType.ZAP).build(), SimOptions.DEFAULT), sim -> Optional.empty(), (e, t) -> {
                });
        assertEquals(0, driver.carry().x(Z.slot()));
        assertEquals(0, MotionCarry.NONE.y(31));
    }
}
