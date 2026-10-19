package de.latzig.game.tob.client.logic.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.ProgramStep;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

class InputMapperTest {

    private static Simulation simulation() {
        return Simulation.start(TestTowers.tower().level(0, "FFF", "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.PSH).build(), SimOptions.DEFAULT);
    }

    @Test
    void steeringGoesToTheSpiderSelectedWhenTheFrameRuns() {
        Simulation sim = simulation();
        InputMapper mapper = new InputMapper();
        mapper.press(GameAction.SELECT_PUSHER);
        mapper.press(GameAction.FORWARD);
        List<Command> sent = new ArrayList<>();

        for (int frame = 0; frame < 3; frame++) {
            Optional<Command> command = mapper.next(sim);
            command.ifPresent(sent::add);
            sim.step(command.map(List::of).orElse(List.of()));
        }

        assertEquals(List.of(new Command.SelectSpider(SpiderId.PUSHER), new Command.Forward(SpiderId.PUSHER)), sent);
        assertEquals(0, mapper.pending());
    }

    @Test
    void thePressesKeepTheirOrderAndTheClientAndDeveloperKeysAreNotQueued() {
        Simulation sim = simulation();
        InputMapper mapper = new InputMapper();
        mapper.press(GameAction.TURN_LEFT);
        mapper.press(GameAction.PROGRAM_WRITE);
        mapper.press(GameAction.PAUSE);
        mapper.press(GameAction.RESTART_TOWER);
        mapper.press(GameAction.FIRE);
        mapper.press(GameAction.PANEL_CHANGE);
        mapper.press(GameAction.BACK);

        mapper.press(GameAction.MENU);
        mapper.press(GameAction.MENU_CONFIRM);

        assertEquals(4, mapper.pending(), "P, F9, Tab, Esc and Enter are handled by the client at once");
        assertEquals(Optional.of(new Command.TurnLeft(SpiderId.ZAPPER)), mapper.next(sim));
        assertEquals(Optional.of(new Command.EnterWriteMode(SpiderId.ZAPPER)), mapper.next(sim));
        assertEquals(Optional.of(new Command.Fire(SpiderId.ZAPPER)), mapper.next(sim));
        assertEquals(Optional.of(new Command.Back(SpiderId.ZAPPER)), mapper.next(sim));
        assertEquals(Optional.empty(), mapper.next(sim));
    }

    @Test
    void aTowerWithoutAZapperStartsWithItsFirstSpiderSelected() {
        assertEquals(Optional.empty(), InputMapper.initialSelection(simulation().world()));
        assertEquals(Optional.of(GameAction.SELECT_PUSHER), InputMapper.initialSelection(Simulation.start(
                TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.PSH).object(1, 0, 0, ObjectType.GRB)
                        .build(), SimOptions.DEFAULT).world()));
        assertEquals(Optional.of(GameAction.SELECT_GRABBER), InputMapper.initialSelection(Simulation.start(
                TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.GRB).build(), SimOptions.DEFAULT)
                .world()));
    }

    @Test
    void aTowerWithoutAnySpiderKeepsTheZapperAsItsHarmlessInitialViewpoint() {
        assertEquals(SpiderId.ZAPPER, InputMapper.initialSpider(
                Simulation.start(TestTowers.tower().level(0, "F").build(), SimOptions.DEFAULT).world()));
    }

