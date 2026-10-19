package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.MeshFrame;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.SimObject;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

class AnimationStateTest {

    private static final float EPS = 1e-4f;
    private static final float PI = (float) Math.PI;

    @Test
    void aSpiderWalkPlaysNorthAndEastForwardAndSouthAndWestBackwards() {
        assertEquals(MeshFrame.of(1, 16f), AnimationState.spiderWalk(Direction.NORTH, 31, 0f));
        assertEquals(MeshFrame.of(1, 496f), AnimationState.spiderWalk(Direction.SOUTH, 31, 0f));
        assertEquals(MeshFrame.of(2, 24f), AnimationState.spiderWalk(Direction.EAST, 31, 0.5f));
        assertEquals(MeshFrame.of(2, 488f), AnimationState.spiderWalk(Direction.WEST, 31, 0.5f));
        assertEquals(MeshFrame.of(1, 512f), AnimationState.spiderWalk(Direction.NORTH, 0, 0.9f));
    }

    @Test
    void standingPlaysFiveHundredTwelveToSixHundredFortyOnceAndHolds() {
        assertEquals(MeshFrame.of(1, 512f), AnimationState.spiderStand(0, 0f));
        assertEquals(MeshFrame.of(1, 584f), AnimationState.spiderStand(4, 0.5f));
        assertEquals(MeshFrame.of(1, 640f), AnimationState.spiderStand(8, 0f));
        assertEquals(MeshFrame.of(1, 640f), AnimationState.spiderStand(500, 0.7f));
    }

    @Test
    void aSpiderWalksOneFieldAndThenStandsFromTheEndOfTheWalk() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFF", "FFF")
                .object(1, 0, 0, ObjectType.ZAP).build(), SimOptions.DEFAULT);
        AnimationState animation = new AnimationState(sim.world());
        SimObject zap = sim.world().spider(SpiderId.ZAPPER);
        assertEquals(MeshFrame.of(1, 512f), animation.frame(zap, 0f));

        animation.onStep(sim.step(List.of(new Command.Forward(SpiderId.ZAPPER))), 1);
        assertEquals(MeshFrame.of(1, 16f), animation.frame(zap, 0f));
        for (int tick = 2; tick <= 32; tick++) {
            animation.onStep(sim.step(List.of()), tick);
        }
        assertEquals(MeshFrame.of(1, 512f), animation.frame(zap, 0f), "the walk ended at tick 32");
        for (int tick = 33; tick <= 40; tick++) {
            animation.onStep(sim.step(List.of()), tick);
        }
        assertEquals(MeshFrame.of(1, 640f), animation.frame(zap, 0.5f));
    }

    @Test
    void aRotatingEnemyHoldsItsAngleWhileFrozenAndSnapsOnThaw() {
        assertEquals(0f, AnimationState.rotationKey(8, 0f), EPS);
        assertEquals(16f, AnimationState.rotationKey(8, 0.5f), EPS);
        assertEquals(32f * 31 + 16f, AnimationState.rotationKey(63, 0.5f), EPS);
        FreezeClock clock = new FreezeClock();
        clock.update(false, 10);
        clock.update(true, 11);
        clock.update(true, 40);
        assertEquals(10, clock.frame());
        clock.update(false, 41);
        assertEquals(41, clock.frame());
    }

    @Test
    void aWatcherTurnsAQuarterTheShortWayAndHalfCounterClockwise() {
        WatcherTurns turns = new WatcherTurns();
        assertEquals(PI, turns.heading(5, Direction.SOUTH, 0, 0f), EPS);

        turns.start(5, Direction.NORTH, Direction.WEST, 4);
        assertEquals(0f, turns.heading(5, Direction.WEST, 4, 0f), EPS);
        assertEquals(-PI / 2 * 2.5f / 4f, turns.heading(5, Direction.WEST, 2, 0.5f), EPS);
        assertEquals(3 * PI / 2, turns.heading(5, Direction.WEST, 0, 0.5f), EPS);

        turns.start(5, Direction.EAST, Direction.SOUTH, 4);
        assertEquals(PI / 2 + PI / 4, turns.heading(5, Direction.SOUTH, 2, 0f), EPS);

        turns.start(5, Direction.NORTH, Direction.SOUTH, 8);
        assertEquals(-PI / 2, turns.heading(5, Direction.SOUTH, 4, 0f), EPS);
    }

    @Test
    void aTimeBombShowsTheGroupOfItsSpeedAndItsFuse() {
        assertEquals(MeshFrame.of(1, 1600f), AnimationState.timeBomb(0, 100, 0f));
        assertEquals(MeshFrame.of(2, 808f), AnimationState.timeBomb(1, 100, 0.5f));
        assertEquals(3, AnimationState.timeBomb(2, 0, 0f).group());
        assertEquals(3, AnimationState.timeBomb(3, 0, 0f).group());
    }
}
