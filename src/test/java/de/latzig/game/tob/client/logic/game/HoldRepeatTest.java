package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Direction;
import de.latzig.game.tob.core.sim.ProgramState;
import de.latzig.game.tob.core.sim.ProgramStep;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

class HoldRepeatTest {

    private static Simulation cameraTower() {
        return Simulation.start(TestTowers.tower().level(0, "FFF", "FFF").object(0, 0, 0, ObjectType.ZAP)
                .cameras(true).build(), SimOptions.DEFAULT);
    }

    /** One driver frame: the held time advances by one frame, then the mapper is asked once. */
    private static Optional<Command> frame(InputMapper mapper, Simulation sim) {
        mapper.advance(SimDriver.FRAME_SECONDS);
        Optional<Command> command = mapper.next(sim);
        sim.step(command.map(List::of).orElse(List.of()));
        return command;
    }

    private static InputMapper onCameraNorth(Simulation sim) {
        InputMapper mapper = new InputMapper();
        mapper.press(GameAction.CAMERA_NORTH);
        mapper.release(GameAction.CAMERA_NORTH);
        frame(mapper, sim);
        assertEquals(CameraId.NORTH, sim.selected());
        return mapper;
    }

    @Test
    void theFirstStepGoesAtOnceThenNothingBefore300MsThenOneStepPerFrameUntilTheRelease() {
        Simulation sim = cameraTower();
        InputMapper mapper = onCameraNorth(sim);
        Command right = new Command.CameraTurnRight(CameraId.NORTH);
        List<Optional<Command>> sent = new ArrayList<>();

        mapper.press(GameAction.TURN_RIGHT);
        for (int i = 0; i < 5; i++) {
            sent.add(frame(mapper, sim));
        }
        mapper.release(GameAction.TURN_RIGHT);
        sent.add(frame(mapper, sim));

        // 0.14 s: the press; 0.28 s: held, but not yet 0.3 s; 0.42, 0.56, 0.70 s: one repeat per frame; released
        assertEquals(List.of(Optional.of(right), Optional.empty(), Optional.of(right), Optional.of(right),
                Optional.of(right), Optional.empty()), sent);
        assertEquals(4 * 0x80, sim.camera(CameraId.NORTH).offset(), "four offset steps, none lost or doubled");
    }

    @Test
    void aRepeatIsOnlySentWhenTheQueueIsEmpty() {
        Simulation sim = cameraTower();
        InputMapper mapper = onCameraNorth(sim);
        mapper.press(GameAction.ZOOM_OUT);
        frame(mapper, sim);
        frame(mapper, sim);
        assertEquals(Optional.of(new Command.CameraZoomOut(CameraId.NORTH)), frame(mapper, sim), "repeating");

        mapper.press(GameAction.PROGRAM_SLOT_3);
        assertEquals(Optional.of(new Command.CameraSetCursor(CameraId.NORTH, 2)), frame(mapper, sim),
                "the queued press goes first; the repeat waits");
        assertEquals(Optional.of(new Command.CameraZoomOut(CameraId.NORTH)), frame(mapper, sim),
                "the slot key does not stop the held zoom");
        assertEquals(0, mapper.pending(), "no backlog of repeats");
    }

    @Test
    void onlyTheInputsOfTheSpecRepeat() {
        for (GameAction action : GameAction.values()) {
            boolean camera = HoldRepeat.CAMERA_REPEATS.contains(action);
            assertEquals(camera, HoldRepeat.repeats(action, CameraId.EAST), action + " on a camera");
            boolean steering = HoldRepeat.SPIDER_REPEATS.contains(action);
            assertEquals(steering, HoldRepeat.repeats(action, SpiderId.PUSHER), action + " on a spider");
        }
        assertEquals(8, HoldRepeat.CAMERA_REPEATS.size(), "arrows, PgUp/PgDn, S/R");
        assertFalse(HoldRepeat.CAMERA_REPEATS.contains(GameAction.FIRE));
        assertEquals(EnumSet.of(GameAction.FORWARD, GameAction.BACK, GameAction.TURN_LEFT, GameAction.TURN_RIGHT,
                GameAction.FIRE), HoldRepeat.SPIDER_REPEATS, "the spider's steering keys, as the panel's buttons");
    }

