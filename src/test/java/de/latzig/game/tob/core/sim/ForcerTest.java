package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.BaseType;
import de.latzig.game.tob.core.model.Behaviour;
import de.latzig.game.tob.core.model.Cell;
import de.latzig.game.tob.core.model.Conditions;
import de.latzig.game.tob.core.model.Effects;
import de.latzig.game.tob.core.model.Lock;
import de.latzig.game.tob.core.model.Moon;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.SampleTowers;
import de.latzig.game.tob.core.model.Sounds;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.model.TowerGrid;
import de.latzig.game.tob.core.model.TowerLimits;

/** Forcer up/down (classes $11/$12, {@code S:9023-9101}) and their lift motion ({@code S:9169-9214}; oracle §6.2). */
class ForcerTest {

    private final List<SimEvent> out = new ArrayList<>();
    private WorldState world;
    private SimContext ctx;

    private void start(TestTowers.Builder tower) {
        world = WorldState.load(tower.build(), SimOptions.DEFAULT);
        ctx = new SimContext(world);
        ctx.begin(out);
        world.thaw();
    }

    private void advance(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            world.forcerLifts().advance(world, out);
        }
    }

    @Test
    void forcerUpRaisesEveryFreeBottomPlatformAndBlocksItsLowerCellAfterThirtyTwoFrames() {
        // level 0: U (0,0) free, U (2,0) with a block (slot 3); level 1: their up stops
        start(TestTowers.tower().level(0, "U.U").level(1, "u.u").object(2, 0, 0, ObjectType.BLK));

        assertTrue(ctx.startForcerLifts(true));
        assertFalse(ctx.startForcerLifts(false), "the lock is global and shared by up and down");

        assertEquals(List.of(new SimEvent.ForcerLiftStarted(0, pos(0, 0, 0), true)), out);
        assertTrue(world.hasFloor(pos(0, 0, 1)), "the floor above appears at once");
        assertEquals(Occupancy.FORCER_LIFT, world.occupancy().holder(pos(0, 0, 0)));
        assertEquals(Occupancy.FORCER_LIFT, world.occupancy().holder(pos(0, 0, 1)));
        assertEquals(LiftState.MOVING_UP, world.liftAt(pos(0, 0, 0)).orElseThrow().state());
        assertEquals(List.of(), world.occupancyProblems());
        advance(31);
        assertTrue(world.forcerLifts().isActive());
        assertFalse(world.isBlocked(pos(0, 0, 0)));
        out.clear();

        advance(1);

        assertEquals(List.of(new SimEvent.ForcerLiftFinished(0, pos(0, 0, 0))), out);
        assertFalse(world.forcerLifts().isActive());
        assertTrue(world.occupancy().isFree(pos(0, 0, 0)));
        assertTrue(world.occupancy().isFree(pos(0, 0, 1)));
        assertEquals(CellFlags.of(BaseType.LIFT_DOWN_STOP), world.flags(pos(0, 0, 0)));
        assertEquals(CellFlags.of(BaseType.LIFT_DOWN_START), world.flags(pos(0, 0, 1)));
        assertEquals(LiftState.TOP, world.liftAt(pos(0, 0, 0)).orElseThrow().state());
        assertEquals(LiftState.BOTTOM, world.liftAt(pos(2, 0, 0)).orElseThrow().state(), "occupied lifts never move");
        assertFalse(world.hasFloor(pos(2, 0, 1)));
        assertEquals(List.of(), world.occupancyProblems());
    }

    @Test
    void forcerDownLowersEveryFreeTopPlatformAndRemovesItsFloorAfterThirtyTwoFrames() {
        // level 1: D (0,0,1) free, D (2,0,1) with a block (slot 3); level 0: their down stops
        start(TestTowers.tower().level(0, "d.d").level(1, "D.D").object(2, 0, 1, ObjectType.BLK));

        assertTrue(ctx.startForcerLifts(false));

        assertEquals(List.of(new SimEvent.ForcerLiftStarted(0, pos(0, 0, 1), false)), out);
        assertFalse(world.isBlocked(pos(0, 0, 0)), "the lower cell is unblocked at once");
        assertEquals(Occupancy.FORCER_LIFT, world.occupancy().holder(pos(0, 0, 0)));
        assertEquals(LiftState.MOVING_DOWN, world.liftAt(pos(0, 0, 1)).orElseThrow().state());
        advance(32);
        assertEquals(CellFlags.of(BaseType.LIFT_UP_START), world.flags(pos(0, 0, 0)));
        assertEquals(CellFlags.of(BaseType.LIFT_UP_STOP), world.flags(pos(0, 0, 1)));
        assertEquals(LiftState.BOTTOM, world.liftAt(pos(0, 0, 1)).orElseThrow().state());
        assertEquals(LiftState.TOP, world.liftAt(pos(2, 0, 1)).orElseThrow().state(), "occupied lifts never move");
        assertTrue(world.isBlocked(pos(2, 0, 0)));
        assertEquals(List.of(), world.occupancyProblems());
    }

    @Test
    void theLockHoldsForThirtyTwoFramesEvenWhenNoLiftQualified() {
        start(TestTowers.tower().level(0, "F"));

        assertTrue(ctx.startForcerLifts(true), "a grab without a lift is still a hit (S:9058)");
        assertTrue(out.isEmpty());
        advance(31);
        assertFalse(ctx.startForcerLifts(false));
        advance(1);
        assertTrue(ctx.startForcerLifts(false));
    }

    @Test
    void atMostTwentyLiftsMoveInListOrder() {
        // 24 up-lifts on rows z=0..2 of level 0, their stops on level 1
        start(TestTowers.tower().level(0, "UUUUUUUU", "UUUUUUUU", "UUUUUUUU")
                .level(1, "uuuuuuuu", "uuuuuuuu", "uuuuuuuu"));

        ctx.startForcerLifts(true);

        List<CellPos> platforms = world.forcerLifts().platforms();
        assertEquals(ForcerLifts.MAX_LIFTS, platforms.size());
        assertEquals(pos(0, 0, 0), platforms.get(0), "south row first");
        assertEquals(pos(3, 2, 0), platforms.get(19));
        assertEquals(LiftState.BOTTOM, world.liftAt(pos(4, 2, 0)).orElseThrow().state());
    }

    /** Phase 6a: a tower beyond the 8 x 8 x 4 area raises {@link ForcerLifts}' limit from 20 to 64. */
    @Test
    void aLargeTowerMovesMoreThanTwentyLiftsAtOnce() {
        // two full rows of up-lifts (level 0 -> 1), 16 columns each: 32 qualifying lifts, well beyond 20
        TowerGrid.Builder builder = TowerGrid.builder();
        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x < TowerGrid.WIDTH; x++) {
                builder.set(x, y, 0, new Cell(BaseType.LIFT_UP_START, null));
                builder.set(x, y, 1, new Cell(BaseType.LIFT_UP_STOP, null));
            }
        }
        TowerGrid grid = builder.build();
        assertEquals(TowerLimits.MAX_LIFTS_LARGE, TowerLimits.maxLifts(grid), "the grid reaches beyond the area");
        TowerDefinition tower = new TowerDefinition("large-test", 0, 0, "Test", "", new Conditions(0, false, 0, 0),
                new Behaviour(false, false, 0), new Effects(false, false, false, 0, 0, 0), new Moon(0, 0, 0, 0, 0),
                SampleTowers.palette(), new Sounds(0, false, false), new Lock(false, ""), grid, Optional.empty());
        world = WorldState.load(tower, SimOptions.DEFAULT);
        ctx = new SimContext(world);
        ctx.begin(out);
        world.thaw();

        assertTrue(ctx.startForcerLifts(true));

        assertEquals(2 * TowerGrid.WIDTH, world.forcerLifts().platforms().size(), "all 32 lifts move, not just 20");
    }

    /** Phase 6a (final review M2): the 64 cap itself bites, not just the raise beyond 20. */
    @Test
    void sixtyFourOfEightyLiftsMoveInALargeTower() {
        // five full rows of up-lifts (level 0 -> 1), 16 columns each: 80 qualifying lifts, well beyond 64
        TowerGrid.Builder builder = TowerGrid.builder();
        for (int y = 0; y <= 4; y++) {
            for (int x = 0; x < TowerGrid.WIDTH; x++) {
                builder.set(x, y, 0, new Cell(BaseType.LIFT_UP_START, null));
                builder.set(x, y, 1, new Cell(BaseType.LIFT_UP_STOP, null));
            }
        }
        TowerGrid grid = builder.build();
        assertEquals(TowerLimits.MAX_LIFTS_LARGE, TowerLimits.maxLifts(grid), "the grid reaches beyond the area");
        TowerDefinition tower = new TowerDefinition("large-test", 0, 0, "Test", "", new Conditions(0, false, 0, 0),
                new Behaviour(false, false, 0), new Effects(false, false, false, 0, 0, 0), new Moon(0, 0, 0, 0, 0),
                SampleTowers.palette(), new Sounds(0, false, false), new Lock(false, ""), grid, Optional.empty());
        world = WorldState.load(tower, SimOptions.DEFAULT);
        ctx = new SimContext(world);
        ctx.begin(out);
        world.thaw();

        assertTrue(ctx.startForcerLifts(true));

        List<CellPos> platforms = world.forcerLifts().platforms();
        assertEquals(TowerLimits.MAX_LIFTS_LARGE, platforms.size(), "exactly 64 of the 80 qualifying lifts move");
        assertEquals(new CellPos(0, 4, 0), platforms.get(0), "highest row scanned first");
        assertEquals(new CellPos(15, 1, 0), platforms.get(63), "the cap bites mid-row");
        assertEquals(LiftState.BOTTOM, world.liftAt(new CellPos(0, 0, 0)).orElseThrow().state(),
                "the last row stays put");
    }

    @Test
    void occupancyProblemsAcceptForcerCellsOnlyWhileTheLiftMoves() {
        start(TestTowers.tower().level(0, "FU").level(1, ".u"));

        world.occupancy().set(pos(0, 0, 0), Occupancy.FORCER_LIFT);

        assertEquals(List.of("cell " + pos(0, 0, 0) + " held by a forcer lift that does not move there"),
                world.occupancyProblems());
    }

    @Test
    void aZapPassesAMovingForcerLiftWhileAPushOrAGrabEndsThere() {
        // F (0,0), U (1,0) with its stop, F (2,0) with a wiper (slot 3), F (3,0); zapper (0,0); 2 robots
        start(TestTowers.tower().level(0, "FUFF").level(1, ".u").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.WIP).conditions(0, false, 0, 2));
        SimObject zap = world.spider(SpiderId.ZAPPER);
        ctx.startForcerLifts(true);
        out.clear();

        assertFalse(Ray.fire(ctx, zap, Direction.EAST, RayType.PUSH));
        assertFalse(Ray.fire(ctx, zap, Direction.EAST, RayType.GRAB));
        assertTrue(Ray.fire(ctx, zap, Direction.EAST, RayType.ZAP));

        assertEquals(List.of(
                new SimEvent.RayFired(0, 0, RayType.PUSH, List.of(new RayStep(pos(1, 0, 0), Direction.EAST,
                        RayType.PUSH))),
                new SimEvent.RayFired(0, 0, RayType.GRAB, List.of(new RayStep(pos(1, 0, 0), Direction.EAST,
                        RayType.GRAB))),
                new SimEvent.RayFired(0, 0, RayType.ZAP, List.of(new RayStep(pos(1, 0, 0), Direction.EAST, RayType.ZAP),
                        new RayStep(pos(2, 0, 0), Direction.EAST, RayType.ZAP))),
                new SimEvent.Destroyed(0, 3, pos(2, 0, 0)), new SimEvent.CounterChanged(0, CounterKind.ROBOTS, 1),
                new SimEvent.RayHit(0, 0, 3, RayOutcome.DESTROYED)), out);
    }

    @Test
    void aGrabStartsTheMotionInItsFrameAndAFreezeHoldsIt() {
        // z=2 "F..", z=1 "F..", z=0 "F.U": grabber (0,0) facing N, forcer up (0,2) slot 3; up-lift (2,0) + stop
        SimRun run = SimRun.startTime(TestTowers.tower().level(0, "F..", "F..", "F.U").level(1, "..u")
                .object(0, 0, 0, ObjectType.GRB).object(0, 2, 0, ObjectType.FCU));
        run.runUntilFrame(9);

        List<SimEvent> frame10 = run.step(new Command.Fire(SpiderId.GRABBER));

        assertTrue(frame10.contains(new SimEvent.ForcerLiftStarted(10, pos(2, 0, 0), true)));
        assertTrue(frame10.contains(new SimEvent.RayHit(10, 2, 3, RayOutcome.GRABBED)));
        assertEquals(31, run.sim.world().forcerLifts().timer(), "step 3c ran in frame 10");
        run.runUntilFrame(20);
        run.sim.world().freeze();
        run.runUntilFrame(30);
        run.sim.world().thaw();
        run.runUntilFrame(50);
        assertTrue(run.sim.world().forcerLifts().isActive(), "ten frozen frames later than frame 41");
        run.runUntilFrame(51);
        assertEquals(List.of(new SimEvent.ForcerLiftFinished(51, pos(2, 0, 0))),
                run.events(SimEvent.ForcerLiftFinished.class));
    }

    @Test
    void forcerUpSkipsALiftWhoseFarCellIsHeldWhileAnotherLiftStillMoves() {
        // U (0,0) with its stop (0,0,1) held by a block (slot 3, the extension of deviation 2): skipped;
        // U (2,0) with its stop free qualifies normally and moves
        start(TestTowers.tower().level(0, "U.U").level(1, "u.u").object(0, 0, 1, ObjectType.BLK));

        assertTrue(ctx.startForcerLifts(true), "still a hit, even though the first lift is skipped (S:9058)");

        assertEquals(List.of(new SimEvent.ForcerLiftStarted(0, pos(2, 0, 0), true)), out);
        assertEquals(List.of(pos(2, 0, 0)), world.forcerLifts().platforms());
        assertFalse(world.hasFloor(pos(0, 0, 1)), "no floor appears above the skipped lift");
        assertEquals(3, world.occupancy().holder(pos(0, 0, 1)), "the holder of the far cell stays in its cell");
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(0, 0, 0)), "the skipped platform stays free");
        assertEquals(LiftState.BOTTOM, world.liftAt(pos(0, 0, 0)).orElseThrow().state(), "the skipped lift never moves");
        assertEquals(LiftState.MOVING_UP, world.liftAt(pos(2, 0, 0)).orElseThrow().state());
        assertEquals(List.of(), world.occupancyProblems());

        advance(32);

        assertEquals(3, world.occupancy().holder(pos(0, 0, 1)), "still unchanged once the lock runs out");
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(0, 0, 0)));
        assertEquals(LiftState.BOTTOM, world.liftAt(pos(0, 0, 0)).orElseThrow().state());
        assertFalse(world.isBlocked(pos(0, 0, 0)), "no flag change on the skipped platform");
        assertFalse(world.hasFloor(pos(0, 0, 1)), "no flag change on the skipped lift's far cell");
        assertFalse(world.forcerLifts().isActive(), "the other lift finished normally");
        assertEquals(List.of(), world.occupancyProblems());
    }

    @Test
    void forcerDownSkipsALiftWhoseFarCellIsHeldWhileAnotherLiftStillMoves() {
        // D (0,0,1) with its stop (0,0,0) held by a block (slot 3, the extension of deviation 2): skipped;
        // D (2,0,1) with its stop free qualifies normally and moves
        start(TestTowers.tower().level(0, "d.d").level(1, "D.D").object(0, 0, 0, ObjectType.BLK));

        assertTrue(ctx.startForcerLifts(false), "still a hit, even though the first lift is skipped (S:9098)");

        assertEquals(List.of(new SimEvent.ForcerLiftStarted(0, pos(2, 0, 1), false)), out);
        assertEquals(List.of(pos(2, 0, 1)), world.forcerLifts().platforms());
        assertTrue(world.isBlocked(pos(0, 0, 0)), "the skipped lift's lower cell stays blocked");
        assertEquals(3, world.occupancy().holder(pos(0, 0, 0)), "the holder of the far cell stays in its cell");
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(0, 0, 1)), "the skipped platform stays free");
        assertEquals(LiftState.TOP, world.liftAt(pos(0, 0, 1)).orElseThrow().state(), "the skipped lift never moves");
        assertEquals(LiftState.MOVING_DOWN, world.liftAt(pos(2, 0, 1)).orElseThrow().state());
        assertEquals(List.of(), world.occupancyProblems());

        advance(32);

        assertEquals(3, world.occupancy().holder(pos(0, 0, 0)), "still unchanged once the lock runs out");
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(0, 0, 1)));
        assertEquals(LiftState.TOP, world.liftAt(pos(0, 0, 1)).orElseThrow().state());
        assertTrue(world.isBlocked(pos(0, 0, 0)), "no flag change on the skipped lift's far cell");
        assertTrue(world.hasFloor(pos(0, 0, 1)), "no flag change on the skipped platform");
        assertFalse(world.forcerLifts().isActive(), "the other lift finished normally");
        assertEquals(List.of(), world.occupancyProblems());
    }
}
