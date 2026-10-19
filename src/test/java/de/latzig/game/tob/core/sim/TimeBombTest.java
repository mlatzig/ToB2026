package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.element.TimeBombBehaviour;

/** Time bomb (class 9, {@code L00B6BC}, {@code S:9724-9781}; oracle §5.2, Phase 2d spec §3.2). */
class TimeBombTest {

    private static final SpiderId P = SpiderId.PUSHER;

    /** F (0..3,0): pusher (0,0), bomb (1,0) slot 3; start time by the zapper at (7,7), so the bomb first acts in frame 1. */
    private static SimRun bomb(int speed) {
        return SimRun.startTime(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.PSH)
                .object(1, 0, 0, ObjectType.TMB).timeBombSpeed(speed));
    }

    @ParameterizedTest
    @CsvSource({"0, 800", "1, 400", "2, 267", "3, 200"})
    void theFuseGrowsBySpeedPlusOneInEveryFrameAndExplodesAt800(int speed, int frame) {
        SimRun run = bomb(speed);
        run.runUntilFrame(frame - 1);
        SimObject bomb = run.sim.world().object(3);
        assertTrue(bomb.isAlive());
        assertEquals((frame - 1) * (speed + 1), bomb.classTimer());
        assertTrue(run.events(SimEvent.Exploded.class).isEmpty());

        run.runUntilFrame(frame);

        assertEquals(List.of(new SimEvent.Exploded(frame, 3,
                List.of(pos(0, 0, 0), pos(1, -1, 0), pos(2, 0, 0), pos(1, 1, 0)))), run.events(SimEvent.Exploded.class));
        assertEquals(ObjectStatus.DESTROYING, bomb.status());
        assertEquals(ObjectStatus.DESTROYING, run.sim.world().spider(P).status(), "the pusher at (0,0) is a neighbour");
        assertEquals(List.of(10, 20, 30, 40, 50, 60, 70, 80, 90),
                run.events(SimEvent.FuseTick.class).stream().map(SimEvent.FuseTick::percent).toList());
        assertEquals(0, run.sim.world().counters().robotsDestroyed());
        assertTrue(run.sim.world().hasFloor(pos(1, 0, 0)));
    }

    @Test
    void theFuseStandsStillWhileTheWorldIsFrozen() {
        SimRun run = bomb(3);
        run.runUntilFrame(50);
        run.sim.world().freeze();
        run.runUntilFrame(60);
        run.sim.world().thaw();
        run.runUntilFrame(209);
        assertTrue(run.events(SimEvent.Exploded.class).isEmpty());

        run.runUntilFrame(210);

        assertEquals(210, run.events(SimEvent.Exploded.class).get(0).frame(), "ten frozen frames later");
    }

    @Test
    void theFuseTicksDuringAPushAndTheBlastCentresOnTheCurrentCell() {
        SimRun run = bomb(3);
        run.step(new Command.TurnRight(P));
        run.runUntilFrame(189);
        run.step(new Command.Fire(P));
        run.runUntilFrame(199);
        SimObject bomb = run.sim.world().object(3);
        assertEquals(pos(2, 0, 0), bomb.cell(), "half-way since frame 197");
        assertEquals(6, bomb.moveCounter());

        run.runUntilFrame(200);

        assertEquals(List.of(new SimEvent.Exploded(200, 3,
                List.of(pos(1, 0, 0), pos(2, -1, 0), pos(3, 0, 0), pos(2, 1, 0)))), run.events(SimEvent.Exploded.class));
        assertTrue(run.sim.world().spider(P).isAlive(), "(0,0) is no neighbour of the current cell (2,0)");
        assertEquals(0, bomb.moveCounter(), "the destruction stops the push");
        assertTrue(run.sim.world().occupancy().isFree(pos(1, 0, 0)), "the old cell is released");
        assertEquals(TimeBombBehaviour.FUSE, bomb.classTimer());
    }
}