    @Test
    void nothingRepeatsInWriteModeOrBeforeTheDelayOnACameraOrAfterTheRelease() {
        HoldRepeat hold = new HoldRepeat();
        hold.press(GameAction.FORWARD);
        hold.advance(0.29);
        assertEquals(Optional.empty(), hold.due(CameraId.NORTH, ProgramState.IDLE), "camera: before 0.3 s");
        assertEquals(Optional.of(GameAction.FORWARD), hold.due(SpiderId.ZAPPER, ProgramState.IDLE),
                "spider: no delay");
        hold.advance(0.02);
        assertEquals(Optional.of(GameAction.FORWARD), hold.due(CameraId.NORTH, ProgramState.RUNNING));
        assertEquals(Optional.empty(), hold.due(SpiderId.ZAPPER, ProgramState.WRITING),
                "in write mode the arrow writes a step: one per press");
        assertEquals(Optional.empty(), hold.due(CameraId.NORTH, ProgramState.WRITING), "the same on a camera");

        hold.press(GameAction.PROGRAM_WRITE);
        assertEquals(Optional.of(GameAction.FORWARD), hold.held(), "a press that never repeats keeps the hold");
        hold.release(GameAction.BACK);
        assertTrue(hold.held().isPresent(), "releasing another input keeps the hold");
        hold.release(GameAction.FORWARD);
        assertEquals(Optional.empty(), hold.due(SpiderId.ZAPPER, ProgramState.IDLE), "released");
    }

    @Test
    void aHeldPanelButtonRepeatsEveryFrameAtOncePacedOnlyByTheSimulationsThrottles() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFF", "FFF").object(0, 0, 0, ObjectType.ZAP)
                .build(), SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        List<Optional<Command>> sent = new ArrayList<>();
        int turns = 0;
        mapper.pressPanel(GameAction.TURN_LEFT);
        for (int i = 0; i < 9; i++) {
            Direction before = sim.world().spider(SpiderId.ZAPPER).facing();
            sent.add(frame(mapper, sim));
            if (sim.world().spider(SpiderId.ZAPPER).facing() != before) {
                turns++;
            }
        }
        mapper.release(GameAction.TURN_LEFT);

