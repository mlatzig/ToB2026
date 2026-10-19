package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

class SimDriverTest {

    private final List<Integer> ticks = new ArrayList<>();

    private static Simulation simulation() {
        return Simulation.start(TestTowers.tower().level(0, "FFF").object(1, 0, 0, ObjectType.ZAP).build(),
                SimOptions.DEFAULT);
    }

    private SimDriver driver(CommandSource source) {
        return new SimDriver(simulation(), source, (events, tick) -> ticks.add(tick));
    }

    @Test
    void runsAtTheOriginalRateOfFiftySeventhsFramesPerSecond() {
        SimDriver driver = driver(sim -> Optional.empty());

        for (int i = 0; i < 60; i++) {
            driver.update(1.0 / 60.0);
        }

        assertEquals(7, driver.ticks(), "1 s = 7.14 frames");
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), ticks);
        assertEquals((1.0 - 7 * SimDriver.FRAME_SECONDS) / SimDriver.FRAME_SECONDS, driver.alpha(), 1e-4);
    }

    @Test
    void alphaStaysWithinZeroInclusiveAndOneExclusive() {
        SimDriver driver = driver(sim -> Optional.empty());
        Random random = new Random(20261008L);

        for (int i = 0; i < 500; i++) {
            driver.update(random.nextDouble() * 0.3);
            float alpha = driver.alpha();
            assertTrue(alpha >= 0f && alpha < 1f, "alpha " + alpha);
        }
    }

    @Test
    void takesAtMostOneCommandPerFrameInQueueOrder() {
        Deque<Command> queue = new ArrayDeque<>(List.of(new Command.TurnLeft(SpiderId.ZAPPER),
                new Command.Fire(SpiderId.ZAPPER), new Command.TurnRight(SpiderId.ZAPPER)));
        List<Command> handedOut = new ArrayList<>();
        int[] calls = {0};
        SimDriver driver = driver(sim -> {
            calls[0]++;
            Optional<Command> next = Optional.ofNullable(queue.poll());
            next.ifPresent(handedOut::add);
            return next;
        });

        driver.update(SimDriver.FRAME_SECONDS * 2.5);

        assertEquals(2, driver.ticks());
        assertEquals(2, calls[0], "one request per frame");
        assertEquals(List.of(new Command.TurnLeft(SpiderId.ZAPPER), new Command.Fire(SpiderId.ZAPPER)), handedOut);
        assertEquals(1, queue.size(), "the third command waits for the next frame");
    }

    @Test
    void pausingHoldsTheSimulationAndTheFraction() {
        SimDriver driver = driver(sim -> Optional.empty());
        driver.update(SimDriver.FRAME_SECONDS * 1.5);
        float alpha = driver.alpha();

        driver.setPaused(true);
        assertEquals(0, driver.update(1.0));

        assertEquals(1, driver.ticks());
        assertEquals(alpha, driver.alpha());
    }

    @Test
    void aStallRunsAtMostFourFramesAndDropsTheBacklog() {
        SimDriver driver = driver(sim -> Optional.empty());

        assertEquals(SimDriver.MAX_STEPS_PER_UPDATE, driver.update(10.0));

        assertTrue(driver.alpha() < 1f);
        assertEquals(1, driver.update(SimDriver.FRAME_SECONDS));
    }

    @Test
    void stopsOnceTheAttemptHasFinished() {
        SimDriver driver = driver(sim -> Optional.of(new Command.Abandon()));

        assertEquals(1, driver.update(SimDriver.FRAME_SECONDS * 3.5));

        assertTrue(driver.simulation().isFinished());
        assertEquals(0, driver.update(1.0));
        assertEquals(0f, driver.alpha());
    }

    @Test
    void anExceptionInAFrameNamesTheTowerAndTheFrame() {
        IllegalStateException boom = new IllegalStateException("boom");
        SimDriver driver = driver(sim -> {
            throw boom;
        });

        SimFailure failure = assertThrows(SimFailure.class, () -> driver.update(SimDriver.FRAME_SECONDS));

        assertEquals("test-000", failure.towerId());
        assertEquals(0, failure.frame());
        assertSame(boom, failure.getCause());
        assertTrue(failure.getMessage().contains("test-000") && failure.getMessage().contains("frame 0"),
                failure.getMessage());
    }
}