    @Test
    void initialSpiderNamesTheFirstPresentSpiderForCameraAndHud() {
        assertEquals(SpiderId.ZAPPER, InputMapper.initialSpider(simulation().world()));
        assertEquals(SpiderId.PUSHER, InputMapper.initialSpider(Simulation.start(
                TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.PSH).object(1, 0, 0, ObjectType.GRB)
                        .build(), SimOptions.DEFAULT).world()));
        assertEquals(SpiderId.GRABBER, InputMapper.initialSpider(Simulation.start(
                TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.GRB).build(), SimOptions.DEFAULT)
                .world()));
    }

    @Test
    void pressesAreDroppedWhilePausedInsteadOfBeingQueuedForAfterResume() {
        Simulation sim = simulation();
        InputMapper mapper = new InputMapper();
        mapper.setPaused(true);

        mapper.press(GameAction.FORWARD);
        mapper.press(GameAction.TURN_LEFT);

        assertEquals(0, mapper.pending(), "M2: presses made while paused must not be queued");
        assertEquals(Optional.empty(), mapper.next(sim));
    }

    @Test
    void unpausingClearsAnyPressQueuedBeforeThePause() {
        Simulation sim = simulation();
        InputMapper mapper = new InputMapper();
        mapper.press(GameAction.FORWARD);
        assertEquals(1, mapper.pending());

        mapper.setPaused(true);
        mapper.setPaused(false);

        assertEquals(0, mapper.pending(), "M2: unpausing clears any queued press so nothing is replayed");
        assertEquals(Optional.empty(), mapper.next(sim));
    }

    @Test
    void pausedMapperQueuesNormallyOnceUnpaused() {
        Simulation sim = simulation();
        InputMapper mapper = new InputMapper();
        mapper.setPaused(true);
        mapper.press(GameAction.FORWARD);
        mapper.setPaused(false);

        mapper.press(GameAction.TURN_LEFT);

        assertEquals(Optional.of(new Command.TurnLeft(SpiderId.ZAPPER)), mapper.next(sim));
    }

    /**
     * Task 9 review (addition B): a release missed while the raw listener that would report it is detached
     * ({@code GameState.onDisable}) must not keep a held panel button or key repeating once the listener is
     * attached again, but {@link InputMapper#releaseHeld} must not touch anything else waiting to be sent.
     */
    @Test
    void releaseHeldDropsTheHeldInputWithoutTouchingTheQueueOrASubmittedCommand() {
        Simulation sim = simulation();
        InputMapper mapper = new InputMapper();
        mapper.press(GameAction.FIRE);
        mapper.pressPanel(GameAction.FORWARD);
        assertEquals(2, mapper.pending());

        mapper.releaseHeld();

        assertEquals(2, mapper.pending(), "releaseHeld does not touch the queue");
        assertEquals(Optional.of(new Command.Fire(SpiderId.ZAPPER)), mapper.next(sim), "the queued presses stand");
        assertEquals(Optional.of(new Command.Forward(SpiderId.ZAPPER)), mapper.next(sim));
        assertEquals(Optional.empty(), mapper.next(sim), "the held FORWARD panel button no longer repeats");
    }

    @Test
    void aSubmittedCommandReplacesTheQueueAndGoesFirst() {
        Simulation sim = simulation();
        InputMapper mapper = new InputMapper();
        mapper.press(GameAction.FORWARD);
        mapper.press(GameAction.FIRE);

        mapper.submit(new Command.Restart());

        assertEquals(0, mapper.pending());
        assertEquals(Optional.of(new Command.Restart()), mapper.next(sim));
        assertEquals(Optional.empty(), mapper.next(sim), "sent once; the presses were dropped");
    }

    @Test
    void everyKeyAddressesTheSelectedSpider() {
        SpiderId g = SpiderId.GRABBER;
        Map<GameAction, Command> expected = new EnumMap<>(GameAction.class);
        expected.put(GameAction.FORWARD, new Command.Forward(g));
        expected.put(GameAction.BACK, new Command.Back(g));
        expected.put(GameAction.TURN_LEFT, new Command.TurnLeft(g));
        expected.put(GameAction.TURN_RIGHT, new Command.TurnRight(g));
        expected.put(GameAction.FIRE, new Command.Fire(g));
        expected.put(GameAction.SELECT_ZAPPER, new Command.SelectSpider(SpiderId.ZAPPER));
        expected.put(GameAction.SELECT_PUSHER, new Command.SelectSpider(SpiderId.PUSHER));
        expected.put(GameAction.SELECT_GRABBER, new Command.SelectSpider(g));
        expected.put(GameAction.CAMERA_NORTH, new Command.SelectCamera(CameraId.NORTH));
        expected.put(GameAction.CAMERA_EAST, new Command.SelectCamera(CameraId.EAST));
        expected.put(GameAction.CAMERA_SOUTH, new Command.SelectCamera(CameraId.SOUTH));
        expected.put(GameAction.CAMERA_WEST, new Command.SelectCamera(CameraId.WEST));
        expected.put(GameAction.PROGRAM_UP, new Command.Write(g, ProgramStep.FORWARD));
        expected.put(GameAction.PROGRAM_DOWN, new Command.Write(g, ProgramStep.BACK));
        expected.put(GameAction.PROGRAM_LEFT, new Command.Write(g, ProgramStep.LEFT));
        expected.put(GameAction.PROGRAM_RIGHT, new Command.Write(g, ProgramStep.RIGHT));
        expected.put(GameAction.PROGRAM_FIRE, new Command.Write(g, ProgramStep.FIRE));
        expected.put(GameAction.PROGRAM_WRITE, new Command.EnterWriteMode(g));
        expected.put(GameAction.PROGRAM_DELETE, new Command.Delete(g));
        expected.put(GameAction.PROGRAM_CLEAR, new Command.Clear(g));
        expected.put(GameAction.PROGRAM_SLOT_1, new Command.SetCursor(g, 0));
        expected.put(GameAction.PROGRAM_SLOT_8, new Command.SetCursor(g, 7));
        expected.put(GameAction.PROGRAM_STOP, new Command.Stop(g));
        expected.put(GameAction.PROGRAM_RUN, new Command.Run(g));
        expected.put(GameAction.PROGRAM_RUN_ALL, new Command.RunAll());

        expected.forEach((action, command) -> assertEquals(Optional.of(command), InputMapper.toCommand(action, g),
                action.toString()));
        for (GameAction nothing : List.of(GameAction.ZOOM_IN, GameAction.ZOOM_OUT, GameAction.PANEL_CHANGE,
                GameAction.PAUSE)) {
            assertEquals(Optional.empty(), InputMapper.toCommand(nothing, g), nothing.toString());
        }
    }

    @Test
    void everyKeyAddressesTheSelectedCameraWithStopAndRunAsZoom() {
        CameraId e = CameraId.EAST;
        Map<GameAction, Command> expected = new EnumMap<>(GameAction.class);
        expected.put(GameAction.FORWARD, new Command.CameraForward(e));
        expected.put(GameAction.BACK, new Command.CameraBack(e));
        expected.put(GameAction.TURN_LEFT, new Command.CameraTurnLeft(e));
        expected.put(GameAction.TURN_RIGHT, new Command.CameraTurnRight(e));
        expected.put(GameAction.FIRE, new Command.CameraFire(e));
        expected.put(GameAction.SELECT_PUSHER, new Command.SelectSpider(SpiderId.PUSHER));
        expected.put(GameAction.CAMERA_WEST, new Command.SelectCamera(CameraId.WEST));
        expected.put(GameAction.PROGRAM_UP, new Command.CameraWrite(e, ProgramStep.FORWARD));
        expected.put(GameAction.PROGRAM_FIRE, new Command.CameraWrite(e, ProgramStep.FIRE));
        expected.put(GameAction.PROGRAM_WRITE, new Command.CameraEnterWriteMode(e));
        expected.put(GameAction.PROGRAM_DELETE, new Command.CameraDelete(e));
        expected.put(GameAction.PROGRAM_CLEAR, new Command.CameraClear(e));
        expected.put(GameAction.PROGRAM_SLOT_3, new Command.CameraSetCursor(e, 2));
        expected.put(GameAction.PROGRAM_STOP, new Command.CameraZoomIn(e));
        expected.put(GameAction.ZOOM_IN, new Command.CameraZoomIn(e));
        expected.put(GameAction.PROGRAM_RUN, new Command.CameraZoomOut(e));
        expected.put(GameAction.ZOOM_OUT, new Command.CameraZoomOut(e));
        expected.put(GameAction.PROGRAM_RUN_ALL, new Command.RunAll());

        expected.forEach((action, command) -> assertEquals(Optional.of(command), InputMapper.toCommand(action, e),
                action.toString()));
        assertEquals(Optional.empty(), InputMapper.toCommand(GameAction.PANEL_CHANGE, e));
    }

    @Test
    void inWriteModePlainArrowsAndSpaceWriteStepsOnASpiderAndOnACamera() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "FFF", "FFF").object(0, 0, 0, ObjectType.ZAP)
                .cameras(true).build(), SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        List<GameAction> keys = List.of(GameAction.PROGRAM_WRITE, GameAction.FORWARD, GameAction.PROGRAM_LEFT,
                GameAction.FIRE, GameAction.PROGRAM_SLOT_3, GameAction.PROGRAM_DELETE, GameAction.CAMERA_SOUTH,
                GameAction.PROGRAM_WRITE, GameAction.TURN_RIGHT, GameAction.PROGRAM_DOWN, GameAction.FIRE);
        for (GameAction key : keys) {
            mapper.press(key);
            for (int frame = 0; frame < 8; frame++) {
                sim.step(mapper.next(sim).map(List::of).orElse(List.of()));
            }
        }

        assertEquals(List.of(ProgramStep.FORWARD, ProgramStep.FIRE, ProgramStep.EMPTY, ProgramStep.EMPTY,
                ProgramStep.EMPTY, ProgramStep.EMPTY, ProgramStep.EMPTY, ProgramStep.EMPTY),
                sim.program(SpiderId.ZAPPER).steps(), "LEFT deleted again; the zapper never moved");
        assertEquals(List.of(ProgramStep.RIGHT, ProgramStep.BACK, ProgramStep.FIRE, ProgramStep.EMPTY,
                ProgramStep.EMPTY, ProgramStep.EMPTY, ProgramStep.EMPTY, ProgramStep.EMPTY),
                sim.cameraProgram(CameraId.SOUTH).steps());
        assertEquals(CameraId.SOUTH, sim.selected(), "the zapper was no longer writing: a plain selection");
        assertEquals(0x100, sim.camera(CameraId.SOUTH).height());
        assertEquals(0, sim.camera(CameraId.SOUTH).offset());
        assertEquals(TestTowers.pos(0, 0, 0), sim.world().spider(SpiderId.ZAPPER).cell());
    }

    @Test
    void zoomKeysMoveTheCameraOnlyWhileACameraIsSelected() {
        Simulation sim = Simulation.start(TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP)
                .cameras(true).build(), SimOptions.DEFAULT);
        InputMapper mapper = new InputMapper();
        for (GameAction key : List.of(GameAction.ZOOM_OUT, GameAction.CAMERA_NORTH, GameAction.ZOOM_OUT,
                GameAction.PROGRAM_RUN, GameAction.FORWARD, GameAction.TURN_LEFT, GameAction.ZOOM_IN)) {
            mapper.press(key);
            for (int frame = 0; frame < 8; frame++) {
                sim.step(mapper.next(sim).map(List::of).orElse(List.of()));
            }
        }

        assertEquals(-3136 - 0x100, sim.camera(CameraId.NORTH).distance(), "out, out, in");
        assertEquals(0x180, sim.camera(CameraId.NORTH).height());
        assertEquals(-0x80, sim.camera(CameraId.NORTH).offset());
        assertTrue(sim.isReady(), "camera keys never end READY");
    }
}
