package de.latzig.game.tob.client.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.frontend.DemoPlayer;
import de.latzig.game.tob.client.logic.frontend.DemoScript;
import de.latzig.game.tob.client.logic.game.AnimationState;
import de.latzig.game.tob.client.logic.game.GameAction;
import de.latzig.game.tob.client.logic.game.InputMapper;
import de.latzig.game.tob.client.logic.game.SimDriver;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.ProgramStep;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.Walk;

/**
 * Task 16 (walk stutter, report {@code _log/stutter/report.md}): a spider walking on over several cells has no stand
 * frame in the simulation, but used to be drawn standing for the whole 140 ms after each walk's last frame (counter 0)
 * and then snapped 16 units. The drawn position is sampled at 60 render frames per second as the game does.
 */
class WalkLookAheadTest {

    private static final double DT = 1.0 / 60.0;
    /** World units one render frame of a walk moves: 16 units per simulation frame. */
    private static final float NORMAL = (float) (16 * DT / SimDriver.FRAME_SECONDS);
    private static final SpiderId Z = SpiderId.ZAPPER;
    private static final SpiderId P = SpiderId.PUSHER;

    /** A column of seven floors, the spider at its south end facing north (z falls as it walks). */
    private static TowerDefinition column(ObjectType spider) {
        return TestTowers.tower().level(0, "F", "F", "F", "F", "F", "F", "F").object(0, 0, 0, spider).build();
    }

    /** Tick, drawn z (world units) and move counter of {@code spider} per render frame until {@code maxTicks}. */
    private static List<float[]> render(SimDriver driver, SpiderId spider, Runnable beforeUpdate, int maxTicks) {
        return render(driver, spider, beforeUpdate, maxTicks, 60 * 30);
    }

    /** As {@link #render(SimDriver, SpiderId, Runnable, int)}, at most {@code frames} render frames. */
    private static List<float[]> render(SimDriver driver, SpiderId spider, Runnable beforeUpdate, int maxTicks,
            int frames) {
        List<float[]> samples = new ArrayList<>();
        for (int i = 0; i < frames && driver.ticks() < maxTicks; i++) {
            beforeUpdate.run();
            driver.update(DT);
            SimObject object = driver.simulation().world().spider(spider);
            float z = Motion.position(object, driver.simulation().world().isFrozen(), driver.alpha(), driver.carry())
                    .z() * Motion.UNITS_PER_FIELD;
            samples.add(new float[] {driver.ticks(), z, object.moveCounter()});
        }
        return samples;
    }

    /** Every render frame moves north by a normal amount: no frozen frame, no snap. */
    private static void assertUniform(List<float[]> samples, String what) {
        int boundaries = 0;
        for (int i = 1; i < samples.size(); i++) {
            float step = samples.get(i - 1)[1] - samples.get(i)[1];
            String at = what + ", render frame " + i + " (tick " + (int) samples.get(i)[0] + ", counter "
                    + (int) samples.get(i)[2] + "): moved " + step + " units, normal " + NORMAL;
            assertTrue(step > NORMAL * 0.5f, "frozen or backwards: " + at);
            assertTrue(step <= NORMAL * 2f + 1e-3f, "snap: " + at);
            boundaries += samples.get(i)[2] == 0 ? 1 : 0;
        }
        assertTrue(boundaries >= 2 * 5, what + ": the run crosses walk boundaries (" + boundaries + " frames)");
    }

