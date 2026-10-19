package de.latzig.game.tob.client.logic.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.io.TowerLibraryLoader;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.TowerAssets;

class DemoPlayerTest {

    /** Runs {@code frames} steps; returns the command of every step (empty where none was sent). */
    private static List<Optional<Command>> run(Simulation sim, DemoPlayer player, int frames) {
        List<Optional<Command>> sent = new ArrayList<>();
        for (int i = 0; i < frames && !sim.isFinished(); i++) {
            Optional<Command> command = player.next(sim);
            sent.add(command);
            player.afterStep(sim.step(command.map(List::of).orElse(List.of())));
        }
        return sent;
    }

    @Test
    void theTableHoldsTheOriginalScripts() {
        assertEquals(18, DemoScripts.ALL.size());
        assertEquals(List.of(1, 2, 5, 4, 5, 4, 2, 1, 3, 5, 1, 1, 1, 1, 1), DemoScripts.script(17).commands(),
                "Grabber Demo: F+ B+ X R X R B F- L X F- F- F F F");
        assertEquals(List.of(6, 5, 4, 1, 1, 2, 1, 3, 5, 4, 1, 1, 2, 1, 1), DemoScripts.script(5).commands());
        assertEquals(List.of(17, 12, 13, 14, 15, 16), DemoScripts.SEQUENCE);
    }

    @Test
    void aMoveIsFedOnlyWhileTheSpiderStandsAndTheScriptEndsAtZero() {
        TowerDefinition tower = TestTowers.tower().level(0, "F", "F", "F").object(0, 0, 0, ObjectType.PSH).build();
        Simulation sim = Simulation.start(tower, new SimOptions(true));
        DemoPlayer player = new DemoPlayer(DemoScript.of(0, "test", "01 01 00"));

        List<Optional<Command>> sent = run(sim, player, 80);

        assertEquals(SpiderId.PUSHER, player.spider(), "the first present spider");
        assertEquals(Optional.of(new Command.Forward(SpiderId.PUSHER)), sent.get(0));
        assertEquals(2, sent.stream().filter(Optional::isPresent).count(), "the second forward waits for the walk");
        int second = sent.subList(1, sent.size()).indexOf(Optional.of(new Command.Forward(SpiderId.PUSHER))) + 1;
        assertTrue(second >= 32, "the walk takes 32 frames: " + second);
        assertTrue(player.isFinished());
        assertEquals(2, player.step());
    }

    /** Phase 5c-2 (report 17 §5, W001696): the byte read last drives the interludes' dolly; 0 after the end. */
    @Test
    void theLastByteReadIsKeptForTheDollyAndIsZeroAfterTheEnd() {
        TowerDefinition tower = TestTowers.tower().level(0, "F", "F", "F").object(0, 0, 0, ObjectType.PSH).build();
        Simulation sim = Simulation.start(tower, new SimOptions(true));
        DemoPlayer player = new DemoPlayer(DemoScript.of(0, "test", "41 C1 00"));

        assertEquals(0, player.lastByte(), "nothing read yet");
        run(sim, player, 1);
        assertEquals((byte) 0x41, player.lastByte());
        run(sim, player, 20);
        assertEquals((byte) 0x41, player.lastByte(), "kept while the spider walks");
        run(sim, player, 20);
        assertEquals((byte) 0xC1, player.lastByte(), "the next byte after the walk");
        run(sim, player, 40);
        assertTrue(player.isFinished());
        assertEquals(0, player.lastByte(), "the terminating 0");
    }

    @Test
    void theWaitCodeHoldsSixteenFramesAndATurnHoldsTheNextFireUntilTheThrottleRunsOut() {
        TowerDefinition tower = TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP).build();
        Simulation sim = Simulation.start(tower, new SimOptions(true));
        DemoPlayer player = new DemoPlayer(DemoScript.of(0, "test", "06 04 05 00"));

        List<Optional<Command>> sent = run(sim, player, 40);

        int turn = sent.indexOf(Optional.of(new Command.TurnRight(SpiderId.ZAPPER)));
        int fire = sent.indexOf(Optional.of(new Command.Fire(SpiderId.ZAPPER)));
        assertEquals(16, turn, "read on the 17th frame after the wait");
        assertEquals(turn + 4, fire, "the original fires the next frame; core.sim's turn throttle needs 4");
        assertTrue(player.isFinished());
    }

    @Test
    void theOriginalScriptsWorkWithTheSpidersFacingNorth() throws IOException {
        assumeTrue(TowerAssets.available(), "converted towers not available");
        List<TowerDefinition> demos = new TowerLibraryLoader().load(TowerAssets.directory().resolve("demo.json"))
                .playableTowers();

        for (int demo = 6; demo <= 17; demo++) {
            Simulation sim = Simulation.start(demos.get(demo), new SimOptions(true));
            DemoPlayer player = new DemoPlayer(DemoScripts.script(demo));
            int moves = 0;
            int sentMoves = 0;
            for (int frame = 0; frame < 600 && !sim.isFinished(); frame++) {
                Optional<Command> command = player.next(sim);
                if (command.isPresent() && (command.get() instanceof Command.Forward
                        || command.get() instanceof Command.Back)) {
                    sentMoves++;
                }
                List<SimEvent> events = sim.step(command.map(List::of).orElse(List.of()));
                player.afterStep(events);
                for (SimEvent event : events) {
                    if (event instanceof SimEvent.MoveStarted move && move.slot() == player.spider().slot()
                            || event instanceof SimEvent.LiftRideStarted ride
                                    && ride.slot() == player.spider().slot()) {
                        moves++;
                    }
                }
            }
            boolean alive = sim.world().spider(player.spider()).isAlive();
            String name = demo + " " + DemoScripts.script(demo).title();
            assertTrue(moves >= sentMoves - 2 && moves > 0, name + ": " + moves + " of " + sentMoves + " moves");
            if (demo <= 11) {
                assertFalse(alive, name + ": a failure interlude kills its spider");
            } else {
                assertTrue(alive, name + ": the Demo Sequence spider survives");
                assertTrue(player.isFinished(), name + ": the script ends");
            }
        }
    }
}