        assertTrue(sent.stream().allMatch(c -> c.equals(Optional.of(new Command.TurnLeft(SpiderId.ZAPPER)))),
                "a turn command in every frame from the first, no 0.3 s delay: " + sent);
        assertEquals(3, turns, "frames 1, 5 and 9: the turn throttle of 4 paces it (S:8381-8396)");
        assertEquals(Optional.empty(), frame(mapper, sim), "released");
    }

    @Test
    void aHeldPanelButtonRepeatsInWriteModeAndAnyActionHolds() {
        Simulation sim = cameraTower();
        InputMapper mapper = new InputMapper();
        mapper.press(GameAction.PROGRAM_WRITE);
        frame(mapper, sim);
        mapper.pressPanel(GameAction.FORWARD);
        for (int i = 0; i < 9; i++) {
            frame(mapper, sim);
        }
        mapper.release(GameAction.FORWARD);

        assertEquals(List.of(ProgramStep.FORWARD, ProgramStep.FORWARD, ProgramStep.FORWARD, ProgramStep.EMPTY,
                ProgramStep.EMPTY, ProgramStep.EMPTY, ProgramStep.EMPTY, ProgramStep.EMPTY),
                sim.program(SpiderId.ZAPPER).steps(), "a step every 4 frames: the write throttle paces it");
        HoldRepeat hold = new HoldRepeat();
        hold.pressPanel(GameAction.PROGRAM_SLOT_3);
        assertEquals(Optional.of(GameAction.PROGRAM_SLOT_3), hold.due(CameraId.WEST, ProgramState.WRITING),
                "panel holds ignore the key table, the viewpoint and write mode");
        hold.pressPanel(GameAction.PAUSE);
        assertEquals(Optional.of(GameAction.PROGRAM_SLOT_3), hold.held(), "Pause is a client action, never held");
    }

    @Test
    void aHeldForwardWalksTheSpiderOnPacedByTheSimulationAndPausingStopsIt() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFFF").object(0, 0, 0, ObjectType.ZAP)
                .build(), SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        mapper.press(GameAction.TURN_RIGHT);
        mapper.release(GameAction.TURN_RIGHT);
        for (int i = 0; i < 5; i++) {
            frame(mapper, sim);
        }
        mapper.press(GameAction.FORWARD);
        int forwards = 0;
        for (int i = 0; i < 70; i++) {
            if (frame(mapper, sim).isPresent()) {
                forwards++;
            }
        }

        assertEquals(TestTowers.pos(2, 0, 0), sim.world().spider(SpiderId.ZAPPER).cell(),
                "two cells in 70 frames: a walk takes 32 frames; refused Forwards are ignored");
        assertTrue(forwards > 60, "a Forward in nearly every frame: " + forwards);

        mapper.setPaused(true);
        mapper.setPaused(false);
        assertEquals(Optional.empty(), frame(mapper, sim), "pausing stops the held input");
    }

    // Report 18 §4, fix (a): held steering keys on a spider repeat every frame like held panel buttons

    private static final SpiderId G = SpiderId.GRABBER;

    /** The grabber at (0,0), the exchanger at (3,0); 5 robots needed, so the swap does not win the tower. */
    private static Simulation swapTower() {
        return Simulation.start(TestTowers.tower().level(0, "FFFF", "FFFF").object(0, 0, 0, ObjectType.GRB)
                .object(3, 0, 0, ObjectType.EXC).conditions(0, false, 0, 5).build(), SimOptions.DEFAULT);
    }

    /** One driver frame like {@link #frame}, returning the frame's events. */
    private static List<SimEvent> step(InputMapper mapper, Simulation sim) {
        mapper.advance(SimDriver.FRAME_SECONDS);
        return sim.step(mapper.next(sim).map(List::of).orElse(List.of()));
    }

    private static Predicate<SimEvent> turnedBy(SpiderId spider) {
        return e -> e instanceof SimEvent.Turned t && t.slot() == spider.slot();
    }

    private static Predicate<SimEvent> firedBy(SpiderId spider) {
        return e -> e instanceof SimEvent.SpiderFired f && f.slot() == spider.slot();
    }

    /** Selects the grabber with a tapped key and lets the select throttle run out. */
    private static InputMapper withGrabber(Simulation sim) {
        InputMapper mapper = new InputMapper();
        tap(mapper, sim, GameAction.SELECT_GRABBER);
        for (int i = 0; i < 5; i++) {
            step(mapper, sim);
        }
        assertEquals(G, sim.selected());
        return mapper;
    }

    /** Taps {@code action} (pressed and released before the next frame) and runs that frame. */
    private static List<SimEvent> tap(InputMapper mapper, Simulation sim, GameAction action) {
        mapper.press(action);
        mapper.release(action);
        return step(mapper, sim);
    }

    /** Turns the selected grabber east with a tap and lets the turn throttle run out. */
    private static void turnEast(InputMapper mapper, Simulation sim) {
        tap(mapper, sim, GameAction.TURN_RIGHT);
        for (int i = 0; i < 4; i++) {
            step(mapper, sim);
        }
        assertEquals(Direction.EAST, sim.world().spider(G).facing());
    }

    @Test
    void aTurnKeyHeldThroughASwapTurnsExactlyInFramePlus8() {
        Simulation sim = swapTower();
        InputMapper mapper = withGrabber(sim);
        turnEast(mapper, sim);
        List<SimEvent> swap = tap(mapper, sim, GameAction.FIRE);
        assertTrue(swap.stream().anyMatch(e -> e instanceof SimEvent.Exchanged), "the swap at +0: " + swap);
        Direction afterSwap = sim.world().spider(G).facing();

        // report 18 §4 (Exp24): TURN_LEFT pressed at +3 and held until +13; the original turns at +8 (S:8381-8402)
        List<Integer> turns = new ArrayList<>();
        for (int plus = 1; plus <= 16; plus++) {
            if (plus == 3) {
                mapper.press(GameAction.TURN_LEFT);
            }
            if (plus == 13) {
                mapper.release(GameAction.TURN_LEFT);
            }
            if (step(mapper, sim).stream().anyMatch(turnedBy(G))) {
                turns.add(plus);
            }
        }
        assertEquals(List.of(8, 12), turns, "first turn when the hit throttle of 8 runs out, then every 4 frames");
        assertEquals(afterSwap.left().left(), sim.world().spider(G).facing());
    }

    @Test
    void aHeldTurnKeyTurnsEvery4FramesFromTheFirstFrame() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFF", "FFF").object(0, 0, 0, ObjectType.ZAP)
                .build(), SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        List<Integer> turns = new ArrayList<>();
        mapper.press(GameAction.TURN_LEFT);
        for (int frame = 1; frame <= 20; frame++) {
            if (frame == 14) {
                mapper.release(GameAction.TURN_LEFT);
            }
            if (step(mapper, sim).stream().anyMatch(turnedBy(SpiderId.ZAPPER))) {
                turns.add(frame);
            }
        }
        assertEquals(List.of(1, 5, 9, 13), turns, "paced by the turn throttle of 4 (S:8381-8396); none after release");
    }

    @Test
    void aHeldFireKeyRepeatsPacedByTheHitAndMissThrottles() {
        // the grabber at (0,0) turned east grabs the klondikes at (1,0), (2,0), (3,0) one by one, then misses
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFFF", "FFFF").object(0, 0, 0, ObjectType.GRB)
                .object(1, 0, 0, ObjectType.KLD).object(2, 0, 0, ObjectType.KLD).object(3, 0, 0, ObjectType.KLD)
                .conditions(0, false, 7, 0).build(), SimOptions.DEFAULT);
        InputMapper mapper = withGrabber(sim);
        turnEast(mapper, sim);
        List<Integer> fires = new ArrayList<>();
        List<Integer> grabs = new ArrayList<>();
        mapper.press(GameAction.FIRE);
        for (int frame = 1; frame <= 28; frame++) {
            List<SimEvent> events = step(mapper, sim);
            if (events.stream().anyMatch(firedBy(G))) {
                fires.add(frame);
            }
            if (events.stream().anyMatch(e -> e instanceof SimEvent.Collected)) {
                grabs.add(frame);
            }
        }
        mapper.release(GameAction.FIRE);
        assertEquals(List.of(1, 9, 17), grabs, "a hit blocks fire for 8 frames (S:8397-8402)");
        assertEquals(List.of(1, 9, 17, 25, 26, 27, 28), fires,
                "then a miss blocks it for 1 frame only: one shot per frame (S:8403-8406)");
        assertTrue(step(mapper, sim).stream().noneMatch(firedBy(G)), "released");
    }

    @Test
    void aTapSendsExactlyOneCommandEvenWhenTwoFramesRunBeforeTheRelease() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFF", "FFF").object(0, 0, 0, ObjectType.ZAP)
                .build(), SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        Command fire = new Command.Fire(SpiderId.ZAPPER);

        mapper.press(GameAction.FIRE);
        assertEquals(Optional.of(fire), frame(mapper, sim));
        mapper.release(GameAction.FIRE);
        assertEquals(Optional.empty(), frame(mapper, sim), "a tap released before the next frame: one shot");

        // a stalled render frame runs two simulation frames before the key's release is seen (SimDriver catch-up):
        // the key was seen held at one frame boundary only, so it counts as held for one frame, as a click that
        // the original saw in one frame
        mapper.press(GameAction.FIRE);
        mapper.advance(2 * SimDriver.FRAME_SECONDS);
        List<Optional<Command>> sent = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Optional<Command> command = mapper.next(sim);
            sim.step(command.map(List::of).orElse(List.of()));
            sent.add(command);
        }
        mapper.release(GameAction.FIRE);
        assertEquals(List.of(Optional.of(fire), Optional.empty()), sent, "one shot from one tap");

        mapper.press(GameAction.TURN_LEFT);
        int turns = 0;
        for (int i = 0; i < 2; i++) {
            turns += step(mapper, sim).stream().anyMatch(turnedBy(SpiderId.ZAPPER)) ? 1 : 0;
        }
        mapper.release(GameAction.TURN_LEFT);
        assertEquals(1, turns, "a turn held over two frames turns once: the turn throttle refuses the repeat");
    }

    @Test
    void aHeldArrowKeyOnASpiderInWriteModeWritesOneStep() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFF", "FFF").object(0, 0, 0, ObjectType.ZAP)
                .build(), SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        mapper.press(GameAction.PROGRAM_WRITE);
        mapper.release(GameAction.PROGRAM_WRITE);
        frame(mapper, sim);
        mapper.press(GameAction.TURN_LEFT);
        for (int i = 0; i < 12; i++) {
            frame(mapper, sim);
        }
        mapper.release(GameAction.TURN_LEFT);
        assertEquals(List.of(ProgramStep.LEFT, ProgramStep.EMPTY, ProgramStep.EMPTY, ProgramStep.EMPTY,
                ProgramStep.EMPTY, ProgramStep.EMPTY, ProgramStep.EMPTY, ProgramStep.EMPTY),
                sim.program(SpiderId.ZAPPER).steps(), "write mode: no repeat, as before");
    }
}
