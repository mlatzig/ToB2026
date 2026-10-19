package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.client.logic.game.PanelState.Look;
import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.ProgramState;
import de.latzig.game.tob.core.sim.ProgramStep;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;
import de.latzig.game.tob.core.sim.Viewpoint;

class PanelStateTest {

    private static final SpiderId Z = SpiderId.ZAPPER;

    private static Simulation zapperAndPusher(boolean cameras) {
        return Simulation.start(TestTowers.tower().level(0, "FFFF", "FFFF").object(0, 0, 0, ObjectType.ZAP)
                .object(3, 1, 0, ObjectType.PSH).cameras(cameras).build(), SimOptions.DEFAULT);
    }

    /** Steps once with the command, then idles three frames (the write throttle is 4). */
    private static void send(Simulation sim, Command command) {
        sim.step(List.of(command));
        for (int i = 0; i < 3; i++) {
            sim.step(List.of());
        }
    }

    private static List<Look> looks(PanelState state, Simulation sim, Viewpoint shown, PanelButton... buttons) {
        List<Look> looks = new ArrayList<>();
        for (PanelButton button : buttons) {
            looks.add(state.look(button, sim, shown, false));
        }
        return looks;
    }

    @Test
    void theShownViewpointIsPressedAndAbsentOrDeadSpidersAndDisabledCamerasAreGrey() {
        PanelState state = new PanelState();
        Simulation noCameras = zapperAndPusher(false);
        assertEquals(List.of(Look.PRESSED, Look.NORMAL, Look.GREY, Look.GREY, Look.GREY),
                looks(state, noCameras, Z, PanelButton.ZAPPER, PanelButton.PUSHER, PanelButton.GRABBER,
                        PanelButton.NORTH, PanelButton.WEST), "no grabber in this tower, cameras disabled");
        assertEquals(List.of(Look.NORMAL, Look.PRESSED, Look.GREY), looks(state, noCameras, CameraId.NORTH,
                PanelButton.ZAPPER, PanelButton.NORTH, PanelButton.EAST), "a shown camera is pressed, not grey");
        assertEquals(List.of(Look.NORMAL, Look.NORMAL), looks(state, zapperAndPusher(true), Z, PanelButton.NORTH,
                PanelButton.WEST), "cameras enabled");

        // a fixed zapper at (0,0) facing east destroys the pusher at (1,0) once READY has ended
        Simulation deadly = Simulation.start(TestTowers.tower().level(0, "FFF", "FFF")
                .object(0, 0, 0, ObjectType.FZE).object(1, 0, 0, ObjectType.PSH).object(0, 1, 0, ObjectType.ZAP)
                .build(), SimOptions.DEFAULT);
        deadly.step(List.of(new Command.Fire(Z)));
        for (int i = 0; i < 10; i++) {
            deadly.step(List.of());
        }
        assertFalse(deadly.world().spider(SpiderId.PUSHER).isAlive(), "the pusher was destroyed");
        assertEquals(Look.GREY, state.look(PanelButton.PUSHER, deadly, Z, false));
    }

    @Test
    void commandsFlashTheirButtonFor250Ms() {
        PanelState state = new PanelState();
        Simulation sim = zapperAndPusher(true);
        state.onCommand(new Command.Forward(Z));
        state.onCommand(new Command.CameraZoomIn(CameraId.EAST));
        state.onCommand(new Command.Write(Z, ProgramStep.FIRE));
        state.onCommand(new Command.RunAll());
        state.onCommand(new Command.CameraDelete(CameraId.EAST));
        state.onCommand(new Command.Clear(Z));
        state.onCommand(new Command.EnterWriteMode(Z));
        state.onCommand(new Command.SelectSpider(SpiderId.PUSHER));

        PanelButton[] flashing = {PanelButton.UP, PanelButton.STOP, PanelButton.FIRE, PanelButton.DELETE,
                PanelButton.CLEAR};
        // Acceptance fix 3: Run All flashes only once a step shows that the simulation accepted it
        assertEquals(List.of(Look.PRESSED, Look.PRESSED, Look.PRESSED, Look.PRESSED, Look.PRESSED, Look.NORMAL,
                Look.NORMAL, Look.NORMAL, Look.NORMAL), looks(state, sim, Z, PanelButton.UP, PanelButton.STOP,
                PanelButton.FIRE, PanelButton.DELETE, PanelButton.CLEAR, PanelButton.RUN_ALL, PanelButton.WRITE,
                PanelButton.DOWN, PanelButton.RUN));
        state.advance(0.2f);
        assertEquals(Look.PRESSED, state.look(PanelButton.UP, sim, Z, false), "0.2 s");
        state.advance(0.06f);
        for (PanelButton button : flashing) {
            assertEquals(Look.NORMAL, state.look(button, sim, Z, false), button + " after 0.26 s");
        }
    }

