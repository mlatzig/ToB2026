package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.ProgramStep.EMPTY;
import static de.latzig.game.tob.core.sim.ProgramStep.FIRE;
import static de.latzig.game.tob.core.sim.ProgramStep.FORWARD;
import static de.latzig.game.tob.core.sim.ProgramStep.LEFT;
import static de.latzig.game.tob.core.sim.ProgramStep.RIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.latzig.game.tob.core.model.ObjectType;

/** Phase 2 carry-over 7: camera moves, zoom and program editing ({@code S:8340-8584}). */
class CameraCommandTest {

    private static final SpiderId Z = SpiderId.ZAPPER;
    private static final SpiderId P = SpiderId.PUSHER;
    private static final CameraId N = CameraId.NORTH;

    private WorldState world;
    private SimContext ctx;
    private SpiderController controller;

    /** Zapper and pusher side by side on a 3 × 3 floor; cameras as given. */
    private void start(boolean cameras) {
        world = WorldState.load(TestTowers.tower().level(0, "FFF", "FFF", "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.PSH).cameras(cameras).build(), SimOptions.DEFAULT);
        ctx = new SimContext(world);
        controller = new SpiderController();
    }

    /** Pipeline steps 2 and 3a of one frame: input, then program step and walk of each spider (no clock). */
    private List<SimEvent> frame(Command... commands) {
        List<SimEvent> out = new ArrayList<>();
        ctx.begin(out);
        controller.applyInput(ctx, List.of(commands));
        for (int slot = 0; slot < WorldState.SPIDER_SLOTS; slot++) {
            SimObject spider = world.object(slot);
            if (!spider.isAlive()) {
                continue;
            }
            if (!spider.isMoving()) {
                controller.programStep(ctx, spider);
            }
            if (spider.isMoving()) {
                Walk.advance(world, spider, out);
            }
        }
        return out;
    }

    private void idle(int frames) {
        for (int i = 0; i < frames; i++) {
            frame();
        }
    }

    private CameraPose pose() {
        return controller.camera(N);
    }

    private SpiderProgram cameraProgram() {
        return controller.cameraProgram(N);
    }

    @Test
    void heightStepsSetTheMoveThrottleToOneAndOffsetStepsTheTurnThrottle() {
        start(true);
        frame(new Command.Forward(Z));
        frame(new Command.Forward(P));
        assertEquals(0, count(SimEvent.MoveStarted.class, frame(new Command.Forward(P))),
                "control: the move throttle 8 of the zapper's walk still blocks the pusher");

        frame(new Command.CameraForward(N));
        assertEquals(0x180, pose().height());
        assertEquals(1, count(SimEvent.MoveStarted.class, frame(new Command.Forward(P))),
                "the camera step set the move throttle from 5 to 1, so the pusher walks one frame later");

        frame(new Command.TurnLeft(Z));
        frame(new Command.CameraTurnRight(N));
        assertEquals(0x80, pose().offset());
        assertEquals(1, count(SimEvent.Turned.class, frame(new Command.TurnLeft(Z))),
                "the offset step set the turn throttle from 3 to 1");
    }

    @Test
    void cameraStepsCheckNoThrottleAndAStepAtTheLimitIsIgnored() {
        start(true);
        frame(new Command.CameraBack(N));
        assertEquals(0x80, pose().height());
        frame(new Command.CameraBack(N));
        assertEquals(0x80, pose().height(), "below $80: ignored");

        frame(new Command.Forward(Z));
        frame(new Command.CameraBack(N));
        assertEquals(0, count(SimEvent.MoveStarted.class, frame(new Command.Forward(P))),
                "an ignored step leaves the move throttle alone");

        for (int i = 0; i < 4; i++) {
            frame(new Command.CameraForward(N));
        }
        assertEquals(0x280, pose().height(), "four steps in four frames: no throttle");
    }

    @Test
    void zoomChangesTheDistanceEndsWriteModeAndSetsTheProgramThrottle() {
        start(true);
        frame(new Command.CameraEnterWriteMode(N));
        frame(new Command.CameraWrite(N, FORWARD));
        idle(3);

        frame(new Command.CameraZoomIn(N));
        assertEquals(-2880, pose().distance());
        assertEquals(ProgramState.IDLE, cameraProgram().state(), "zoom leaves write mode");
        assertEquals(List.of(FORWARD, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), cameraProgram().steps());

        frame(new Command.Run(Z));
        assertEquals(ProgramState.IDLE, controller.program(Z).state(), "frame 1: Run blocked");
        frame(new Command.RunAll());
        assertEquals(ProgramState.IDLE, controller.program(P).state(), "frame 2: Run All blocked");
        frame(new Command.EnterWriteMode(Z), new Command.Write(Z, LEFT));
        assertEquals(EMPTY, controller.program(Z).slot(0), "frame 3: Write blocked");
        frame(new Command.CameraDelete(N));
        assertEquals(FORWARD, cameraProgram().slot(0), "frame 4: Delete blocked");
        idle(3);
        frame(new Command.Run(Z));
        assertEquals(ProgramState.RUNNING, controller.program(Z).state(), "frame 8: the throttle has run out");

        frame(new Command.CameraZoomOut(N));
        frame(new Command.CameraZoomOut(N));
        assertEquals(-3392, pose().distance(), "zoom checks no throttle");
    }

    @Test
    void aZoomAtItsLimitIsIgnoredCompletelyAndKeepsWriteMode() {
        start(true);
        for (int i = 0; i < 3; i++) {
            frame(new Command.CameraZoomIn(N));
        }
        assertEquals(-2368, pose().distance(), "deviation 5");
        idle(8);
        frame(new Command.CameraEnterWriteMode(N));

        frame(new Command.CameraZoomIn(N));

        assertEquals(-2368, pose().distance());
        assertEquals(ProgramState.WRITING, cameraProgram().state(), "S:8478-8495 leaves before the state change");
        frame(new Command.CameraWrite(N, RIGHT));
        assertEquals(RIGHT, cameraProgram().slot(0), "and before the program throttle");
    }

    @Test
    void inWriteModeCameraSteeringWritesStepsInsteadOfMovingTheCamera() {
        start(true);
        frame(new Command.CameraFire(N));
        frame(new Command.CameraEnterWriteMode(N));
        frame(new Command.CameraForward(N));
        idle(3);
        frame(new Command.CameraTurnLeft(N));
        idle(3);
        frame(new Command.CameraFire(N));

        assertEquals(List.of(FORWARD, LEFT, FIRE, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), cameraProgram().steps(),
                "Fire outside write mode wrote nothing");
        assertEquals(0x100, pose().height());
        assertEquals(0, pose().offset());
        assertEquals(3, cameraProgram().cursor());
    }

    @Test
    void deleteClearAndSetCursorWorkOnCameraPrograms() {
        start(true);
        frame(new Command.CameraEnterWriteMode(N));
        for (Command step : List.of(new Command.CameraForward(N), new Command.CameraTurnLeft(N),
                new Command.CameraFire(N))) {
            frame(step);
            idle(3);
        }

        frame(new Command.CameraSetCursor(N, 2));
        assertEquals(2, cameraProgram().cursor());
        frame(new Command.CameraDelete(N));
        assertEquals(List.of(FORWARD, FIRE, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), cameraProgram().steps());
        assertEquals(1, cameraProgram().cursor());
        assertEquals(ProgramState.IDLE, cameraProgram().state(), "Delete leaves write mode");

        frame(new Command.CameraClear(N));
        assertEquals(List.of(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), cameraProgram().steps(),
                "Clear ignores the program throttle of the Delete");
        assertEquals(0, cameraProgram().cursor());
    }

    @Test
    void withCamerasDisabledEveryCameraCommandIsIgnored() {
        start(false);
        frame(new Command.SelectCamera(N));
        assertEquals(N, controller.selected(), "a camera can still be selected");

        for (Command command : List.of(new Command.CameraForward(N), new Command.CameraTurnLeft(N),
                new Command.CameraZoomIn(N), new Command.CameraEnterWriteMode(N), new Command.CameraSetCursor(N, 5),
                new Command.RunAll())) {
            frame(command);
            idle(8);
        }

        assertEquals(0x100, pose().height());
        assertEquals(0, pose().offset());
        assertEquals(-3136, pose().distance());
        assertEquals(ProgramState.IDLE, cameraProgram().state());
        assertEquals(0, cameraProgram().cursor());
        assertEquals(ProgramState.IDLE, controller.program(Z).state(), "Run All from a disabled camera");
    }

    @Test
    void aProgramWrittenByCameraSteeringGoesToASpiderWithTheCameraHeading() {
        start(true);
        frame(new Command.SelectCamera(CameraId.WEST));
        frame(new Command.CameraEnterWriteMode(CameraId.WEST));
        frame(new Command.CameraForward(CameraId.WEST));
        idle(3);
        frame(new Command.CameraTurnRight(CameraId.WEST));
        idle(3);

        List<SimEvent> events = frame(new Command.SelectSpider(P));

        assertEquals(List.of(new SimEvent.Turned(0, 1, Direction.WEST)), events);
        assertEquals(Direction.WEST, world.spider(P).facing());
        assertEquals(List.of(FORWARD, RIGHT, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), controller.program(P).steps());
        assertEquals(ProgramState.IDLE, controller.cameraProgram(CameraId.WEST).state());
        assertTrue(controller.cameraProgram(CameraId.WEST).steps().stream().allMatch(step -> step == EMPTY),
                "the camera program is cleared");
        assertEquals(CameraId.WEST, controller.selected(), "the selection stays");
        assertEquals(0x100, controller.camera(CameraId.WEST).height(), "write mode did not move the camera");
    }

    private static long count(Class<? extends SimEvent> type, List<SimEvent> events) {
        return events.stream().filter(type::isInstance).count();
    }
}