    @Test
    void aHeldForwardKeyIsDrawnWithUniformMotionAcrossThreeWalks() {
        Simulation sim = Simulation.start(column(ObjectType.ZAP), SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        SimDriver driver = new SimDriver(sim, mapper, (events, tick) -> {
        });
        mapper.press(GameAction.FORWARD);
        render(driver, Z, () -> mapper.advance(DT), 1);

        List<float[]> samples = render(driver, Z, () -> mapper.advance(DT), 3 * 32 + 2);

        assertUniform(samples, "held Forward");
        assertEquals(TestTowers.pos(0, 3, 0), sim.world().spider(Z).cell(), "three walks done, the fourth started");
    }

    @Test
    void aDemoScriptIsDrawnWithUniformMotionAcrossThreeWalks() {
        Simulation sim = Simulation.start(column(ObjectType.PSH), new SimOptions(true));
        DemoPlayer player = new DemoPlayer(DemoScript.of(0, "test", "01 01 01 01 00"));
        SimDriver driver = new SimDriver(sim, player, (events, tick) -> player.afterStep(events));
        render(driver, P, () -> {
        }, 1);

        List<float[]> samples = render(driver, P, () -> {
        }, 3 * 32 + 2);

        assertUniform(samples, "demo script");
        assertEquals(TestTowers.pos(0, 3, 0), sim.world().spider(P).cell());
    }

    /** A driver that writes four Forward steps into the zapper's program and runs it in step 51. */
    private static SimDriver programDriver(Simulation sim) {
        Map<Integer, Command> script = Map.of(1, new Command.EnterWriteMode(Z), 11,
                new Command.Write(Z, ProgramStep.FORWARD), 21, new Command.Write(Z, ProgramStep.FORWARD), 31,
                new Command.Write(Z, ProgramStep.FORWARD), 41, new Command.Write(Z, ProgramStep.FORWARD), 51,
                new Command.Run(Z));
        int[] tick = {0};
        return new SimDriver(sim, s -> Optional.ofNullable(script.get(++tick[0])), (events, t) -> {
        });
    }

    /** No render frame moves more than twice the normal walk step (a start from standing glides at double speed). */
    private static void assertNoSnap(List<float[]> samples, String what) {
        for (int i = 1; i < samples.size(); i++) {
            float step = Math.abs(samples.get(i - 1)[1] - samples.get(i)[1]);
            assertTrue(step <= NORMAL * 2f + 1e-3f, what + ": snap at render frame " + i + " (tick "
                    + (int) samples.get(i)[0] + "): " + step + " units, normal " + NORMAL);
        }
    }

    @Test
    void aPressInAnyRenderFrameOfTheIntervalStartsTheWalkWithoutASnap() {
        // render frame 9 runs the first step (9 / 60 s > 0.14 s): a press seen there is taken by that very step
        for (int pressAt = 0; pressAt <= 9; pressAt++) {
            Simulation sim = Simulation.start(column(ObjectType.ZAP), SimOptions.DEFAULT);
            InputMapper mapper = new InputMapper();
            SimDriver driver = new SimDriver(sim, mapper, (events, tick) -> {
            });
            List<float[]> samples = new ArrayList<>(render(driver, Z, () -> mapper.advance(DT), 100, pressAt));
            mapper.press(GameAction.FORWARD);
            mapper.release(GameAction.FORWARD);
            samples.addAll(render(driver, Z, () -> mapper.advance(DT), 100, 30));

            assertNoSnap(samples, "press before render frame " + (pressAt + 1));
            assertTrue(sim.world().spider(Z).isMoving(), "the tap started a walk");
        }
    }

    @Test
    void aProgramStartedWithRunStartsItsFirstWalkWithoutASnap() {
        Simulation sim = Simulation.start(column(ObjectType.ZAP), SimOptions.DEFAULT);
        SimDriver driver = programDriver(sim);
        render(driver, Z, () -> {
        }, 45);

        List<float[]> samples = render(driver, Z, () -> {
        }, 60);

        assertNoSnap(samples, "Run");
        assertTrue(sim.world().spider(Z).isMoving(), "the program walks");
    }

    @Test
    void aRunningProgramIsDrawnWithUniformMotionAcrossThreeWalks() {
        Simulation sim = Simulation.start(column(ObjectType.ZAP), SimOptions.DEFAULT);
        SimDriver driver = programDriver(sim);
        render(driver, Z, () -> {
        }, 51);

        List<float[]> samples = render(driver, Z, () -> {
        }, 51 + 3 * 32 + 1);

        assertUniform(samples, "running program");
        assertEquals(TestTowers.pos(0, 3, 0), sim.world().spider(Z).cell());
    }

    @Test
    void aWrongGuessGlidesBackAndLeavesTheSpiderWhereItStands() {
        Simulation sim = Simulation.start(column(ObjectType.ZAP), SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        SimDriver driver = new SimDriver(sim, mapper, (events, tick) -> {
        });
        mapper.press(GameAction.FORWARD);
        List<float[]> samples = new ArrayList<>(render(driver, Z, () -> mapper.advance(DT), 32));
        assertEquals(0, sim.world().spider(Z).moveCounter(), "the first walk has just ended");
        // a few render frames into the boundary interval (drawn walking on), then the key is released
        samples.addAll(render(driver, Z, () -> mapper.advance(DT), 32, 4));
        mapper.release(GameAction.FORWARD);
        samples.addAll(render(driver, Z, () -> mapper.advance(DT), 33));
        assertEquals(-Walk.UNITS_PER_FRAME, driver.carry().z(Z.slot()), "the guess is glided back over this frame");
        samples.addAll(render(driver, Z, () -> mapper.advance(DT), 34));

        SimObject zapper = sim.world().spider(Z);
        assertEquals(TestTowers.pos(0, 1, 0), zapper.cell(), "no second walk");
        assertTrue(Math.abs(driver.carry().z(Z.slot())) <= MotionCarry.MAX_UNITS);
        for (int i = 1; i < samples.size(); i++) {
            float step = Math.abs(samples.get(i - 1)[1] - samples.get(i)[1]);
            assertTrue(step <= NORMAL * 2f + 1e-3f, "snap at render frame " + i + ": " + step);
        }
        float drawn = Motion.position(zapper, false, driver.alpha(), driver.carry()).z() * Motion.UNITS_PER_FIELD;
        assertEquals(zapper.worldZ(), drawn, 1e-3f, "drawn where it stands after the glide back");
    }

    @Test
    void atTheBoundaryThePredictedWalkPlaysAWalkKeyNotAStandKey() {
        Simulation sim = Simulation.start(column(ObjectType.ZAP), SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        AnimationState animation = new AnimationState(sim.world());
        SimDriver driver = new SimDriver(sim, mapper, animation::onStep);
        mapper.press(GameAction.FORWARD);
        render(driver, Z, () -> mapper.advance(DT), 32);
        SimObject zapper = sim.world().spider(Z);
        assertEquals(0, zapper.moveCounter(), "standing between two walks");

        float alpha = driver.alpha();
        assertEquals(Direction.NORTH, driver.carry().walk(Z.slot()));
        assertEquals(AnimationState.spiderWalk(Direction.NORTH, Walk.FRAMES, alpha),
                animation.frame(zapper, alpha, driver.carry()));
        assertTrue(animation.frame(zapper, alpha, driver.carry()).key() < AnimationState.WALK_KEYS);
        assertTrue(animation.frame(zapper, alpha).key() >= AnimationState.WALK_KEYS, "without the look-ahead: stand");
    }

    /** The predicted walk of the zapper for {@code peeked}. */
    private static Direction predicted(Simulation sim, Command peeked) {
        return WalkLookAhead.predict(sim, Optional.ofNullable(peeked))[Z.slot()];
    }

    /** The zapper in the middle of the column, cells free ahead and behind. */
    private static Simulation middle(SimOptions options) {
        return Simulation.start(TestTowers.tower().level(0, "F", "F", "F", "F", "F", "F", "F")
                .object(0, 3, 0, ObjectType.ZAP).build(), options);
    }

    @Test
    void forwardAndBackArePredictedAsWalkStartWouldAcceptThem() {
        assertEquals(Direction.NORTH, predicted(middle(SimOptions.DEFAULT), new Command.Forward(Z)));
        assertEquals(Direction.SOUTH, predicted(middle(new SimOptions(true)), new Command.Back(Z)));
        assertEquals(null, predicted(middle(SimOptions.DEFAULT), new Command.Back(Z)),
                "the cell behind is free, but backwards is not allowed (default off)");
        assertEquals(null, predicted(middle(SimOptions.DEFAULT), new Command.TurnLeft(Z)), "a turn is no walk");
        assertEquals(null, predicted(middle(SimOptions.DEFAULT), new Command.Forward(P)), "another spider's move");
        assertEquals(null, predicted(middle(SimOptions.DEFAULT), null), "nothing peeked, no program");
    }

    @Test
    void noWalkIsPredictedIntoABlockedCellOnALiftOrInWriteMode() {
        Simulation blocked = Simulation.start(TestTowers.tower().level(0, "F", "F").object(0, 0, 0, ObjectType.ZAP)
                .object(0, 1, 0, ObjectType.BLK).build(), SimOptions.DEFAULT);
        assertEquals(null, predicted(blocked, new Command.Forward(Z)), "the cell ahead is held by the block");

        Simulation lift = Simulation.start(TestTowers.tower().level(0, "F", "U", "F").level(1, ".", "u", ".")
                .object(0, 1, 0, ObjectType.ZAP).build(), new SimOptions(true));
        assertEquals(Direction.NORTH, predicted(lift, new Command.Forward(Z)), "off the lift");
        assertEquals(null, predicted(lift, new Command.Back(Z)), "Back on a lift rides it, it does not walk");

        Simulation writing = middle(SimOptions.DEFAULT);
        writing.step(List.of(new Command.EnterWriteMode(Z)));
        assertEquals(null, predicted(writing, new Command.Forward(Z)), "write mode writes the step");
    }

    @Test
    void peekingHasNoSideEffects() {
        Simulation sim = Simulation.start(column(ObjectType.ZAP), SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        mapper.press(GameAction.FORWARD);
        assertEquals(Optional.of(new Command.Forward(Z)), mapper.peek(sim));
        assertEquals(Optional.of(new Command.Forward(Z)), mapper.peek(sim));
        assertEquals(1, mapper.pending());
        assertEquals(Optional.of(new Command.Forward(Z)), mapper.next(sim));
        assertEquals(Optional.of(new Command.Forward(Z)), mapper.peek(sim), "the held key, seen or not");
        assertEquals(Optional.empty(), mapper.next(sim), "the repeat still waits to be seen held");

        DemoPlayer player = new DemoPlayer(DemoScript.of(0, "test", "01 02 00"));
        Simulation demo = Simulation.start(column(ObjectType.PSH), new SimOptions(true));
        assertEquals(Optional.of(new Command.Forward(P)), player.peek(demo));
        assertEquals(0, player.step());
        assertEquals(null, player.spider());
        assertEquals(Optional.of(new Command.Forward(P)), player.next(demo));
        demo.step(List.of(new Command.Forward(P)));
        assertEquals(Optional.empty(), player.peek(demo), "walking: the next byte waits");
        assertEquals(Optional.empty(), new DemoPlayer(DemoScript.of(0, "test", "00")).peek(demo));

        DemoPlayer waiting = new DemoPlayer(DemoScript.of(0, "test", "06 01 00"));
        Simulation idle = Simulation.start(column(ObjectType.PSH), new SimOptions(true));
        assertEquals(Optional.empty(), waiting.peek(idle), "the wait byte sends nothing");
        assertEquals(Optional.empty(), waiting.next(idle));
        assertEquals(Optional.empty(), waiting.peek(idle), "waiting: the Forward behind the wait is not due yet");
    }
}