    @Test
    void aStepOfTheShownSpidersRunningProgramFlashesItsSteeringButton() {
        PanelState state = new PanelState();
        Simulation sim = zapperAndPusher(false);
        send(sim, new Command.EnterWriteMode(Z));
        send(sim, new Command.Write(Z, ProgramStep.LEFT));
        send(sim, new Command.Write(Z, ProgramStep.FIRE));
        send(sim, new Command.Stop(Z));
        for (int i = 0; i < 8; i++) {
            sim.step(List.of());
        }
        state.onStep(sim, Z);

        sim.step(List.of(new Command.Run(Z)));
        state.onStep(sim, Z);
        assertEquals(Look.PRESSED, state.look(PanelButton.LEFT, sim, Z, false), "step 0 runs in the Run frame");
        assertEquals(Look.PRESSED, state.look(PanelButton.SLOT_1, sim, Z, false), "the executing step");
        assertEquals(Look.PRESSED, state.look(PanelButton.RUN, sim, Z, false), "Run stays pressed while running");
        state.advance(0.3f);
        sim.step(List.of());
        state.onStep(sim, Z);
        assertEquals(Look.PRESSED, state.look(PanelButton.FIRE, sim, Z, false), "step 1");
        assertEquals(Look.NORMAL, state.look(PanelButton.LEFT, sim, Z, false));

        PanelState switched = new PanelState();
        switched.onStep(sim, SpiderId.PUSHER);
        sim.step(List.of());
        switched.onStep(sim, Z);
        assertEquals(Look.NORMAL, switched.look(PanelButton.UP, sim, Z, false), "switching never flashes");
        assertEquals(Look.NORMAL, switched.look(PanelButton.FIRE, sim, Z, false));
    }

    /** One frame as {@code GameState} drives it: the command (if any) to the panel, the step, then the step hook. */
    private static void frame(PanelState state, Simulation sim, Command... command) {
        for (Command c : command) {
            state.onCommand(c);
        }
        sim.step(List.of(command));
        state.onStep(sim, sim.selected());
    }

    private static void idle(PanelState state, Simulation sim, int frames) {
        for (int i = 0; i < frames; i++) {
            frame(state, sim);
        }
    }

    /** Two quick turns, then walks back and forth: the program runs for a while, its cursor soon past slot 0. */
    private static final List<ProgramStep> LONG_PROGRAM = List.of(ProgramStep.LEFT, ProgramStep.RIGHT,
            ProgramStep.FORWARD, ProgramStep.BACK, ProgramStep.FORWARD, ProgramStep.BACK, ProgramStep.FORWARD,
            ProgramStep.BACK);

    private static final List<Look> RUN = List.of(Look.PRESSED, Look.NORMAL);
    private static final List<Look> RUN_ALL = List.of(Look.NORMAL, Look.PRESSED);
    private static final List<Look> NONE = List.of(Look.NORMAL, Look.NORMAL);

    /** Run and Run All as drawn for the selected viewpoint, after the click flashes have run down. */
    private static List<Look> runLooks(PanelState state, Simulation sim) {
        state.advance(0.3f);
        return looks(state, sim, sim.selected(), PanelButton.RUN, PanelButton.RUN_ALL);
    }

    /** Writes four left turns (the program runs for a while) for the spider, then idles past the throttles. */
    private static void program(PanelState state, Simulation sim, SpiderId spider) {
        frame(state, sim, new Command.EnterWriteMode(spider));
        for (ProgramStep step : LONG_PROGRAM) {
            frame(state, sim, new Command.Write(spider, step));
            idle(state, sim, 4);
        }
        frame(state, sim, new Command.Stop(spider));
        idle(state, sim, 8);
    }

    private static Simulation programmed(PanelState state) {
        return programmed(state, false);
    }

