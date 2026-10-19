package de.latzig.game.tob.core.sim;

import static de.latzig.game.tob.core.sim.ProgramStep.EMPTY;
import static de.latzig.game.tob.core.sim.ProgramStep.FIRE;
import static de.latzig.game.tob.core.sim.ProgramStep.FORWARD;
import static de.latzig.game.tob.core.sim.ProgramStep.LEFT;
import static de.latzig.game.tob.core.sim.ProgramStep.RIGHT;
import static de.latzig.game.tob.core.sim.TestTowers.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.latzig.game.tob.core.model.ObjectType;

class SpiderControllerTest {

    private static final SpiderId Z = SpiderId.ZAPPER;
    private static final SpiderId P = SpiderId.PUSHER;

    private WorldState world;
    private SimContext ctx;
    private SpiderController controller;

    private void start(TestTowers.Builder tower, boolean backwardsAllowed) {
        world = WorldState.load(tower.build(), new SimOptions(backwardsAllowed));
        ctx = new SimContext(world);
        controller = new SpiderController();
    }

    private void start(TestTowers.Builder tower) {
        start(tower, false);
    }

    private static TestTowers.Builder single() {
        return TestTowers.tower().level(0, "F").object(0, 0, 0, ObjectType.ZAP);
    }

    /** Pipeline steps 2 and 3a of one frame: input, then program step and movement of each spider (no clock). */
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
                if (spider.moveKind() == MoveKind.WALK) {
                    Walk.advance(world, spider, out);
                } else {
                    LiftRide.advance(world, spider, out);
                }
            }
        }
        return out;
    }

    private List<SimEvent> idle(int frames) {
        List<SimEvent> all = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            all.addAll(frame());
        }
        return all;
    }

    /** Writes a program; three idle frames after each write keep the 4-frame write throttle satisfied. */
    private void writeProgram(SpiderId spider, ProgramStep... steps) {
        frame(new Command.EnterWriteMode(spider));
        for (ProgramStep step : steps) {
            frame(new Command.Write(spider, step));
            idle(3);
        }
    }

    private static long count(List<SimEvent> events, Class<? extends SimEvent> type) {
        return events.stream().filter(type::isInstance).count();
    }

    /** The events without the ray events of the fires (rays are tested in {@code RayTest}). */
    private static List<SimEvent> withoutRays(List<SimEvent> events) {
        return events.stream()
                .filter(event -> !(event instanceof SimEvent.RayFired || event instanceof SimEvent.RayHit)).toList();
    }

    private SimObject zap() {
        return world.spider(Z);
    }

    @Test
    void turnsChangeTheFacingAtOnceAndKeepReady() {
        start(single());

        assertEquals(List.of(new SimEvent.Turned(0, 0, Direction.EAST)), frame(new Command.TurnRight(Z)));
        assertEquals(Direction.EAST, zap().facing());
        assertTrue(world.isReady());
    }

    @Test
    void turnsAndFiresShareOneFourFrameThrottle() {
        start(single());
        frame(new Command.TurnRight(Z));

        assertEquals(0, count(frame(new Command.TurnRight(Z)), SimEvent.Turned.class));
        assertEquals(0, count(frame(new Command.Fire(Z)), SimEvent.SpiderFired.class));
        assertEquals(0, count(frame(new Command.TurnLeft(Z)), SimEvent.Turned.class));
        assertEquals(1, count(frame(new Command.TurnRight(Z)), SimEvent.Turned.class));
        assertEquals(Direction.SOUTH, zap().facing());
        assertTrue(world.isReady());
    }

    @Test
    void aFireWithoutAHitBlocksTheNextFireForOneFrameOnly() {
        start(single());
        SimEvent fired = new SimEvent.SpiderFired(0, 0, pos(0, 0, 0), Direction.NORTH);

        assertEquals(List.of(new SimEvent.ReadyEnded(0), fired), withoutRays(frame(new Command.Fire(Z))));
        assertEquals(List.of(fired), withoutRays(frame(new Command.Fire(Z))));
    }

    @Test
    void aFireThatHitsBlocksTheNextFireForEightFramesAndAMissForOne() {
        // flags at (0,2) and (0,3) north of the zapper: two hits, then misses (destroying flags let rays pass)
        start(TestTowers.tower().level(0, "F", "F", "F", "F").object(0, 0, 0, ObjectType.ZAP)
                .object(0, 2, 0, ObjectType.BFL).object(0, 3, 0, ObjectType.BFL));
        List<Integer> accepted = new ArrayList<>();

        for (int f = 0; f < 20; f++) {
            if (count(frame(new Command.Fire(Z)), SimEvent.SpiderFired.class) == 1) {
                accepted.add(f);
            }
        }

        assertEquals(List.of(0, 8, 16, 17, 18, 19), accepted);
        assertEquals(ObjectStatus.DESTROYING, world.object(3).status());
        assertEquals(ObjectStatus.DESTROYING, world.object(4).status());
    }

    @Test
    void programFiresAreNotThrottledAndDoNotSetTheThrottle() {
        start(TestTowers.tower().level(0, "F", "F", "F", "F").object(0, 0, 0, ObjectType.ZAP)
                .object(0, 2, 0, ObjectType.BFL).object(0, 3, 0, ObjectType.BFL));
        writeProgram(Z, FIRE, FIRE);

        List<SimEvent> first = frame(new Command.Run(Z));
        List<SimEvent> second = frame(new Command.Fire(Z));

        assertTrue(first.contains(new SimEvent.RayHit(0, 0, 3, RayOutcome.DESTROYED)));
        assertEquals(2, count(second, SimEvent.SpiderFired.class), "the direct fire and the program's second step");
        assertTrue(second.contains(new SimEvent.RayHit(0, 0, 4, RayOutcome.DESTROYED)));
    }

    @Test
    void forwardBackFireAndRunEndReadyEvenWhenTheyFail() {
        for (Command command : List.of(new Command.Forward(Z), new Command.Back(Z), new Command.Fire(Z),
                new Command.Run(Z))) {
            start(single());
            assertTrue(frame(command).contains(new SimEvent.ReadyEnded(0)), command.toString());
            assertFalse(world.isReady(), command.toString());
        }
        for (Command command : List.of(new Command.TurnLeft(Z), new Command.EnterWriteMode(Z),
                new Command.SelectSpider(P), new Command.Stop(Z), new Command.Clear(Z))) {
            start(single());
            frame(command);
            assertTrue(world.isReady(), command.toString());
        }
    }

    @Test
    void theMoveThrottleIsEightAfterAStartedMoveAndOneAfterAFailedOne() {
        start(TestTowers.tower().level(0, "F", "F", "F").object(0, 1, 0, ObjectType.ZAP), true);
        assertEquals(1, count(frame(new Command.Forward(Z)), SimEvent.MoveStarted.class));
        for (int f = 1; f <= 7; f++) {
            assertEquals(0, count(frame(new Command.Back(Z)), SimEvent.MoveReversed.class), "frame " + f);
        }
        assertEquals(1, count(frame(new Command.Back(Z)), SimEvent.MoveReversed.class), "frame 8, counter 24");

        start(TestTowers.tower().level(0, "F", "F").object(0, 1, 0, ObjectType.ZAP), true);
        assertEquals(0, count(frame(new Command.Forward(Z)), SimEvent.MoveStarted.class), "no floor north");
        assertEquals(1, count(frame(new Command.Back(Z)), SimEvent.MoveStarted.class));
    }

    @Test
    void backWalksBackwardsKeepingTheFacingOnlyWhenAllowed() {
        TestTowers.Builder row = TestTowers.tower().level(0, "FFF").object(1, 0, 0, ObjectType.ZAP);
        start(row, false);
        frame(new Command.TurnRight(Z));
        assertEquals(0, count(frame(new Command.Back(Z)), SimEvent.MoveStarted.class));
        assertFalse(world.isReady());

        start(row, true);
        frame(new Command.TurnRight(Z));
        assertEquals(List.of(new SimEvent.ReadyEnded(0), new SimEvent.MoveStarted(0, 0, pos(1, 0, 0), pos(0, 0, 0))),
                frame(new Command.Back(Z)));
        assertEquals(Direction.WEST, zap().moveDirection());
        idle(31);
        assertFalse(zap().isMoving());
        assertEquals(pos(0, 0, 0), zap().cell());
        assertEquals(Direction.EAST, zap().facing());
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 16, 22})
    void backWhileWalkingIsRefusedAtTheClaimHalfWayAndReleaseCounters(int backFrame) {
        start(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP));
        frame(new Command.TurnRight(Z));
        frame(new Command.Forward(Z));
        idle(backFrame - 1);
        assertEquals(32 - backFrame, zap().moveCounter());

        assertEquals(0, count(frame(new Command.Back(Z)), SimEvent.MoveReversed.class));
        assertEquals(31 - backFrame, zap().moveCounter());
        idle(31 - backFrame);
        assertFalse(zap().isMoving());
        assertEquals(pos(1, 0, 0), zap().cell());
    }

    @Test
    void backWhileWalkingReversesAtOtherCounters() {
        start(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP));
        frame(new Command.TurnRight(Z));
        frame(new Command.Forward(Z));
        idle(10);
        assertEquals(21, zap().moveCounter());

        assertEquals(List.of(new SimEvent.MoveReversed(0, 0)), frame(new Command.Back(Z)));
        assertEquals(10, zap().moveCounter());
        assertEquals(Occupancy.FREE, world.occupancy().holder(pos(1, 0, 0)));
        assertEquals(Direction.WEST, zap().moveDirection());
        assertEquals(Direction.EAST, zap().facing());
        idle(10);
        assertFalse(zap().isMoving());
        assertEquals(pos(0, 0, 0), zap().cell());
    }

    @Test
    void backOnALiftRidesAndBackWhileRidingReversesEvenAtCounter22() {
        start(TestTowers.tower().level(0, "U").level(1, "u").object(0, 0, 0, ObjectType.ZAP));

        assertTrue(frame(new Command.Back(Z)).contains(new SimEvent.LiftRideStarted(0, 0, pos(0, 0, 0),
                pos(0, 0, 1))));
        idle(9);
        assertEquals(22, zap().moveCounter());
        assertEquals(List.of(new SimEvent.MoveReversed(0, 0)), frame(new Command.Back(Z)));
        idle(8);
        assertTrue(zap().isRiding());
        frame();
        assertFalse(zap().isMoving());
        assertEquals(pos(0, 0, 0), zap().cell());
        assertFalse(world.hasFloor(pos(0, 0, 1)));
    }

    @Test
    void inWriteModeTheActionCommandsBecomeProgramSteps() {
        start(TestTowers.tower().level(0, "F", "F").object(0, 0, 0, ObjectType.ZAP));

        assertEquals(List.of(), frame(new Command.EnterWriteMode(Z), new Command.Forward(Z)));
        assertEquals(List.of(), frame(new Command.TurnLeft(Z)));
        idle(2);
        assertEquals(List.of(), frame(new Command.TurnLeft(Z)));

        assertEquals(List.of(FORWARD, LEFT, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), controller.program(Z).steps());
        assertEquals(Direction.NORTH, zap().facing());
        assertFalse(zap().isMoving());
        assertTrue(world.isReady());
    }

    @Test
    void programEditingUsesTheProgramThrottle() {
        start(single());
        SpiderProgram program = controller.program(Z);
        frame(new Command.EnterWriteMode(Z), new Command.Write(Z, FORWARD));
        idle(3);
        frame(new Command.Write(Z, LEFT));
        idle(1);
        frame(new Command.Delete(Z));
        assertEquals(LEFT, program.slot(1), "delete two frames after a write is throttled");
        idle(1);

        frame(new Command.Delete(Z));
        assertEquals(List.of(FORWARD, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), program.steps());
        assertEquals(ProgramState.IDLE, program.state());
        frame(new Command.EnterWriteMode(Z), new Command.Write(Z, RIGHT));
        assertEquals(FORWARD, program.slot(0), "write one frame after a delete is throttled");
        idle(6);
        frame(new Command.Write(Z, RIGHT));
        assertEquals(List.of(RIGHT, FORWARD, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), program.steps());

        frame(new Command.Clear(Z));
        assertEquals(List.of(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), program.steps(),
                "clear ignores the throttle");
    }

    /** Zapper and pusher on a tower with cameras; the zapper has written FORWARD, FIRE and is still writing. */
    private void zapperWritingForwardFire() {
        start(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.PSH)
                .cameras(true));
        frame(new Command.EnterWriteMode(Z), new Command.Write(Z, FORWARD));
        idle(3);
        frame(new Command.Write(Z, FIRE));
    }

    @Test
    void aWritingSpiderCopiesItsProgramToACameraAndKeepsTheSelection() {
        zapperWritingForwardFire();

        assertEquals(List.of(), frame(new Command.SelectCamera(CameraId.EAST)));
        assertEquals(controller.program(Z).steps(), controller.cameraProgram(CameraId.EAST).steps());
        assertEquals(FIRE, controller.cameraProgram(CameraId.EAST).slot(1));
        assertEquals(ProgramState.IDLE, controller.program(Z).state());
        assertEquals(Z, controller.selected());

        frame(new Command.SelectCamera(CameraId.EAST));
        assertEquals(Z, controller.selected(), "select throttle");
        idle(2);
        frame(new Command.SelectCamera(CameraId.EAST));
        assertEquals(CameraId.EAST, controller.selected(), "no write mode any more: a normal selection");
    }

    @Test
    void aWritingCameraGivesItsHeadingAndProgramToASpiderAndIsCleared() {
        zapperWritingForwardFire();
        frame(new Command.SelectCamera(CameraId.EAST));
        idle(3);
        frame(new Command.SelectCamera(CameraId.EAST));
        frame(new Command.CameraEnterWriteMode(CameraId.EAST));
        assertEquals(ProgramState.WRITING, controller.cameraProgram(CameraId.EAST).state());
        idle(2);

        assertEquals(List.of(new SimEvent.Turned(0, 1, Direction.EAST)), frame(new Command.SelectSpider(P)));
        assertEquals(List.of(FORWARD, FIRE, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), controller.program(P).steps());
        assertEquals(Direction.EAST, world.spider(P).facing());
        SpiderProgram camera = controller.cameraProgram(CameraId.EAST);
        assertEquals(List.of(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), camera.steps());
        assertEquals(0, camera.cursor());
        assertEquals(ProgramState.IDLE, camera.state());
        assertEquals(CameraId.EAST, controller.selected());

        start(single());
        frame(new Command.CameraEnterWriteMode(CameraId.EAST));
        assertEquals(ProgramState.IDLE, controller.cameraProgram(CameraId.EAST).state(), "tower without cameras");
    }

    @Test
    void selectingAnotherSpiderInWriteModeDoesNotCopy() {
        zapperWritingForwardFire();

        frame(new Command.SelectSpider(P));

        assertEquals(P, controller.selected());
        assertEquals(List.of(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), controller.program(P).steps());
        assertEquals(ProgramState.WRITING, controller.program(Z).state());
        assertEquals(Direction.NORTH, world.spider(P).facing());
    }

    @Test
    void selectingWithoutWriteModeOnlyChangesTheSelection() {
        start(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.PSH));
        frame(new Command.EnterWriteMode(P), new Command.Write(P, LEFT));

        frame(new Command.SelectSpider(P));

        assertEquals(P, controller.selected());
        assertEquals(List.of(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), controller.program(Z).steps());
        assertEquals(LEFT, controller.program(P).slot(0));
    }

    @Test
    void commandsForDeadOrAbsentSpidersAreIgnored() {
        start(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP));
        assertEquals(List.of(), frame(new Command.Forward(P), new Command.Fire(P), new Command.Run(P)));
        assertTrue(world.isReady());

        Rules.destroy(world, zap(), new ArrayList<>());
        assertEquals(List.of(), frame(new Command.TurnLeft(Z), new Command.Fire(Z), new Command.RunAll()));
        assertTrue(world.isReady());
        assertEquals(ProgramState.IDLE, controller.program(Z).state());
    }

    @Test
    void runExecutesStepZeroInTheSameFrame() {
        start(TestTowers.tower().level(0, "F", "F").object(0, 0, 0, ObjectType.ZAP));
        writeProgram(Z, FORWARD);

        assertEquals(List.of(new SimEvent.ReadyEnded(0), new SimEvent.MoveStarted(0, 0, pos(0, 0, 0), pos(0, 1, 0))),
                frame(new Command.Run(Z)));
        assertEquals(31, zap().moveCounter());
        assertEquals(0, controller.program(Z).cursor());
    }

    @Test
    void programStepsTakeOneFrameEachAreNotThrottledAndFailedMovesAreNotRetried() {
        start(single());
        writeProgram(Z, LEFT, LEFT, FIRE, FIRE, FORWARD, RIGHT);

        List<List<SimEvent>> frames = new ArrayList<>();
        frames.add(frame(new Command.Run(Z)));
        for (int i = 1; i < 8; i++) {
            frames.add(frame());
        }
        assertEquals(ProgramState.RUNNING, controller.program(Z).state());
        frames.add(frame());
        assertEquals(ProgramState.IDLE, controller.program(Z).state());

        SimEvent firedSouth = new SimEvent.SpiderFired(0, 0, pos(0, 0, 0), Direction.SOUTH);
        assertEquals(List.of(new SimEvent.ReadyEnded(0), new SimEvent.Turned(0, 0, Direction.WEST)), frames.get(0));
        assertEquals(List.of(new SimEvent.Turned(0, 0, Direction.SOUTH)), frames.get(1));
        assertEquals(List.of(firedSouth), withoutRays(frames.get(2)));
        assertEquals(List.of(firedSouth), withoutRays(frames.get(3)));
        assertEquals(List.of(), frames.get(4), "FORWARD into the void fails and is not retried");
        assertEquals(List.of(new SimEvent.Turned(0, 0, Direction.WEST)), frames.get(5));
        assertEquals(List.of(), frames.get(6), "empty slot 6");
        assertEquals(List.of(), frames.get(7), "empty slot 7");
        assertEquals(List.of(), frames.get(8), "past slot 7: stop");
    }

    @Test
    void directBackDuringARunningProgramReversesTheWalkAndTheProgramContinuesAfterwards() {
        // Trace (b) of the final review: the panel has no program-state check for movement buttons (S:8350-8380),
        // so a direct Back reverses the walk the program started; the program resumes at the next standing frame.
        start(TestTowers.tower().level(0, "FFF").object(1, 0, 0, ObjectType.ZAP));
        writeProgram(Z, LEFT, FORWARD);

        frame(new Command.Run(Z));
        idle(1);
        assertTrue(zap().isMoving());
        assertEquals(Direction.WEST, zap().moveDirection());
        idle(8);
        assertEquals(23, zap().moveCounter(), "not yet at the claim counter (22), so no cell was claimed");

        assertEquals(List.of(new SimEvent.MoveReversed(0, 0)), frame(new Command.Back(Z)));
        assertEquals(8, zap().moveCounter());
        assertEquals(Direction.EAST, zap().moveDirection(), "the walk reverses regardless of the running program");
        assertEquals(ProgramState.RUNNING, controller.program(Z).state());

        idle(7);
        assertTrue(zap().isMoving(), "counter 1: not finished yet");
        assertEquals(List.of(new SimEvent.MoveFinished(0, 0, pos(1, 0, 0))), idle(1));
        assertFalse(zap().isMoving());
        assertEquals(pos(1, 0, 0), zap().cell());
        assertEquals(ProgramState.RUNNING, controller.program(Z).state(),
                "still running, waiting for the next standing frame (L00BC9A, S:9144-9150)");

        assertEquals(List.of(), frame(), "slot 2 and beyond are empty");
    }

    @Test
    void deleteStopsARunningProgram() {
        // Clarification 4 (S:8548-8573): Delete stops a running program and leaves write mode, even though it only
        // removes a slot if the cursor is past 0.
        start(TestTowers.tower().level(0, "FFF").object(1, 0, 0, ObjectType.ZAP));
        writeProgram(Z, LEFT, FORWARD);

        frame(new Command.Run(Z));
        idle(1);
        assertEquals(ProgramState.RUNNING, controller.program(Z).state());
        assertTrue(zap().isMoving(), "the FORWARD step keeps the program at cursor 1 for a long time");

        idle(6);
        assertTrue(zap().isMoving(), "still walking: the program throttle has not cleared yet");

        frame(new Command.Delete(Z));
        assertEquals(ProgramState.IDLE, controller.program(Z).state(),
                "Delete stops a running program and leaves write mode (S:8548-8573)");
        assertTrue(zap().isMoving(), "the ongoing walk itself is not aborted, only the program");

        idle(5);
        assertEquals(ProgramState.IDLE, controller.program(Z).state(), "stays stopped: no further step runs");
    }

    @Test
    void clearStopsARunningProgramAtOnceIgnoringTheThrottle() {
        start(TestTowers.tower().level(0, "FFF").object(1, 0, 0, ObjectType.ZAP));
        writeProgram(Z, LEFT, FORWARD);

        frame(new Command.Run(Z));
        idle(1);
        assertEquals(ProgramState.RUNNING, controller.program(Z).state());
        assertTrue(zap().isMoving());

        frame(new Command.Clear(Z));
        assertEquals(ProgramState.IDLE, controller.program(Z).state(),
                "Clear stops a running program at once, ignoring the program throttle (S:8574-8581)");
        assertEquals(List.of(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), controller.program(Z).steps());
    }

    @Test
    void runAllStartsEveryProgramInOneFrame() {
        start(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.PSH));
        writeProgram(Z, RIGHT);
        writeProgram(P, LEFT);

        assertEquals(List.of(new SimEvent.ReadyEnded(0), new SimEvent.Turned(0, 0, Direction.EAST),
                new SimEvent.Turned(0, 1, Direction.WEST)), frame(new Command.RunAll()));
        for (SpiderId id : SpiderId.values()) {
            assertEquals(ProgramState.RUNNING, controller.program(id).state(), id.toString());
        }
    }

    /**
     * {@code S:8528}: Run All also sets the state of the <em>selected</em> entry, so a writing camera leaves write
     * mode. A later selection away from it is then a normal selection, not the camera-to-spider copy.
     */
    @Test
    void runAllEndsASelectedCamerasWriteModeSoALaterSelectionIsPlain() {
        start(TestTowers.tower().level(0, "FF").object(0, 0, 0, ObjectType.ZAP).object(1, 0, 0, ObjectType.PSH)
                .cameras(true));
        frame(new Command.SelectCamera(CameraId.EAST));
        frame(new Command.CameraEnterWriteMode(CameraId.EAST));
        frame(new Command.CameraWrite(CameraId.EAST, FORWARD));
        idle(3);

        assertTrue(frame(new Command.RunAll()).contains(new SimEvent.ReadyEnded(0)));
        assertEquals(ProgramState.IDLE, controller.cameraProgram(CameraId.EAST).state(),
                "Run All ends the selected camera's write mode (S:8528)");

        idle(3);
        assertEquals(List.of(), frame(new Command.SelectSpider(P)), "a plain selection: no copy, no Turned event");
        assertEquals(P, controller.selected());
        assertEquals(Direction.NORTH, world.spider(P).facing(), "heading unchanged");
        assertEquals(List.of(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), controller.program(P).steps(),
                "program unchanged");
        assertEquals(List.of(FORWARD, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY),
                controller.cameraProgram(CameraId.EAST).steps(), "camera program not cleared");
    }
}