    private static Simulation programmed(PanelState state, boolean cameras) {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFFFFFFF", "FFFFFFFF", "FFFFFFFF")
                .object(0, 0, 0, ObjectType.ZAP).object(3, 1, 0, ObjectType.PSH).cameras(cameras).build(),
                SimOptions.DEFAULT);
        program(state, sim, Z);
        program(state, sim, SpiderId.PUSHER);
        return sim;
    }

    private static void runUntilIdle(PanelState state, Simulation sim, SpiderId spider) {
        for (int i = 0; i < 500 && sim.program(spider).state() == ProgramState.RUNNING; i++) {
            frame(state, sim);
        }
        assertEquals(ProgramState.IDLE, sim.program(spider).state(), "the program has ended");
    }

    /**
     * Final review M4, fix 2: Run alone ({@code tob-rewrite.s} {@code S:8515-8522}: {@code W001C14 := 1}) keeps Run
     * pressed while the program runs ({@code $4(a5) == 1} survives the 8-frame timer, {@code S:8233-8242}); the end of
     * the program clears it ({@code S:10115-10118}).
     */
    @Test
    void runAloneKeepsRunPressedUntilTheProgramEnds() {
        PanelState state = new PanelState();
        Simulation sim = programmed(state);
        frame(state, sim, new Command.Run(Z));
        assertEquals(RUN, runLooks(state, sim));
        idle(state, sim, 12);
        assertEquals(ProgramState.RUNNING, sim.program(Z).state());
        assertEquals(RUN, runLooks(state, sim));
        runUntilIdle(state, sim, Z);
        assertEquals(NONE, runLooks(state, sim));
    }

    /**
     * Run All ({@code S:8525-8538}) sets {@code W001C14 := 2} but writes state 1 into all three spider programs, the
     * value Run writes: there is no per-spider "started by Run All". A viewpoint switch re-reads {@code W001C14 :=
     * $4(a5)} ({@code S:8329}), i.e. 1, so Run is pressed from then on, also back on the spider shown at the click.
     */
    @Test
    void runAllIsPressedOnlyForTheSpiderShownAtTheClickAndAViewpointSwitchShowsRun() {
        PanelState state = new PanelState();
        Simulation sim = programmed(state);
        frame(state, sim, new Command.RunAll());
        assertEquals(RUN_ALL, runLooks(state, sim), "the zapper was shown at the click");
        idle(state, sim, 12);
        assertEquals(ProgramState.RUNNING, sim.program(Z).state());
        assertEquals(RUN_ALL, runLooks(state, sim), "held while it runs, not just a flash");

        frame(state, sim, new Command.SelectSpider(SpiderId.PUSHER));
        assertEquals(SpiderId.PUSHER, sim.selected());
        assertEquals(ProgramState.RUNNING, sim.program(SpiderId.PUSHER).state());
        assertEquals(RUN, runLooks(state, sim), "S:8329: the pusher's program state is 1, as after Run");
        idle(state, sim, 4);
        frame(state, sim, new Command.SelectSpider(Z));
        assertEquals(Z, sim.selected());
        assertEquals(ProgramState.RUNNING, sim.program(Z).state());
        assertEquals(RUN, runLooks(state, sim), "back on the zapper: Run; Run All is gone for good");
    }

    /** The end of the shown spider's program sets {@code W001C14 := -1} ({@code S:10115-10118}). */
    @Test
    void theProgramEndDropsRunAllAndALaterRunShowsRun() {
        PanelState state = new PanelState();
        Simulation sim = programmed(state);
        frame(state, sim, new Command.RunAll());
        assertEquals(RUN_ALL, runLooks(state, sim));
        runUntilIdle(state, sim, Z);
        assertEquals(NONE, runLooks(state, sim));
        idle(state, sim, 8);
        frame(state, sim, new Command.Run(Z));
        assertEquals(RUN, runLooks(state, sim));
    }

    /**
     * Stop, Write, Delete and Clear overwrite {@code W001C14} with 0, 3, 4, 5 and stop the program
     * ({@code S:8496-8498}, {@code S:8541-8546}, {@code S:8570-8571}, {@code S:8578-8579}); a Run after them shows Run.
     */
    @Test
    void stopWriteDeleteAndClearDropRunAll() {
        List<Command> commands = List.of(new Command.Stop(Z), new Command.EnterWriteMode(Z), new Command.Delete(Z),
                new Command.Clear(Z));
        for (Command command : commands) {
            PanelState state = new PanelState();
            Simulation sim = programmed(state);
            frame(state, sim, new Command.RunAll());
            idle(state, sim, 12);
            assertEquals(RUN_ALL, runLooks(state, sim), command + ": precondition");
            assertTrue(sim.program(Z).cursor() >= 1, command + ": Delete needs a step before the cursor");
            frame(state, sim, command);
            assertNotEquals(ProgramState.RUNNING, sim.program(Z).state(), command + " stopped the program");
            assertEquals(NONE, runLooks(state, sim), command.toString());
            if (command instanceof Command.EnterWriteMode) {
                frame(state, sim, new Command.Stop(Z));
            }
            idle(state, sim, 8);
            frame(state, sim, new Command.Run(Z));
            assertEquals(RUN, runLooks(state, sim), command + ", then Run");
        }
    }

    /** A Run All refused by the program throttle ({@code S:8525-8526}, {@code tst W001C06; bne}) changes nothing. */
    @Test
    void aRunAllRejectedByTheThrottleLeavesRunPressed() {
        PanelState state = new PanelState();
        Simulation sim = programmed(state);
        frame(state, sim, new Command.Run(Z));
        frame(state, sim, new Command.RunAll());
        assertEquals(ProgramState.IDLE, sim.program(SpiderId.PUSHER).state(), "the sim refused Run All");
        assertEquals(RUN, runLooks(state, sim));
        idle(state, sim, 12);
        assertEquals(RUN, runLooks(state, sim));
    }

    /** A Run refused by the program throttle ({@code S:8515-8516}) leaves Run All pressed. */
    @Test
    void aRunRejectedByTheThrottleLeavesRunAllPressed() {
        PanelState state = new PanelState();
        Simulation sim = programmed(state);
        frame(state, sim, new Command.RunAll());
        idle(state, sim, 2);
        int cursor = sim.program(Z).cursor();
        frame(state, sim, new Command.Run(Z));
        assertTrue(sim.program(Z).cursor() >= cursor, "the sim refused Run: no restart");
        assertEquals(RUN_ALL, runLooks(state, sim));
    }

    /** Run All needs an alive selected spider ({@code S:8340-8346}); refused, it changes nothing. */
    @Test
    void aRunAllRejectedForADeadSelectedSpiderChangesNothing() {
        PanelState state = new PanelState();
        // a fixed zapper at (0,0) facing east destroys the pusher at (1,0) once READY has ended
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFFFFFFF", "FFFFFFFF", "FFFFFFFF", "FFFFFFFF")
                .object(0, 0, 0, ObjectType.FZE).object(1, 0, 0, ObjectType.PSH).object(4, 1, 0, ObjectType.ZAP)
                .build(), SimOptions.DEFAULT);
        program(state, sim, Z);
        frame(state, sim, new Command.Fire(Z));
        idle(state, sim, 10);
        assertFalse(sim.world().spider(SpiderId.PUSHER).isAlive(), "the pusher was destroyed");
        frame(state, sim, new Command.Run(Z));
        idle(state, sim, 8);
        frame(state, sim, new Command.SelectSpider(SpiderId.PUSHER));
        idle(state, sim, 4);
        assertEquals(SpiderId.PUSHER, sim.selected());
        assertEquals(ProgramState.RUNNING, sim.program(Z).state());

        frame(state, sim, new Command.RunAll());
        assertEquals(ProgramState.IDLE, sim.program(SpiderId.PUSHER).state(), "the sim refused Run All");
        state.advance(0.3f);
        assertEquals(RUN, looks(state, sim, Z, PanelButton.RUN, PanelButton.RUN_ALL), "the zapper still shows Run");
    }

    /**
     * Acceptance fix 3: in the original a Run or Run All refused by the program throttle ({@code S:8515-8516},
     * {@code S:8525-8526}) or for a dead selected spider shows nothing; only an accepted one changes the highlight.
     * So Run and Run All do not flash on the click, only once a step shows that the simulation accepted them.
     */
    @Test
    void refusedRunAndRunAllShowNoFlash() {
        PanelState state = new PanelState();
        Simulation sim = programmed(state);
        frame(state, sim, new Command.Run(Z));
        frame(state, sim, new Command.RunAll());
        assertEquals(ProgramState.IDLE, sim.program(SpiderId.PUSHER).state(), "the sim refused Run All");
        assertEquals(RUN, looks(state, sim, Z, PanelButton.RUN, PanelButton.RUN_ALL), "no Run All flash");

        PanelState runAll = new PanelState();
        Simulation other = programmed(runAll);
        frame(runAll, other, new Command.RunAll());
        idle(runAll, other, 2);
        frame(runAll, other, new Command.Run(Z));
        assertEquals(RUN_ALL, looks(runAll, other, Z, PanelButton.RUN, PanelButton.RUN_ALL), "no Run flash");
    }

    /** From a camera an accepted Run All flashes (its highlight is not kept, {@code S:8233-8242}). */
    @Test
    void anAcceptedRunAllFromACameraFlashes() {
        PanelState state = new PanelState();
        Simulation sim = programmed(state, true);
        frame(state, sim, new Command.SelectCamera(CameraId.NORTH));
        idle(state, sim, 4);
        assertEquals(CameraId.NORTH, sim.selected(), "precondition: the camera is shown");
        frame(state, sim, new Command.RunAll());
        assertEquals(ProgramState.RUNNING, sim.program(Z).state(), "the sim accepted Run All");
        assertEquals(Look.PRESSED, state.look(PanelButton.RUN_ALL, sim, CameraId.NORTH, false), "the flash");
        state.advance(0.3f);
        assertEquals(Look.NORMAL, state.look(PanelButton.RUN_ALL, sim, CameraId.NORTH, false), "only a flash");
    }

    /**
     * Acceptance fix 4: leaving the original's options menu redraws the panel and shows Run ({@code S:8089-8090}); so
     * the end of the pause drops a Run All highlight, and Run shows while the program still runs.
     */
    @Test
    void theEndOfThePauseDropsRunAllSoRunShows() {
        PanelState state = new PanelState();
        Simulation sim = programmed(state);
        frame(state, sim, new Command.RunAll());
        assertEquals(RUN_ALL, runLooks(state, sim));
        state.onPauseEnd();
        assertEquals(RUN, runLooks(state, sim), "Run after the pause menu");
        idle(state, sim, 4);
        assertEquals(RUN, runLooks(state, sim), "for good");
    }

    @Test
    void writeAndPauseStayPressedAndTheSlotsShowTheirStepsAndTheCursor() {
        PanelState state = new PanelState();
        Simulation sim = zapperAndPusher(false);
        assertEquals(Look.PRESSED, state.look(PanelButton.SLOT_1, sim, Z, false),
                "M4: the slot at the cursor is pressed even while idle (tob-rewrite.s L009E7A; ToB 2009 "
                        + "GamePanelWindowNormal.updateProgramButtons, position == i)");
        assertEquals(Look.PRESSED, state.look(PanelButton.PAUSE, sim, Z, true));
        assertEquals(Look.NORMAL, state.look(PanelButton.PAUSE, sim, Z, false));

        send(sim, new Command.EnterWriteMode(Z));
        send(sim, new Command.Write(Z, ProgramStep.FORWARD));
        send(sim, new Command.Write(Z, ProgramStep.RIGHT));

        assertEquals(Look.PRESSED, state.look(PanelButton.WRITE, sim, Z, false), "write mode");
        assertEquals(Look.PRESSED, state.look(PanelButton.SLOT_3, sim, Z, false), "the write cursor");
        assertEquals(Look.NORMAL, state.look(PanelButton.SLOT_2, sim, Z, false));
        assertEquals("panel.prg_up0", state.region(PanelButton.SLOT_1, Look.NORMAL, sim, Z));
        assertEquals("panel.prg_right0", state.region(PanelButton.SLOT_2, Look.NORMAL, sim, Z));
        assertEquals("panel.prg_spc1", state.region(PanelButton.SLOT_3, Look.PRESSED, sim, Z));
        assertEquals("panel.run_all1", state.region(PanelButton.RUN_ALL, Look.PRESSED, sim, Z));
        assertEquals("panel.grabber0", state.region(PanelButton.GRABBER, Look.GREY, sim, Z));
        assertEquals(Look.NORMAL, state.look(PanelButton.WRITE, sim, SpiderId.PUSHER, false),
                "the pusher's own program is idle");
        assertEquals("panel.prg_spc0", state.region(PanelButton.SLOT_1, Look.NORMAL, sim, SpiderId.PUSHER));
    }
}
